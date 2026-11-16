package com.example.busnowbar.util

import com.example.busnowbar.data.AlertSchedule
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
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

    private fun schedule(
        id: Long,
        startHour: Int,
        endHour: Int,
        paused: Boolean = false,
        days: Set<java.time.DayOfWeek> = java.time.DayOfWeek.entries.toSet(),
    ) = AlertSchedule(id, days, startHour, 0, endHour, 0, paused)

    @Test
    fun `여러 일정 중 가장 가까운 시작 시각을 고른다`() {
        val schedules = listOf(schedule(1, 18, 19), schedule(2, 7, 8))
        assertEquals(2 * hourMs, DailySchedule.delayMillisUntilNextStart(at(5), schedules))
    }

    @Test
    fun `일시정지된 일정은 다음 시작 계산에서 빠진다`() {
        val schedules = listOf(schedule(1, 7, 8, paused = true), schedule(2, 18, 19))
        assertEquals(13 * hourMs, DailySchedule.delayMillisUntilNextStart(at(5), schedules))
    }

    @Test
    fun `동작할 일정이 없으면 다음 시작은 null`() {
        assertNull(DailySchedule.delayMillisUntilNextStart(at(5), emptyList()))
        assertNull(DailySchedule.delayMillisUntilNextStart(at(5), listOf(schedule(1, 7, 8, paused = true))))
        assertNull(DailySchedule.delayMillisUntilNextStart(at(5), listOf(schedule(1, 7, 8, days = emptySet()))))
    }

    @Test
    fun `진행 중인 일정의 종료 시각을 돌려준다`() {
        val schedules = listOf(schedule(1, 7, 8), schedule(2, 7, 9))
        assertEquals(at(9), DailySchedule.runningWindowEnd(at(7, 30), schedules))
    }

    @Test
    fun `시작 직전 1분 이내면 진행 중으로 본다`() {
        val schedules = listOf(schedule(1, 7, 8))
        assertEquals(at(8), DailySchedule.runningWindowEnd(at(6, 59).withSecond(30), schedules))
        assertNull(DailySchedule.runningWindowEnd(at(6, 50), schedules))
    }

    @Test
    fun `종료 시각이 지났거나 일시정지거나 요일이 아니면 진행 중이 아니다`() {
        val now = at(7, 30)
        assertNull(DailySchedule.runningWindowEnd(at(8), listOf(schedule(1, 7, 8))))
        assertNull(DailySchedule.runningWindowEnd(now, listOf(schedule(1, 7, 8, paused = true))))
        assertNull(DailySchedule.runningWindowEnd(now, listOf(schedule(1, 7, 8, days = setOf(now.plusDays(1).dayOfWeek)))))
    }
}
