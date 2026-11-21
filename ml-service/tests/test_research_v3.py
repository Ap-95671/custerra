import numpy as np
import pytest
from research.evaluate_v3 import partitions, block_uncertainty, evaluate
from research.train import preprocess

def test_blocked_folds_keep_region_days_disjoint_and_dates_ordered():
    rows, _ = preprocess()
    for region in ['BEJAIA', 'SIDI_BEL_ABBES']:
        fit, cal, test = partitions(rows, region)
        assert set(r['id'] for r in fit).isdisjoint(r['id'] for r in cal + test)
        assert set(r['id'] for r in cal).isdisjoint(r['id'] for r in test)
        assert max(r['date'] for r in fit) < min(r['date'] for r in cal)
        assert max(r['date'] for r in cal) < min(r['date'] for r in test)
        assert {r['region'] for r in fit+cal}.isdisjoint(r['region'] for r in test)

def test_paired_block_interval_is_deterministic_and_baseline_pairing_is_exact():
    labels = np.array([0,1]*15)
    first = block_uncertainty(labels, np.full(30,.5), .5, repeats=100)
    assert first == block_uncertainty(labels, np.full(30,.5), .5, repeats=100)
    assert first['brierMinusFrequencyBaselineInterval'] == [0.,0.]
    assert first['brierPercentileInterval'] == [.25,.25]
    with pytest.raises(ValueError): block_uncertainty([0], [.5], .5)

def test_extended_results_keep_separate_diagnostics_and_withhold_operational_release():
    result=evaluate()
    assert result['releaseDecision']=='RESEARCH_ONLY'
    assert result['operationalIntegration'] is False
    assert len(result['folds'])==2
    assert all(f['calibratedLogistic']['n']==30 for f in result['folds'])
    assert all(len(f['uncertainty']['brierPercentileInterval'])==2 for f in result['folds'])
