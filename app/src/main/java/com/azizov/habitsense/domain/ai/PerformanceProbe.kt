package com.azizov.habitsense.domain.ai

import com.azizov.habitsense.domain.ai.model.PerformanceMetrics

object PerformanceProbe {
    fun <T> measure(block: () -> T): Pair<T, PerformanceMetrics> {
        val runtime = Runtime.getRuntime()
        runtime.gc()
        val startMemoryKb = runtime.totalMemory() / 1024
        val startNs = System.nanoTime()

        val result = block()

        val endNs = System.nanoTime()
        val endMemoryKb = runtime.totalMemory() / 1024
        val elapsedMs = (endNs - startNs) / 1_000_000
        val memoryDeltaKb = (endMemoryKb - startMemoryKb).coerceAtLeast(0)

        return result to PerformanceMetrics(
            executionTimeMs = elapsedMs,
            memoryDeltaKb = memoryDeltaKb
        )
    }
}
