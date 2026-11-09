package com.example.lcklive.data;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URI;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * lolesports.com 웹사이트가 내부적으로 쓰는 API를 호출한다.
 *
 * 주의: 라이엇의 "공식 공개 API"가 아니다. lolesports.com 프론트엔드에 박혀 있는
 * 공개 키를 그대로 쓰는 비공식 방식이라, 라이엇이 바꾸면 동작이 멈출 수 있다.
 * 개인 학습/개인용 앱 용도로만 쓰자.
 */
public class LolEsportsApi {

    private static final String API_BASE = "https://esports-api.lolesports.com/persisted/gw/";
    private static final String FEED_BASE = "https://feed.lolesports.com/livestats/v1/";
    // lolesports.com 웹사이트에 공개되어 있는 키 (비공식)
    private static final String API_KEY = "0TvQnueqKa5mxJntVWt0w4LpLfEkrV1Ta8rQBb9Z";
    private static final String LANG = "ko-KR";

    /** 어느 리그의 팀들을 팀 선택 목록에 보여줄지 */
    public static final String LCK_SLUG = "lck";

    /** 응원하는 팀의 약자 (예: "T1", "GEN", "HLE") */
    private final String teamCode;

    /** 스케줄에서 다음 경기를 못 찾았을 때 미래 페이지를 최대 몇 장 더 볼지 */
    private static final int MAX_EXTRA_PAGES = 2;

    /** 팀 목록을 모을 때 과거 일정을 최대 몇 장까지 볼지 / 몇 팀 모이면 멈출지 */
    private static final int MAX_TEAM_PAGES = 4;
    private static final int LEAGUE_SIZE = 10;

    /** 팀 목록 조회(fetchLeagueTeams)만 할 때는 팀 코드 없이 만들어도 된다 */
    public LolEsportsApi() {
        this(null);
    }

    public LolEsportsApi(String teamCode) {
        this.teamCode = teamCode;
    }

    // ───────────────────────── 공개 메서드 ─────────────────────────

    /** 내 팀의 현재 상태(라이브 / 다음 경기 / 최근 경기)를 한 번에 조회 */
    public TeamStatus fetchStatus() throws IOException, JSONException {
        TeamStatus status = new TeamStatus();
        status.fetchedAtMillis = System.currentTimeMillis();

        // 1) 지금 라이브 중인 경기
        JSONObject live = getJson(API_BASE + "getLive?hl=" + LANG);
        if (live != null) {
            status.live = findTeamMatch(live.getJSONObject("data").getJSONObject("schedule")
                    .optJSONArray("events"), MatchInfo.State.LIVE, teamCode);
        }

        // 2) 스케줄에서 다음 경기 / 최근 경기
        String pageToken = null;
        for (int page = 0; page <= MAX_EXTRA_PAGES; page++) {
            String url = API_BASE + "getSchedule?hl=" + LANG
                    + (pageToken != null ? "&pageToken=" + encode(pageToken) : "");
            JSONObject json = getJson(url);
            if (json == null) break;
            JSONObject schedule = json.getJSONObject("data").getJSONObject("schedule");
            // getLive 가 가끔 늦게 반영되므로, 스케줄에서 진행 중인 경기도 라이브로 인정한다
            applySchedule(schedule.optJSONArray("events"), status, teamCode);

            if (status.next != null) break;
            JSONObject pages = schedule.optJSONObject("pages");
            pageToken = pages == null ? null : optStringOrNull(pages, "newer");
            if (pageToken == null) break;
        }

        // 3) 라이브 경기라면 세트별 실시간 스탯까지 붙인다
        if (status.live != null) {
            try {
                status.live.liveGame = fetchLiveGame(status.live);
            } catch (IOException | JSONException e) {
                // 실시간 스탯은 부가 정보라 실패해도 스코어는 보여준다
                status.live.liveGame = null;
            }
        }
        return status;
    }

