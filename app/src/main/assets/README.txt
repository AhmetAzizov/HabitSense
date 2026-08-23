Place trained TensorFlow Lite models in this directory.

Expected default model file:
- habit_predictor.tflite

Loaded via:
FileUtil.loadMappedFile(context, "habit_predictor.tflite")

If the model file is missing, TfliteHabitPredictor falls back to moving-average logic.
