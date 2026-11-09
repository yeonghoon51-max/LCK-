package com.example.lcklive.ui;

import android.Manifest;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.activity.ComponentActivity;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.wear.tiles.TileService;

import com.example.lcklive.R;
import com.example.lcklive.data.LolEsportsApi;
import com.example.lcklive.data.TeamInfo;
import com.example.lcklive.notify.MatchCheckWorker;
import com.example.lcklive.notify.MatchNotifier;
import com.example.lcklive.settings.TeamPrefs;
import com.example.lcklive.tile.TeamTileService;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * 워치 앱 화면 = 응원 팀 고르기.
 * 경기 정보는 타일에서 보고, 알림은 백그라운드 작업(MatchCheckWorker)이 보낸다.
 * 팀 목록은 API 에서 받아와서 줄(TextView)을 하나씩 만들어 붙인다.
 */
public class MainActivity extends ComponentActivity {

    private final ExecutorService executor = Executors.newSingleThreadExecutor();
    private final Handler mainHandler = new Handler(Looper.getMainLooper());

    private LinearLayout teamList;
    private TextView tvStatus, tvCurrent;
    private List<TeamInfo> teams = new ArrayList<>();

    private final ActivityResultLauncher<String> notificationPermission =
            registerForActivityResult(new ActivityResultContracts.RequestPermission(), granted -> {
                // 거절해도 팀 선택과 타일은 그대로 쓸 수 있고 알림만 안 온다
            });

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        teamList = findViewById(R.id.teamList);
        tvStatus = findViewById(R.id.tvStatus);
        tvCurrent = findViewById(R.id.tvCurrent);
        tvStatus.setOnClickListener(v -> load());   // 실패했을 때 탭해서 다시 시도

        MatchNotifier.createChannel(this);
        notificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS);
        MatchCheckWorker.start(this);

        showCurrent("");
        load();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        executor.shutdownNow();
    }

    private void load() {
        tvStatus.setVisibility(View.VISIBLE);
        tvStatus.setText("LCK 팀 불러오는 중…");
        executor.execute(() -> {
            try {
                List<TeamInfo> result = new LolEsportsApi().fetchLeagueTeams(LolEsportsApi.LCK_SLUG);
                mainHandler.post(() -> {
                    teams = result;
                    showTeams();
                });
            } catch (Exception e) {
                mainHandler.post(() -> tvStatus.setText("불러오기 실패 · 탭해서 다시 시도"));
            }
        });
    }

    private void showTeams() {
        if (isFinishing()) return;
        if (teams.isEmpty()) {
            tvStatus.setText("팀을 찾지 못했어요 · 탭해서 다시 시도");
            return;
        }
        tvStatus.setVisibility(View.GONE);
        teamList.removeAllViews();
        String current = TeamPrefs.code(this);
        for (TeamInfo team : teams) {
            teamList.addView(row(team, team.code.equals(current)));
        }
    }

    private TextView row(TeamInfo team, boolean selected) {
        TextView tv = new TextView(this);
        tv.setLayoutParams(new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT));
        tv.setText((selected ? "✓ " : "") + team.name);
        tv.setTextSize(TypedValue.COMPLEX_UNIT_SP, 15);
        tv.setTextColor(getColor(selected ? R.color.t1_red : R.color.text));
        tv.setGravity(Gravity.CENTER);
        int pad = Math.round(TypedValue.applyDimension(
                TypedValue.COMPLEX_UNIT_DIP, 9, getResources().getDisplayMetrics()));
        tv.setPadding(0, pad, 0, pad);
        tv.setOnClickListener(v -> choose(team));
        return tv;
    }

    private void choose(TeamInfo team) {
        if (team.code.equals(TeamPrefs.code(this))) return;

        TeamPrefs.save(this, team);
        MatchNotifier.resetMemory(this);   // 이전 팀 경기 상태로 엉뚱한 알림이 가지 않도록
        MatchCheckWorker.start(this);      // 새 팀 기준으로 바로 다시 확인
        TileService.getUpdater(this).requestUpdate(TeamTileService.class);

        showTeams();   // ✓ 위치 갱신
        showCurrent(" · 타일에 적용됐어요");
    }

    private void showCurrent(String suffix) {
        tvCurrent.setText("지금 응원 팀: " + TeamPrefs.code(this) + suffix);
    }
}
