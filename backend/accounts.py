"""Persistent accounts and private workspaces. No credentials in browser storage."""
from contextlib import contextmanager
from functools import lru_cache
from pathlib import Path
import hashlib
import hmac
import json
import os
import re
import secrets
import sqlite3
import threading
import time
import uuid

from fastapi import APIRouter, HTTPException, Request, Response
from pydantic import BaseModel, ConfigDict, Field, field_validator, model_validator

router = APIRouter(prefix='/api')
COOKIE = 'mirutex_session'
SESSION_TTL = 7 * 86400
hash_slot = threading.BoundedSemaphore(1)


class StrictModel(BaseModel):
    model_config = ConfigDict(extra='forbid')


class Credentials(StrictModel):
    username: str = Field(min_length=3, max_length=32)
    password: str = Field(min_length=1, max_length=128)

    @field_validator('username')
    @classmethod
    def username_valid(cls, value):
        value = value.strip().lower()
        if not re.fullmatch(r'[a-z0-9][a-z0-9_.-]{2,31}', value):
            raise ValueError('Логин: 3–32 латинские буквы, цифры, точка, дефис или _')
        return value


class Registration(Credentials):
    display_name: str = Field(min_length=1, max_length=60)

    @model_validator(mode='after')
    def registration_valid(self):
        self.display_name = self.display_name.strip()
        if not self.display_name or self.username in {'user', 'admin', 'root'}:
            raise ValueError('Выберите своё имя и логин. user зарезервирован для демо.')
        if len(self.password) < 10:
            raise ValueError('Пароль должен содержать не менее 10 символов')
        return self


JAVA_FILE = r'(?:[^\W\d]|[$_])[\w$]*\.java'


class ProjectData(StrictModel):
    source: str = Field(max_length=256_000)
    files: dict[str, str] = Field(default_factory=dict, max_length=20)
    filename: str = Field(min_length=6, max_length=200)
    label: str = Field(default='', max_length=160)

    @model_validator(mode='after')
    def files_valid(self):
        if not re.fullmatch(JAVA_FILE, self.filename):
            raise ValueError('Нужно имя Java-файла без пути')
        if any(not re.fullmatch(JAVA_FILE, name) for name in self.files):
            raise ValueError('Имена файлов не должны содержать пути')
        # A stale duplicate of the editable main file must never leak into a shared snapshot.
        self.files = {name: value for name, value in self.files.items() if name != self.filename}
        merged = {**self.files, self.filename: self.source}
        if len(merged) > 20 or sum(len(v.encode('utf-8')) for v in merged.values()) > 512_000:
            raise ValueError('В проекте допускается до 20 файлов и 512 KB исходников')
        return self


class Snapshot(ProjectData):
    baseline: ProjectData | None = None


class WorkspaceState(StrictModel):
    project: ProjectData
    baseline: ProjectData
    history: list[Snapshot] = Field(default_factory=list, max_length=20)
    inputs: str = Field(default='5\n---\n0\n---\n3', max_length=200_000)

    @model_validator(mode='after')
    def size_valid(self):
        if len(self.model_dump_json().encode()) > 2_000_000:
            raise ValueError('Пространство превышает 2 MB. Удалите старые снимки или экспортируйте проект.')
        return self


class WorkspaceCreate(StrictModel):
    name: str = Field(min_length=1, max_length=80)
    description: str = Field(default='', max_length=500)
    state: WorkspaceState

    @field_validator('name')
    @classmethod
    def name_valid(cls, value):
        if not value.strip():
            raise ValueError('Введите название пространства')
        return value.strip()


class WorkspaceUpdate(StrictModel):
    version: int = Field(ge=1)
    state: WorkspaceState | None = None
    name: str | None = Field(default=None, min_length=1, max_length=80)
    description: str | None = Field(default=None, max_length=500)
    archived: bool | None = None

    @field_validator('name')
    @classmethod
    def name_valid(cls, value):
        if value is not None and not value.strip():
            raise ValueError('Введите название пространства')
        return value.strip() if value is not None else value


class VersionRequest(StrictModel):
    version: int = Field(ge=1)


def digest(token):
    return hashlib.sha256(token.encode()).hexdigest()


