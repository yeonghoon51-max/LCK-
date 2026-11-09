package com.example.lcklive.alert;

import com.example.lcklive.data.MatchInfo;
import com.example.lcklive.data.TeamStatus;

import java.util.ArrayList;
import java.util.List;

/**
 * "이전에 본 상태"와 "지금 상태"를 비교해서 어떤 알림을 보낼지 정한다.
 * 안드로이드 코드가 하나도 없어서 일반 자바로 테스트할 수 있다.
 */
public final class AlertDecider {

    /** 경기 시작 몇 분 전에 미리 알려줄지 */
    public static final long REMIND_BEFORE_MILLIS = 10 * 60 * 1000L;

    /** 마지막으로 확인했던 상태 (SharedPreferences 에 저장됨) */
    public static class Memory {
        public String matchId;
        public String state;            // "LIVE" / "COMPLETED" / null
        public int myWins, oppWins;
        public String remindedMatchId;  // 이미 "곧 시작" 알림을 보낸 경기
    }

    public static class Alert {
        public final String title;
        public final String text;

        Alert(String title, String text) {
            this.title = title;
            this.text = text;
        }

        @Override
        public String toString() {
            return title + " / " + text;
        }
    }

    private AlertDecider() {
    }

    /** 보낼 알림 목록을 돌려주고, memory 는 지금 상태로 갱신한다 */
    public static List<Alert> decide(Memory memory, TeamStatus now, long nowMillis) {
        List<Alert> alerts = new ArrayList<>();
        MatchInfo live = now.live;

        if (live != null) {
            boolean newMatch = !live.matchId.equals(memory.matchId) || !"LIVE".equals(memory.state);
            if (newMatch) {
                alerts.add(new Alert("🔴 " + team(live) + " 경기 시작!",
                        team(live) + " vs " + live.oppCode + " · " + live.leagueName));
            } else if (live.myWins != memory.myWins || live.oppWins != memory.oppWins) {
                boolean weTook = live.myWins > memory.myWins;
                alerts.add(new Alert(weTook ? "✅ " + team(live) + " 세트 승리!" : "❌ " + team(live) + " 세트 패배",
                        team(live) + " " + live.scoreText() + " " + live.oppCode));
            }
            memory.matchId = live.matchId;
            memory.state = "LIVE";
            memory.myWins = live.myWins;
            memory.oppWins = live.oppWins;
        } else if ("LIVE".equals(memory.state)) {
            // 아까는 라이브였는데 지금은 아님 → 경기 끝
            MatchInfo done = now.lastCompleted;
            if (done != null && done.matchId.equals(memory.matchId)) {
                alerts.add(new Alert(done.wonSeries() ? "🏆 " + team(done) + " 승리!" : team(done) + " 패배",
                        team(done) + " " + done.scoreText() + " " + done.oppCode + " · 경기 종료"));
            } else {
                alerts.add(new Alert("경기 종료", "응원하는 팀의 경기가 끝났어요"));
            }
            memory.state = "COMPLETED";
        }

        // 곧 시작하는 경기 미리 알림
        MatchInfo next = now.next;
        if (next != null && !next.matchId.equals(memory.remindedMatchId)) {
            long untilStart = next.startTimeMillis - nowMillis;
            if (untilStart > 0 && untilStart <= REMIND_BEFORE_MILLIS) {
                long minutes = Math.max(1, Math.round(untilStart / 60_000.0));
                alerts.add(new Alert("⏰ " + minutes + "분 후 " + team(next) + " 경기",
                        team(next) + " vs " + next.oppCode + " · " + next.leagueName));
                memory.remindedMatchId = next.matchId;
            }
        }
        return alerts;
    }

    private static String team(MatchInfo m) {
        return m.myCode != null ? m.myCode : "내 팀";
    }
}
