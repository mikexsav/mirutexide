"""Real SQLite/session tests. The Java runner is independently tested over HTTP."""
import json
import time
from unittest.mock import AsyncMock, patch

import pytest
from fastapi.testclient import TestClient
from app import app
import accounts


@pytest.fixture(autouse=True)
def private_database(tmp_path, monkeypatch):
    monkeypatch.setenv('MIRUTEX_DB_PATH', str(tmp_path / 'accounts.sqlite3'))
    monkeypatch.setenv('MIRUTEX_REQUIRE_AUTH', 'true')
    monkeypatch.delenv('MIRUTEX_PUBLIC_ORIGIN', raising=False)
    monkeypatch.delenv('MIRUTEX_COOKIE_SECURE', raising=False)
    monkeypatch.delenv('MIRUTEX_TRUST_PROXY', raising=False)
    yield


def state():
    p = {'source': 'class Main {}', 'filename': 'Main.java', 'files': {}, 'label': 'Начало'}
    return {'project': p, 'baseline': p, 'history': [], 'inputs': '5\n---\n0'}


def register(client, name='mirutextest'):
    r = client.post('/api/auth/register', json={'username': name, 'password': 'Correct horse test 42!', 'display_name': 'Тест Мирутекс'})
    assert r.status_code == 201, r.text
    client.headers['X-Mirutex-CSRF'] = r.json()['csrf_token']
    return r.json()


def create(client):
    r = client.post('/api/workspaces', json={'name': 'Мой Java-проект', 'state': state()})
    assert r.status_code == 201, r.text
    return r.json()


def test_password_and_session_are_not_stored_verbatim():
    c = TestClient(app)
    auth = register(c)
    with accounts.store().connect() as db:
        user = db.execute('SELECT * FROM users').fetchone()
        session = db.execute('SELECT * FROM sessions').fetchone()
    assert user['password_hash'].startswith('scrypt$17$8$1$')
    assert 'Correct horse' not in user['password_hash']
    assert session['token_hash'] != c.cookies.get(accounts.COOKIE)
    assert len(session['token_hash']) == 64
    assert c.get('/api/auth/session').json()['user'] == auth['user']


def test_login_logout_and_cookie_flags():
    c = TestClient(app)
    register(c)
    token = c.cookies.get(accounts.COOKIE)
    assert c.post('/api/auth/logout').status_code == 200
    c.cookies.set(accounts.COOKIE, token)
    assert c.get('/api/auth/session').json()['user'] is None
    c.cookies.clear()
    assert c.post('/api/auth/login', json={'username': 'mirutextest', 'password': 'wrong'}).status_code == 401
    r = c.post('/api/auth/login', json={'username': 'MIRUTEXTEST', 'password': 'Correct horse test 42!'})
    assert r.status_code == 200
    assert 'httponly' in r.headers['set-cookie'].lower() and 'samesite=lax' in r.headers['set-cookie'].lower()
    assert token != c.cookies.get(accounts.COOKIE)


def test_secure_cookie_in_production(monkeypatch):
    monkeypatch.setenv('MIRUTEX_COOKIE_SECURE', 'true')
    c = TestClient(app, base_url='https://testserver')
    r = c.post('/api/auth/register', json={'username': 'secureuser', 'password': 'Correct horse test 42!', 'display_name': 'QA'})
    assert r.status_code == 201 and 'secure' in r.headers['set-cookie'].lower()
    assert c.get('/api/auth/session').json()['user']['username'] == 'secureuser'


@pytest.mark.parametrize('change', [{'username': 'user'}, {'username': '../root'}, {'password': 'short'}, {'display_name': '  '}])
def test_invalid_registration(change):
    data = {'username': 'validuser', 'password': 'Correct horse test 42!', 'display_name': 'QA'}
    assert TestClient(app).post('/api/auth/register', json={**data, **change}).status_code == 422


