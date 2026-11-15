package com.example.busnowbar.util

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.ZoneId
import java.time.ZonedDateTime

class DailyScheduleTest {

    private val seoul = ZoneId.of("Asia/Seoul")

    private fun at(hour: Int, minute: Int = 0, day: Int = 15): ZonedDateTime =
        ZonedDateTime.of(2026, 10, day, hour, minute, 0, 0, seoul)

    private val hourMs = 60L * 60 * 1000
    private val minuteMs = 60L * 1000

    @Test
    fun `같은 날 07시 전이면 그날 07시까지 기다린다`() {
        assertEquals(2 * hourMs, DailySchedule.delayMillisUntilNext(at(5)))
        assertEquals(30 * minuteMs, DailySchedule.delayMillisUntilNext(at(6, 30)))
    }

    @Test
    fun `07시를 지났으면 다음 날 07시로 넘어간다`() {
        assertEquals(23 * hourMs, DailySchedule.delayMillisUntilNext(at(8)))
    }

    @Test
    fun `정확히 07시 정각이면 다음 날로 넘긴다`() {
        assertEquals(24 * hourMs, DailySchedule.delayMillisUntilNext(at(7)))
    }

    @Test
    fun `종료 시각 hour minute 도 바꿔서 계산된다`() {
        assertEquals(10 * minuteMs, DailySchedule.delayMillisUntilNext(at(7), hour = 7, minute = 10))
    }

    @Test
    fun `todayAt 은 날짜를 넘기지 않고 같은 날 시각을 돌려준다`() {
        val endAt = DailySchedule.todayAt(at(7, 15), hour = 7, minute = 10)
        assertTrue(endAt.isBefore(at(7, 15)))
        assertEquals(at(7, 10), endAt)
    }

    @Test
    fun `activeDays에 오늘이 포함되면 평소처럼 그날 시각을 쓴다`() {
        val now = at(5)
        assertEquals(2 * hourMs, DailySchedule.delayMillisUntilNext(now, activeDays = setOf(now.dayOfWeek)))
    }

    @Test
    fun `activeDays에 오늘이 없으면 다음으로 포함된 요일까지 넘어간다`() {
        val now = at(5)
        val tomorrow = now.plusDays(1).dayOfWeek
        assertEquals(24 * hourMs + 2 * hourMs, DailySchedule.delayMillisUntilNext(now, activeDays = setOf(tomorrow)))
    }

    @Test
    fun `activeDays가 이틀 뒤 요일만 포함하면 이틀 뒤로 넘어간다`() {
        val now = at(5)
        val twoDaysLater = now.plusDays(2).dayOfWeek
        assertEquals(2 * 24 * hourMs + 2 * hourMs, DailySchedule.delayMillisUntilNext(now, activeDays = setOf(twoDaysLater)))
    }

    @Test
    fun `activeDays가 비어있으면 요일 필터 없이 다음 시각만 돌려준다`() {
        assertEquals(2 * hourMs, DailySchedule.delayMillisUntilNext(at(5), activeDays = emptySet()))
    }
}
