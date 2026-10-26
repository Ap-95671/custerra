"""Smoke the isolated local deployment without printing credentials or cookies."""
import json, urllib.request, urllib.error, http.cookiejar, time, uuid, os
base=os.environ.get('CUSTERRA_SMOKE_BASE_URL','http://127.0.0.1:3000').rstrip('/')
readiness=os.environ.get('CUSTERRA_SMOKE_READINESS_URL','http://127.0.0.1:8080/actuator/health/readiness')
jar=http.cookiejar.CookieJar();client=urllib.request.build_opener(urllib.request.HTTPCookieProcessor(jar))
def call(path,body=None,method='GET',token=None):
 headers={'Content-Type':'application/json'}
 if token:headers['X-CSRF-TOKEN']=token
 data=None if body is None else json.dumps(body).encode()
 req=urllib.request.Request(base+path,data=data,headers=headers,method=method)
 with client.open(req,timeout=10) as r:return r.status,r.read()
for attempt in range(120):
 try:
  with urllib.request.urlopen(readiness,timeout=2) as r:assert json.load(r)['status']=='UP'
  assert call('/health')[0]==200 and call('/api/v1/health')[0]==200
  break
 except (OSError,AssertionError):
  if attempt==119:raise
  time.sleep(1)
_,raw=call('/api/v1/auth/csrf');token=json.loads(raw)['token']
name='deploy_'+uuid.uuid4().hex[:12]
assert call('/api/v1/auth/register',{'username':name,'password':'Temporary-deployment-fixture'},'POST',token)[0]==201
request=urllib.request.Request(base+'/api/v1/auth/login',data=f'username={name}&password=Temporary-deployment-fixture'.encode(),headers={'Content-Type':'application/x-www-form-urlencoded','X-CSRF-TOKEN':token},method='POST')
with client.open(request) as r:assert r.status==204
assert json.loads(call('/api/v1/auth/me')[1])['username']==name
assert call('/api/v1/notifications')[0]==200
try:call('/api/internal/operations/providers');raise AssertionError('Operations exposed')
except urllib.error.HTTPError as e:assert e.code==404
_,raw=call('/api/v1/auth/csrf');token=json.loads(raw)['token']
_,raw=call('/api/v1/developer/clients',{'name':'Container verification'},'POST',token);issued=json.loads(raw)
request=urllib.request.Request(base+'/api/v1/b2b/hazards/nearby?latitude=1000&longitude=0',headers={'Authorization':'Bearer '+issued['apiKey']})
try:urllib.request.urlopen(request);raise AssertionError('Invalid coordinates accepted')
except urllib.error.HTTPError as e:assert e.code==400
assert json.loads(call('/api/v1/developer/usage')[1])['requestsToday']==1
assert call('/api/v1/developer/clients/'+issued['id'],method='DELETE',token=token)[0]==204
try:urllib.request.urlopen(request);raise AssertionError('Revoked key accepted')
except urllib.error.HTTPError as e:assert e.code==401
assert call('/api/v1/auth/logout',method='POST',token=token)[0]==204
print('Container migrations/readiness, session/CSRF proxy, notification inbox, API-key issuance/usage/revocation and logout passed.')
