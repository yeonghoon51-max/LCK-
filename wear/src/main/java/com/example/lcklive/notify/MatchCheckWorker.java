package com.example.lcklive.notify;

import android.content.Context;

import androidx.annotation.NonNull;
import androidx.wear.tiles.TileService;
import androidx.work.Constraints;
import androidx.work.ExistingPeriodicWorkPolicy;
import androidx.work.ExistingWorkPolicy;
import androidx.work.NetworkType;
import androidx.work.OneTimeWorkRequest;
import androidx.work.PeriodicWorkRequest;
import androidx.work.WorkManager;
import androidx.work.Worker;
import androidx.work.WorkerParameters;

import com.example.lcklive.data.LolEsportsApi;
import com.example.lcklive.data.TeamStatus;
import com.example.lcklive.schedule.CheckSchedule;
import com.example.lcklive.settings.TeamPrefs;
import com.example.lcklive.tile.TeamTileService;

import java.util.concurrent.TimeUnit;

/**
 * 앱을 안 켜 놔도 백그라운드에서 응원 팀 경기를 확인하고 알림을 보낸다.
 *
 * "실행이 끝나면 다음 실행을 예약"하는 1회성 작업을 계속 이어 붙인다.
 * 언제 다시 확인할지는 CheckSchedule 이 정한다 (경기 없으면 푹 자서 배터리 절약).
 *   - 경기 중         → 2분 뒤
 *   - 다음 경기 있음   → 시작 10분 전 / 시작 직후 (길어도 6시간마다 한 번)
 *   - 경기 없음        → 6시간 뒤
 * 그리고 혹시 사슬이 끊겨도 되살아나도록 6시간 주기 작업을 안전망으로 하나 둔다.
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
            // 타일도 새 정보로 다시 그려 달라고 요청 (시스템이 너무 잦은 요청은 알아서 걸러줌)
            TileService.getUpdater(getApplicationContext()).requestUpdate(TeamTileService.class);
            delayMillis = CheckSchedule.nextCheckDelay(status, System.currentTimeMillis());
        } catch (Exception e) {
            delayMillis = TimeUnit.MINUTES.toMillis(5);   // 네트워크 오류 → 5분 뒤 재시도
        }
        // 주의: REPLACE 라서 지금 실행 중인 나 자신이 취소 처리되지만, 할 일은 이미 끝났으므로 괜찮다
        scheduleNext(getApplicationContext(), delayMillis);
        return Result.success();
    }

    /** 앱을 처음 켤 때 한 번 불러서 백그라운드 확인을 시작한다 */
    public static void start(Context context) {
        PeriodicWorkRequest periodic = new PeriodicWorkRequest.Builder(
                MatchCheckWorker.class, 6, TimeUnit.HOURS)
                .setConstraints(networkConstraint())
                .build();
        WorkManager.getInstance(context).enqueueUniquePeriodicWork(
                PERIODIC_WORK, ExistingPeriodicWorkPolicy.UPDATE, periodic);   // 예전 15분 주기를 6시간으로 교체
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
