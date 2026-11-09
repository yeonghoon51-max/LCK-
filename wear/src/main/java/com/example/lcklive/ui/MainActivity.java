package com.example.lcklive.ui;

import android.os.Bundle;
import android.widget.TextView;

import androidx.activity.ComponentActivity;

import com.example.lcklive.R;
import com.example.lcklive.settings.TeamPrefs;
import com.example.lcklive.sync.PhoneTeamSync;

/**
 * 워치 앱 화면은 안내만 한다. 워치 앱은 타일을 추가하기 위한 통로이고,
 * 경기 정보는 타일과 폰 앱에서, 알림은 폰 알림이 워치로 전달되는 걸로 본다.
 */
public class MainActivity extends ComponentActivity {

    private TextView tvTeam;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        tvTeam = findViewById(R.id.tvTeam);

        // 워치 앱을 폰보다 늦게 설치했을 수도 있으니, 폰이 이미 보내 둔 팀을 한 번 가져온다
        PhoneTeamSync.pull(this, changed -> {
            if (changed) showTeam();
        });
    }

    @Override
    protected void onResume() {
        super.onResume();
        showTeam();
    }

    private void showTeam() {
        tvTeam.setText("응원 팀 " + TeamPrefs.code(this));
    }
}