def password_hash(password, salt=None):
    salt = salt or secrets.token_hex(16)
    if not hash_slot.acquire(timeout=2):
        raise HTTPException(429, 'Сервис входа занят. Повторите через несколько секунд.', headers={'Retry-After': '3'})
    try:
        key = hashlib.scrypt(password.encode(), salt=bytes.fromhex(salt), n=2**17, r=8, p=1,
                             maxmem=256 * 1024 * 1024, dklen=32)
    finally:
        hash_slot.release()
    return f'scrypt$17$8$1${salt}${key.hex()}'


def password_matches(password, stored):
    # Equal-cost verification also applies to unknown users.
    if stored is None:
        password_hash(password, '00' * 16)
        return False
    try:
        return hmac.compare_digest(password_hash(password, stored.split('$')[4]), stored)
    except (ValueError, IndexError):
        return False


class Store:
    def __init__(self, path):
        self.path = Path(path)
        self.path.parent.mkdir(parents=True, exist_ok=True)
        fd = os.open(self.path, os.O_CREAT | os.O_APPEND | os.O_WRONLY, 0o600)
        os.close(fd)
        os.chmod(self.path, 0o600)
        with self.connect() as db:
            db.executescript('''
                PRAGMA journal_mode=WAL;
                CREATE TABLE IF NOT EXISTS users(
                  id TEXT PRIMARY KEY, username TEXT NOT NULL UNIQUE, display_name TEXT NOT NULL,
                  password_hash TEXT NOT NULL, created_at INTEGER NOT NULL);
                CREATE TABLE IF NOT EXISTS sessions(
                  token_hash TEXT PRIMARY KEY, user_id TEXT NOT NULL REFERENCES users(id) ON DELETE CASCADE,
                  csrf TEXT NOT NULL, expires_at INTEGER NOT NULL);
                CREATE INDEX IF NOT EXISTS session_expiry ON sessions(expires_at);
                CREATE TABLE IF NOT EXISTS workspaces(
                  id TEXT PRIMARY KEY, owner_id TEXT NOT NULL REFERENCES users(id) ON DELETE CASCADE,
                  name TEXT NOT NULL, description TEXT NOT NULL, state TEXT NOT NULL,
                  version INTEGER NOT NULL DEFAULT 1, archived INTEGER NOT NULL DEFAULT 0,
                  created_at INTEGER NOT NULL, updated_at INTEGER NOT NULL);
                CREATE INDEX IF NOT EXISTS workspace_owner ON workspaces(owner_id,updated_at);
                CREATE TABLE IF NOT EXISTS shares(
                  id TEXT PRIMARY KEY, token_hash TEXT NOT NULL UNIQUE,
                  workspace_id TEXT NOT NULL REFERENCES workspaces(id) ON DELETE CASCADE,
                  name TEXT NOT NULL, state TEXT NOT NULL, version INTEGER NOT NULL,
                  created_at INTEGER NOT NULL, expires_at INTEGER NOT NULL, revoked INTEGER NOT NULL DEFAULT 0);
                CREATE TABLE IF NOT EXISTS rate_limits(
                  key TEXT PRIMARY KEY, count INTEGER NOT NULL, reset_at INTEGER NOT NULL);
            ''')
        os.chmod(self.path, 0o600)

    @contextmanager
    def connect(self, write=False):
        db = sqlite3.connect(self.path, timeout=10)
        db.row_factory = sqlite3.Row
        db.execute('PRAGMA foreign_keys=ON')
        try:
            if write:
                db.execute('BEGIN IMMEDIATE')
            yield db
            db.commit()
        except BaseException:
            db.rollback()
            raise
        finally:
            db.close()

    def rate(self, key, maximum, period, units=1):
        now = int(time.time())
        with self.connect(write=True) as db:
            db.execute('DELETE FROM rate_limits WHERE reset_at < ?', (now,))
            row = db.execute('SELECT * FROM rate_limits WHERE key=?', (key,)).fetchone()
            if row and row['count'] + units > maximum:
                raise HTTPException(429, 'Слишком много запросов. Попробуйте позже.',
                                    headers={'Retry-After': str(max(1, row['reset_at'] - now))})
            if row:
                db.execute('UPDATE rate_limits SET count=count+? WHERE key=?', (units, key))
            else:
                db.execute('INSERT INTO rate_limits VALUES(?,?,?)', (key, units, now + period))

    def session(self, request):
        token = request.cookies.get(COOKIE, '')
        if not token or len(token) > 150:
            return None
        with self.connect() as db:
            row = db.execute('''SELECT u.id,u.username,u.display_name,s.csrf,s.expires_at
                     FROM sessions s JOIN users u ON u.id=s.user_id
                     WHERE s.token_hash=? AND s.expires_at>?''', (digest(token), int(time.time()))).fetchone()
        return dict(row) if row else None

    def quota(self, db, owner, payload, exclude=''):
        used = db.execute('SELECT COALESCE(SUM(length(CAST(state AS BLOB))),0) FROM workspaces WHERE owner_id=? AND id!=?',
                          (owner, exclude)).fetchone()[0]
        used += db.execute('''SELECT COALESCE(SUM(length(CAST(s.state AS BLOB))),0)
                   FROM shares s JOIN workspaces w ON w.id=s.workspace_id
                   WHERE w.owner_id=? AND s.revoked=0 AND s.expires_at>?''', (owner, int(time.time()))).fetchone()[0]
        if used + len(payload.encode()) > 20_000_000:
            raise HTTPException(413, 'Хранилище аккаунта заполнено (20 MB). Экспортируйте и очистите историю проектов.')


