package com.example.readersmusic;

import android.content.Context;

public enum MusicType {
    FIGHT, SORROW, CALM, FEAR;
    public String getType(Context c){
        switch (this){
            case FIGHT:
                return c.getString(R.string.type_battle);
            case SORROW:
                return  c.getString(R.string.type_sorrow);
            case CALM:
                return  c.getString(R.string.type_calm);
            case FEAR:
                return  c.getString(R.string.type_fear);
            default:
                return null;
        }

    }
}
