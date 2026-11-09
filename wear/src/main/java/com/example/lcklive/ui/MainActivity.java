package com.example.lcklive.ui;

import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.View;
import android.widget.TextView;

import androidx.activity.ComponentActivity;

import com.example.lcklive.R;
import com.example.lcklive.data.GameStats;
import com.example.lcklive.data.LolEsportsApi;
import com.example.lcklive.data.MatchInfo;
import com.example.lcklive.data.TeamStatus;
import com.example.lcklive.settings.TeamPrefs;
import com.example.lcklive.sync.PhoneTeamSync;
import com.example.lcklive.util.Formats;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * 워치 화면. 켜져 있는 동안 주기적으로 새로고침한다.
 * 응원 팀은 폰 앱에서 고르고, 알림도 폰이 보낸다 (갤럭시워치가 폰 알림을 자동으로 보여줌).
 */
public class MainActivity extends ComponentActivity {

    private static final long REFRESH_LIVE_MS = 10_000;     // 경기 중: 10초
    private static final long REFRESH_IDLE_MS = 60_000;     // 평소: 1분

    private final ExecutorService executor = Executors.newSingleThreadExecutor();
    private final Handler mainHandler = new Handler(Looper.getMainLooper());
    private final Runnable refreshTask = this::refresh;
    private boolean visible;

    private TextView tvBadge, tvTeams, tvScore, tvGameInfo, tvKills, tvGold, tvObjects, tvTime, tvUpdated, tvTeamHint;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        tvBadge = findViewById(R.id.tvBadge);
        tvTeams = findViewById(R.id.tvTeams);
        tvScore = findViewById(R.id.tvScore);
        tvGameInfo = findViewById(R.id.tvGameInfo);
        tvKills = findViewById(R.id.tvKills);
        tvGold = findViewById(R.id.tvGold);
        tvObjects = findViewById(R.id.tvObjects);
        tvTime = findViewById(R.id.tvTime);
        tvUpdated = findViewById(R.id.tvUpdated);
        tvTeamHint = findViewById(R.id.tvTeamHint);

        findViewById(R.id.root).setOnClickListener(v -> {   // 화면 탭 → 즉시 새로고침
            tvUpdated.setText("새로고침 중…");
            mainHandler.removeCallbacks(refreshTask);
            refresh();
        });

