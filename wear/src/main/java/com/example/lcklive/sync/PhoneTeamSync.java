package com.example.lcklive.sync;

import android.content.Context;
import android.net.Uri;

import androidx.wear.tiles.TileService;

import com.example.lcklive.data.TeamInfo;
import com.example.lcklive.settings.TeamPrefs;
import com.example.lcklive.tile.TeamTileService;
import com.google.android.gms.wearable.DataItem;
import com.google.android.gms.wearable.DataMap;
import com.google.android.gms.wearable.DataMapItem;
import com.google.android.gms.wearable.Wearable;

/** 폰이 보낸 응원 팀을 워치에 저장한다 */
public final class PhoneTeamSync {

    public interface Callback {
        void onDone(boolean changed);
    }

    private PhoneTeamSync() {
    }

    /** 폰이 이미 올려 둔 팀 데이터를 직접 가져온다 (워치 앱을 나중에 설치한 경우 대비) */
    public static void pull(Context context, Callback callback) {
        Context app = context.getApplicationContext();
        Uri uri = new Uri.Builder().scheme("wear").authority("*").path(TeamSyncContract.PATH).build();
        Wearable.getDataClient(app).getDataItems(uri)
                .addOnSuccessListener(buffer -> {
                    boolean changed = false;
                    try {
                        for (DataItem item : buffer) {
                            changed |= save(app, DataMapItem.fromDataItem(item).getDataMap());
                        }
                    } finally {
                        buffer.release();
                    }
                    callback.onDone(changed);
                })
                .addOnFailureListener(e -> callback.onDone(false));   // 폰 연결이 없으면 지금 팀 그대로
    }

    /** 받은 데이터를 저장. 팀이 실제로 바뀌었으면 true */
    static boolean save(Context context, DataMap map) {
        String code = map.getString(TeamSyncContract.KEY_CODE);
        if (code == null || code.equals(TeamPrefs.code(context))) return false;

        TeamPrefs.save(context, new TeamInfo(code, map.getString(TeamSyncContract.KEY_NAME, code)));
        TileService.getUpdater(context).requestUpdate(TeamTileService.class);   // 타일도 새 팀으로
        return true;
    }
}
