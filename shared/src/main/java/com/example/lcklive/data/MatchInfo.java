package com.example.lcklive.data;

/**
 * 내 팀 경기 하나(Bo3, Bo5 시리즈 전체)의 정보.
 * 항상 "내 팀 쪽"과 "상대 쪽"으로 정리해서 담아 둔다.
 */
public class MatchInfo {

    public enum State { UPCOMING, LIVE, COMPLETED }

    public String matchId;
    public State state;
    public long startTimeMillis;
    public String leagueName;   // 예: "LCK", "Worlds"
    public String blockName;    // 예: "1주 차", "결승전"
    public int bestOf;          // 3 이면 Bo3

    public String myName;
    public String myCode;       // 예: "T1"
    public String myTeamId;
    public String oppName;
    public String oppCode;      // 예: "GEN", "HLE"
    public int myWins;
    public int oppWins;

    /** 진행 중인 세트의 실시간 스탯. 없으면 null */
    public GameStats liveGame;

    public String scoreText() {
        return myWins + " : " + oppWins;
    }

    public boolean wonSeries() {
        return myWins > oppWins;
    }
}
