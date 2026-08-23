package com.azizov.habitsense.domain.util

import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.format.DateTimeParseException

object DateStringUtils {
    private val formatter: DateTimeFormatter = DateTimeFormatter.ISO_LOCAL_DATE

    fun parse(dateString: String): LocalDate = LocalDate.parse(dateString, formatter)

    fun format(date: LocalDate): String = date.format(formatter)

    fun isValid(dateString: String): Boolean = try {
        parse(dateString)
        true
    } catch (_: DateTimeParseException) {
        false
    }

    fun datesBetweenInclusive(startDate: String, endDate: String): List<String> {
        val start = parse(startDate)
        val end = parse(endDate)
        require(!start.isAfter(end)) { "startDate must be <= endDate" }

        val dates = mutableListOf<String>()
        var current = start
        while (!current.isAfter(end)) {
            dates.add(format(current))
            current = current.plusDays(1)
        }
        return dates
    }

    fun minusDays(dateString: String, days: Long): String =
        format(parse(dateString).minusDays(days))

    fun plusDays(dateString: String, days: Long): String =
        format(parse(dateString).plusDays(days))
}
