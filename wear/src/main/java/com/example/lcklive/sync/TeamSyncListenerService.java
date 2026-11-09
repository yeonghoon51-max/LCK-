package com.example.lcklive.sync;

import androidx.annotation.NonNull;

import com.google.android.gms.wearable.DataEvent;
import com.google.android.gms.wearable.DataEventBuffer;
import com.google.android.gms.wearable.DataMapItem;
import com.google.android.gms.wearable.WearableListenerService;

/**
 * 폰 앱에서 팀을 바꾸면 시스템이 이 서비스를 깨워서 알려준다.
 * 워치 앱이 꺼져 있어도 동작한다.
 */
public class TeamSyncListenerService extends WearableListenerService {

    @Override
    public void onDataChanged(@NonNull DataEventBuffer events) {
        for (DataEvent event : events) {
            if (event.getType() == DataEvent.TYPE_CHANGED
                    && TeamSyncContract.PATH.equals(event.getDataItem().getUri().getPath())) {
                PhoneTeamSync.save(this, DataMapItem.fromDataItem(event.getDataItem()).getDataMap());
            }
        }
    }
}
