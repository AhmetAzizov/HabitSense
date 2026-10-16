# Training `habit_predictor.tflite`

## Before you run the script

1. **Exported CSV on your Mac**  
   From the phone: `Downloads/HabitSense/habit_prediction_train.csv`  
   (Optional: `habit_prediction_train_metadata.json` in the same folder.)

2. **Python 3.10+** recommended.

3. **Dependencies** (once per machine):

   ```bash
   cd /path/to/HabitSense
   pip install -r tools/requirements.txt
   ```

4. **Enough rows**  
   Export should report thousands of rows (enriched synthetic over ~1 year).  
   If `Export rows: 0`, fix the app export first.

5. **Time split**  
   The trainer holds out the **last 20% of calendar dates** for validation (no random row split).  
   Document those date ranges in your thesis.

## Train and install into the app

```bash
python3 tools/train_habit_predictor.py \
  --csv ~/Downloads/habit_prediction_train.csv \
  --metadata ~/Downloads/habit_prediction_train_metadata.json \
  --output app/src/main/assets/habit_predictor.tflite
```

Then rebuild the app (`./gradlew assembleDebug`).  
`TfliteHabitPredictor` loads the new asset automatically.

## Stub-only generator (no CSV)

```bash
python3 tools/generate_habit_predictor_tflite.py
```

Use only for smoke tests, not thesis evaluation.
