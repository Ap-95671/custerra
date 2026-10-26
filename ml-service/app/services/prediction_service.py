"""JSON-only inference: no pickle loading, sklearn runtime or provider calls."""
from pathlib import Path
import json, math
from datetime import datetime, timezone
MODEL=json.loads((Path(__file__).resolve().parents[2]/'artifacts/model.json').read_text())
def sigmoid(x):
    return 1/(1+math.exp(-x)) if x>=0 else math.exp(x)/(1+math.exp(x))
def predict(inputs):
    values=[inputs[name] for name in MODEL['features']]
    outside=any(value<low or value>high for value,low,high in zip(values,MODEL['fitMin'],MODEL['fitMax']))
    contributions=[(v-m)/s*c for v,m,s,c in zip(values,MODEL['mean'],MODEL['scale'],MODEL['coefficients'])]
    score=sigmoid((sum(contributions)+MODEL['intercept'])*MODEL['calibrationCoefficient']+MODEL['calibrationIntercept'])
    return {'modelName':MODEL['modelName'],'modelVersion':MODEL['modelVersion'],'predictionTimestamp':datetime.now(timezone.utc).isoformat(),
      'informationType':'EXPERIMENTAL_PREDICTION','status':'OUT_OF_DOMAIN' if outside else 'RESEARCH_ONLY',
      'inputs':inputs,'score':None if outside else score,'scoreMeaning':'Research dataset fire-label score; not disaster probability',
      'confidence':'UNKNOWN','geographicScope':MODEL['scope'],'limitations':MODEL['limitations'],
      'datasetSha256':MODEL['datasetSha256'],'contributions':[] if outside else [dict(feature=n,logitContribution=v) for n,v in zip(MODEL['features'],contributions)]}
