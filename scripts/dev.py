"""One foreground supervisor. Owns and terminates only processes it starts."""
import json
import os
from pathlib import Path
import shutil
import signal
import socket
import subprocess
import sys
import time
import urllib.request
ROOT=Path(__file__).resolve().parents[1]
os.chdir(ROOT)
RUNTIME=ROOT/'.runtime';RUNTIME.mkdir(exist_ok=True)
for port in (5173,8000,8081):
    with socket.socket() as probe:
        probe.settimeout(.2)
        if probe.connect_ex(('127.0.0.1',port))==0:
            raise SystemExit(f'Порт {port} занят. Остановите предыдущий экземпляр Mirutex перед запуском.')
env=os.environ.copy()
if sys.platform=='darwin' and not env.get('JAVA_HOME'):
    env['JAVA_HOME']=subprocess.check_output(['/usr/libexec/java_home','-v','21'],text=True).strip()
java=str(Path(env['JAVA_HOME'])/'bin/java') if env.get('JAVA_HOME') else 'java'
def run(args):subprocess.run(args,check=True,env=env)
if not shutil.which('docker'):raise SystemExit('Нужен Docker CLI и запущенная служба Docker (Docker Desktop / Colima).')
run(['docker','info','--format','{{.ServerVersion}}'])
image=env.get('JAVA_IMAGE','eclipse-temurin:21-jdk')
if subprocess.run(['docker','image','inspect',image],stdout=subprocess.DEVNULL,stderr=subprocess.DEVNULL).returncode:
    run(['docker','pull',image])
if '--no-build' not in sys.argv:
    run(['./mvnw','-B','-ntp','package'])
    if not (ROOT/'.venv').exists():run([sys.executable,'-m','venv','.venv'])
    run([str(ROOT/'.venv/bin/pip'),'install','-r','backend/requirements.lock'])
    run(['npm','--prefix','frontend','ci'])
    run(['npm','--prefix','frontend','run','build'])
children=[];logs=[]
def stop(*_):
    for p in reversed(children):
        if p.poll() is None:
            try:os.killpg(p.pid,signal.SIGTERM)
            except ProcessLookupError:pass
    for p in children:
        try:p.wait(timeout=8)
        except subprocess.TimeoutExpired:os.killpg(p.pid,signal.SIGKILL)
    for f in logs:f.close()
def spawn(args,name):
    log=(RUNTIME/(name+'.log')).open('w');logs.append(log)
    p=subprocess.Popen(args,env=env,stdout=log,stderr=subprocess.STDOUT,start_new_session=True);children.append(p)
def wait_url(url):
    for _ in range(90):
        if any(p.poll() is not None for p in children):raise RuntimeError('Сервис завершился. Проверьте .runtime/*.log и занятость портов.')
        try:
            with urllib.request.urlopen(url,timeout=2) as r:
                if r.status==200:return
        except (OSError,TimeoutError):pass
        time.sleep(.5)
    raise RuntimeError('Сервис не ответил: '+url)
signal.signal(signal.SIGTERM,lambda *_:sys.exit(0))
try:
    shutil.copy2(ROOT/'analyzer/target/analyzer-1.0.0.jar',RUNTIME/'engine.jar')
    spawn([java,'-Xmx384m','-jar',str(RUNTIME/'engine.jar')],'engine')
    wait_url('http://127.0.0.1:8081/engine/health')
    spawn([str(ROOT/'.venv/bin/uvicorn'),'app:app','--app-dir','backend','--host','127.0.0.1','--port','8000'],'api')
    wait_url('http://127.0.0.1:8000/health')
    spawn(['npm','--prefix','frontend','run','dev','--','--host','127.0.0.1','--port','5173','--strictPort'],'frontend')
    wait_url('http://127.0.0.1:5173')
    print('\nMirutex: http://127.0.0.1:5173\nAPI: http://127.0.0.1:8000/docs\nCtrl+C — остановить все сервисы.',flush=True)
    while all(p.poll() is None for p in children):time.sleep(1)
except KeyboardInterrupt:pass
finally:stop()
