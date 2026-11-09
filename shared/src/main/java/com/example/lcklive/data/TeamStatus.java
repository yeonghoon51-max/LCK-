package com.example.lcklive.data;

/** 내 팀을 한 번 조회했을 때의 결과: 지금 경기 / 다음 경기 / 가장 최근 끝난 경기 */
public class TeamStatus {
    public MatchInfo live;
    public MatchInfo next;
    public MatchInfo lastCompleted;
    public long fetchedAtMillis;
}
