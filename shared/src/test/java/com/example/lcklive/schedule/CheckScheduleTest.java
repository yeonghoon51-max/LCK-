package com.example.lcklive.schedule;

import static com.example.lcklive.schedule.CheckSchedule.HOUR;
import static com.example.lcklive.schedule.CheckSchedule.MAX_SLEEP_MILLIS;
import static com.example.lcklive.schedule.CheckSchedule.MINUTE;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import com.example.lcklive.data.MatchInfo;
import com.example.lcklive.data.TeamStatus;

import org.junit.Test;

import java.time.ZoneId;
import java.time.ZonedDateTime;

public class CheckScheduleTest {

    static final ZoneId SEOUL = ZoneId.of("Asia/Seoul");

    static long seoul(int month, int day, int hour, int minute) {
        return ZonedDateTime.of(2026, month, day, hour, minute, 0, 0, SEOUL).toInstant().toEpochMilli();
    }

    static TeamStatus nextAt(long start) {
        MatchInfo m = new MatchInfo();
        m.startTimeMillis = start;
        TeamStatus s = new TeamStatus();
        s.next = m;
        return s;
    }

    static TeamStatus live() {
        TeamStatus s = new TeamStatus();
        s.live = new MatchInfo();
        return s;
    }

    // ── 백그라운드 확인 ──

    @Test
    public void noMatch_sleepsSixHours() {
        assertEquals(MAX_SLEEP_MILLIS, CheckSchedule.nextCheckDelay(new TeamStatus(), seoul(11, 10, 12, 0)));
    }

    @Test
    public void matchInFourDays_sleepsAtMostSixHours() {
        long now = seoul(11, 10, 12, 0);
        assertEquals(MAX_SLEEP_MILLIS, CheckSchedule.nextCheckDelay(nextAt(seoul(11, 14, 19, 0)), now));
    }

    @Test
    public void matchInThreeHours_wakesForTenMinuteReminder() {
        long now = seoul(11, 14, 16, 0);
        assertEquals(3 * HOUR - 10 * MINUTE, CheckSchedule.nextCheckDelay(nextAt(seoul(11, 14, 19, 0)), now));
    }

    @Test
    public void matchInFiveMinutes_wakesRightAfterStart() {
        long now = seoul(11, 14, 18, 55);
        assertEquals(6 * MINUTE, CheckSchedule.nextCheckDelay(nextAt(seoul(11, 14, 19, 0)), now));
    }

    @Test
    public void live_checksEveryTwoMinutes() {
        assertEquals(2 * MINUTE, CheckSchedule.nextCheckDelay(live(), seoul(11, 14, 19, 30)));
    }

    @Test
    public void delayedStart_checksOften_butOldDataIsIgnored() {
        long start = seoul(11, 14, 19, 0);
        assertEquals("15분 지연", 2 * MINUTE, CheckSchedule.nextCheckDelay(nextAt(start), start + 15 * MINUTE));
        assertEquals("5시간 지나도 시작 안 함 → 데이터 이상", MAX_SLEEP_MILLIS,
                CheckSchedule.nextCheckDelay(nextAt(start), start + 5 * HOUR));
    }

    // ── 타일 ──

    @Test
    public void tile_noMatchToday_waitsUntilMidnight() {
        long now = seoul(11, 10, 22, 0);   // 밤 10시 → 자정까지 2시간
        assertEquals(2 * HOUR + MINUTE, CheckSchedule.tileFreshness(nextAt(seoul(11, 14, 19, 0)), now, SEOUL));
    }

    @Test
    public void tile_noMatchToday_morning_cappedAtSixHours() {
        long now = seoul(11, 10, 9, 0);
        assertEquals(MAX_SLEEP_MILLIS, CheckSchedule.tileFreshness(new TeamStatus(), now, SEOUL));
    }

    @Test
    public void tile_matchToday_waitsUntilStart() {
        long now = seoul(11, 14, 17, 0);
        assertEquals(2 * HOUR + MINUTE, CheckSchedule.tileFreshness(nextAt(seoul(11, 14, 19, 0)), now, SEOUL));
    }

    @Test
    public void tile_live_everyMinute() {
        assertEquals(MINUTE, CheckSchedule.tileFreshness(live(), seoul(11, 14, 19, 30), SEOUL));
    }

    @Test
    public void isToday_usesWatchTimeZone() {
        long now = seoul(11, 14, 9, 0);
        MatchInfo tonight = nextAt(seoul(11, 14, 23, 0)).next;
        MatchInfo tomorrowEarly = nextAt(seoul(11, 15, 1, 0)).next;
        assertTrue(CheckSchedule.isToday(tonight, now, SEOUL));
        assertFalse(CheckSchedule.isToday(tomorrowEarly, now, SEOUL));
        assertFalse(CheckSchedule.isToday(null, now, SEOUL));
    }
}
