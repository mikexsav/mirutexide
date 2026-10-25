"""Mirutex API. Java source is only executed in a disposable, limited Docker container."""
import asyncio
import io
import os
import re
import tarfile
import uuid
import zipfile
from typing import Any

import httpx
from fastapi import FastAPI, File, HTTPException, UploadFile, Request
from fastapi.responses import Response, JSONResponse
from pydantic import BaseModel, Field
from fastapi.exceptions import RequestValidationError
import accounts

app = FastAPI(title='Mirutex IDE', version='2.0.0')
app.include_router(accounts.router)
EXECUTION_PATHS = {'/api/compile', '/api/execute', '/api/graph/edit', '/api/refactor', '/api/tests/compare'}
active_runner_users: set[str] = set()


@app.exception_handler(RequestValidationError)
async def validation_error(request: Request, exc: RequestValidationError):
    # Do not echo submitted passwords or source text in validation errors.
    return JSONResponse({'detail': '\n'.join(str(e['msg']).removeprefix('Value error, ') for e in exc.errors()[:8])}, status_code=422)


@app.middleware('http')
async def account_boundary(request: Request, call_next):
    user_id = None
    try:
        if request.method not in {'GET', 'HEAD', 'OPTIONS'} and request.url.path.startswith('/api/'):
            accounts.check_origin(request)
        guarded = request.url.path in EXECUTION_PATHS or (request.url.path == '/api/graph/generate' and bool(request.cookies.get(accounts.COOKIE)))
        if guarded and os.getenv('MIRUTEX_REQUIRE_AUTH') == 'true':
            user_id = accounts.require_user(request, mutation=True)['id']
            if user_id in active_runner_users or len(active_runner_users) >= 6:
                user_id = None
                raise HTTPException(429, 'Запуск уже выполняется. Дождитесь результата и повторите действие.', headers={'Retry-After': '10'})
            active_runner_users.add(user_id)
        response = await call_next(request)
        if request.url.path.startswith(('/api/auth/', '/api/workspaces', '/api/shares/')):
            response.headers['Cache-Control'] = 'no-store'
        return response
    except HTTPException as exc:
        return JSONResponse({'detail': exc.detail}, status_code=exc.status_code, headers=exc.headers)
    finally:
        if user_id:
            active_runner_users.discard(user_id)
MAX_SOURCE = 256_000
JAVA_IMAGE = os.getenv('JAVA_IMAGE', 'eclipse-temurin:21-jdk')
ENGINE_URL = os.getenv('ENGINE_URL', 'http://127.0.0.1:8081')
TIMEOUT = float(os.getenv('EXECUTION_TIMEOUT', '15'))
LIMIT = 100_000
slots = asyncio.Semaphore(2)
IDENT = r'(?:[^\W\d]|[$_])[\w$]*'

class SourceRequest(BaseModel):
    source: str = Field(min_length=1, max_length=MAX_SOURCE)
    stdin: str = Field(default='', max_length=20_000)
    files: dict[str, str] = Field(default_factory=dict, max_length=20)

class CompareRequest(BaseModel):
    original: str = Field(min_length=1, max_length=MAX_SOURCE)
    modified: str = Field(min_length=1, max_length=MAX_SOURCE)
    modified_files: dict[str, str] = Field(default_factory=dict, max_length=20)
    original_files: dict[str, str] = Field(default_factory=dict, max_length=20)
    inputs: list[str] = Field(default_factory=lambda: [''], min_length=1, max_length=10)

class RefactorRequest(SourceRequest):
    inputs: list[str] = Field(default_factory=lambda: [''], min_length=1, max_length=10)

async def engine(operation: str, data: dict) -> dict:
    try:
        async with httpx.AsyncClient(timeout=45) as client:
            r = await client.post(f'{ENGINE_URL}/engine/{operation}', json=data)
        if r.is_error:
            try:
                detail = r.json().get('detail', r.json().get('message', r.text[:4000]))
            except ValueError:
                detail = r.text[:4000]
            raise HTTPException(r.status_code, detail)
        return r.json()
    except httpx.RequestError as exc:
        raise HTTPException(503, f'Java Engine недоступен: {type(exc).__name__}') from exc

@app.get('/health')
async def health():
    components = {'api': True, 'engine': False, 'docker': False, 'java_image': False, 'storage': False}
    try:
        with accounts.store().connect() as db:
            components['storage'] = db.execute('SELECT 1').fetchone()[0] == 1
    except (OSError, accounts.sqlite3.Error):
        pass
    try:
        async with httpx.AsyncClient(timeout=2) as client:
            components['engine'] = (await client.get(f'{ENGINE_URL}/engine/health')).is_success
        p = await asyncio.create_subprocess_exec('docker', 'image', 'inspect', JAVA_IMAGE,
                                                 stdout=asyncio.subprocess.DEVNULL, stderr=asyncio.subprocess.DEVNULL)
        await asyncio.wait_for(p.wait(), 3)
        components['docker'] = components['java_image'] = p.returncode == 0
    except (OSError, asyncio.TimeoutError, httpx.RequestError):
        pass
    return {'status': 'ok' if all(components.values()) else 'degraded', 'components': components}

