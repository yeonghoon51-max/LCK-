package com.example.lcklive.data;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import org.json.JSONArray;
import org.json.JSONObject;
import org.junit.Test;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** API 응답(JSON) 파싱 테스트. 실제 응답 모양을 흉내 낸 가짜 JSON 을 쓴다. */
public class LolEsportsApiTest {

    static final String T1_ID = "98767991853197861";
    static final String GEN_ID = "100205573495116443";

    /** T1 vs GEN 경기 이벤트 하나 (tFirst 면 T1 이 teams[0]) */
    static String t1VsGen(String state, String start, boolean tFirst, int t1Wins, int genWins) {
        String t1 = "{\"id\":\"" + T1_ID + "\",\"name\":\"T1\",\"code\":\"T1\",\"result\":{\"gameWins\":" + t1Wins + "}}";
        String gen = "{\"id\":\"" + GEN_ID + "\",\"name\":\"Gen.G\",\"code\":\"GEN\",\"result\":{\"gameWins\":" + genWins + "}}";
        return "{\"startTime\":\"" + start + "\",\"state\":\"" + state + "\",\"type\":\"match\","
                + "\"league\":{\"name\":\"Worlds\"},"
                + "\"match\":{\"id\":\"M-" + start + "\",\"teams\":[" + (tFirst ? t1 + "," + gen : gen + "," + t1) + "],"
                + "\"strategy\":{\"count\":5}}}";
    }

    static final String HLE_VS_DK = "{\"startTime\":\"2026-10-29T08:00:00Z\",\"state\":\"completed\",\"type\":\"match\","
            + "\"league\":{\"name\":\"LCK\"},\"match\":{\"id\":\"X\",\"teams\":["
            + "{\"name\":\"Hanwha Life Esports\",\"code\":\"HLE\",\"result\":{\"gameWins\":2}},"
            + "{\"name\":\"Dplus KIA\",\"code\":\"DK\",\"result\":{\"gameWins\":0}}],\"strategy\":{\"count\":3}}}";
    static final String SHOW = "{\"startTime\":\"2026-10-29T07:00:00Z\",\"state\":\"completed\",\"type\":\"show\"}";

    static JSONArray schedule() {
        return new JSONArray("[" + SHOW + "," + HLE_VS_DK + ","
                + t1VsGen("completed", "2026-10-20T08:00:00Z", false, 3, 1) + ","
                + t1VsGen("completed", "2026-10-25T08:00:00Z", true, 1, 3) + ","
                + t1VsGen("unstarted", "2026-11-02T08:00:00Z", false, 0, 0) + ","
                + t1VsGen("unstarted", "2026-11-01T08:00:00Z", true, 0, 0) + "]");
    }

    static long millis(String iso) {
        return Instant.parse(iso).toEpochMilli();
    }

    @Test
    public void schedule_t1Fan() throws Exception {
        TeamStatus st = new TeamStatus();
        LolEsportsApi.applySchedule(schedule(), st, "T1");

        assertNull(st.live);
        assertEquals("가장 빠른 예정 경기", millis("2026-11-01T08:00:00Z"), st.next.startTimeMillis);
        assertEquals("GEN", st.next.oppCode);
        assertEquals(5, st.next.bestOf);
        assertEquals("가장 최근 경기 1:3", 1, st.lastCompleted.myWins);
        assertEquals(3, st.lastCompleted.oppWins);
        assertFalse(st.lastCompleted.wonSeries());
    }

    @Test
    public void schedule_genFan_scoresAreFlipped() throws Exception {
        TeamStatus st = new TeamStatus();
        LolEsportsApi.applySchedule(schedule(), st, "GEN");

        assertEquals("GEN", st.next.myCode);
        assertEquals("T1", st.next.oppCode);
        assertEquals(3, st.lastCompleted.myWins);
        assertTrue(st.lastCompleted.wonSeries());
    }

