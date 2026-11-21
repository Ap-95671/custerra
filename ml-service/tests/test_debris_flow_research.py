import csv

import numpy as np
import pytest

from research.evaluate_debris_flow import evaluate, partitions, preprocess


def test_cross_region_experiment_excludes_shared_fire_and_reports_uncertainty(tmp_path):
    path = tmp_path / 'synthetic.csv'
    with path.open('w', newline='') as output:
        writer = csv.writer(output)
        writer.writerow(['Region', 'Fire', 'Response', 'Rain', 'Slope'])
        for region in ['fit', 'hold']:
            for burn in range(10):
                for index in range(8):
                    fire = 'shared' if burn == 0 else f'{region}-{burn}'
                    writer.writerow([region, fire, index % 2, 15 + index + burn, 6 + index % 3])
        writer.writerow(['hold', 'missing', 1, -9999, 5])
    regions, groups, labels, _, excluded = preprocess(path, 'Region', 'Fire', ['Rain', 'Slope'])
    train, calibration, test = partitions(regions, groups, labels, ['hold'])
    assert excluded == 1
    assert np.all(regions[test] == 'hold')
    assert not set(groups[train]) & set(groups[calibration])
    assert not set(groups[test]) & set(groups[np.r_[train, calibration]])
    report = evaluate(path, 'Region', 'Fire', 'Rain', ['Slope'], ['hold'])
    assert report['splits']['heldOut']['positives'] == 40
    assert report['metrics']['rainfallOnlyCalibrated']['reliabilityBins']
    assert len(report['calibratedBrierDifferenceVsRainfall95PctWildfireBootstrap']) == 2
    assert report == evaluate(path, 'Region', 'Fire', 'Rain', ['Slope'], ['hold'])


def test_unavailable_release_fields_fail_closed(tmp_path):
    path = tmp_path / 'mismatched.csv'
    path.write_text('Response,Region\n1,CA\n')
    with pytest.raises(ValueError, match='Missing release columns'):
        preprocess(path, 'Region', 'Fire', ['Rain'])
