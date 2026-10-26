import json
from pathlib import Path
import numpy as np
from fastapi.testclient import TestClient
from app.main import app
from app.services.prediction_service import predict
from research.train import preprocess, split, train
client=TestClient(app)
INPUT={'region':'BEJAIA','observedDate':'2012-09-01','temperatureC':30,'relativeHumidityPct':60,'windKmh':15,'rainMm':0}
def test_provenance_splits_and_no_geographic_time_test_overlap():
    rows,rejected=preprocess();a,b,c=split(rows)
    assert len(rows)==243 and len(rejected)==1
    assert (len(a),len(b),len(c))==(61,31,30)
    assert set(r['region'] for r in a+b).isdisjoint(r['region'] for r in c)
    assert max(r['date'] for r in a)<min(r['date'] for r in b)<min(r['date'] for r in c)
    assert len({r['id'] for r in a+b+c})==122

def test_reproduction_and_exported_inference():
    path=Path(__file__).resolve().parents[1]/'artifacts/model.json';before=json.loads(path.read_text())
    artifact,report=train()
    assert artifact==before
    assert report['baseline']['n']==30 and 'brier' in report['calibratedLogistic']
    assert 0<=predict(INPUT)['score']<=1
    from sklearn.linear_model import LogisticRegression
    from sklearn.preprocessing import StandardScaler
    a,b,_=split(preprocess()[0]);x=np.array([r['x'] for r in a]);s=StandardScaler().fit(x)
    m=LogisticRegression(C=1,solver='lbfgs',max_iter=2000,random_state=0).fit(s.transform(x),[r['y'] for r in a])
    c=LogisticRegression(C=1,solver='lbfgs',max_iter=2000,random_state=0).fit(m.decision_function(s.transform([r['x'] for r in b])).reshape(-1,1),[r['y'] for r in b])
    expected=c.predict_proba(m.decision_function(s.transform([[INPUT[n] for n in artifact['features']]])).reshape(-1,1))[0,1]
    assert abs(predict(INPUT)['score']-expected)<1e-12

def test_api_metadata_bounds_and_future_abstention():
    assert client.get('/health').json()['mode']=='RESEARCH_ONLY'
    result=client.post('/v1/research/fire-label',json=INPUT)
    assert result.status_code==200 and result.json()['confidence']=='UNKNOWN'
    for field,value in [('region','INDIA'),('observedDate','2026-10-25'),('rainMm',-1),('extra','x')]:
        assert client.post('/v1/research/fire-label',json={**INPUT,field:value}).status_code==422
    result=client.post('/v1/research/fire-label',json={**INPUT,'temperatureC':55}).json()
    assert result['status']=='OUT_OF_DOMAIN' and result['score'] is None