def test_csrf_and_origin_are_enforced():
    c = TestClient(app)
    register(c)
    c.headers.pop('X-Mirutex-CSRF')
    assert c.post('/api/workspaces', json={'name': 'X', 'state': state()}).status_code == 403
    r = c.post('/api/auth/login', json={'username': 'mirutextest', 'password': 'Correct horse test 42!'}, headers={'Origin': 'https://attacker.example'})
    assert r.status_code == 403
    assert c.post('/api/auth/login', json={'username': 'mirutextest', 'password': 'Correct horse test 42!'}, headers={'Sec-Fetch-Site': 'cross-site'}).status_code == 403


def test_workspace_persists_across_new_connections_and_owner_sessions():
    c = TestClient(app)
    register(c)
    w = create(c)
    c2 = TestClient(app)
    r = c2.post('/api/auth/login', json={'username': 'mirutextest', 'password': 'Correct horse test 42!'})
    assert r.status_code == 200
    data = c2.get('/api/workspaces/' + w['id']).json()
    assert data['state']['project']['source'] == 'class Main {}'
    assert c2.get('/api/workspaces').json()['workspaces'][0]['name'] == 'Мой Java-проект'
    assert c2.get('/api/workspaces').headers['cache-control'] == 'no-store'
    assert accounts.Store(accounts.store().path).path == accounts.store().path


@pytest.mark.parametrize('operation', ['read', 'update', 'list_shares', 'create_share', 'revoke_share'])
def test_other_account_cannot_access_workspace(operation):
    owner, stranger = TestClient(app), TestClient(app)
    register(owner)
    w = create(owner)
    register(stranger, 'stranger')
    path = '/api/workspaces/' + w['id']
    r = {
        'read': lambda: stranger.get(path),
        'update': lambda: stranger.put(path, json={'version': 1, 'name': 'stolen'}),
        'list_shares': lambda: stranger.get(path + '/shares'),
        'create_share': lambda: stranger.post(path + '/shares', json={'version': 1}),
        'revoke_share': lambda: stranger.delete(path + '/shares/no-such-id'),
    }[operation]()
    assert r.status_code == 404, r.text
    assert owner.get(path).json()['name'] == w['name']


def test_compare_and_swap_prevents_two_tab_overwrite_and_preserves_history():
    c = TestClient(app)
    register(c)
    w = create(c)
    new = state()
    new['history'] = [{**new['project'], 'label': 'До правки', 'baseline': new['baseline']}]
    new['project'] = {**new['project'], 'source': 'class Main { int x; }'}
    path = '/api/workspaces/' + w['id']
    r = c.put(path, json={'version': 1, 'state': new})
    assert r.status_code == 200 and r.json()['version'] == 2
    assert r.json()['snapshots'] == 1
    assert c.put(path, json={'version': 1, 'state': state()}).status_code == 409
    assert c.get(path).json()['state'] == r.json()['state']
    assert c.put(path, json={'version': 2, 'state': new}).json()['version'] == 2


def test_archive_restore_and_rename():
    c = TestClient(app)
    register(c)
    w = create(c)
    path = '/api/workspaces/' + w['id']
    archived = c.put(path, json={'version': 1, 'archived': True, 'name': 'Архив'}).json()
    assert archived['archived'] is True and archived['name'] == 'Архив'
    assert c.put(path, json={'version': 2, 'archived': False}).json()['archived'] is False


def test_empty_and_invalid_java_drafts_are_saved_without_losing_code():
    c = TestClient(app)
    register(c)
    w = create(c)
    path = '/api/workspaces/' + w['id']
    draft = state()
    for version, source in [(1, ''), (2, 'class Main { broken syntax')]:
        draft['project'] = {**draft['project'], 'source': source}
        r = c.put(path, json={'version': version, 'state': draft})
        assert r.status_code == 200, r.text
        assert c.get(path).json()['state']['project']['source'] == source


