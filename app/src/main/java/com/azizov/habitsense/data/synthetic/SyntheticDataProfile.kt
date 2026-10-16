package com.azizov.habitsense.data.synthetic

/**
 * Controls how synthetic rows are sampled before insertion into Room.
 *
 * - [INDEPENDENT]: legacy i.i.d. habit completion (useful for baseline comparisons).
 * - [ENRICHED]: streaks, weekday effects, and mood-linked habits for trainable ML signal.
 */
enum class SyntheticDataProfile {
    INDEPENDENT,
    ENRICHED
}
