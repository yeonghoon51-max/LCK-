package com.example.lcklive.ui;

import android.Manifest;
import android.content.Intent;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.View;
import android.widget.TextView;

import androidx.activity.ComponentActivity;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;

import com.example.lcklive.R;
import com.example.lcklive.data.GameStats;
import com.example.lcklive.data.LolEsportsApi;
import com.example.lcklive.data.MatchInfo;
import com.example.lcklive.data.TeamInfo;
import com.example.lcklive.data.TeamStatus;
import com.example.lcklive.notify.MatchCheckWorker;
import com.example.lcklive.notify.MatchNotifier;
import com.example.lcklive.settings.TeamPrefs;
import com.example.lcklive.sync.WatchSync;
import com.example.lcklive.util.Formats;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/** 폰 메인 화면: 응원 팀 + 경기 상황. 켜져 있는 동안 주기적으로 새로고침한다. */
public class MainActivity extends ComponentActivity {

    private static final long REFRESH_LIVE_MS = 10_000;     // 경기 중: 10초
    private static final long REFRESH_IDLE_MS = 60_000;     // 평소: 1분

    private final ExecutorService executor = Executors.newSingleThreadExecutor();
    private final Handler mainHandler = new Handler(Looper.getMainLooper());
    private final Runnable refreshTask = this::refresh;
    private boolean visible;

    private TextView tvMyTeam, tvBadge, tvTeams, tvScore, tvGameInfo, tvKills, tvGold, tvObjects, tvTime, tvUpdated;

    private final ActivityResultLauncher<String> notificationPermission =
            registerForActivityResult(new ActivityResultContracts.RequestPermission(), granted -> {
                // 거절해도 화면은 그대로 쓸 수 있고 알림만 안 온다
            });

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        tvMyTeam = findViewById(R.id.tvMyTeam);
        tvBadge = findViewById(R.id.tvBadge);
        tvTeams = findViewById(R.id.tvTeams);
        tvScore = findViewById(R.id.tvScore);
        tvGameInfo = findViewById(R.id.tvGameInfo);
        tvKills = findViewById(R.id.tvKills);
        tvGold = findViewById(R.id.tvGold);
        tvObjects = findViewById(R.id.tvObjects);
        tvTime = findViewById(R.id.tvTime);
        tvUpdated = findViewById(R.id.tvUpdated);

        findViewById(R.id.btnChangeTeam).setOnClickListener(v ->
                startActivity(new Intent(this, TeamPickerActivity.class)));
        findViewById(R.id.card).setOnClickListener(v -> {   // 카드 탭 → 즉시 새로고침
            tvUpdated.setText("새로고침 중…");
            mainHandler.removeCallbacks(refreshTask);
            refresh();
        });

        MatchNotifier.createChannel(this);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {   // 안드로이드 13+ 은 알림 권한 필요
            notificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS);
        }
        MatchCheckWorker.start(this);
        // 워치 앱을 나중에 설치했어도 같은 팀이 되도록, 켤 때마다 지금 팀을 한 번 보내 둔다
        WatchSync.send(this, new TeamInfo(TeamPrefs.code(this), TeamPrefs.name(this)));
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
        String teamCode = TeamPrefs.code(this);   // 팀 선택 화면에서 바꿨을 수 있으니 매번 읽는다
        tvMyTeam.setText(TeamPrefs.name(this));
        executor.execute(() -> {
            TeamStatus status;
            try {
                status = new LolEsportsApi(teamCode).fetchStatus();
                MatchNotifier.check(getApplicationContext(), status);
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
        tvUpdated.setText("업데이트 " + Formats.clock(status.fetchedAtMillis) + " · 카드를 누르면 새로고침");
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
        tvGameInfo.setText(g.gameNumber + "세트 · " + (g.myBlue ? "블루" : "레드") + " 진영" + paused);
        tvKills.setText("킬  " + g.myKills + " : " + g.oppKills);
        tvGold.setText("골드  " + Formats.goldDiff(g.goldDiff()));
        tvGold.setTextColor(getColor(g.goldDiff() >= 0 ? R.color.good_green : R.color.live_red));
        tvObjects.setText("타워 " + g.myTowers + ":" + g.oppTowers
                + "   용 " + g.myDragons + ":" + g.oppDragons
                + "   바론 " + g.myBarons + ":" + g.oppBarons);
    }

    private void renderUpcoming(MatchInfo next, MatchInfo last) {
        tvBadge.setText("다음 경기 · " + next.leagueName);
        tvBadge.setTextColor(getColor(R.color.t1_red));
        tvTeams.setText(next.myCode + " vs " + next.oppCode);
        tvScore.setVisibility(View.GONE);
        hideLiveStats();
        tvTime.setVisibility(View.VISIBLE);
        tvTime.setText(Formats.matchTime(next.startTimeMillis) + " · Bo" + next.bestOf);

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
        tvUpdated.setText("연결 실패 · 카드를 눌러 다시 시도");
    }
}