@lru_cache(maxsize=4)
def store_at(path):
    return Store(path)


def store():
    return store_at(os.getenv('MIRUTEX_DB_PATH', str(Path(__file__).resolve().parents[1] / '.runtime' / 'mirutex.sqlite3')))


def current_user(request):
    return store().session(request)


def require_user(request, mutation=False):
    user = current_user(request)
    if not user:
        raise HTTPException(401, 'Войдите в аккаунт Mirutex IDE')
    if mutation:
        supplied = request.headers.get('x-mirutex-csrf', '')
        if not hmac.compare_digest(supplied, user['csrf']):
            raise HTTPException(403, 'Сессия изменилась. Обновите страницу и повторите действие.')
    return user


def require_execution(request, units=1):
    if os.getenv('MIRUTEX_REQUIRE_AUTH', 'false').lower() != 'true':
        return
    user = require_user(request, mutation=True)
    store().rate('runner:' + user['id'], int(os.getenv('MIRUTEX_RUNNER_QUOTA', '120')), 3600, units)


def check_origin(request):
    origin = request.headers.get('origin')
    allowed = os.getenv('MIRUTEX_PUBLIC_ORIGIN', '').rstrip('/')
    if not allowed:
        allowed = f'{request.url.scheme}://{request.headers.get("host", "")}'
    local = {'http://127.0.0.1:5173', 'http://localhost:5173'} if not os.getenv('MIRUTEX_PUBLIC_ORIGIN') else set()
    if request.headers.get('sec-fetch-site') == 'cross-site' or (origin and origin.rstrip('/') not in {allowed, *local}):
        raise HTTPException(403, 'Запрос должен исходить со страницы Mirutex IDE')


def auth_rate(request, kind):
    # X-Real-IP is overwritten by the trusted host proxy; never trust forwarded-for chains.
    address = request.headers.get('x-real-ip') if os.getenv('MIRUTEX_TRUST_PROXY') == 'true' else None
    address = address or (request.client.host if request.client else 'unknown')
    store().rate(f'{kind}:{address}', 5 if kind == 'register' else 15, 900)


def public_user(user):
    return {key: user[key] for key in ['id', 'username', 'display_name']}


def new_session(request, response, user):
    token, csrf, now = secrets.token_urlsafe(32), secrets.token_urlsafe(32), int(time.time())
    with store().connect(write=True) as db:
        db.execute('DELETE FROM sessions WHERE expires_at<=?', (now,))
        old = request.cookies.get(COOKIE)
        if old:
            db.execute('DELETE FROM sessions WHERE token_hash=?', (digest(old),))
        db.execute('INSERT INTO sessions VALUES(?,?,?,?)', (digest(token), user['id'], csrf, now + SESSION_TTL))
    response.set_cookie(COOKIE, token, max_age=SESSION_TTL, httponly=True,
                        secure=os.getenv('MIRUTEX_COOKIE_SECURE') == 'true', samesite='lax', path='/')
    response.headers['Cache-Control'] = 'no-store'
    return {'user': public_user(user), 'csrf_token': csrf}


