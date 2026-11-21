"""Geographic holdouts and week-block uncertainty; no model artifact is changed."""
from pathlib import Path
import json
import numpy as np
from sklearn.linear_model import LogisticRegression
from sklearn.preprocessing import StandardScaler
from research.train import preprocess, metrics, SHA
PROTOCOL = 'historical-blocked-audit-v3'
SEED = 20261104

def partitions(rows, region):
    return ([r for r in rows if r['region'] == region and r['date'] < '2012-08-01'],
            [r for r in rows if r['region'] == region and '2012-08-01' <= r['date'] < '2012-09-01'],
            [r for r in rows if r['region'] != region and r['date'] >= '2012-09-01'])

def block_uncertainty(labels, scores, baseline, repeats=2000, block=7):
    """Paired circular moving-block bootstrap; conditional on one fitted model/month."""
    labels, scores = np.asarray(labels), np.asarray(scores)
    if len(labels) < block or len(labels) != len(scores):
        raise ValueError('At least one full block and matching arrays are required')
    rng = np.random.default_rng(SEED)
    n = len(labels)
    differences, briers = [], []
    for _ in range(repeats):
        starts = rng.integers(0, n, size=int(np.ceil(n / block)))
        indices = np.concatenate([(start + np.arange(block)) % n for start in starts])[:n]
        model_loss = (scores[indices] - labels[indices])**2
        base_loss = (baseline - labels[indices])**2
        briers.append(model_loss.mean())
        differences.append((model_loss - base_loss).mean())
    return {'method': 'PAIRED_CIRCULAR_MOVING_BLOCK_BOOTSTRAP', 'seed': SEED, 'blockRows': block,
            'replicates': repeats, 'intervalLevel': .95,
            'brierPercentileInterval': np.quantile(briers, [.025, .975]).tolist(),
            'brierMinusFrequencyBaselineInterval': np.quantile(differences, [.025, .975]).tolist(),
            'interpretation': 'Conditional descriptive interval over one small held-out month; not model, spatial or future-event uncertainty.'}

def evaluate():
    rows, rejected = preprocess()
    folds = []
    for region in ['BEJAIA', 'SIDI_BEL_ABBES']:
        fit, cal, heldout = partitions(rows, region)
        x, y = np.array([r['x'] for r in fit]), np.array([r['y'] for r in fit])
        xc, yc = np.array([r['x'] for r in cal]), np.array([r['y'] for r in cal])
        xt, yt = np.array([r['x'] for r in heldout]), np.array([r['y'] for r in heldout])
        scaler = StandardScaler().fit(x)
        model = LogisticRegression(C=1, max_iter=2000, random_state=0).fit(scaler.transform(x), y)
        calibrator = LogisticRegression(C=1, max_iter=2000, random_state=0).fit(model.decision_function(scaler.transform(xc)).reshape(-1,1), yc)
        scores = calibrator.predict_proba(model.decision_function(scaler.transform(xt)).reshape(-1,1))[:,1]
        baseline = np.full(len(yt), y.mean())
        folds.append({'trainingRegion': region,
            'partitions': {name: [r['id'] for r in part] for name, part in [('fit', fit), ('calibration', cal), ('test', heldout)]},
            'trainingFrequencyBaseline': metrics(yt, baseline), 'calibratedLogistic': metrics(yt, scores),
            'uncertainty': block_uncertainty(yt, scores, float(y.mean())),
            'errors': [{'id': row['id'], 'label': row['y'], 'score': float(score)} for row, score in zip(heldout, scores) if bool(score >= .5) != bool(row['y'])]})
    return {'protocolVersion': PROTOCOL, 'datasetSha256': SHA, 'usableRows': len(rows), 'rejectedRows': rejected,
        'folds': folds, 'releaseDecision': 'RESEARCH_ONLY', 'operationalIntegration': False,
        'limitations': ['Two folds reuse the same two regions and four-month historical dataset; they are not independent external replications.',
            'One direction was previously reported. This audit cannot restore a fresh blinded test set or justify selecting a better direction.',
            'Same-day weather/label ascertainment limitations remain. No temporal advance forecasting claim.',
            'Seven-day blocks preserve some local dependence; 30-row months cannot establish a reliable uncertainty distribution.',
            'Calibration and error rates are reported per fold, without pooling incompatible regional evidence.',
            'No flood or landslide susceptibility model is released; independent labels and geographic covariates remain prerequisites.']}

if __name__ == '__main__':
    result = evaluate()
    destination = Path(__file__).resolve().parents[2] / 'research/results-v3.json'
    destination.write_text(json.dumps(result, indent=2, allow_nan=False)+'\n')
    print(json.dumps({'protocol': PROTOCOL, 'releaseDecision': result['releaseDecision'], 'folds': [{'trainingRegion': f['trainingRegion'], 'testN': f['calibratedLogistic']['n'], 'brier': f['calibratedLogistic']['brier'], 'pairedBrierInterval': f['uncertainty']['brierMinusFrequencyBaselineInterval']} for f in result['folds']]}, indent=2))