@app.post('/api/files/upload')
async def upload(file: UploadFile = File(...)):
    if not file.filename or not file.filename.endswith('.java'):
        raise HTTPException(400, 'Выберите .java файл')
    data = await file.read(MAX_SOURCE + 1)
    if len(data) > MAX_SOURCE:
        raise HTTPException(413, 'Файл превышает 256 KB')
    try:
        source = data.decode('utf-8-sig')
    except UnicodeDecodeError:
        raise HTTPException(400, 'Нужна кодировка UTF-8')
    graph = await engine('parse', {'source': source})
    return {'filename': graph['filename'], 'source': source, 'graph': graph, 'bytes': len(data)}

@app.post('/api/graph/parse')
async def parse(req: SourceRequest):
    return await engine('parse', {'source': req.source})

@app.post('/api/graph/generate')
async def generate(req: dict[str, Any], request: Request):
    result = await engine('generate', req)
    if result['source'] != req.get('source'):
        accounts.require_execution(request)
        check = await docker_run(result['source'], compile_only=True)
        if not check['compiled'] or check['exit_code'] != 0 or check['timed_out'] or check['output_limited']:
            raise HTTPException(422, 'Генерация не прошла компиляцию: ' + check['stderr'])
    return result

@app.post('/api/graph/edit')
async def edit(req: dict[str, Any], request: Request):
    accounts.require_execution(request)
    result = await engine('edit', {k: v for k, v in req.items() if k != 'files'})
    check = await docker_run(result['source'], files=req.get('files', {}), compile_only=True)
    if not check['compiled'] or check['exit_code'] != 0 or check['timed_out'] or check['output_limited']:
        raise HTTPException(422, 'Изменение отклонено компилятором. Исходный код сохранён. ' + check['stderr'])
    return result

@app.post('/api/analysis')
async def analyze(req: SourceRequest):
    return await engine('analyze', {'source': req.source})

async def project(source: str, files: dict[str, str]):
    meta = await engine('parse', {'source': source})
    result = dict(files)
    result[meta['filename']] = source
    if len(result) > 20 or sum(len(s.encode('utf-8')) for s in result.values()) > MAX_SOURCE * 2:
        raise HTTPException(422, 'Проект слишком большой (до 20 файлов / 512 KB)')
    for name in result:
        if not re.fullmatch(IDENT + r'\.java', name):
            raise HTTPException(422, 'Имена файлов должны быть Java-идентификаторами без путей')
    if not re.fullmatch(IDENT + r'(?:\.' + IDENT + r')*', meta['mainClass']):
        raise HTTPException(422, 'Некорректное имя main-класса')
    return meta, result

async def docker_run(source: str, stdin: str = '', files: dict[str, str] | None = None, compile_only=False):
    meta, sources = await project(source, files or {})
    payload = io.BytesIO()
    with tarfile.open(fileobj=payload, mode='w') as archive:
        for name, text in {**sources, 'stdin.txt': stdin, 'sources.txt': '\n'.join(sources)}.items():
            data = text.encode('utf-8'); info = tarfile.TarInfo(name); info.size = len(data); info.mode = 0o600
            archive.addfile(info, io.BytesIO(data))
    name = 'mirutex-' + uuid.uuid4().hex
    marker = b'\x1eMIRUTEX_COMPILED\x1f\n'
    script = ('tar -xf -; javac -J-Xmx128m -J-XX:ActiveProcessorCount=1 -proc:none --release 21 '
              '-encoding UTF-8 -d /work @sources.txt || exit $?; '
              "printf '\\036MIRUTEX_COMPILED\\037\\n'; "
              + (':' if compile_only else 'exec java -Xmx96m -Xss512k -XX:ActiveProcessorCount=1 -Dfile.encoding=UTF-8 -cp /work "$1" < stdin.txt'))
    command = ['docker', 'run', '--pull', 'never', '--rm', '-i', '--name', name, '--network', 'none', '--cpus', '1',
               '--memory', '384m', '--memory-swap', '384m', '--pids-limit', '128', '--read-only',
               '--cap-drop', 'ALL', '--security-opt', 'no-new-privileges', '--user', '65534:65534',
               '--ulimit', 'fsize=10485760:10485760', '--log-driver', 'none',
               '--tmpfs', '/work:rw,nosuid,size=64m,mode=1777', '--tmpfs', '/tmp:rw,noexec,nosuid,size=16m,mode=1777',
               '--workdir', '/work', JAVA_IMAGE, 'sh', '-c', script, 'runner', meta['mainClass']]
    async with slots:
        try:
            process = await asyncio.create_subprocess_exec(*command, stdin=asyncio.subprocess.PIPE,
                        stdout=asyncio.subprocess.PIPE, stderr=asyncio.subprocess.PIPE)
        except OSError as exc:
            raise HTTPException(503, 'Docker CLI недоступен') from exc
        out = bytearray(); err = bytearray(); overflow = asyncio.Event()
        async def read(stream, target):
            while chunk := await stream.read(8192):
                remaining = LIMIT - len(target)
                target.extend(chunk[:max(0, remaining)])
                if len(chunk) > remaining:
                    overflow.set()
        async def feed():
            try:
                process.stdin.write(payload.getvalue()); await process.stdin.drain(); process.stdin.close()
            except (BrokenPipeError, ConnectionResetError):
                pass
        tasks = [asyncio.create_task(read(process.stdout, out)), asyncio.create_task(read(process.stderr, err)), asyncio.create_task(feed())]
        done_task = asyncio.create_task(process.wait()); over_task = asyncio.create_task(overflow.wait())
        timed_out = False
        try:
            done, _ = await asyncio.wait([done_task, over_task], timeout=TIMEOUT, return_when=asyncio.FIRST_COMPLETED)
            timed_out = not done
        finally:
            if process.returncode is None or overflow.is_set() or timed_out:
                cleanup = await asyncio.create_subprocess_exec('docker', 'rm', '-f', name,
                                stdout=asyncio.subprocess.DEVNULL, stderr=asyncio.subprocess.DEVNULL)
                try:
                    await asyncio.wait_for(cleanup.wait(), 5)
                except asyncio.TimeoutError:
                    cleanup.kill(); await cleanup.wait()
                if process.returncode is None:
                    process.kill()
            await process.wait(); await asyncio.gather(*tasks, return_exceptions=True)
            over_task.cancel(); await asyncio.gather(over_task, done_task, return_exceptions=True)
        compiled = bytes(out).startswith(marker)
        stdout = bytes(out)[len(marker):] if compiled else bytes(out)
        if not compiled and (b'docker:' in err or b'Cannot connect' in err or process.returncode == 125):
            raise HTTPException(503, 'Docker runner недоступен: ' + err.decode(errors='replace')[:1500])
        return {'compiled': compiled, 'exit_code': process.returncode,
                'stdout': stdout.decode('utf-8', errors='replace'), 'stderr': err.decode('utf-8', errors='replace'),
                'timed_out': timed_out, 'output_limited': overflow.is_set(),
                'phase': 'compile' if not compiled else 'compile_only' if compile_only else 'execution'}

