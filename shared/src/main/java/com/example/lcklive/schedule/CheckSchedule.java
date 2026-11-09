package com.example.lcklive.schedule;

import com.example.lcklive.alert.AlertDecider;
import com.example.lcklive.data.MatchInfo;
import com.example.lcklive.data.TeamStatus;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;

/**
 * "다음엔 언제 인터넷으로 확인할까"를 정한다. 배터리를 아끼려고 경기가 없으면 푹 잔다.
 * 안드로이드 코드가 없어서 일반 자바로 테스트할 수 있다.
 */
public final class CheckSchedule {

    static final long MINUTE = 60_000L;
    static final long HOUR = 60 * MINUTE;

    /** 경기 일정이 바뀌었을 수도 있으니, 아무리 길어도 이만큼마다는 한 번 확인 */
    public static final long MAX_SLEEP_MILLIS = 6 * HOUR;
    /** 시작 시간이 이만큼 지났는데도 시작 안 한 경기는 데이터가 이상한 것으로 보고 무시 */
    static final long STALE_AFTER_MILLIS = 3 * HOUR;

    private CheckSchedule() {
    }

    /** 백그라운드 알림 확인: 다음 확인까지 기다릴 시간 */
    public static long nextCheckDelay(TeamStatus status, long now) {
        if (status.live != null) {
            return 2 * MINUTE;                                 // 경기 중: 세트 결과를 빨리 알려주려고 자주
        }
        if (status.next != null) {
            long untilStart = status.next.startTimeMillis - now;
            long untilReminder = untilStart - AlertDecider.REMIND_BEFORE_MILLIS;
            if (untilReminder > MINUTE) {
                return Math.min(untilReminder, MAX_SLEEP_MILLIS);   // "10분 전" 알림 시각까지 잔다
            }
            if (untilStart > 0) {
                return untilStart + MINUTE;                    // 경기 시작 직후에 깨어남
            }
            if (untilStart > -STALE_AFTER_MILLIS) {
                return 2 * MINUTE;                             // 시작이 늦어지는 중
            }
        }
        return MAX_SLEEP_MILLIS;                               // 잡힌 경기 없음
    }

    /** 타일: 다시 그릴 때까지의 시간 */
    public static long tileFreshness(TeamStatus status, long now, ZoneId zone) {
        if (status.live != null) {
            return MINUTE;                                     // 경기 중: 1분마다
        }
        long wait = untilNextMidnight(now, zone) + MINUTE;     // 날짜가 바뀌면 "오늘" 표시도 바뀌어야 함
        MatchInfo next = status.next;
        if (next != null) {
            long untilStart = next.startTimeMillis - now;
            if (untilStart > 0) {
                wait = Math.min(wait, untilStart + MINUTE);    // 시작하면 LIVE 로 바꿔 그림
            } else if (untilStart > -STALE_AFTER_MILLIS) {
                wait = 2 * MINUTE;                             // 시작이 늦어지는 중
            }
        }
        return Math.max(MINUTE, Math.min(wait, MAX_SLEEP_MILLIS));
    }

    /** 경기가 "오늘"(시계 시간대 기준) 열리는지 */
    public static boolean isToday(MatchInfo match, long now, ZoneId zone) {
        return match != null && date(match.startTimeMillis, zone).equals(date(now, zone));
    }

    private static long untilNextMidnight(long now, ZoneId zone) {
        long midnight = date(now, zone).plusDays(1).atStartOfDay(zone).toInstant().toEpochMilli();
        return midnight - now;
    }

    private static LocalDate date(long millis, ZoneId zone) {
        return Instant.ofEpochMilli(millis).atZone(zone).toLocalDate();
    }
}
