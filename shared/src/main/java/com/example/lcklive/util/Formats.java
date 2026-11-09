package com.example.lcklive.util;

import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Locale;

/** 폰 화면, 워치 화면, 타일이 같이 쓰는 표시 형식 */
public final class Formats {

    private static final DateTimeFormatter MATCH_TIME =
            DateTimeFormatter.ofPattern("M/d (E) HH:mm", Locale.KOREAN);
    private static final DateTimeFormatter TIME =
            DateTimeFormatter.ofPattern("HH:mm", Locale.KOREAN);
    private static final DateTimeFormatter CLOCK =
            DateTimeFormatter.ofPattern("HH:mm:ss", Locale.KOREAN);

    private Formats() {
    }

    /** 2300 → "+2.3k", -800 → "-0.8k" */
    public static String goldDiff(int diff) {
        String sign = diff > 0 ? "+" : diff < 0 ? "-" : "±";
        return String.format(Locale.US, "%s%.1fk", sign, Math.abs(diff) / 1000.0);
    }

    /** 경기 시작 시각, 예: "11/6 (금) 22:00" */
    public static String matchTime(long millis) {
        return Instant.ofEpochMilli(millis).atZone(ZoneId.systemDefault()).format(MATCH_TIME);
    }

    /** 오늘 경기 시각, 예: "19:00" */
    public static String time(long millis) {
        return Instant.ofEpochMilli(millis).atZone(ZoneId.systemDefault()).format(TIME);
    }

    /** 업데이트 시각, 예: "01:08:46" */
    public static String clock(long millis) {
        return Instant.ofEpochMilli(millis).atZone(ZoneId.systemDefault()).format(CLOCK);
    }
}
