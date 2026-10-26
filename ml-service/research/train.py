"""Fixed geographic/time holdouts; no random row split or test-set tuning."""
from pathlib import Path
import csv, hashlib, json
from datetime import date
import numpy as np
from sklearn.linear_model import LogisticRegression
from sklearn.preprocessing import StandardScaler
from sklearn.metrics import precision_score, recall_score, f1_score, roc_auc_score, average_precision_score, brier_score_loss, confusion_matrix
ROOT = Path(__file__).resolve().parents[1]
SHA = '3be9ea1575c093cf6bfa7b56110cedc827aa8ca7953bf571530f8cd9990e0a0c'
FEATURES = ['temperatureC', 'relativeHumidityPct', 'windKmh', 'rainMm']

def preprocess(path=ROOT/'data/algerian-forest-fires.csv'):
    raw=path.read_bytes()
    if hashlib.sha256(raw).hexdigest()!=SHA: raise ValueError('Dataset checksum mismatch')
    rows=[]; rejected=[]; region=None; seen=set()
    for line, parts in enumerate(csv.reader(raw.decode('utf-8-sig').splitlines()),1):
        if not parts or not parts[0].strip(): continue
        if 'Region Dataset' in parts[0]:
            region='BEJAIA' if 'Bejaia' in parts[0] else 'SIDI_BEL_ABBES';continue
        if parts[0]=='day':continue
        if len(parts)!=14 or parts[-1].strip() not in ('fire','not fire'):
            rejected.append({'line':line,'reason':'Malformed column count or label'});continue
        day=date(int(parts[2]),int(parts[1]),int(parts[0]));identity=f'{region}:{day}'
        if identity in seen: raise ValueError('Duplicate region/day')
        seen.add(identity)
        values=[float(v.strip()) for v in parts[3:7]]
        if not all(np.isfinite(values)):raise ValueError('Nonfinite input')
        rows.append({'id':identity,'region':region,'date':day.isoformat(),'x':values,'y':int(parts[-1].strip()=='fire')})
    return rows,rejected

def split(rows):
    # Exclude all other rows; preserve a full calendar-month gap between fit and test.
    train=[r for r in rows if r['region']=='BEJAIA' and r['date']<'2012-08-01']
    calibration=[r for r in rows if r['region']=='BEJAIA' and '2012-08-01'<=r['date']<'2012-09-01']
    test=[r for r in rows if r['region']=='SIDI_BEL_ABBES' and r['date']>='2012-09-01']
    return train,calibration,test

def metrics(y,p):
    decision=np.asarray(p)>=0.5
    bins=[];ece=0.0
    for lo in np.linspace(0,0.8,5):
        mask=(p>=lo)&((p<lo+0.2) if lo<0.8 else (p<=1))
        if mask.any():
            avg=float(np.mean(p[mask]));observed=float(np.mean(y[mask]));n=int(mask.sum());ece+=n/len(y)*abs(avg-observed)
            bins.append({'lower':float(lo),'count':n,'meanScore':avg,'observedFraction':observed})
    return {'n':len(y),'positiveCount':int(sum(y)), 'precision':float(precision_score(y,decision,zero_division=0)),
      'recall':float(recall_score(y,decision,zero_division=0)), 'f1':float(f1_score(y,decision,zero_division=0)),
      'rocAuc':float(roc_auc_score(y,p)), 'prAucAveragePrecision':float(average_precision_score(y,p)),
      'brier':float(brier_score_loss(y,p)), 'ece5Bins':ece,'calibrationBins':bins,
      'confusionMatrixTN_FP_FN_TP':confusion_matrix(y,decision,labels=[0,1]).ravel().tolist()}

def train():
    rows,rejected=preprocess(); fit,cal,test=split(rows)
    x=np.array([r['x'] for r in fit]);y=np.array([r['y'] for r in fit])
    scaler=StandardScaler().fit(x)
    model=LogisticRegression(C=1.0,solver='lbfgs',max_iter=2000,random_state=0).fit(scaler.transform(x),y)
    xc=np.array([r['x'] for r in cal]);yc=np.array([r['y'] for r in cal])
    # Sigmoid calibration uses only the reserved August partition; never refit on test data.
    calibrator=LogisticRegression(C=1.0,solver='lbfgs',max_iter=2000,random_state=0).fit(model.decision_function(scaler.transform(xc)).reshape(-1,1),yc)
    xt=np.array([r['x'] for r in test]);yt=np.array([r['y'] for r in test])
    uncal=model.predict_proba(scaler.transform(xt))[:,1]
    probabilities=calibrator.predict_proba(model.decision_function(scaler.transform(xt)).reshape(-1,1))[:,1]
    baseline=np.full(len(yt),y.mean())
    artifact={'modelName':'algerian-weather-label-logistic','modelVersion':'research-v1','informationType':'EXPERIMENTAL_PREDICTION',
      'datasetSha256':SHA,'features':FEATURES,'mean':scaler.mean_.tolist(),'scale':scaler.scale_.tolist(),
      'coefficients':model.coef_[0].tolist(),'intercept':float(model.intercept_[0]),
      'calibrationCoefficient':float(calibrator.coef_[0,0]),'calibrationIntercept':float(calibrator.intercept_[0]),
      'fitMin':x.min(axis=0).tolist(),'fitMax':x.max(axis=0).tolist(),'trainingPositiveFraction':float(y.mean()),
      'scope':'Retrospective 2012 weather-label classification in Bejaia and Sidi Bel Abbes, Algeria; no live forecast',
      'limitations':['Small observational dataset; event-label ascertainment is not independently verified.',
       'Same-day weather includes total daily rain; unusable as an advance forecast.',
       'No terrain, ignition, fuel, suppression or independent incident inventory.',
       'Only one geographic/time holdout; adjacent days are correlated; calibration is fragile.',
       'Score estimates a dataset label, not local danger, ignition probability or fire spread.',
       'Feature contributions are associations, not causes. No operational release approved.']}
    report={'datasetRows':len(rows),'rejectedRows':rejected,'excludedRows':len(rows)-len(fit)-len(cal)-len(test),
      'partitions':{name:[r['id'] for r in data] for name,data in [('train',fit),('calibration',cal),('test',test)]},
      'baseline':metrics(yt,baseline),'uncalibratedLogistic':metrics(yt,uncal),'calibratedLogistic':metrics(yt,probabilities),
      'errors':[{'id':r['id'],'label':r['y'],'score':float(p)} for r,p in zip(test,probabilities) if (p>=.5)!=bool(r['y'])],
      'testOutsideFitEnvelope':sum(any(v<low or v>high for v,low,high in zip(r['x'],artifact['fitMin'],artifact['fitMax'])) for r in test),
      'releaseDecision':'RESEARCH_ONLY: insufficient external validation; no live risk integration',
      'calibrationMethod':'Regularized sigmoid fit only on August Bejaia; fixed C=1 and threshold=0.5; test used once for reporting'}
    for name,value in [('model.json',artifact),('evaluation.json',report)]:
        (ROOT/'artifacts'/name).write_text(json.dumps(value,indent=2,allow_nan=False)+'\n')
    print(json.dumps({k:report[k] for k in ('datasetRows','rejectedRows','baseline','calibratedLogistic','releaseDecision')},indent=2))
    return artifact,report
if __name__=='__main__':train()
