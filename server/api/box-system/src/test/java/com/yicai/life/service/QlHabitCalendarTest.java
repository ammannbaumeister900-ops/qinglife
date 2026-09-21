package com.yicai.life.service;

import org.junit.jupiter.api.Test;
import java.time.LocalDate;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class QlHabitCalendarTest {
    private final LocalDate start=LocalDate.of(2026,9,1);
    private QlHabitCalendar.Pause pause(int from,Integer through) { return new QlHabitCalendar.Pause(start.plusDays(from-1),through==null?null:start.plusDays(through-1)); }
    private QlHabitCalendar.Progress progress(int day, boolean paused, QlHabitCalendar.Pause... intervals) { return QlHabitCalendar.calculate(start,14,start.plusDays(day-1),paused,Arrays.asList(intervals)); }
    @Test void missedDaysDoNotExtendAndLastDayIsStillRecordable() {
        assertEquals(7,progress(7,false).day);
        assertTrue(progress(14,false).canRecord);
        assertFalse(progress(14,false).completed);
        assertTrue(progress(15,false).completed);
        assertFalse(progress(15,false).canRecord);
        assertEquals(start.plusDays(13),progress(15,false).endDate);
    }
    @Test void pauseTodayFreezesTodayEvenAfterWeeks() {
        QlHabitCalendar.Progress p=progress(28,true,pause(3,null));
        assertEquals(3,p.day); assertFalse(p.completed); assertFalse(p.canRecord); assertNull(p.endDate);
    }
    @Test void resumeOnLaterDateContinuesFrozenDay() {
        QlHabitCalendar.Progress p=progress(6,false,pause(3,5));
        assertEquals(3,p.day); assertTrue(p.canRecord); assertEquals(start.plusDays(16),p.endDate);
        assertTrue(progress(18,false,pause(3,5)).completed);
    }
    @Test void sameDayResumeDoesNotRestoreExcludedDate() {
        assertEquals(3,progress(3,false,pause(3,3)).day);
        assertFalse(progress(3,false,pause(3,3)).canRecord);
        assertEquals(3,progress(4,false,pause(3,3)).day);
        assertTrue(progress(4,false,pause(3,3)).canRecord);
    }
    @Test void repeatedAndAdjacentPausesNeverDoubleCount() {
        QlHabitCalendar.Progress p=progress(8,false,pause(3,3),pause(3,5),pause(6,7));
        assertEquals(3,p.day); assertEquals(start.plusDays(18),p.endDate);
    }
    @Test void twentyOneDayPlanAndMonthBoundary() {
        LocalDate monthEnd=LocalDate.of(2026,12,31);
        QlHabitCalendar.Progress p=QlHabitCalendar.calculate(monthEnd,21,monthEnd.plusDays(21),false,Collections.emptyList());
        assertTrue(p.completed); assertEquals(LocalDate.of(2027,1,20),p.endDate);
    }
    @Test void futurePlanCannotRecordAndLatePauseCannotUndoExpiry() {
        assertFalse(QlHabitCalendar.calculate(start,14,start.minusDays(1),false,Collections.emptyList()).canRecord);
        assertTrue(progress(16,true,pause(16,null)).completed);
    }
}