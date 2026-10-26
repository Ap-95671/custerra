import { afterEach, expect, test, vi } from 'vitest';
import { NextRequest } from 'next/server';
import { GET, POST } from '../src/app/api/[...path]/route';
afterEach(()=>{vi.unstubAllGlobals();vi.unstubAllEnvs();});
test('proxy refuses internal operational paths and missing backend configuration',async()=>{
  vi.stubEnv('BACKEND_INTERNAL_URL','');
  expect((await GET(new NextRequest('http://local/api/internal/operations'))).status).toBe(404);
  expect((await GET(new NextRequest('http://local/api/v1/auth/me'))).status).toBe(503);
});
test('proxy forwards session and CSRF only to the configured backend and preserves cookies',async()=>{
  vi.stubEnv('BACKEND_INTERNAL_URL','http://backend:8080');
  const fetcher=vi.fn().mockResolvedValue(new Response(null,{status:204,headers:{'Set-Cookie':'JSESSIONID=fixture; HttpOnly','X-Internal':'hidden'}}));vi.stubGlobal('fetch',fetcher);
  const response=await POST(new NextRequest('http://local/api/v1/auth/login',{method:'POST',headers:{Cookie:'JSESSIONID=fixture','X-CSRF-TOKEN':'fixture','X-Forwarded-For':'spoof'},body:'username=fixture'}));
  const [url,options]=fetcher.mock.calls[0];
  expect(String(url)).toBe('http://backend:8080/api/v1/auth/login');expect(options.redirect).toBe('manual');
  expect(options.headers.get('X-CSRF-TOKEN')).toBe('fixture');expect(options.headers.get('X-Forwarded-For')).toBeNull();
  expect(response.status).toBe(204);expect(response.headers.get('set-cookie')).toContain('HttpOnly');expect(response.headers.get('x-internal')).toBeNull();
});
test('proxy caps request bodies before contacting backend',async()=>{
  vi.stubEnv('BACKEND_INTERNAL_URL','http://backend:8080');const fetcher=vi.fn();vi.stubGlobal('fetch',fetcher);
  expect((await POST(new NextRequest('http://local/api/v1/routes/assessment',{method:'POST',body:'x'.repeat(1048577)}))).status).toBe(413);expect(fetcher).not.toHaveBeenCalled();
});
test('proxy sanitizes upstream failures',async()=>{
  vi.stubEnv('BACKEND_INTERNAL_URL','http://backend:8080');vi.stubGlobal('fetch',vi.fn().mockRejectedValue(new Error('secret connection details')));
  const response=await GET(new NextRequest('http://local/api/v1/auth/me'));expect(response.status).toBe(503);expect(await response.text()).not.toContain('secret');
});
