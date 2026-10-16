#!/usr/bin/env python3
"""
Train habit_predictor.tflite from HabitSense exported CSV.

Expects files from HabitPredictionDatasetFileExporter (MainActivity dev export):
  - habit_prediction_train.csv
  - habit_prediction_train_metadata.json (optional but recommended)

Install: pip install -r tools/requirements.txt
"""

from __future__ import annotations

import argparse
import csv
import json
import sys
from pathlib import Path

import numpy as np
import tensorflow as tf

FEATURE_DIM = 42
LABEL_COLUMN = "label"
DATE_COLUMN = "target_date"
ID_COLUMNS = ("target_date", "habit_id", LABEL_COLUMN)

REPO_ROOT = Path(__file__).resolve().parents[1]
DEFAULT_OUTPUT = REPO_ROOT / "app" / "src" / "main" / "assets" / "habit_predictor.tflite"


def parse_args() -> argparse.Namespace:
    parser = argparse.ArgumentParser(description="Train HabitSense habit_predictor.tflite from export CSV.")
    parser.add_argument(
        "--csv",
        type=Path,
        required=True,
        help="Path to habit_prediction_train.csv from Downloads/HabitSense",
    )
    parser.add_argument(
        "--metadata",
        type=Path,
        default=None,
        help="Path to *_metadata.json (default: infer next to CSV)",
    )
    parser.add_argument(
        "--output",
        type=Path,
        default=DEFAULT_OUTPUT,
        help="Output .tflite path (default: app asset)",
    )
    parser.add_argument(
        "--val-date-fraction",
        type=float,
        default=0.2,
        help="Hold out last fraction of calendar dates for validation (default: 0.2)",
    )
    parser.add_argument("--epochs", type=int, default=40)
    parser.add_argument("--batch-size", type=int, default=64)
    parser.add_argument("--seed", type=int, default=42)
    parser.add_argument("--learning-rate", type=float, default=1e-3)
    return parser.parse_args()


def load_metadata(metadata_path: Path | None, csv_path: Path) -> dict:
    if metadata_path is None:
        candidate = csv_path.with_name(csv_path.stem + "_metadata.json")
        if candidate.exists():
            metadata_path = candidate
    if metadata_path is None or not metadata_path.exists():
        return {}
    return json.loads(metadata_path.read_text(encoding="utf-8"))


def feature_columns_from_header(header: list[str]) -> list[str]:
    reserved = set(ID_COLUMNS)
    features = [name for name in header if name not in reserved]
    if len(features) != FEATURE_DIM:
        raise ValueError(f"Expected {FEATURE_DIM} feature columns, got {len(features)}")
    return features


def load_dataset(csv_path: Path) -> tuple[np.ndarray, np.ndarray, np.ndarray, list[str]]:
    with csv_path.open(newline="", encoding="utf-8") as handle:
        reader = csv.DictReader(handle)
        if reader.fieldnames is None:
            raise ValueError("CSV has no header row")
        feature_names = feature_columns_from_header(list(reader.fieldnames))
        features: list[list[float]] = []
        labels: list[float] = []
        dates: list[str] = []
        for row in reader:
            dates.append(row[DATE_COLUMN])
            labels.append(float(row[LABEL_COLUMN]))
            features.append([float(row[name]) for name in feature_names])
    x = np.asarray(features, dtype=np.float32)
    y = np.asarray(labels, dtype=np.float32).reshape(-1, 1)
    dates_arr = np.asarray(dates)
    return x, y, dates_arr, feature_names


def time_based_split(
    x: np.ndarray,
    y: np.ndarray,
    dates: np.ndarray,
    val_fraction: float,
) -> tuple[np.ndarray, np.ndarray, np.ndarray, np.ndarray, set[str], set[str]]:
    unique_dates = sorted(set(dates.tolist()))
    if len(unique_dates) < 5:
        raise ValueError("Need at least 5 distinct target_date values for a time split")
    split_index = int(len(unique_dates) * (1.0 - val_fraction))
    split_index = max(1, min(split_index, len(unique_dates) - 1))
    train_dates = set(unique_dates[:split_index])
    val_dates = set(unique_dates[split_index:])
    train_mask = np.isin(dates, list(train_dates))
    val_mask = np.isin(dates, list(val_dates))
    return x[train_mask], y[train_mask], x[val_mask], y[val_mask], train_dates, val_dates


def moving_average_baseline(x: np.ndarray) -> np.ndarray:
    """Mirror app logic: mean of target_habit_completed slots (every 6th feature, index 0 mod 6)."""
    completed_slots = x[:, 0::6]
    inactive_slots = x[:, 1::6]
    active_mask = inactive_slots < 0.5
    weights = active_mask.astype(np.float32)
    weighted_sum = (completed_slots * weights).sum(axis=1)
    counts = weights.sum(axis=1)
    probs = np.divide(weighted_sum, counts, out=np.full(len(x), 0.5, dtype=np.float32), where=counts > 0)
    return probs.reshape(-1, 1)


