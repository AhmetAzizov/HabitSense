habit_predictor.tflite
----------------------
Habit-completion model loaded by TfliteHabitPredictor via:
  FileUtil.loadMappedFile(context, "habit_predictor.tflite")

I/O contract (HabitPredictionFeatureSchema):
  - Input:  float32[1, 42]  (7 days x 6 features per day)
  - Output: float32[1, 1]  (probability in [0, 1])

The checked-in file is the network trained by tools/train_habit_predictor.py
on an exported habit_prediction_train.csv (time-based holdout). Same tensor
shapes and feature order are required if you retrain.

Retrain from a phone export:
  pip install -r tools/requirements.txt
  python3 tools/train_habit_predictor.py \
    --csv ~/Downloads/habit_prediction_train.csv \
    --metadata ~/Downloads/habit_prediction_train_metadata.json \
    --output app/src/main/assets/habit_predictor.tflite

tools/generate_habit_predictor_tflite.py writes a hand-set stub for smoke tests
only. Running it overwrites the trained asset.

If the model file is missing, TfliteHabitPredictor falls back to moving-average logic.
