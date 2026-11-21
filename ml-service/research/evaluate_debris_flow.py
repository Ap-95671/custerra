"""Retrospective experiment for the USGS runoff-generated postfire inventory."""
from __future__ import annotations

import argparse
from collections import Counter
import csv
import hashlib
import json
import math
from pathlib import Path

import numpy as np
from sklearn.linear_model import LogisticRegression
from sklearn.metrics import average_precision_score, brier_score_loss, confusion_matrix
from sklearn.model_selection import GroupShuffleSplit
from sklearn.pipeline import make_pipeline
from sklearn.preprocessing import StandardScaler

VERSION = 'usgs-postfire-retrospective-v1'
SEED = 20261118
TARGET = 'Response'


def preprocess(path, region_column, fire_column, features):
    if not features or len({TARGET, region_column, fire_column, *features}) != len(features) + 3:
        raise ValueError('Label, region, fire and feature columns must be distinct.')
    data = []
    excluded = 0
    with Path(path).open(encoding='utf-8-sig', newline='') as source:
        reader = csv.DictReader(source)
        required = {TARGET, region_column, fire_column, *features}
        if not required.issubset(reader.fieldnames or []):
            raise ValueError(f'Missing release columns: {sorted(required - set(reader.fieldnames or []))}')
        for row in reader:
            region, fire, label = (str(row[key] or '').strip() for key in (region_column, fire_column, TARGET))
            try:
                values = tuple(float(row[name]) for name in features)
            except (TypeError, ValueError):
                excluded += 1
                continue
            if (not region or not fire or region == '-9999' or fire == '-9999' or label not in {'0', '1'}
                    or any(not math.isfinite(value) or value == -9999 for value in values)):
                excluded += 1
                continue
            data.append((region, fire, int(label), values))
    if not data:
        raise ValueError('No complete observation rows.')
    region, fire, label, values = zip(*data, strict=True)
    return np.array(region), np.array(fire), np.array(label), np.array(values), excluded


def partitions(regions, groups, labels, held_out):
    test = np.isin(regions, held_out)
    if set(regions[test]) != set(held_out) or len(np.unique(labels[test])) != 2:
        raise ValueError('Each requested region must exist. The test set must contain both labels.')
    eligible = np.flatnonzero(~test & ~np.isin(groups, groups[test]))
    if len(np.unique(groups[eligible])) < 3:
        raise ValueError('Too few distinct training and calibration wildfires.')
    splitter = GroupShuffleSplit(n_splits=32, test_size=0.25, random_state=SEED)
    for train, calibrate in splitter.split(eligible, groups=groups[eligible]):
        fit, calibration = eligible[train], eligible[calibrate]
        if len(np.unique(labels[fit])) == len(np.unique(labels[calibration])) == 2:
            return fit, calibration, np.flatnonzero(test)
    raise ValueError('Unable to form wildfire-disjoint training and calibration sets with both labels.')


def logistic_scores(values, labels, fit, calibration, test):
    model = make_pipeline(StandardScaler(), LogisticRegression())
    model.fit(values[fit], labels[fit])
    scores = model.decision_function(values[calibration]).reshape(-1, 1)
    calibrator = LogisticRegression().fit(scores, labels[calibration])
    raw = model.predict_proba(values[test])[:, 1]
    calibrated = calibrator.predict_proba(model.decision_function(values[test]).reshape(-1, 1))[:, 1]
    return raw, calibrated


def summarize(labels, probabilities):
    prediction = probabilities >= 0.5
    tn, fp, fn, tp = confusion_matrix(labels, prediction, labels=[0, 1]).ravel().tolist()
    bins = []
    for index in range(10):
        chosen = np.minimum((probabilities * 10).astype(int), 9) == index
        if np.any(chosen):
            bins.append({'bin': index, 'observations': int(chosen.sum()),
                         'meanScore': float(probabilities[chosen].mean()), 'observedFraction': float(labels[chosen].mean())})
    return {'averagePrecision': float(average_precision_score(labels, probabilities)),
            'brier': float(brier_score_loss(labels, probabilities)),
            'precisionAt0_5': tp / (tp + fp) if tp + fp else None,
            'recallAt0_5': tp / (tp + fn), 'trueNegatives': tn, 'falsePositives': fp,
            'falseNegatives': fn, 'truePositives': tp, 'reliabilityBins': bins,
            'expectedCalibrationError': sum(b['observations'] * abs(b['meanScore'] - b['observedFraction']) for b in bins) / len(labels)}


