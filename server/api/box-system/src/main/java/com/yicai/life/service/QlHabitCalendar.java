package com.yicai.life.service;

import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.*;

/** Calendar progress is independent of whether a daily record was submitted. */
public final class QlHabitCalendar {
    private QlHabitCalendar() { }
    public static final class Pause {
        public final LocalDate start, end;
        public Pause(LocalDate start, LocalDate end) { this.start = start; this.end = end; }
    }
    public static final class Progress {
        public final int day;
        public final boolean completed, canRecord;
        public final LocalDate endDate;
        Progress(int day, boolean completed, boolean canRecord, LocalDate endDate) {
            this.day = day; this.completed = completed; this.canRecord = canRecord; this.endDate = endDate;
        }
    }
    public static Progress calculate(LocalDate start, int length, LocalDate today, boolean paused, List<Pause> pauses) {
        if (length != 14 && length != 21) throw new IllegalArgumentException("Invalid plan length");
        // Merge adjacent/overlapping intervals; repeated pause/resume on a date excludes it only once.
        List<Pause> sorted = new ArrayList<>(pauses);
        sorted.sort(Comparator.comparing(p -> p.start));
        List<Pause> ranges = new ArrayList<>();
        for (Pause p : sorted) {
            LocalDate end = p.end == null ? LocalDate.MAX : p.end;
            if (end.isBefore(p.start)) throw new IllegalArgumentException("Invalid pause interval");
            if (!ranges.isEmpty()) {
                Pause last = ranges.get(ranges.size() - 1);
                if (last.end.equals(LocalDate.MAX) || !p.start.isAfter(last.end.plusDays(1))) {
                    ranges.set(ranges.size() - 1, new Pause(last.start, end.isAfter(last.end) ? end : last.end));
                    continue;
                }
            }
            ranges.add(new Pause(p.start, end));
        }
        long elapsed = Math.max(0, ChronoUnit.DAYS.between(start, today));
        boolean excludedToday = false;
        LocalDate projectedEnd = start.plusDays(length - 1);
        for (Pause p : ranges) {
            LocalDate from = p.start.isBefore(start) ? start : p.start;
            LocalDate through = p.end.isBefore(today.minusDays(1)) ? p.end : today.minusDays(1);
            if (!through.isBefore(from)) elapsed -= ChronoUnit.DAYS.between(from, through) + 1;
            if (!today.isBefore(from) && !today.isAfter(p.end)) excludedToday = true;
            if (projectedEnd != null && !from.isAfter(projectedEnd)) {
                if (p.end.equals(LocalDate.MAX)) projectedEnd = null;
                else if (!p.end.isBefore(from)) projectedEnd = projectedEnd.plusDays(ChronoUnit.DAYS.between(from, p.end) + 1);
            }
        }
        boolean completed = elapsed >= length;
        return new Progress((int)Math.min(length, elapsed + 1), completed,
                !completed && !paused && !excludedToday && !today.isBefore(start), projectedEnd);
    }
}