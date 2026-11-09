package com.example.lcklive.tile;

import android.util.Log;

import androidx.annotation.NonNull;
import androidx.concurrent.futures.CallbackToFutureAdapter;
import androidx.wear.protolayout.ActionBuilders;
import androidx.wear.protolayout.ColorBuilders;
import androidx.wear.protolayout.DimensionBuilders;
import androidx.wear.protolayout.LayoutElementBuilders;
import androidx.wear.protolayout.ModifiersBuilders;
import androidx.wear.protolayout.ResourceBuilders;
import androidx.wear.protolayout.TimelineBuilders;
import androidx.wear.tiles.RequestBuilders;
import androidx.wear.tiles.TileBuilders;
import androidx.wear.tiles.TileService;

import com.example.lcklive.data.GameStats;
import com.example.lcklive.data.LolEsportsApi;
import com.example.lcklive.data.MatchInfo;
import com.example.lcklive.data.TeamStatus;
import com.example.lcklive.schedule.CheckSchedule;
import com.example.lcklive.settings.TeamPrefs;
import com.example.lcklive.util.Formats;

import com.google.common.util.concurrent.ListenableFuture;

import java.time.ZoneId;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * 타일: 시계 화면을 옆으로 넘기면 앱을 안 켜도 응원 팀 스코어가 보인다.
 *
 * 타일은 일반 화면(XML 레이아웃)과 달리 "이렇게 그려줘" 하는 설계도(protolayout)를
 * 시스템에 넘기면 시스템이 대신 그려준다. 그래서 TextView 대신 Text.Builder 를 쓴다.
 * 탭하면 타일을 새로고침한다 (워치 앱 화면은 안내만 하므로).
 */
public class TeamTileService extends TileService {

    private static final String TAG = "TeamTile";
    private static final String RESOURCES_VERSION = "1";

    private static final int WHITE = 0xFFFFFFFF;
    private static final int DIM = 0xFF9E9E9E;
    private static final int T1_RED = 0xFFE2012D;
    private static final int LIVE_RED = 0xFFFF4D4D;
    private static final int GREEN = 0xFF4CD964;

    private final ExecutorService executor = Executors.newSingleThreadExecutor();

    /** 시스템이 "타일 내용 줘" 할 때 불린다. 네트워크는 백그라운드 스레드에서. */
    @NonNull
    @Override
    protected ListenableFuture<TileBuilders.Tile> onTileRequest(
            @NonNull RequestBuilders.TileRequest request) {
        return CallbackToFutureAdapter.getFuture(completer -> {
            executor.execute(() -> {
                LayoutElementBuilders.LayoutElement content;
                long freshness;
                try {
                    TeamStatus status = new LolEsportsApi(TeamPrefs.code(this)).fetchStatus();
                    long now = System.currentTimeMillis();
                    content = buildContent(status, now);
                    // 경기 중엔 1분, 오늘 경기 있으면 시작 때까지, 없으면 자정까지 다시 안 그린다
                    freshness = CheckSchedule.tileFreshness(status, now, ZoneId.systemDefault());
                } catch (Exception e) {
                    Log.w(TAG, "타일 데이터 조회 실패", e);   // Logcat 에서 "TeamTile" 로 검색
                    content = column(
                            text(TeamPrefs.code(this) + " 라이브", 14, T1_RED, true),
                            text("연결 실패", 12, DIM, false),
                            text(e.getClass().getSimpleName(), 10, DIM, false));
                    freshness = 5 * 60_000;
                }
                completer.set(new TileBuilders.Tile.Builder()
                        .setResourcesVersion(RESOURCES_VERSION)
                        .setFreshnessIntervalMillis(freshness)
                        .setTileTimeline(TimelineBuilders.Timeline.fromLayoutElement(clickableRoot(content)))
                        .build());
            });
            return "team tile request";
        });
    }

    /** 이미지 같은 리소스는 안 쓰므로 빈 리소스 */
    @NonNull
    @Override
    protected ListenableFuture<ResourceBuilders.Resources> onTileResourcesRequest(
            @NonNull RequestBuilders.ResourcesRequest request) {
        return CallbackToFutureAdapter.getFuture(completer -> {
            completer.set(new ResourceBuilders.Resources.Builder()
                    .setVersion(RESOURCES_VERSION)
                    .build());
            return "team tile resources";
        });
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        executor.shutdown();
    }

    // ───────────────────────── 상태별 내용 ─────────────────────────

    private LayoutElementBuilders.LayoutElement buildContent(TeamStatus status, long now) {
        ZoneId zone = ZoneId.systemDefault();
        if (status.live != null) {
            return liveContent(status.live);
        }
        if (CheckSchedule.isToday(status.next, now, zone)) {
            MatchInfo n = status.next;
            return column(
                    text("오늘 경기", 12, T1_RED, true),
                    text(n.myCode + " vs " + n.oppCode, 18, WHITE, true),
                    spacer(4),
                    text(Formats.time(n.startTimeMillis) + " 시작", 16, WHITE, true),
                    text(n.leagueName + " · Bo" + n.bestOf, 11, DIM, false));
        }
        if (CheckSchedule.isToday(status.lastCompleted, now, zone)) {
            MatchInfo m = status.lastCompleted;
            return column(
                    text("오늘 경기 종료", 12, DIM, true),
                    text(m.myCode + " vs " + m.oppCode, 16, WHITE, true),
                    text(m.scoreText(), 30, WHITE, true),
                    text(m.wonSeries() ? m.myCode + " 승리 🏆" : m.myCode + " 패배", 12, m.wonSeries() ? GREEN : DIM, false));
        }
        return noMatchToday(status);
    }

