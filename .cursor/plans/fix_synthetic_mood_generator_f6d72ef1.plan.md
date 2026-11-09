---
name: Fix synthetic mood generator
overview: Replace the truncating integer mood blend in the ENRICHED synthetic engine with a continuous latent AR(1) mood that is rounded only when logged, so moods 1-5 all occur, and lock the distribution in with JVM tests.
todos:
  - id: latent-mood
    content: Replace integer mood blend with Gaussian AR(1) latent mood in SyntheticBehaviorEngine (advance daily, round only when logged, Box-Muller helper)
    status: completed
  - id: config-fields
    content: Add moodMean and moodStdDev to SyntheticDataConfig and update the moodPersistence KDoc
    status: completed
  - id: distribution-tests
    content: Add mood distribution, persistence, missingness and determinism tests to SyntheticBehaviorEngineTest; make the streak test deterministic if it breaks
    status: completed
  - id: verify-and-record
    content: Run JVM unit tests (and instrumented tests if a device is attached); report new mood distribution, completion base rate and heuristic label counts for the seed-42 config
    status: completed
isProject: false
---

# Fix synthetic mood generator (step 1 of 5)

## Root cause

In [app/src/main/java/com/azizov/habitsense/data/synthetic/SyntheticBehaviorEngine.kt](app/src/main/java/com/azizov/habitsense/data/synthetic/SyntheticBehaviorEngine.kt), `sampleMood` blends the previous **integer** mood with a uniform draw and truncates:

```kotlin
val value = persistence * previousMood!! + (1f - persistence) * drawn
value.toInt().coerceIn(1, 5)
```

From mood 3 the largest possible value is 0.65*3 + 0.35*5 = 3.7, which truncates to 3, so the state can only drift down. Measured earlier: 0 of 2,190 days at mood 4-5, mean about 2.0. Swapping `toInt()` for rounding is not enough, because an integer state then gets stuck in 2-4.

## Change

Only the ENRICHED profile is touched. `INDEPENDENT` already draws mood uniformly and stays as is.

**1. Latent mood in `SyntheticBehaviorEngine`**

- Replace `previousMood: Int?` with `latentMood: Float?`.
- Advance it every simulated day (also on days where mood is not logged), using a variance-preserving Gaussian AR(1), and round only the value that gets logged:

```kotlin
val rho = config.moodPersistence.coerceIn(0f, 1f)
val next = if (previous == null) {
    config.moodMean + config.moodStdDev * nextGaussian()
} else {
    config.moodMean + rho * (previous - config.moodMean) +
        sqrt(1f - rho * rho) * config.moodStdDev * nextGaussian()
}
latentMood = next
if (random.nextFloat() >= config.moodLogProbability) return null
return next.roundToInt().coerceIn(1, 5)
```

- Add a small private `nextGaussian()` (Box-Muller on `random.nextDouble()`), since `kotlin.random.Random` has none.
- `sampleExpenses`, `sampleHabitCompletion`, `enrichedLogit` and `completionProbability` are not changed.

**2. Config in [app/src/main/java/com/azizov/habitsense/data/synthetic/SyntheticDataConfig.kt](app/src/main/java/com/azizov/habitsense/data/synthetic/SyntheticDataConfig.kt)**

- Add `moodMean: Float = 3f` and `moodStdDev: Float = 1.1f`.
- Update the `moodPersistence` KDoc: it becomes the lag-1 autocorrelation of the latent mood. Default stays 0.65.

A read-only simulation of this model (3,000 timelines of 365 days) gave an average mood share of about 9 / 24 / 35 / 24 / 9 percent for moods 1-5, with every level present in every timeline and lag-1 correlation around 0.59.

## Decisions baked in

- **Behaviour on unlogged days is left as today.** Habits and spending keep using the logged mood, and treat an unlogged day as neutral. Letting the hidden mood drive behaviour is arguably more realistic, but it changes the planted effects and is outside this step.
- **`moodStdDev = 1.1`** is my choice for a spread that uses the whole 1-5 scale without piling up at the ends. 0.9 makes moods 1 and 5 rare (about 5 percent each); 1.3 makes them common (about 12 percent each).
- **No schema version bump.** Feature builders and `HabitPredictionFeatureSchema` are untouched; only the data distribution changes.

## Tests

Extend [app/src/test/java/com/azizov/habitsense/data/synthetic/SyntheticBehaviorEngineTest.kt](app/src/test/java/com/azizov/habitsense/data/synthetic/SyntheticBehaviorEngineTest.kt) (pure JVM, fixed seeds, 365 days, `moodLogProbability = 1f`):

- Every mood 1-5 occurs, mean is within 2.4-3.6, and the share of mood 4-5 is within 0.12-0.50, for several seeds.
- Logged moods have lag-1 correlation above 0.35 with the default persistence, and below 0.25 in absolute value with `moodPersistence = 0f`.
- With `moodLogProbability = 0.85f`, the unlogged share is close to 15 percent and logged values stay in 1-5.
- Same seed gives the same mood sequence.

The bounds come from the simulation above, with margin.

One existing test is at risk: `enriched_streakIncreasesNextDayCompletionProbability` depends on the habit being on a streak after 45 seeded days, and the new random draws can flip that. If it fails, I will make it deterministic rather than hunt for a lucky seed.

## Verification

- Run `./gradlew :app:testDebugUnitTest` with `JAVA_HOME` pointing at the Android Studio JBR (there is no `java` on the PATH).
- Run the instrumented suite only if an emulator or device is attached; otherwise I will report it as not run. Its `> 0.55` accuracy assertions are likely to keep passing, but they are seed-dependent.
- Report the new mood distribution, the completion base rate and the heuristic label counts for the seed-42 export config, so the before/after is on record.

## Known consequences, not fixed in this step

- **The checked-in `habit_predictor.tflite` becomes stale.** It was trained on data with no mood above 3. It is retrained in step 3, not here.
- **The completion base rate will rise.** Average mood moves from about 2 to about 3, and mood feeds completion. This makes the "always complete" baseline stronger, which step 2 and 3 have to deal with.
- **`StressedSpendingDay` will still dominate the heuristic labels.** I attributed that skew to mood in the audit, but it is also driven by spend: the rule fires on `spend > 150`, and the generator's daily totals are usually above that. `BalancedDay` should start to appear, but the label balance is a step 4 topic.
