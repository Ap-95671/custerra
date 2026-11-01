"""Reproduce the frozen historical experiment; audit errors and runtime abstention without retuning."""
from pathlib import Path
import json
import platform
import numpy as np
import sklearn
from research.train import preprocess, split, train, metrics, SHA


def evaluate():
    artifact, existing = train()
    rows, rejected = preprocess()
    fit, calibration, test = split(rows)
    x = np.array([row['x'] for row in test])
    y = np.array([row['y'] for row in test])
    standardized = (x - np.array(artifact['mean'])) / np.array(artifact['scale'])
    logit = standardized @ np.array(artifact['coefficients']) + artifact['intercept']
    scores = 1 / (1 + np.exp(-(logit * artifact['calibrationCoefficient'] + artifact['calibrationIntercept'])))
    accepted = np.all((x >= np.array(artifact['fitMin'])) & (x <= np.array(artifact['fitMax'])), axis=1)
    evaluations = {}
    majority = np.full(len(y), float(artifact['trainingPositiveFraction'] >= .5))
    for name, original in [('majority', metrics(y, majority)), ('frequency', existing['baseline']),
                           ('logistic', existing['uncalibratedLogistic']), ('calibratedLogistic', existing['calibratedLogistic'])]:
        values = dict(original)
        tn, fp, fn, tp = values['confusionMatrixTN_FP_FN_TP']
        values.update(falsePositiveRate=fp/(fp+tn) if fp+tn else None,
                      falseNegativeRate=fn/(fn+tp) if fn+tp else None)
        evaluations[name] = values
    selective = None
    if accepted.any():
        decisions = scores[accepted] >= .5
        labels = y[accepted]
        selective = {'n': int(accepted.sum()), 'truePositive': int(np.sum(decisions & (labels == 1))),
                     'falsePositive': int(np.sum(decisions & (labels == 0))),
                     'falseNegative': int(np.sum(~decisions & (labels == 1))), 'trueNegative': int(np.sum(~decisions & (labels == 0))),
                     'brier': float(np.mean((scores[accepted]-labels)**2))}
    return {'protocolVersion': 'historical-audit-v2', 'datasetSha256': SHA, 'datasetRows': len(rows),
            'rejectedRows': rejected, 'featureMissingCount': int(np.isnan(np.array([r['x'] for r in rows])).sum()),
            'partitions': {name: {'n': len(data), 'positiveLabels': sum(r['y'] for r in data),
                                  'regions': sorted({r['region'] for r in data}), 'from': min(r['date'] for r in data), 'to': max(r['date'] for r in data)}
                           for name, data in [('train', fit), ('calibration', calibration), ('test', test)]},
            'evaluations': evaluations, 'abstention': {'criterion': 'outside any training feature min/max; historical region/date gates remain required',
                'acceptedCount': int(accepted.sum()), 'withheldCount': int((~accepted).sum()), 'coverageFraction': float(accepted.mean()),
                'withheldPositiveLabels': int(y[~accepted].sum()), 'acceptedSubset': selective},
            'testRows': [{'id': r['id'], 'label': r['y'], 'researchScore': float(p), 'runtimeWouldAbstain': not bool(a)}
                         for r, p, a in zip(test, scores, accepted)],
            'environment': {'python': platform.python_version(), 'numpy': np.__version__, 'scikitLearn': sklearn.__version__},
            'releaseDecision': 'RESEARCH_ONLY', 'limitations': [
                'One held-out region/month, only 30 test rows; no broad geographic or temporal validation.',
                'Same-day variables and unverified label ascertainment prevent an advance forecasting claim.',
                'Full-test metrics are retrospective diagnostics; runtime abstention reduces coverage and is separately measured.',
                'No new hyperparameters or decision threshold selected using test results.',
                'No operational probability or accuracy claim; no new model release authorized.']}


if __name__ == '__main__':
    result = evaluate()
    target = Path(__file__).resolve().parents[2] / 'research'
    target.mkdir(exist_ok=True)
    (target / 'results.json').write_text(json.dumps(result, indent=2, allow_nan=False) + '\n')
    print(json.dumps({'releaseDecision': result['releaseDecision'], 'abstention': result['abstention']}, indent=2))