    /**
     * 리그(예: LCK)에 참가하는 팀 목록.
     * 팀 목록 API 는 전 세계 팀이 다 나와서 너무 크기 때문에,
     * 그 리그의 최근 일정에 나온 팀들을 모아서 만든다.
     */
    public List<TeamInfo> fetchLeagueTeams(String leagueSlug) throws IOException, JSONException {
        JSONObject leagues = getJson(API_BASE + "getLeagues?hl=" + LANG);
        String leagueId = leagues == null ? null : findLeagueId(leagues, leagueSlug);
        if (leagueId == null) throw new IOException("리그를 찾을 수 없음: " + leagueSlug);

        Map<String, TeamInfo> teams = new LinkedHashMap<>();
        String pageToken = null;
        for (int page = 0; page < MAX_TEAM_PAGES && teams.size() < LEAGUE_SIZE; page++) {
            String url = API_BASE + "getSchedule?hl=" + LANG + "&leagueId=" + leagueId
                    + (pageToken != null ? "&pageToken=" + encode(pageToken) : "");
            JSONObject json = getJson(url);
            if (json == null) break;
            JSONObject schedule = json.getJSONObject("data").getJSONObject("schedule");
            collectTeams(schedule.optJSONArray("events"), teams);

            // 비시즌엔 최근 일정에 팀이 몇 개 안 나오므로 과거 페이지로 거슬러 올라간다
            JSONObject pages = schedule.optJSONObject("pages");
            pageToken = pages == null ? null : optStringOrNull(pages, "older");
            if (pageToken == null) break;
        }

        List<TeamInfo> list = new ArrayList<>(teams.values());
        list.sort(Comparator.comparing(t -> t.name.toLowerCase(Locale.ROOT)));
        return list;
    }

    // ───────────────────────── 실시간 세트 스탯 ─────────────────────────

    private GameStats fetchLiveGame(MatchInfo match) throws IOException, JSONException {
        JSONObject details = getJson(API_BASE + "getEventDetails?hl=" + LANG + "&id=" + match.matchId);
        if (details == null) return null;
        JSONObject matchJson = details.getJSONObject("data").getJSONObject("event").getJSONObject("match");

        if (match.myTeamId == null) {
            match.myTeamId = findTeamId(matchJson.optJSONArray("teams"), teamCode);
        }

        // 진행 중인 세트 찾기
        JSONArray games = matchJson.optJSONArray("games");
        if (games == null) return null;
        JSONObject current = null;
        for (int i = 0; i < games.length(); i++) {
            JSONObject g = games.getJSONObject(i);
            if ("inProgress".equals(g.optString("state"))) {
                current = g;
                break;
            }
        }
        if (current == null) return null;   // 세트 사이 쉬는 시간

        JSONObject window = getJson(FEED_BASE + "window/" + current.getString("id")
                + "?startingTime=" + liveStatsStartingTime());
        if (window == null) return null;

        GameStats stats = parseWindow(window, match.myTeamId);
        if (stats != null) stats.gameNumber = current.optInt("number");
        return stats;
    }

    /**
     * livestats 피드는 "이 시각부터의 프레임"을 돌려준다.
     * 너무 최근 시각을 주면 데이터가 없어서 60초 전, 10초 단위로 맞춘다.
     */
    static String liveStatsStartingTime() {
        long sec = Instant.now().getEpochSecond() - 60;
        sec -= sec % 10;
        return Instant.ofEpochSecond(sec).toString();   // 예: 2026-10-29T12:34:50Z
    }

    // ───────────────────────── JSON 파싱 (테스트하기 쉽게 static) ─────────────────────────

    /** 이벤트 목록에서 내 팀이 나오는 첫 경기를 찾는다 */
    static MatchInfo findTeamMatch(JSONArray events, MatchInfo.State state, String teamCode)
            throws JSONException {
        if (events == null) return null;
        for (int i = 0; i < events.length(); i++) {
            MatchInfo m = parseTeamEvent(events.getJSONObject(i), teamCode);
            if (m != null) {
                m.state = state;
                return m;
            }
        }
        return null;
    }

