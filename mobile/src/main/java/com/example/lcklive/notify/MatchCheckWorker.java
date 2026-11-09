package com.example.lcklive.notify;

import android.content.Context;

import androidx.annotation.NonNull;
import androidx.work.Constraints;
import androidx.work.ExistingPeriodicWorkPolicy;
import androidx.work.ExistingWorkPolicy;
import androidx.work.NetworkType;
import androidx.work.OneTimeWorkRequest;
import androidx.work.PeriodicWorkRequest;
import androidx.work.WorkManager;
import androidx.work.Worker;
import androidx.work.WorkerParameters;

import com.example.lcklive.alert.AlertDecider;
import com.example.lcklive.data.LolEsportsApi;
import com.example.lcklive.data.TeamStatus;
import com.example.lcklive.settings.TeamPrefs;

import java.util.concurrent.TimeUnit;

/**
 * 앱을 안 켜 놔도 백그라운드에서 응원 팀 경기를 확인하고 알림을 보낸다.
 * 폰 알림은 갤럭시워치에도 자동으로 전달되므로, 워치는 따로 확인하지 않는다.
 *
 * WorkManager 의 주기 작업은 최소 15분 간격이라, 경기 중에는 그보다 자주 확인하려고
 * "실행이 끝나면 다음 실행을 예약"하는 1회성 작업을 계속 이어 붙인다.
 *   - 경기 중         → 2분 뒤
 *   - 곧 경기 시작     → 시작 10분 전 / 시작 직후
 *   - 경기 없음        → 30분 뒤
 * 그리고 혹시 사슬이 끊겨도 되살아나도록 15분 주기 작업을 안전망으로 하나 둔다.
 */
public class MatchCheckWorker extends Worker {

    private static final String CHAIN_WORK = "t1_check_chain";
    private static final String PERIODIC_WORK = "t1_check_periodic";

    public MatchCheckWorker(@NonNull Context context, @NonNull WorkerParameters params) {
        super(context, params);
    }

    @NonNull
    @Override
    public Result doWork() {
        long delayMillis;
        try {
            TeamStatus status = new LolEsportsApi(TeamPrefs.code(getApplicationContext())).fetchStatus();
            MatchNotifier.check(getApplicationContext(), status);
            delayMillis = nextDelayMillis(status, System.currentTimeMillis());
        } catch (Exception e) {
            delayMillis = TimeUnit.MINUTES.toMillis(5);   // 네트워크 오류 → 5분 뒤 재시도
        }
        // 주의: REPLACE 라서 지금 실행 중인 나 자신이 취소 처리되지만, 할 일은 이미 끝났으므로 괜찮다
        scheduleNext(getApplicationContext(), delayMillis);
        return Result.success();
    }

    /** 다음 확인까지 얼마나 기다릴지 */
    static long nextDelayMillis(TeamStatus status, long nowMillis) {
        long minute = TimeUnit.MINUTES.toMillis(1);
        if (status.live != null) {
            return 2 * minute;
        }
        if (status.next != null) {
            long untilStart = status.next.startTimeMillis - nowMillis;
            long untilReminder = untilStart - AlertDecider.REMIND_BEFORE_MILLIS;
            if (untilReminder > minute) {
                return Math.min(untilReminder, 30 * minute);   // 미리 알림 시각에 맞춰 깨어남
            }
            if (untilStart > 0) {
                return untilStart + minute;                    // 경기 시작 직후에 깨어남
            }
            return 2 * minute;   // 시작 시간이 지났는데 아직 라이브 아님 (지연 중)
        }
        return 30 * minute;
    }

    /** 앱을 처음 켤 때 한 번 불러서 백그라운드 확인을 시작한다 */
    public static void start(Context context) {
        PeriodicWorkRequest periodic = new PeriodicWorkRequest.Builder(
                MatchCheckWorker.class, 15, TimeUnit.MINUTES)
                .setConstraints(networkConstraint())
                .build();
        WorkManager.getInstance(context).enqueueUniquePeriodicWork(
                PERIODIC_WORK, ExistingPeriodicWorkPolicy.KEEP, periodic);
        scheduleNext(context, 0);
    }

    private static void scheduleNext(Context context, long delayMillis) {
        OneTimeWorkRequest request = new OneTimeWorkRequest.Builder(MatchCheckWorker.class)
                .setInitialDelay(delayMillis, TimeUnit.MILLISECONDS)
                .setConstraints(networkConstraint())
                .build();
        WorkManager.getInstance(context).enqueueUniqueWork(
                CHAIN_WORK, ExistingWorkPolicy.REPLACE, request);
    }

    private static Constraints networkConstraint() {
        return new Constraints.Builder()
                .setRequiredNetworkType(NetworkType.CONNECTED)
                .build();
    }
}
