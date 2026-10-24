"""Real HTTP + Java 21 Docker tests. Start the app before running. No runner mocks."""
import io
import os
import time
import zipfile
from pathlib import Path
import httpx
import pytest
BASE=os.getenv('MIRUTEX_URL','http://127.0.0.1:8000')
ROOT=Path(__file__).resolve().parents[1]
CLIENT=httpx.Client(base_url=BASE,timeout=180)
SIMPLE='public class Main {public static void main(String[] args){System.out.println("Привет");} private static int twice(int n){return n*2;}}'
def post(path, data):
    r=CLIENT.post(path,json=data)
    assert r.status_code==200,r.text[:3000]
    return r.json()
def test_health():
    r=CLIENT.get('/health');assert r.json()['status']=='ok',r.text

def test_roundtrip_edit_compile_and_behavior():
    g=post('/api/graph/parse',{'source':SIMPLE})
    assert post('/api/graph/generate',g)['source']==SIMPLE
    n=next(n for n in g['nodes'] if n['kind']=='call')
    edited=post('/api/graph/edit',{'graph':g,'operation':{'type':'replace','nodeId':n['id'],'code':'System.out.println("Привет" + "");'}})
    assert post('/api/compile',{'source':edited['source']})['compiled']
    result=post('/api/tests/compare',{'original':SIMPLE,'modified':edited['source'],'inputs':['','кириллица']})
    assert result['behavior_preserved_for_tests']

def test_real_divergence_detected():
    result=post('/api/tests/compare',{'original':SIMPLE,'modified':SIMPLE.replace('Привет','Другой результат')})
    assert not result['behavior_preserved_for_tests']

def test_analysis_links_real_nodes():
    source='class Main {void f(int n){for(int i=0;i<n;i++){for(int j=0;j<n;j++){System.out.println(i+j);}}}}'
    report=post('/api/analysis',{'source':source})
    assert any(i['type']=='NESTED_LOOP' and i['nodeId'] for i in report['issues'])
    assert any(i['complexity']=='O(n^2)' for i in report['issues'])

@pytest.mark.parametrize('name',['ScopeShowcase.java','MonolithDemo.java'])
def test_official_scope_large_monolith(name):
    source=(ROOT/'examples'/name).read_text()
    start=time.perf_counter();g=post('/api/graph/parse',{'source':source});elapsed=time.perf_counter()-start
    assert g['nodes'] and post('/api/graph/generate',g)['source']==source
    report=post('/api/analysis',{'source':source});assert report['methodsAnalyzed']>0
    result=post('/api/tests/compare',{'original':source,'modified':g['source'],'inputs':['5','0','-2']})
    assert result['behavior_preserved_for_tests'],result
    print(f'{name}: {len(source.splitlines())} lines, {len(g["nodes"])} nodes, parse {elapsed:.3f}s, 3 real comparisons PASS')

def test_refactor_quality_gate_and_zip():
    source=(ROOT/'examples'/'MonolithDemo.java').read_text()
    r=post('/api/refactor',{'source':source,'inputs':['5','0','-2']})
    assert r['accepted'] and len(r['files'])==2 and len(r['extracted'])==100,r
    assert r['validation']['behavior_preserved_for_tests']
    download=CLIENT.post('/api/files/download',json={'source':r['source'],'files':r['files']})
    assert download.status_code==200
    with zipfile.ZipFile(io.BytesIO(download.content)) as archive:
        assert set(archive.namelist())=={'MonolithDemo.java','MonolithDemoComponent.java'}

def test_compile_error_does_not_pass():
    invalid='class Main { public static void main(String[] args){ int x="wrong"; }}'
    r=post('/api/compile',{'source':invalid});assert not r['compiled'] and r['exit_code']!=0
    c=post('/api/tests/compare',{'original':invalid,'modified':invalid});assert not c['behavior_preserved_for_tests']

def test_infinite_loop_is_killed():
    r=post('/api/execute',{'source':'class Main {public static void main(String[] args){while(true){}}}'})
    assert r['compiled'] and r['timed_out']

def test_output_flood_is_limited():
    r=post('/api/execute',{'source':'class Main {public static void main(String[] args){while(true){System.out.println("xxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxx");}}}'})
    assert r['output_limited'] and len(r['stdout'])<=100000

def test_network_and_host_files_are_isolated():
    source='''class Main {public static void main(String[] args)throws Exception{
      System.out.println(java.nio.file.Files.exists(java.nio.file.Path.of("/Users/mirusmeta")));
      try{new java.net.Socket().connect(new java.net.InetSocketAddress("1.1.1.1",80),500);System.out.println("network exposed");}
      catch(java.io.IOException e){System.out.println("network blocked");}
    }}'''
    r=post('/api/execute',{'source':source});assert r['exit_code']==0,r
    assert r['stdout']=='false\nnetwork blocked\n',r

def test_throw_stderr_and_exit_code_are_observed():
    source='class Main {public static void main(String[] args){throw new IllegalStateException("Ошибка");}}'
    r=post('/api/tests/compare',{'original':source,'modified':source})
    assert r['behavior_preserved_for_tests'] and r['results'][0]['original']['exit_code']!=0
    assert 'Ошибка' in r['results'][0]['original']['stderr']

def test_bad_graph_and_syntax_are_rejected():
    assert CLIENT.post('/api/graph/parse',json={'source':'class {'}).status_code==422
    graph=post('/api/graph/parse',{'source':SIMPLE});graph['edges']=[]
    assert CLIENT.post('/api/graph/generate',json=graph).status_code==422

def test_package_and_multiple_classes():
    source='package demo; class Helper {static String value(){return "Пакет";}} public class Main {public static void main(String[] args){System.out.println(Helper.value());}}'
    r=post('/api/execute',{'source':source});assert r['exit_code']==0 and r['stdout']=='Пакет\n',r

def test_graph_compile_gate_keeps_invalid_change_out():
    graph=post('/api/graph/parse',{'source':SIMPLE})
    call=next(n for n in graph['nodes'] if n['kind']=='call')
    r=CLIENT.post('/api/graph/edit',json={'graph':graph,'operation':{'type':'replace','nodeId':call['id'],'code':'unknownMethod();'}})
    assert r.status_code==422 and 'компилятор' in r.json()['detail'].lower()
    assert post('/api/graph/generate',graph)['source']==SIMPLE

def test_heap_cpu_and_read_only_root():
    source='''class Main {public static void main(String[] args)throws Exception{
      System.out.println(Runtime.getRuntime().maxMemory() < 150L*1024*1024);
      System.out.println(Runtime.getRuntime().availableProcessors());
      try{java.nio.file.Files.writeString(java.nio.file.Path.of("/etc/mirutex-test"),"x");System.out.println("writable");}
      catch(java.io.IOException e){System.out.println("read only");}
    }}'''
    r=post('/api/execute',{'source':source})
    assert r['exit_code']==0 and r['stdout']=='true\n1\nread only\n',r