    @Test
    public void schedule_hleFan_onlyHleMatches() throws Exception {
        TeamStatus st = new TeamStatus();
        LolEsportsApi.applySchedule(schedule(), st, "HLE");

        assertNull(st.next);
        assertEquals("DK", st.lastCompleted.oppCode);
    }

    @Test
    public void live_whenMyTeamIsSecond() throws Exception {
        JSONArray live = new JSONArray("[" + t1VsGen("inProgress", "2026-10-29T08:00:00Z", false, 2, 1) + "]");

        MatchInfo m = LolEsportsApi.findTeamMatch(live, MatchInfo.State.LIVE, "T1");
        assertEquals("2 : 1", m.scoreText());
        assertEquals(T1_ID, m.myTeamId);

        MatchInfo g = LolEsportsApi.findTeamMatch(live, MatchInfo.State.LIVE, "GEN");
        assertEquals("1 : 2", g.scoreText());
    }

    static final String WINDOW = "{\"gameMetadata\":{"
            + "\"blueTeamMetadata\":{\"esportsTeamId\":\"" + GEN_ID + "\"},"
            + "\"redTeamMetadata\":{\"esportsTeamId\":\"" + T1_ID + "\"}},"
            + "\"frames\":["
            + "{\"gameState\":\"in_game\",\"blueTeam\":{\"totalGold\":1,\"totalKills\":0,\"dragons\":[]},"
            + "\"redTeam\":{\"totalGold\":1,\"totalKills\":0,\"dragons\":[]}},"
            + "{\"gameState\":\"in_game\",\"blueTeam\":{\"totalGold\":40100,\"totalKills\":8,\"towers\":3,\"barons\":0,\"dragons\":[\"ocean\"]},"
            + "\"redTeam\":{\"totalGold\":42400,\"totalKills\":12,\"towers\":5,\"barons\":1,\"dragons\":[\"infernal\",\"cloud\"]}}]}";

    @Test
    public void window_usesLastFrame_redSide() throws Exception {
        GameStats g = LolEsportsApi.parseWindow(new JSONObject(WINDOW), T1_ID);
        assertFalse(g.myBlue);
        assertEquals(12, g.myKills);
        assertEquals(8, g.oppKills);
        assertEquals(2300, g.goldDiff());
        assertEquals(2, g.myDragons);
        assertEquals(1, g.myBarons);
        assertEquals(5, g.myTowers);
    }

    @Test
    public void window_blueSide() throws Exception {
        GameStats g = LolEsportsApi.parseWindow(new JSONObject(WINDOW), GEN_ID);
        assertTrue(g.myBlue);
        assertEquals(-2300, g.goldDiff());
    }

    @Test
    public void liveStatsStartingTime_isRoundedTo10Seconds() {
        assertTrue(LolEsportsApi.liveStatsStartingTime().matches("\\d{4}-\\d\\d-\\d\\dT\\d\\d:\\d\\d:\\d0Z"));
    }

    @Test
    public void findLeagueId() throws Exception {
        JSONObject leagues = new JSONObject("{\"data\":{\"leagues\":["
                + "{\"id\":\"1\",\"slug\":\"lpl\"},{\"id\":\"98767991310872058\",\"slug\":\"lck\"}]}}");
        assertEquals("98767991310872058", LolEsportsApi.findLeagueId(leagues, "lck"));
        assertNull(LolEsportsApi.findLeagueId(leagues, "lec"));
    }

    @Test
    public void collectTeams_noDuplicates_noTbd() throws Exception {
        String tbd = "{\"type\":\"match\",\"match\":{\"teams\":[{\"name\":\"TBD\",\"code\":\"TBD\"},{\"name\":\"T1\",\"code\":\"T1\"}]}}";
        JSONArray events = schedule();
        events.put(new JSONObject(tbd));

        Map<String, TeamInfo> teams = new LinkedHashMap<>();
        LolEsportsApi.collectTeams(events, teams);

        assertEquals(List.of("HLE", "DK", "GEN", "T1"), List.copyOf(teams.keySet()));
        assertEquals("Gen.G", teams.get("GEN").name);
    }
}