    /** getSchedule 이벤트들을 훑어서 다음 경기 / 최근 경기 / (라이브 보정)을 채운다 */
    static void applySchedule(JSONArray events, TeamStatus status, String teamCode) throws JSONException {
        if (events == null) return;
        for (int i = 0; i < events.length(); i++) {
            JSONObject e = events.getJSONObject(i);
            MatchInfo m = parseTeamEvent(e, teamCode);
            if (m == null) continue;

            switch (e.optString("state")) {
                case "completed":
                    m.state = MatchInfo.State.COMPLETED;
                    // 스케줄은 시간순이므로 나중에 나온 게 더 최근 경기
                    if (status.lastCompleted == null
                            || m.startTimeMillis >= status.lastCompleted.startTimeMillis) {
                        status.lastCompleted = m;
                    }
                    break;
                case "inProgress":
                    m.state = MatchInfo.State.LIVE;
                    if (status.live == null) status.live = m;
                    break;
                default: // "unstarted"
                    m.state = MatchInfo.State.UPCOMING;
                    if (status.next == null || m.startTimeMillis < status.next.startTimeMillis) {
                        status.next = m;
                    }
                    break;
            }
        }
    }

    /** 이벤트 하나가 내 팀 경기면 MatchInfo로, 아니면 null */
    static MatchInfo parseTeamEvent(JSONObject event, String teamCode) throws JSONException {
        if (!"match".equals(event.optString("type"))) return null;   // "show" 같은 방송 이벤트 제외
        JSONObject match = event.optJSONObject("match");
        if (match == null) return null;
        JSONArray teams = match.optJSONArray("teams");
        if (teams == null || teams.length() != 2) return null;

        int myIndex = -1;
        for (int i = 0; i < 2; i++) {
            if (teamCode.equalsIgnoreCase(teams.getJSONObject(i).optString("code"))) {
                myIndex = i;
            }
        }
        if (myIndex < 0) return null;

        JSONObject mine = teams.getJSONObject(myIndex);
        JSONObject opp = teams.getJSONObject(1 - myIndex);

        MatchInfo m = new MatchInfo();
        m.matchId = match.optString("id", event.optString("id"));
        m.startTimeMillis = parseTime(event.optString("startTime"));
        m.blockName = event.optString("blockName");
        JSONObject league = event.optJSONObject("league");
        m.leagueName = league != null ? league.optString("name") : "";
        JSONObject strategy = match.optJSONObject("strategy");
        m.bestOf = strategy != null ? strategy.optInt("count", 1) : 1;

        m.myName = mine.optString("name", teamCode);
        m.myCode = mine.optString("code", teamCode);
        m.myTeamId = optStringOrNull(mine, "id");
        m.oppName = opp.optString("name", "TBD");
        m.oppCode = opp.optString("code", "TBD");
        m.myWins = gameWins(mine);
        m.oppWins = gameWins(opp);
        return m;
    }

    /** livestats window 응답에서 마지막 프레임을 내 팀 기준 스탯으로 변환 */
    static GameStats parseWindow(JSONObject window, String myTeamId) throws JSONException {
        JSONArray frames = window.optJSONArray("frames");
        if (frames == null || frames.length() == 0) return null;
        JSONObject last = frames.getJSONObject(frames.length() - 1);

        JSONObject meta = window.optJSONObject("gameMetadata");
        boolean myBlue = true;
        if (meta != null && myTeamId != null) {
            JSONObject blueMeta = meta.optJSONObject("blueTeamMetadata");
            myBlue = blueMeta != null && myTeamId.equals(blueMeta.optString("esportsTeamId"));
        }

        JSONObject mine = last.getJSONObject(myBlue ? "blueTeam" : "redTeam");
        JSONObject opp = last.getJSONObject(myBlue ? "redTeam" : "blueTeam");

        GameStats s = new GameStats();
        s.myBlue = myBlue;
        s.gameState = last.optString("gameState");
        s.myKills = mine.optInt("totalKills");
        s.oppKills = opp.optInt("totalKills");
        s.myGold = mine.optInt("totalGold");
        s.oppGold = opp.optInt("totalGold");
        s.myTowers = mine.optInt("towers");
        s.oppTowers = opp.optInt("towers");
        s.myBarons = mine.optInt("barons");
        s.oppBarons = opp.optInt("barons");
        s.myDragons = arrayLength(mine.optJSONArray("dragons"));
        s.oppDragons = arrayLength(opp.optJSONArray("dragons"));
        return s;
    }

