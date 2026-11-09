package com.example.lcklive.sync;

import android.content.Context;

import com.example.lcklive.data.TeamInfo;
import com.google.android.gms.wearable.PutDataMapRequest;
import com.google.android.gms.wearable.Wearable;

/**
 * 폰에서 고른 응원 팀을 워치로 보낸다 (Wearable Data Layer).
 * 워치 앱이 없거나 워치가 연결돼 있지 않으면 조용히 무시되고,
 * 나중에 연결되면 시스템이 알아서 전달해 준다.
 */
public final class WatchSync {

    private WatchSync() {
    }

    public static void send(Context context, TeamInfo team) {
        PutDataMapRequest request = PutDataMapRequest.create(TeamSyncContract.PATH);
        request.getDataMap().putString(TeamSyncContract.KEY_CODE, team.code);
        request.getDataMap().putString(TeamSyncContract.KEY_NAME, team.name);
        request.getDataMap().putLong(TeamSyncContract.KEY_SENT_AT, System.currentTimeMillis());

        Wearable.getDataClient(context.getApplicationContext())
                .putDataItem(request.asPutDataRequest().setUrgent())   // urgent: 배터리 절약 대기 없이 바로 전송
                .addOnFailureListener(e -> {
                    // 워치 기능이 없는 폰 등. 폰 앱은 그대로 쓸 수 있으니 무시한다.
                });
    }
}
