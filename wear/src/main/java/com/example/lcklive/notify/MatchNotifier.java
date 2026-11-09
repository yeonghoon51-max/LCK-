package com.example.lcklive.notify;

import android.Manifest;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.content.Context;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;

import androidx.core.app.NotificationCompat;
import androidx.core.app.NotificationManagerCompat;
import androidx.core.content.ContextCompat;

import com.example.lcklive.R;
import com.example.lcklive.alert.AlertDecider;
import com.example.lcklive.data.TeamStatus;

import java.util.List;

/** AlertDecider 가 정한 알림을 실제 시계 알림(진동)으로 띄운다 */
public final class MatchNotifier {

    private static final String CHANNEL_ID = "team_match";
    private static final String PREFS = "alert_memory";
    private static int nextNotificationId = 1000;

    private MatchNotifier() {
    }

    /** 앱 화면과 백그라운드 작업이 동시에 부를 수 있어서 synchronized */
    public static synchronized void check(Context context, TeamStatus status) {
        SharedPreferences prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
        AlertDecider.Memory memory = load(prefs);

        List<AlertDecider.Alert> alerts = AlertDecider.decide(memory, status, System.currentTimeMillis());

        save(prefs, memory);
        for (AlertDecider.Alert alert : alerts) {
            show(context, alert);
        }
    }

    /** 응원 팀을 바꾸면 이전 팀의 "마지막 상태"를 지운다 (안 지우면 엉뚱한 "경기 종료" 알림이 갈 수 있음) */
    public static synchronized void resetMemory(Context context) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().clear().apply();
    }

    public static void createChannel(Context context) {
        NotificationChannel channel = new NotificationChannel(
                CHANNEL_ID, "응원 팀 경기 알림", NotificationManager.IMPORTANCE_HIGH);
        channel.setDescription("경기 시작, 세트 종료, 경기 종료 알림");
        channel.enableVibration(true);
        context.getSystemService(NotificationManager.class).createNotificationChannel(channel);
    }

    private static void show(Context context, AlertDecider.Alert alert) {
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS)
                != PackageManager.PERMISSION_GRANTED) {
            return;   // 사용자가 알림 권한을 안 줬음
        }
        createChannel(context);
        NotificationCompat.Builder builder = new NotificationCompat.Builder(context, CHANNEL_ID)
                .setSmallIcon(R.drawable.ic_t1)
                .setContentTitle(alert.title)
                .setContentText(alert.text)
                .setPriority(NotificationCompat.PRIORITY_HIGH)
                .setCategory(NotificationCompat.CATEGORY_EVENT)
                .setAutoCancel(true);
        NotificationManagerCompat.from(context).notify(nextNotificationId++, builder.build());
    }

    private static AlertDecider.Memory load(SharedPreferences prefs) {
        AlertDecider.Memory m = new AlertDecider.Memory();
        m.matchId = prefs.getString("matchId", null);
        m.state = prefs.getString("state", null);
        m.myWins = prefs.getInt("myWins", 0);
        m.oppWins = prefs.getInt("oppWins", 0);
        m.remindedMatchId = prefs.getString("remindedMatchId", null);
        return m;
    }

    private static void save(SharedPreferences prefs, AlertDecider.Memory m) {
        prefs.edit()
                .putString("matchId", m.matchId)
                .putString("state", m.state)
                .putInt("myWins", m.myWins)
                .putInt("oppWins", m.oppWins)
                .putString("remindedMatchId", m.remindedMatchId)
                .apply();
    }
}