def paired_brier_interval(labels, candidate, baseline, groups):
    cluster_indices = [np.flatnonzero(groups == site) for site in np.unique(groups)]
    if len(cluster_indices) < 5:
        return None
    difference = (candidate - labels) ** 2 - (baseline - labels) ** 2
    rng = np.random.default_rng(SEED)
    replicates = []
    for _ in range(1000):
        chosen = rng.integers(0, len(cluster_indices), len(cluster_indices))
        observations = np.concatenate([cluster_indices[index] for index in chosen])
        replicates.append(float(difference[observations].mean()))
    return np.quantile(replicates, [0.025, 0.975]).tolist()


def evaluate(path, region_column, fire_column, rainfall, features, held_out):
    columns = [rainfall, *features]
    regions, groups, labels, values, excluded = preprocess(path, region_column, fire_column, columns)
    fit, calibration, test = partitions(regions, groups, labels, held_out)
    reference = np.full(len(test), labels[fit].mean())
    rain_raw, rain_calibrated = logistic_scores(values[:, :1], labels, fit, calibration, test)
    full_raw, full_calibrated = logistic_scores(values, labels, fit, calibration, test)
    models = {'trainingPrevalence': reference, 'rainfallOnly': rain_raw, 'rainfallOnlyCalibrated': rain_calibrated,
              'rainfallAndTerrain': full_raw, 'rainfallAndTerrainCalibrated': full_calibrated}
    test_labels = labels[test]
    errors = {}
    for region in held_out:
        included = regions[test] == region
        prediction = full_calibrated[included] >= 0.5
        actual = test_labels[included].astype(bool)
        errors[region] = {'falsePositives': int((prediction & ~actual).sum()),
                          'falseNegatives': int((~prediction & actual).sum()),
                          'observations': int(included.sum()), 'positives': int(actual.sum())}
    return {'version': VERSION, 'source': 'https://doi.org/10.5066/P1R9VXC4',
            'inputSha256': hashlib.sha256(Path(path).read_bytes()).hexdigest(), 'features': columns,
            'excludedIncompleteRows': excluded, 'retainedLabels': dict(sorted(Counter(map(int, labels)).items())),
            'splits': {name: {'rows': len(split), 'wildfires': len(np.unique(groups[split])),
                              'positives': int(labels[split].sum())}
                       for name, split in [('fit', fit), ('calibration', calibration), ('heldOut', test)]},
            'heldOutRegions': sorted(set(held_out)), 'metrics': {name: summarize(test_labels, score) for name, score in models.items()},
            'calibratedBrierDifferenceVsRainfall95PctWildfireBootstrap': paired_brier_interval(
                test_labels, full_calibrated, rain_calibrated, groups[test]),
            'calibratedErrorsByRegion': errors,
            'interpretation': 'Retrospective association in monitored burned watersheds. No operational probabilities or warnings.'}


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('inventory', type=Path)
    parser.add_argument('--region-column', required=True)
    parser.add_argument('--fire-column', required=True)
    parser.add_argument('--rainfall-column', required=True)
    parser.add_argument('--terrain-column', action='append', required=True)
    parser.add_argument('--held-out-region', action='append', required=True)
    parser.add_argument('--output', type=Path, required=True)
    args = parser.parse_args()
    report = evaluate(args.inventory, args.region_column, args.fire_column, args.rainfall_column,
                      args.terrain_column, args.held_out_region)
    args.output.write_text(json.dumps(report, indent=2, allow_nan=False) + '\n', encoding='utf-8')


if __name__ == '__main__':
    main()
