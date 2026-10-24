"""API unit tests; runner mocked here. Real Docker coverage lives in testing/test_integration.py."""
from unittest.mock import AsyncMock, patch
from fastapi.testclient import TestClient
from app import app
client = TestClient(app)
GRAPH = {'filename':'Main.java','source':'class Main {}','nodes':[], 'edges':[]}
def result(output='', code=0, compiled=True, timed_out=False, limited=False):
    return {'compiled':compiled,'exit_code':code,'stdout':output,'stderr':'','timed_out':timed_out,'output_limited':limited,'phase':'execution'}
def test_upload():
    with patch('app.engine', new_callable=AsyncMock, return_value=GRAPH):
        r=client.post('/api/files/upload',files={'file':('Main.java',b'class Main {}')})
    assert r.status_code==200 and r.json()['graph']['filename']=='Main.java'
def test_reject_non_java():
    assert client.post('/api/files/upload',files={'file':('bad.txt',b'hello')}).status_code==400
def test_reject_large_file():
    assert client.post('/api/files/upload',files={'file':('Main.java',b'x'*256001)}).status_code==413
def test_reject_non_utf8():
    assert client.post('/api/files/upload',files={'file':('Main.java',b'\xff')}).status_code==400
def compare(a,b,**extra):
    with patch('app.docker_run',new_callable=AsyncMock,side_effect=[a,b]):
        return client.post('/api/tests/compare',json={'original':'class Main {}','modified':'class Main {}','inputs':[''],**extra})
def test_compare_same():
    assert compare(result('30\n'),result('30\n')).json()['behavior_preserved_for_tests'] is True
def test_compare_different():
    assert compare(result('30\n'),result('50\n')).json()['behavior_preserved_for_tests'] is False
def test_compare_timeout_never_passes():
    assert compare(result(timed_out=True),result(timed_out=True)).json()['behavior_preserved_for_tests'] is False
def test_compile_failures_never_pass():
    assert compare(result(compiled=False,code=1),result(compiled=False,code=1)).json()['behavior_preserved_for_tests'] is False
def test_truncated_output_never_passes():
    assert compare(result(limited=True),result(limited=True)).json()['behavior_preserved_for_tests'] is False
def test_compare_requires_inputs():
    assert client.post('/api/tests/compare',json={'original':'a','modified':'b','inputs':[]}).status_code==422
def test_input_limit():
    assert client.post('/api/tests/compare',json={'original':'a','modified':'b','inputs':['a'*20001]}).status_code==422
def test_download_rejects_traversal():
    with patch('app.engine',new_callable=AsyncMock,return_value={'filename':'Main.java','mainClass':'Main'}):
        assert client.post('/api/files/download',json={'source':'class Main {}','files':{'../Escape.java':'class Escape{}'}}).status_code==422
def test_refactor_rejected_when_behavior_changes():
    preview={'source':'class Main {}','files':{},'extracted':['f']}
    with patch('app.engine',new_callable=AsyncMock,return_value=preview),patch('app.comparison',new_callable=AsyncMock,return_value={'behavior_preserved_for_tests':False,'results':[]}):
        r=client.post('/api/refactor',json={'source':'class Main {}'})
    assert r.json()['accepted'] is False