@router.get('/auth/session')
def session_info(request: Request, response: Response):
    user = current_user(request)
    response.headers['Cache-Control'] = 'no-store'
    return {'user': public_user(user) if user else None, 'csrf_token': user['csrf'] if user else None,
            'execution_requires_account': os.getenv('MIRUTEX_REQUIRE_AUTH') == 'true'}


@router.post('/auth/register', status_code=201)
def register(req: Registration, request: Request, response: Response):
    check_origin(request)
    auth_rate(request, 'register')
    user = {'id': uuid.uuid4().hex, 'username': req.username, 'display_name': req.display_name}
    encoded = password_hash(req.password)
    try:
        with store().connect(write=True) as db:
            if db.execute('SELECT COUNT(*) FROM users').fetchone()[0] >= int(os.getenv('MIRUTEX_MAX_ACCOUNTS', '200')):
                raise HTTPException(503, 'Регистрация временно закрыта: достигнут лимит демонстрационного сервера.')
            db.execute('INSERT INTO users VALUES(?,?,?,?,?)', (*user.values(), encoded, int(time.time())))
    except sqlite3.IntegrityError:
        raise HTTPException(409, 'Этот логин уже занят')
    return new_session(request, response, user)


@router.post('/auth/login')
def login(req: Credentials, request: Request, response: Response):
    check_origin(request)
    auth_rate(request, 'login')
    with store().connect() as db:
        user = db.execute('SELECT * FROM users WHERE username=?', (req.username,)).fetchone()
    if not password_matches(req.password, user['password_hash'] if user else None):
        raise HTTPException(401, 'Неверный логин или пароль')
    return new_session(request, response, user)


@router.post('/auth/logout')
def logout(request: Request, response: Response):
    check_origin(request)
    require_user(request, mutation=True)
    with store().connect(write=True) as db:
        db.execute('DELETE FROM sessions WHERE token_hash=?', (digest(request.cookies[COOKIE]),))
    response.delete_cookie(COOKIE, path='/', httponly=True, samesite='lax', secure=os.getenv('MIRUTEX_COOKIE_SECURE') == 'true')
    return {'ok': True}


def owned(db, workspace_id, owner):
    row = db.execute('SELECT * FROM workspaces WHERE id=? AND owner_id=?', (workspace_id, owner)).fetchone()
    if not row:
        raise HTTPException(404, 'Пространство не найдено')
    return row


def workspace_json(row, full=False):
    data = {k: row[k] for k in ['id', 'name', 'description', 'version', 'created_at', 'updated_at']}
    data['archived'] = bool(row['archived'])
    state = json.loads(row['state'])
    p = state['project']
    data.update(filename=p['filename'], lines=len(p['source'].splitlines()),
                files=len(set(p['files']) | {p['filename']}), snapshots=len(state['history']))
    if full:
        data['state'] = state
    return data


@router.get('/workspaces')
def workspaces(request: Request, response: Response):
    user = require_user(request)
    response.headers['Cache-Control'] = 'no-store'
    with store().connect() as db:
        rows = db.execute('SELECT * FROM workspaces WHERE owner_id=? ORDER BY updated_at DESC', (user['id'],)).fetchall()
    return {'workspaces': [workspace_json(row) for row in rows]}


@router.post('/workspaces', status_code=201)
def create_workspace(req: WorkspaceCreate, request: Request):
    check_origin(request)
    user = require_user(request, mutation=True)
    now, wid, payload = int(time.time()), uuid.uuid4().hex, req.state.model_dump_json()
    with store().connect(write=True) as db:
        if db.execute('SELECT COUNT(*) FROM workspaces WHERE owner_id=?', (user['id'],)).fetchone()[0] >= 30:
            raise HTTPException(413, 'В аккаунте допускается до 30 пространств, включая архив')
        store().quota(db, user['id'], payload)
        db.execute('INSERT INTO workspaces VALUES(?,?,?,?,?,1,0,?,?)', (wid, user['id'], req.name, req.description, payload, now, now))
        return workspace_json(owned(db, wid, user['id']), full=True)


@router.get('/workspaces/{wid}')
def read_workspace(wid: str, request: Request, response: Response):
    user = require_user(request)
    response.headers['Cache-Control'] = 'no-store'
    with store().connect() as db:
        return workspace_json(owned(db, wid, user['id']), full=True)