def binary_metrics(y_true: np.ndarray, y_prob: np.ndarray) -> dict[str, float]:
    y_pred = (y_prob >= 0.5).astype(np.float32)
    accuracy = float((y_pred == y_true).mean())
    tp = float(((y_pred == 1) & (y_true == 1)).sum())
    fp = float(((y_pred == 1) & (y_true == 0)).sum())
    fn = float(((y_pred == 0) & (y_true == 1)).sum())
    precision = tp / (tp + fp) if (tp + fp) > 0 else 0.0
    recall = tp / (tp + fn) if (tp + fn) > 0 else 0.0
    f1 = 2 * precision * recall / (precision + recall) if (precision + recall) > 0 else 0.0
    return {"accuracy": accuracy, "precision": precision, "recall": recall, "f1": f1}


def build_model(input_dim: int) -> tf.keras.Model:
    inputs = tf.keras.Input(shape=(input_dim,), dtype=tf.float32, name="features")
    x = tf.keras.layers.Dense(64, activation="relu")(inputs)
    x = tf.keras.layers.Dropout(0.1)(x)
    x = tf.keras.layers.Dense(32, activation="relu")(x)
    outputs = tf.keras.layers.Dense(1, activation="sigmoid", name="probability")(x)
    return tf.keras.Model(inputs=inputs, outputs=outputs)


def export_tflite(model: tf.keras.Model, output_path: Path) -> None:
    converter = tf.lite.TFLiteConverter.from_keras_model(model)
    tflite_model = converter.convert()
    output_path.parent.mkdir(parents=True, exist_ok=True)
    output_path.write_bytes(tflite_model)

    interpreter = tf.lite.Interpreter(model_content=tflite_model)
    interpreter.allocate_tensors()
    input_shape = interpreter.get_input_details()[0]["shape"]
    output_shape = interpreter.get_output_details()[0]["shape"]
    if list(input_shape) not in ([1, FEATURE_DIM], [FEATURE_DIM]):
        raise ValueError(f"Unexpected TFLite input shape {input_shape}")
    if list(output_shape) not in ([1, 1], [1]):
        raise ValueError(f"Unexpected TFLite output shape {output_shape}")


def main() -> int:
    args = parse_args()
    if not args.csv.exists():
        print(f"CSV not found: {args.csv}", file=sys.stderr)
        return 1

    metadata = load_metadata(args.metadata, args.csv)
    expected_dim = metadata.get("featureDimension", FEATURE_DIM)
    if expected_dim != FEATURE_DIM:
        print(f"Metadata featureDimension={expected_dim} does not match trainer ({FEATURE_DIM})", file=sys.stderr)
        return 1

    tf.keras.utils.set_random_seed(args.seed)
    x, y, dates, feature_names = load_dataset(args.csv)
    x_train, y_train, x_val, y_val, train_dates, val_dates = time_based_split(
        x, y, dates, args.val_date_fraction
    )

    model = build_model(FEATURE_DIM)
    model.compile(
        optimizer=tf.keras.optimizers.Adam(learning_rate=args.learning_rate),
        loss="binary_crossentropy",
        metrics=[tf.keras.metrics.AUC(name="auc"), "accuracy"],
    )

    callbacks = [
        tf.keras.callbacks.EarlyStopping(monitor="val_auc", mode="max", patience=6, restore_best_weights=True),
    ]
    model.fit(
        x_train,
        y_train,
        validation_data=(x_val, y_val),
        epochs=args.epochs,
        batch_size=args.batch_size,
        verbose=1,
        callbacks=callbacks,
    )

    nn_prob = model.predict(x_val, verbose=0)
    baseline_prob = moving_average_baseline(x_val)
    nn_metrics = binary_metrics(y_val, nn_prob)
    baseline_metrics = binary_metrics(y_val, baseline_prob)

    export_tflite(model, args.output)

    print("\n=== Training summary ===")
    print(f"Rows total: {len(x)}  train: {len(x_train)}  val: {len(x_val)}")
    print(f"Train dates: {min(train_dates)} .. {max(train_dates)} ({len(train_dates)} days)")
    print(f"Val dates:   {min(val_dates)} .. {max(val_dates)} ({len(val_dates)} days)")
    print(f"Feature columns: {len(feature_names)} (schema v{metadata.get('featureSchemaVersion', '?')})")
    print("\nValidation (held-out dates):")
    print(f"  TFLite model  — acc={nn_metrics['accuracy']:.4f} f1={nn_metrics['f1']:.4f}")
    print(f"  Moving avg    — acc={baseline_metrics['accuracy']:.4f} f1={baseline_metrics['f1']:.4f}")
    print(f"\nWrote {args.output} ({args.output.stat().st_size} bytes)")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
