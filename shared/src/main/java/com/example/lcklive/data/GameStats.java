package com.example.lcklive.data;

/** 진행 중인 한 세트의 실시간 스탯 (내 팀 기준으로 정리) */
public class GameStats {

    public int gameNumber;      // 몇 세트인지
    public boolean myBlue;      // 내 팀이 블루 진영인지
    public String gameState;    // "in_game", "paused", "finished"

    public int myKills, oppKills;
    public int myGold, oppGold;
    public int myTowers, oppTowers;
    public int myDragons, oppDragons;
    public int myBarons, oppBarons;

    public int goldDiff() {
        return myGold - oppGold;
    }
}
