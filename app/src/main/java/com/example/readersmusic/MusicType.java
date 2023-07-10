package com.example.readersmusic;

import android.content.Context;

public enum MusicType {
    Battle(R.string.type_battle), SORROW(R.string.type_sorrow), CALM(R.string.type_calm), FEAR(R.string.type_fear);

    private final int name;

    MusicType(int name) {
        this.name = name;
    }

    public String getType(Context c){
        switch (this){
            case Battle:
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