@router.put('/workspaces/{wid}')
def update_workspace(wid: str, req: WorkspaceUpdate, request: Request):
    check_origin(request)
    user = require_user(request, mutation=True)
    with store().connect(write=True) as db:
        old = owned(db, wid, user['id'])
        if old['version'] != req.version:
            raise HTTPException(409, 'На сервере уже есть новая версия. Откройте её или сохраните свою копией.')
        payload = req.state.model_dump_json() if req.state else old['state']
        store().quota(db, user['id'], payload, exclude=wid)
        values = (req.name if req.name is not None else old['name'],
                  req.description if req.description is not None else old['description'], payload,
                  int(req.archived) if req.archived is not None else old['archived'])
        changed = values != (old['name'], old['description'], old['state'], old['archived'])
        if changed:
            db.execute('UPDATE workspaces SET name=?,description=?,state=?,archived=?,version=version+1,updated_at=? WHERE id=?',
                       (*values, int(time.time()), wid))
        return workspace_json(owned(db, wid, user['id']), full=True)


@router.post('/workspaces/{wid}/shares', status_code=201)
def create_share(wid: str, req: VersionRequest, request: Request):
    check_origin(request)
    user = require_user(request, mutation=True)
    now, token, sid = int(time.time()), secrets.token_urlsafe(32), uuid.uuid4().hex
    with store().connect(write=True) as db:
        row = owned(db, wid, user['id'])
        if row['version'] != req.version:
            raise HTTPException(409, 'Пространство изменилось. Сохраните актуальную версию перед публикацией.')
        if db.execute('SELECT COUNT(*) FROM shares WHERE workspace_id=? AND revoked=0 AND expires_at>?', (wid, now)).fetchone()[0] >= 10:
            raise HTTPException(413, 'Отзовите старые ссылки: допускается 10 действующих ссылок на пространство')
        project = json.loads(row['state'])['project']
        # Deliberately publish only the current source, never private baselines/history/stdin.
        payload = json.dumps({'project': project, 'baseline': project, 'history': [], 'inputs': ''}, ensure_ascii=False)
        db.execute("UPDATE shares SET state='{}' WHERE expires_at<=? OR revoked=1", (now,))
        store().quota(db, user['id'], payload)
        db.execute('INSERT INTO shares VALUES(?,?,?,?,?,?,?,?,0)',
                   (sid, digest(token), wid, row['name'], payload, row['version'], now, now + SESSION_TTL))
    return {'id': sid, 'token': token, 'expires_at': now + SESSION_TTL, 'version': req.version}


@router.get('/workspaces/{wid}/shares')
def list_shares(wid: str, request: Request, response: Response):
    user = require_user(request)
    response.headers['Cache-Control'] = 'no-store'
    with store().connect() as db:
        owned(db, wid, user['id'])
        rows = db.execute('SELECT id,version,created_at,expires_at,revoked FROM shares WHERE workspace_id=? ORDER BY created_at DESC LIMIT 50', (wid,)).fetchall()
    return {'shares': [dict(r) for r in rows]}


@router.delete('/workspaces/{wid}/shares/{sid}')
def revoke_share(wid: str, sid: str, request: Request):
    check_origin(request)
    user = require_user(request, mutation=True)
    with store().connect(write=True) as db:
        owned(db, wid, user['id'])
        updated = db.execute("UPDATE shares SET revoked=1,state='{}' WHERE id=? AND workspace_id=?", (sid, wid)).rowcount
        if not updated:
            raise HTTPException(404, 'Ссылка не найдена')
    return {'ok': True}


@router.get('/shares/{token}')
def read_share(token: str, response: Response):
    if len(token) > 100:
        raise HTTPException(404, 'Ссылка недействительна или отозвана')
    response.headers['Cache-Control'] = 'no-store'
    with store().connect() as db:
        row = db.execute('SELECT * FROM shares WHERE token_hash=? AND revoked=0 AND expires_at>?', (digest(token), int(time.time()))).fetchone()
    if not row:
        raise HTTPException(404, 'Ссылка недействительна, истекла или отозвана')
    return {'name': row['name'], 'version': row['version'], 'expires_at': row['expires_at'], 'state': json.loads(row['state'])}