        // 워치 앱을 폰보다 늦게 설치했을 수도 있으니, 폰이 이미 보내 둔 팀을 한 번 가져온다
        PhoneTeamSync.pull(this, changed -> {
            if (changed && visible) refresh();
        });
    }

    @Override
    protected void onResume() {
        super.onResume();
        visible = true;
        refresh();
    }

    @Override
    protected void onPause() {
        super.onPause();
        visible = false;
        mainHandler.removeCallbacks(refreshTask);
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        executor.shutdownNow();
    }

    /** 네트워크는 백그라운드 스레드에서, 화면 갱신은 메인 스레드에서 */
    private void refresh() {
        String teamCode = TeamPrefs.code(this);   // 폰에서 바뀌었을 수 있으니 매번 읽는다
        tvTeamHint.setText("응원 팀 " + teamCode + " · 폰 앱에서 변경");
        executor.execute(() -> {
            TeamStatus status;
            try {
                status = new LolEsportsApi(teamCode).fetchStatus();
            } catch (Exception e) {
                mainHandler.post(this::showError);
                scheduleRefresh(REFRESH_IDLE_MS);
                return;
            }
            TeamStatus result = status;
            mainHandler.post(() -> render(result, teamCode));
            scheduleRefresh(status.live != null ? REFRESH_LIVE_MS : REFRESH_IDLE_MS);
        });
    }

    private void scheduleRefresh(long delayMs) {
        mainHandler.post(() -> {
            mainHandler.removeCallbacks(refreshTask);
            if (visible) mainHandler.postDelayed(refreshTask, delayMs);   // 화면 꺼져 있으면 멈춤
        });
    }

    // ───────────────────────── 화면 그리기 ─────────────────────────

    private void render(TeamStatus status, String teamCode) {
        if (status.live != null) {
            renderLive(status.live);
        } else if (status.next != null) {
            renderUpcoming(status.next, status.lastCompleted);
        } else if (status.lastCompleted != null) {
            renderCompleted(status.lastCompleted);
        } else {
            tvBadge.setText("예정된 경기 없음");
            tvBadge.setTextColor(getColor(R.color.text_dim));
            tvTeams.setText(teamCode);
            tvScore.setVisibility(View.GONE);
            hideLiveStats();
            tvTime.setVisibility(View.GONE);
        }
        tvUpdated.setText("업데이트 " + Formats.clock(status.fetchedAtMillis));
    }

    private void renderLive(MatchInfo m) {
        tvBadge.setText("● LIVE · " + m.leagueName);
        tvBadge.setTextColor(getColor(R.color.live_red));
        tvTeams.setText(m.myCode + " vs " + m.oppCode);
        tvScore.setVisibility(View.VISIBLE);
        tvScore.setText(m.scoreText());
        tvTime.setVisibility(View.GONE);

        GameStats g = m.liveGame;
        if (g == null) {
            hideLiveStats();
            tvGameInfo.setVisibility(View.VISIBLE);
            tvGameInfo.setText("Bo" + m.bestOf + " · 세트 대기 중");
            return;
        }
        tvGameInfo.setVisibility(View.VISIBLE);
        tvKills.setVisibility(View.VISIBLE);
        tvGold.setVisibility(View.VISIBLE);
        tvObjects.setVisibility(View.VISIBLE);

        String paused = "paused".equals(g.gameState) ? " · 일시정지" : "";
        tvGameInfo.setText(g.gameNumber + "세트 · " + (g.myBlue ? "블루" : "레드") + paused);
        tvKills.setText("킬  " + g.myKills + " : " + g.oppKills);
        tvGold.setText("골드  " + Formats.goldDiff(g.goldDiff()));
        tvGold.setTextColor(getColor(g.goldDiff() >= 0 ? R.color.good_green : R.color.live_red));
        tvObjects.setText("타워 " + g.myTowers + ":" + g.oppTowers
                + " · 용 " + g.myDragons + ":" + g.oppDragons
                + " · 바론 " + g.myBarons + ":" + g.oppBarons);
    }

    private void renderUpcoming(MatchInfo next, MatchInfo last) {
        tvBadge.setText("다음 경기 · " + next.leagueName);
        tvBadge.setTextColor(getColor(R.color.t1_red));
        tvTeams.setText(next.myCode + " vs " + next.oppCode);
        tvScore.setVisibility(View.GONE);
        hideLiveStats();
        tvTime.setVisibility(View.VISIBLE);
        tvTime.setText(Formats.matchTime(next.startTimeMillis) + "\nBo" + next.bestOf);

        if (last != null) {   // 지난 경기 결과도 한 줄로
            tvGameInfo.setVisibility(View.VISIBLE);
            tvGameInfo.setText("지난 경기  " + last.myCode + " " + last.scoreText() + " " + last.oppCode
                    + (last.wonSeries() ? " 승" : " 패"));
        }
    }

    private void renderCompleted(MatchInfo m) {
        tvBadge.setText("경기 종료 · " + m.leagueName);
        tvBadge.setTextColor(getColor(R.color.text_dim));
        tvTeams.setText(m.myCode + " vs " + m.oppCode);
        tvScore.setVisibility(View.VISIBLE);
        tvScore.setText(m.scoreText());
        hideLiveStats();
        tvGameInfo.setVisibility(View.VISIBLE);
        tvGameInfo.setText(m.wonSeries() ? m.myCode + " 승리 🏆" : m.myCode + " 패배");
        tvTime.setVisibility(View.VISIBLE);
        tvTime.setText(Formats.matchTime(m.startTimeMillis));
    }

    private void hideLiveStats() {
        tvGameInfo.setVisibility(View.GONE);
        tvKills.setVisibility(View.GONE);
        tvGold.setVisibility(View.GONE);
        tvObjects.setVisibility(View.GONE);
    }

    private void showError() {
        tvUpdated.setText("연결 실패 · 탭해서 다시 시도");
    }
}