    /** 오늘 경기 없음 + 다음 경기(또는 지난 경기) 한 줄 */
    private LayoutElementBuilders.LayoutElement noMatchToday(TeamStatus status) {
        LayoutElementBuilders.Column.Builder col = columnBuilder()
                .addContent(text("오늘 경기 없음", 12, DIM, true))
                .addContent(text(TeamPrefs.code(this), 20, WHITE, true))
                .addContent(spacer(6));
        if (status.next != null) {
            MatchInfo n = status.next;
            col.addContent(text("다음 · " + Formats.matchTime(n.startTimeMillis), 13, WHITE, false));
            col.addContent(text("vs " + n.oppCode + " · " + n.leagueName, 11, DIM, false));
        } else if (status.lastCompleted != null) {
            MatchInfo m = status.lastCompleted;
            col.addContent(text("지난 경기 " + m.scoreText() + " " + m.oppCode
                    + (m.wonSeries() ? " 승" : " 패"), 12, DIM, false));
        } else {
            col.addContent(text("잡힌 일정 없음", 12, DIM, false));
        }
        return col.build();
    }

    private LayoutElementBuilders.LayoutElement liveContent(MatchInfo m) {
        LayoutElementBuilders.Column.Builder col = columnBuilder()
                .addContent(text("● LIVE · " + m.leagueName, 12, LIVE_RED, true))
                .addContent(text(m.myCode + " vs " + m.oppCode, 16, WHITE, true))
                .addContent(text(m.scoreText(), 30, WHITE, true));

        GameStats g = m.liveGame;
        if (g == null) {
            col.addContent(text("세트 대기 중", 12, DIM, false));
        } else {
            col.addContent(text(g.gameNumber + "세트 · 킬 " + g.myKills + " : " + g.oppKills, 13, WHITE, false));
            col.addContent(text("골드 " + Formats.goldDiff(g.goldDiff()), 13, g.goldDiff() >= 0 ? GREEN : LIVE_RED, false));
        }
        return col.build();
    }

    // ───────────────────────── protolayout 도우미 ─────────────────────────

    /** 화면 전체를 채우고, 탭하면 타일을 새로 불러오는 바깥 상자 */
    private LayoutElementBuilders.LayoutElement clickableRoot(LayoutElementBuilders.LayoutElement content) {
        // LoadAction: 시스템이 onTileRequest 를 다시 불러서 최신 정보로 다시 그린다
        ActionBuilders.LoadAction reload = new ActionBuilders.LoadAction.Builder().build();

        return new LayoutElementBuilders.Box.Builder()
                .setWidth(DimensionBuilders.expand())
                .setHeight(DimensionBuilders.expand())
                .setHorizontalAlignment(LayoutElementBuilders.HORIZONTAL_ALIGN_CENTER)
                .setVerticalAlignment(LayoutElementBuilders.VERTICAL_ALIGN_CENTER)
                .setModifiers(new ModifiersBuilders.Modifiers.Builder()
                        .setClickable(new ModifiersBuilders.Clickable.Builder()
                                .setId("reload")
                                .setOnClick(reload)
                                .build())
                        .build())
                .addContent(content)
                .build();
    }

    private static LayoutElementBuilders.Column.Builder columnBuilder() {
        return new LayoutElementBuilders.Column.Builder()
                .setHorizontalAlignment(LayoutElementBuilders.HORIZONTAL_ALIGN_CENTER);
    }

    private static LayoutElementBuilders.LayoutElement column(LayoutElementBuilders.LayoutElement... items) {
        LayoutElementBuilders.Column.Builder col = columnBuilder();
        for (LayoutElementBuilders.LayoutElement item : items) col.addContent(item);
        return col.build();
    }

    private static LayoutElementBuilders.LayoutElement text(String s, float sizeSp, int color, boolean bold) {
        return new LayoutElementBuilders.Text.Builder()
                .setText(s)
                .setMaxLines(1)
                .setFontStyle(new LayoutElementBuilders.FontStyle.Builder()
                        .setSize(DimensionBuilders.sp(sizeSp))
                        .setColor(ColorBuilders.argb(color))
                        .setWeight(bold ? LayoutElementBuilders.FONT_WEIGHT_BOLD
                                : LayoutElementBuilders.FONT_WEIGHT_NORMAL)
                        .build())
                .build();
    }

    private static LayoutElementBuilders.LayoutElement spacer(float heightDp) {
        return new LayoutElementBuilders.Spacer.Builder()
                .setHeight(DimensionBuilders.dp(heightDp))
                .build();
    }
}
