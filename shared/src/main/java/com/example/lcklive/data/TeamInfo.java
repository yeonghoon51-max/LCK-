package com.example.lcklive.data;

/** 팀 선택 목록에 보여줄 팀 하나 */
public class TeamInfo {
    public final String code;   // 예: "T1", "GEN"
    public final String name;   // 예: "T1", "Gen.G"

    public TeamInfo(String code, String name) {
        this.code = code;
        this.name = name;
    }

    @Override
    public String toString() {
        return code + " (" + name + ")";
    }
}
