from research.evaluate_v2 import evaluate


def test_false_positive_denominator_and_abstention_population_are_explicit():
    report = evaluate()
    values = report['evaluations']['calibratedLogistic']
    tn, fp, fn, tp = values['confusionMatrixTN_FP_FN_TP']
    assert values['falsePositiveRate'] == fp / (fp + tn)
    assert values['falseNegativeRate'] == fn / (fn + tp)
    abstention = report['abstention']
    assert abstention['acceptedCount'] + abstention['withheldCount'] == len(report['testRows']) == 30
    assert sum(row['runtimeWouldAbstain'] for row in report['testRows']) == abstention['withheldCount']
    assert report['releaseDecision'] == 'RESEARCH_ONLY'


def test_holdout_metadata_does_not_hide_temporal_or_geographic_scope():
    report = evaluate()
    train, calibration, test = (report['partitions'][name] for name in ['train', 'calibration', 'test'])
    assert train['to'] < calibration['from'] < test['from']
    assert set(train['regions']).isdisjoint(test['regions'])
    assert set(calibration['regions']).isdisjoint(test['regions'])
    assert test['n'] == 30 and report['rejectedRows'] == [{'line': 171, 'reason': 'Malformed column count or label'}]
