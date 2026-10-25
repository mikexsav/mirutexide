"""Verify the public Nginx boundary. Run with MIRUTEX_PUBLIC_URL set to the deployed HTTPS URL."""
import os
import httpx

base = os.environ['MIRUTEX_PUBLIC_URL'].rstrip('/')
source = 'class Main {public static void main(String[] args){System.out.println("demo");}}'
with httpx.Client(base_url=base, timeout=45) as client:
    for path in ['/', '/demo/', '/mirutex.png']:
        response = client.get(path)
        assert response.status_code == 200, (path, response.status_code)
    assert client.get('/admin/').status_code == 401
    for path in ['/api/compile', '/api/execute', '/api/refactor', '/api/tests/compare', '/api/graph/edit']:
        response = client.post(path, json={'source': source})
        assert response.status_code == 401, (path, response.status_code)
    for path in ['/api/graph/parse', '/api/analysis', '/api/files/download']:
        response = client.post(path, json={'source': source})
        assert response.status_code == 200, (path, response.status_code, response.text[:100])
    print('PASS: public entry, demo APIs, protected execution and mutations')
