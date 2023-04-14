package com.example.readersmusic;

public enum MusicType {
    FIGHT, SORROW, CALM;
    public String getType(){
        switch (this){
            case FIGHT:
                return "Fight";
            case SORROW:
                return "Sorrow";
            case CALM:
                return "Calm";
            default:
                return null;
        }

    }
}