    static String findTeamId(JSONArray teams, String teamCode) throws JSONException {
        if (teams == null) return null;
        for (int i = 0; i < teams.length(); i++) {
            JSONObject t = teams.getJSONObject(i);
            if (teamCode.equalsIgnoreCase(t.optString("code"))) return optStringOrNull(t, "id");
        }
        return null;
    }

    /** getLeagues 응답에서 slug(예: "lck")로 리그 id 찾기 */
    static String findLeagueId(JSONObject leaguesJson, String slug) throws JSONException {
        JSONArray leagues = leaguesJson.getJSONObject("data").optJSONArray("leagues");
        if (leagues == null) return null;
        for (int i = 0; i < leagues.length(); i++) {
            JSONObject l = leagues.getJSONObject(i);
            if (slug.equalsIgnoreCase(l.optString("slug"))) return optStringOrNull(l, "id");
        }
        return null;
    }

    /** 일정에 나온 팀들을 code 기준으로 중복 없이 모은다 (아직 안 정해진 "TBD" 는 제외) */
    static void collectTeams(JSONArray events, Map<String, TeamInfo> out) throws JSONException {
        if (events == null) return;
        for (int i = 0; i < events.length(); i++) {
            JSONObject match = events.getJSONObject(i).optJSONObject("match");
            JSONArray teams = match == null ? null : match.optJSONArray("teams");
            if (teams == null) continue;
            for (int j = 0; j < teams.length(); j++) {
                JSONObject t = teams.getJSONObject(j);
                String code = optStringOrNull(t, "code");
                if (code == null || "TBD".equalsIgnoreCase(code)) continue;
                code = code.toUpperCase(Locale.ROOT);
                if (!out.containsKey(code)) out.put(code, new TeamInfo(code, t.optString("name", code)));
            }
        }
    }

    // ───────────────────────── 작은 도우미들 ─────────────────────────

    private static String encode(String s) throws IOException {
        return URLEncoder.encode(s, "UTF-8");
    }

    private static int gameWins(JSONObject team) {
        JSONObject result = team.optJSONObject("result");
        return result != null ? result.optInt("gameWins") : 0;
    }

    private static int arrayLength(JSONArray arr) {
        return arr == null ? 0 : arr.length();
    }

    private static long parseTime(String iso) {
        try {
            return Instant.parse(iso).toEpochMilli();
        } catch (Exception e) {
            return 0;
        }
    }

    private static String optStringOrNull(JSONObject o, String key) {
        if (o.isNull(key)) return null;
        String s = o.optString(key, null);
        return (s == null || s.isEmpty()) ? null : s;
    }

    /** GET 요청 후 JSON 반환. 204(데이터 없음)/404면 null */
    private JSONObject getJson(String urlString) throws IOException, JSONException {
        HttpURLConnection conn = (HttpURLConnection) URI.create(urlString).toURL().openConnection();
        try {
            conn.setConnectTimeout(10_000);
            conn.setReadTimeout(10_000);
            conn.setRequestProperty("x-api-key", API_KEY);
            conn.setRequestProperty("Accept", "application/json");

            int code = conn.getResponseCode();
            if (code == HttpURLConnection.HTTP_NO_CONTENT || code == HttpURLConnection.HTTP_NOT_FOUND) {
                return null;
            }
            if (code != HttpURLConnection.HTTP_OK) {
                throw new IOException("HTTP " + code + " : " + urlString);
            }
            try (InputStream in = conn.getInputStream()) {
                return new JSONObject(readAll(in));
            }
        } finally {
            conn.disconnect();
        }
    }

    private static String readAll(InputStream in) throws IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        byte[] buf = new byte[8192];
        int n;
        while ((n = in.read(buf)) != -1) out.write(buf, 0, n);
        return out.toString(StandardCharsets.UTF_8.name());
    }
}
