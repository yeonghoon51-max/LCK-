package com.example.lcklive.ui;

import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.activity.ComponentActivity;

import com.example.lcklive.R;
import com.example.lcklive.data.LolEsportsApi;
import com.example.lcklive.data.TeamInfo;
import com.example.lcklive.notify.MatchCheckWorker;
import com.example.lcklive.notify.MatchNotifier;
import com.example.lcklive.settings.TeamPrefs;
import com.example.lcklive.sync.WatchSync;

import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * 응원할 LCK 팀 고르기 (폰). 팀 목록은 API 에서 받아와서 줄(TextView)을 하나씩 만들어 붙인다.
 * 고르면 폰 알림 대상이 바뀌고 워치에도 전달된다.
 */
public class TeamPickerActivity extends ComponentActivity {

    private final ExecutorService executor = Executors.newSingleThreadExecutor();
    private final Handler mainHandler = new Handler(Looper.getMainLooper());

    private LinearLayout teamList;
    private TextView tvStatus;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_team_picker);
        teamList = findViewById(R.id.teamList);
        tvStatus = findViewById(R.id.tvStatus);
        tvStatus.setOnClickListener(v -> load());   // 실패했을 때 탭해서 다시 시도
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
                List<TeamInfo> teams = new LolEsportsApi().fetchLeagueTeams(LolEsportsApi.LCK_SLUG);
                mainHandler.post(() -> showTeams(teams));
            } catch (Exception e) {
                mainHandler.post(() -> tvStatus.setText("불러오기 실패 · 탭해서 다시 시도"));
            }
        });
    }

    private void showTeams(List<TeamInfo> teams) {
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
        tv.setText((selected ? "✓  " : "") + team.name + "   " + team.code);
        tv.setTextSize(TypedValue.COMPLEX_UNIT_SP, 18);
        tv.setTextColor(getColor(selected ? R.color.t1_red : R.color.text));
        tv.setGravity(Gravity.CENTER_VERTICAL);
        int pad = Math.round(TypedValue.applyDimension(
                TypedValue.COMPLEX_UNIT_DIP, 16, getResources().getDisplayMetrics()));
        tv.setPadding(pad, pad, pad, pad);
        tv.setBackgroundResource(R.drawable.row_bg);
        tv.setOnClickListener(v -> choose(team));
        return tv;
    }

    private void choose(TeamInfo team) {
        if (!team.code.equals(TeamPrefs.code(this))) {
            TeamPrefs.save(this, team);
            MatchNotifier.resetMemory(this);
            MatchCheckWorker.start(this);   // 새 팀 기준으로 바로 다시 확인
            WatchSync.send(this, team);     // 워치에도 같은 팀 적용
        }
        finish();   // 메인 화면으로 돌아가면 onResume 에서 새 팀으로 새로고침된다
    }
}
