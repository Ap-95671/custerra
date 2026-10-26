# Experimental research — Milestone 18

This is a reproducible retrospective environmental-classification study, not a wildfire forecast, susceptibility map or production risk model. It asks whether four contemporaneous weather variables classify the supplied historical fire/not-fire label. No LLM is used. Live location assessments and journey notifications do not consume the model.

## Provenance

Faroudja Abid (2019), *Algerian Forest Fires*, UCI Machine Learning Repository, DOI [10.24432/C5KW4N](https://doi.org/10.24432/C5KW4N), [dataset and CC BY 4.0 license](https://archive.ics.uci.edu/dataset/547/algerian+forest+fires+dataset). The source covers two Algerian regions during June–September 2012. Attribution must accompany redistribution. The original CSV is retained unchanged in `ml-service/data`; SHA-256 is `3be9ea1575c093cf6bfa7b56110cedc827aa8ca7953bf571530f8cd9990e0a0c`. One malformed source row (line 171, inconsistent columns) is quarantined, leaving 243 usable rows. The repository lists no missing values; preprocessing independently detects the malformed row rather than silently repairing it.

## Protocol and leakage controls

The fixed protocol uses Bejaia June–July for training (61 rows), Bejaia August for sigmoid calibration (31), and Sidi Bel Abbes September for testing (30). The remaining 121 valid rows are deliberately unused. Test data differs in geography and occurs after fitting/calibration. There is no shuffled row split, overlapping region/day, test-driven hyperparameter selection or refit on test labels. The exported partition identities make the boundary auditable. Adjacent days remain correlated, and a single held-out region/month cannot establish generalization.

Features are temperature, humidity, wind and daily rain. Date/region identify partitions and scope but are not model features. FWI components and indices are excluded to reduce label-proxy leakage. Daily weather may include information unavailable before an incident; the task is expressly retrospective. Label collection/ascertainment was not independently audited. This prevents interpreting the experiment as advance prediction.

A fixed standardized logistic regression (`C=1`, threshold 0.5) is compared with a training-frequency baseline. The scaler is fit only on training data. Regularized sigmoid calibration is fit only on August. No complex model is justified by this dataset. JSON coefficients, preprocessing constants and scope are exported without pickle. Runtime inference uses only those coefficients and does not require scikit-learn.

## Results and release decision

The held-out set contains 30 records, including 16 positive labels. The calibrated model has precision 0.571, recall 1.000, F1 0.727, ROC-AUC 0.871 and average precision 0.898. Its Brier score is 0.241 versus 0.254 for the frequency baseline. The calibration experiment has five-bin ECE 0.223, worse than the baseline's 0.074. There are 12 false positives, 0 false negatives, 2 true negatives and 16 true positives at the fixed threshold. Full uncalibrated/calibrated metrics, calibration bins, out-of-fit-envelope counts and misclassified record identities are in `ml-service/artifacts/evaluation.json`.

**Release decision: RESEARCH_ONLY.** The small Brier improvement and numerous false positives do not justify operational use. Calibration was measured, not assumed successful. No calibrated disaster probability, causal explanation, general confidence interval or live geographic validity is claimed. Independent incident labels, multiple seasons/regions, blocked repeated evaluation, uncertainty estimation and calibration validation are required before expanding scope.

## Reproduction and inference

From `ml-service`, create a Python 3.14 virtual environment, install `requirements.txt`, then run:

```sh
python research/train.py
python -m pytest -q
python -m uvicorn app.main:app --host 127.0.0.1 --port 8001
```

`GET /health` identifies research mode. `POST /v1/research/fire-label` accepts:

```json
{"region":"BEJAIA","observedDate":"2012-09-01","temperatureC":30,"relativeHumidityPct":60,"windKmh":15,"rainMm":0}
```

Requests outside the two named regions or historical date scope are rejected. Inputs outside the fitted feature envelope produce `OUT_OF_DOMAIN` with a null score. Accepted outputs include model/version, input/date, prediction timestamp, source hash, scope, UNKNOWN confidence, limitations and feature contributions. Contributions explain the fitted logit, not causes. Scores estimate a research dataset label, not ignition probability or danger. The separately run Python API remains private; no Spring endpoint or notification silently enables it.

Tests verify checksum and quarantining, geographic/time split isolation, deterministic reproduction, exported inference equivalence to scikit-learn, request bounds, historical scope and out-of-domain abstention. API tests run in-process; live deployment remains separate. Method reference: [scikit-learn probability calibration](https://scikit-learn.org/stable/modules/calibration.html).
