habit_predictor.tflite
----------------------
Default habit-completion model loaded by TfliteHabitPredictor via:
  FileUtil.loadMappedFile(context, "habit_predictor.tflite")

I/O contract (HabitPredictionFeatureSchema):
  - Input:  float32[1, 42]  (7 days x 6 features per day)
  - Output: float32[1, 1]  (probability in [0, 1])

The checked-in file is a small stub for integration/androidTest. Replace it with your
thesis-trained export when ready (same tensor shapes and feature order).

Regenerate the stub:
  pip install -r tools/requirements.txt
  python3 tools/generate_habit_predictor_tflite.py

If the model file is missing, TfliteHabitPredictor falls back to moving-average logic.
