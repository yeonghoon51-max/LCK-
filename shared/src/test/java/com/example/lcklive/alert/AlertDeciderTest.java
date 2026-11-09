package com.example.lcklive.alert;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import com.example.lcklive.data.MatchInfo;
import com.example.lcklive.data.TeamStatus;

import org.junit.Test;

import java.util.List;

/** 경기 흐름을 순서대로 흘려보내면서 알림이 딱 한 번씩 나오는지 확인 */
public class AlertDeciderTest {

    static final long START = 1_800_000_000_000L;

    static MatchInfo match(String myCode, int myWins, int oppWins) {
        MatchInfo m = new MatchInfo();
        m.matchId = "M1";
        m.myCode = myCode;
        m.oppCode = "GEN";
        m.leagueName = "Worlds";
        m.startTimeMillis = START;
        m.myWins = myWins;
        m.oppWins = oppWins;
        return m;
    }

    static TeamStatus upcoming(MatchInfo m) {
        TeamStatus s = new TeamStatus();
        s.next = m;
        return s;
    }

    static TeamStatus live(MatchInfo m) {
        TeamStatus s = new TeamStatus();
        s.live = m;
        return s;
    }

    static TeamStatus finished(MatchInfo m) {
        TeamStatus s = new TeamStatus();
        s.lastCompleted = m;
        return s;
    }

    @Test
    public void fullMatchFlow() {
        AlertDecider.Memory mem = new AlertDecider.Memory();
        long eightMinBefore = START - 8 * 60_000;

        List<AlertDecider.Alert> r = AlertDecider.decide(mem, upcoming(match("T1", 0, 0)), eightMinBefore);
        assertEquals("⏰ 8분 후 T1 경기", r.get(0).title);
        assertTrue("미리 알림은 한 번만", AlertDecider.decide(mem, upcoming(match("T1", 0, 0)), eightMinBefore).isEmpty());

        r = AlertDecider.decide(mem, live(match("T1", 0, 0)), START);
        assertEquals("🔴 T1 경기 시작!", r.get(0).title);
        assertTrue("시작 알림은 한 번만", AlertDecider.decide(mem, live(match("T1", 0, 0)), START).isEmpty());

        r = AlertDecider.decide(mem, live(match("T1", 1, 0)), START);
        assertEquals("✅ T1 세트 승리!", r.get(0).title);
        assertEquals("T1 1 : 0 GEN", r.get(0).text);

        r = AlertDecider.decide(mem, live(match("T1", 1, 1)), START);
        assertEquals("❌ T1 세트 패배", r.get(0).title);

        r = AlertDecider.decide(mem, finished(match("T1", 3, 1)), START);
        assertEquals("🏆 T1 승리!", r.get(0).title);
        assertEquals("T1 3 : 1 GEN · 경기 종료", r.get(0).text);
        assertTrue("종료 알림은 한 번만", AlertDecider.decide(mem, finished(match("T1", 3, 1)), START).isEmpty());
    }

    @Test
    public void usesChosenTeamName() {
        List<AlertDecider.Alert> r = AlertDecider.decide(new AlertDecider.Memory(), live(match("HLE", 0, 0)), START);
        assertEquals("🔴 HLE 경기 시작!", r.get(0).title);
    }
}