@app.post('/api/compile')
async def compile_source(req: SourceRequest, request: Request):
    accounts.require_execution(request)
    return await docker_run(req.source, files=req.files, compile_only=True)

@app.post('/api/execute')
async def execute(req: SourceRequest, request: Request):
    accounts.require_execution(request)
    return await docker_run(req.source, req.stdin, req.files)

async def comparison(req: CompareRequest):
    results = []
    for stdin in req.inputs:
        if len(stdin) > 20_000:
            raise HTTPException(422, 'Тестовый ввод превышает 20000 символов')
        original = await docker_run(req.original, stdin, req.original_files)
        modified = await docker_run(req.modified, stdin, req.modified_files)
        valid = all(r['compiled'] and not r['timed_out'] and not r['output_limited'] for r in [original, modified])
        same = valid and all(original[k] == modified[k] for k in ['exit_code', 'stdout', 'stderr'])
        results.append({'stdin': stdin, 'passed': same, 'original': original, 'modified': modified})
    return {'behavior_preserved_for_tests': all(r['passed'] for r in results), 'results': results,
            'note': 'Сравнение stdout, stderr и exit code на заданных входах. Это не формальное доказательство эквивалентности.'}

@app.post('/api/tests/compare')
async def compare(req: CompareRequest, request: Request):
    accounts.require_execution(request, len(req.inputs) * 2)
    return await comparison(req)

@app.post('/api/refactor')
async def refactor(req: RefactorRequest, request: Request):
    accounts.require_execution(request, len(req.inputs) * 2)
    if req.files:
        raise HTTPException(422, 'Повторное выделение из многофайлового проекта пока не поддерживается. Сохраните или откатите результат.')
    preview = await engine('refactor', {'source': req.source})
    preview['accepted'] = False
    if not preview['extracted']:
        preview['validation'] = None
        return preview
    validation = await comparison(CompareRequest(original=req.source, modified=preview['source'],
                                                 modified_files=preview['files'], inputs=req.inputs))
    preview['validation'] = validation
    preview['accepted'] = validation['behavior_preserved_for_tests'] and all(
        r['original']['exit_code'] == r['modified']['exit_code'] == 0 for r in validation['results'])
    preview['note'] = ('Компиляция всех файлов и сравнение поведения на заданных входах пройдены. Можно применить изменения.'
                       if preview['accepted'] else 'Предпросмотр отклонён: компиляция или тесты не пройдены. Исходный код не изменён.')
    return preview

@app.post('/api/files/download')
async def download(req: SourceRequest):
    meta, files = await project(req.source, req.files)
    if len(files) == 1:
        return Response(req.source, media_type='text/plain; charset=utf-8',
                        headers={'Content-Disposition': 'attachment; filename="result.java"'})
    data = io.BytesIO()
    with zipfile.ZipFile(data, 'w', zipfile.ZIP_DEFLATED) as archive:
        for name, source in files.items():
            archive.writestr(name, source)
    return Response(data.getvalue(), media_type='application/zip',
                    headers={'Content-Disposition': 'attachment; filename="mirutex-project.zip"'})
