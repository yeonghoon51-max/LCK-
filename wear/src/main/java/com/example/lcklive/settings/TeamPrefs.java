package com.example.lcklive.settings;

import android.content.Context;
import android.content.SharedPreferences;

import com.example.lcklive.data.TeamInfo;

/** 응원하는 팀을 시계에 저장해 둔다. 처음엔 T1. */
public final class TeamPrefs {

    private static final String PREFS = "team_prefs";
    private static final String KEY_CODE = "code";
    private static final String KEY_NAME = "name";

    public static final String DEFAULT_CODE = "T1";

    private TeamPrefs() {
    }

    public static String code(Context context) {
        return prefs(context).getString(KEY_CODE, DEFAULT_CODE);
    }

    public static String name(Context context) {
        return prefs(context).getString(KEY_NAME, DEFAULT_CODE);
    }

    public static void save(Context context, TeamInfo team) {
        prefs(context).edit()
                .putString(KEY_CODE, team.code)
                .putString(KEY_NAME, team.name)
                .apply();
    }

    private static SharedPreferences prefs(Context context) {
        return context.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }
}