def test_readonly_share_is_frozen_and_excludes_private_history():
    c = TestClient(app)
    register(c)
    w = create(c)
    path = '/api/workspaces/' + w['id']
    r = c.post(path + '/shares', json={'version': 1})
    assert r.status_code == 201, r.text
    share = r.json()
    public = TestClient(app)
    link = '/api/shares/' + share['token']
    shared = public.get(link).json()
    assert shared['state']['history'] == [] and shared['state']['inputs'] == ''
    assert shared['state']['baseline'] == shared['state']['project']
    new = state()
    new['project'] = {**new['project'], 'source': 'class Main { int changed; }'}
    assert c.put(path, json={'version': 1, 'state': new}).status_code == 200
    assert public.get(link).json()['state']['project']['source'] == 'class Main {}'
    assert c.delete(path + '/shares/' + share['id']).status_code == 200
    assert public.get(link).status_code == 404


def test_expired_session_and_share_are_rejected():
    c = TestClient(app)
    register(c)
    w = create(c)
    share = c.post('/api/workspaces/' + w['id'] + '/shares', json={'version': 1}).json()
    with accounts.store().connect(write=True) as db:
        db.execute('UPDATE sessions SET expires_at=?', (int(time.time()) - 1,))
        db.execute('UPDATE shares SET expires_at=?', (int(time.time()) - 1,))
    assert c.get('/api/workspaces').status_code == 401
    assert c.get('/api/shares/' + share['token']).status_code == 404


def test_share_does_not_leak_stale_main_file_from_refactor_file_map():
    c = TestClient(app)
    register(c)
    data = state()
    data['project'] = {**data['project'], 'files': {'Main.java': 'private OLD source', 'Helper.java': 'class Helper {}'}}
    w = c.post('/api/workspaces', json={'name': 'Clean snapshot', 'state': data}).json()
    assert 'Main.java' not in w['state']['project']['files']
    share = c.post('/api/workspaces/' + w['id'] + '/shares', json={'version': 1}).json()
    published = TestClient(app).get('/api/shares/' + share['token'])
    assert 'private OLD source' not in published.text
    assert 'Helper.java' in published.json()['state']['project']['files']


@pytest.mark.parametrize('path', ['/api/compile', '/api/execute', '/api/refactor', '/api/tests/compare', '/api/graph/edit'])
def test_public_cannot_execute_java(path):
    assert TestClient(app).post(path, json={}).status_code == 401


def test_generate_cannot_bypass_execution_authorization():
    with patch('app.engine', new_callable=AsyncMock, return_value={'source': 'class Changed {}'}), patch('app.docker_run', new_callable=AsyncMock) as runner:
        assert TestClient(app).post('/api/graph/generate', json={'source': 'class Main {}'}).status_code == 401
        runner.assert_not_called()


def test_authenticated_runner_and_quota(monkeypatch):
    c = TestClient(app)
    register(c)
    monkeypatch.setenv('MIRUTEX_RUNNER_QUOTA', '1')
    with patch('app.docker_run', new_callable=AsyncMock, return_value={'compiled': True}):
        assert c.post('/api/compile', json={'source': 'class Main {}'}).status_code == 200
        assert c.post('/api/compile', json={'source': 'class Main {}'}).status_code == 429


def test_login_rate_limit_and_database_does_not_expose_secrets():
    c = TestClient(app)
    for _ in range(15):
        assert c.post('/api/auth/login', json={'username': 'missing', 'password': 'bad'}).status_code == 401
    r = c.post('/api/auth/login', json={'username': 'missing', 'password': 'bad'})
    assert r.status_code == 429 and int(r.headers['retry-after']) > 0


def test_workspace_rejects_path_traversal_and_oversized_state():
    c = TestClient(app)
    register(c)
    bad = state()
    bad['project']['filename'] = '../Escape.java'
    assert c.post('/api/workspaces', json={'name': 'X', 'state': bad}).status_code == 422
    huge = state()
    huge['project'] = {**huge['project'], 'source': 'x' * 256_001}
    assert c.post('/api/workspaces', json={'name': 'X', 'state': huge}).status_code == 422
