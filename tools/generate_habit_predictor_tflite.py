#!/usr/bin/env python3
"""
Build habit_predictor.tflite for HabitSense.

Contract (must match HabitPredictionFeatureSchema + TfliteHabitPredictor):
  - Input:  float32[1, 42]  (7 days x 6 features per day)
  - Output: float32[1, 1]  (P(habit complete tomorrow))

This is a lightweight stub for device tests and app integration until a thesis-trained
model replaces it. Regenerate after changing WINDOW_DAYS or FEATURES_PER_DAY in Kotlin.
"""

from __future__ import annotations

import sys
from pathlib import Path

import numpy as np
import tensorflow as tf

WINDOW_DAYS = 7
FEATURES_PER_DAY = 6
INPUT_DIM = WINDOW_DAYS * FEATURES_PER_DAY
OUTPUT_DIM = 1

REPO_ROOT = Path(__file__).resolve().parents[1]
DEFAULT_OUTPUT = REPO_ROOT / "app" / "src" / "main" / "assets" / "habit_predictor.tflite"


def build_keras_model() -> tf.keras.Model:
    """Small MLP; weights emphasize per-day completion slots (indices 0, 6, 12, ...)."""
    inputs = tf.keras.Input(shape=(INPUT_DIM,), dtype=tf.float32, name="features")
    x = tf.keras.layers.Dense(16, activation="relu", name="hidden")(inputs)
    outputs = tf.keras.layers.Dense(OUTPUT_DIM, activation="sigmoid", name="probability")(x)
    model = tf.keras.Model(inputs=inputs, outputs=outputs, name="habit_predictor_stub")

    # Bias toward interpreting completion features (deterministic smoke-test behavior).
    hidden = model.get_layer("hidden")
    kernel = hidden.get_weights()[0].copy()
    kernel.fill(0.0)
    for day in range(WINDOW_DAYS):
        kernel[day * FEATURES_PER_DAY, day % 16] = 1.0
    hidden.set_weights([kernel, np.zeros(16, dtype=np.float32)])

    out = model.get_layer("probability")
    out.set_weights([np.ones((16, 1), dtype=np.float32) * 0.25, np.array([-1.5], dtype=np.float32)])

    return model


def convert_to_tflite(model: tf.keras.Model) -> bytes:
    converter = tf.lite.TFLiteConverter.from_keras_model(model)
    converter.optimizations = []
    return converter.convert()


def verify_tflite(model_bytes: bytes) -> None:
    interpreter = tf.lite.Interpreter(model_content=model_bytes)
    interpreter.allocate_tensors()
    input_details = interpreter.get_input_details()
    output_details = interpreter.get_output_details()

    input_shape = input_details[0]["shape"]
    output_shape = output_details[0]["shape"]
    if list(input_shape) not in ([1, INPUT_DIM], [INPUT_DIM]):
        raise ValueError(f"Unexpected input shape {input_shape}, expected [1, {INPUT_DIM}]")
    if list(output_shape) not in ([1, OUTPUT_DIM], [OUTPUT_DIM]):
        raise ValueError(f"Unexpected output shape {output_shape}, expected [1, {OUTPUT_DIM}]")

    sample = np.zeros((1, INPUT_DIM), dtype=np.float32)
    sample[0, 0] = 1.0  # one completed day slot
    interpreter.set_tensor(input_details[0]["index"], sample)
    interpreter.invoke()
    prob = float(interpreter.get_tensor(output_details[0]["index"])[0, 0])
    if not (0.0 <= prob <= 1.0):
        raise ValueError(f"Output probability out of range: {prob}")


def main() -> int:
    output_path = Path(sys.argv[1]) if len(sys.argv) > 1 else DEFAULT_OUTPUT
    output_path.parent.mkdir(parents=True, exist_ok=True)

    model = build_keras_model()
    tflite_model = convert_to_tflite(model)
    verify_tflite(tflite_model)

    output_path.write_bytes(tflite_model)
    print(f"Wrote {output_path} ({len(tflite_model)} bytes)")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
