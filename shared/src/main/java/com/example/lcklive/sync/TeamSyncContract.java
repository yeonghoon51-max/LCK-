package com.example.lcklive.sync;

/**
 * 폰 → 워치로 응원 팀을 보낼 때 쓰는 약속(경로, 키 이름).
 * 폰과 워치가 같은 값을 써야 하므로 공용 모듈에 둔다.
 */
public final class TeamSyncContract {

    /** Wearable Data Layer 의 데이터 경로 */
    public static final String PATH = "/team";

    public static final String KEY_CODE = "code";
    public static final String KEY_NAME = "name";
    /** 같은 팀을 다시 보내도 "바뀜"으로 처리되도록 넣는 시각 */
    public static final String KEY_SENT_AT = "sentAt";

    private TeamSyncContract() {
    }
}
