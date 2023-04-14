package com.example.readersmusic;

import android.content.Context;

import java.util.ArrayList;
import java.util.Collections;

public class MusicLoader {
    private MusicType type;
    private ArrayList<Music> musicList;
    private Context context;
    private int orderIndex;

    public MusicLoader(MusicType type, Context context) {
        this.context = context;
        this.type = type;
        musicList = new ArrayList<>();
        switch (type){
            case CALM:
                loadCalm();
                break;
            case FIGHT:
                loadFight();
                break;
            case SORROW:
                loadSorrow();
                break;
        }
    }

    public Music getLastMusic(){
        if(orderIndex<=1)
            return musicList.get(0);
        orderIndex--;
        return musicList.get(orderIndex-1);
    }
    public Music getMusicInOrder(){
        if(orderIndex>=musicList.size()) {
            orderIndex = 0;
            Collections.shuffle(musicList);
        }
        return musicList.get(orderIndex++);
    }

    /*
    Unused musics:
    wars of faith
    trials of odin
     */
    private void loadSorrow() {
        musicList.add(new Music(R.raw.chasm, MusicType.SORROW, context));
        musicList.add(new Music(R.raw.childhood, MusicType.SORROW, context));
        musicList.add(new Music(R.raw.faith, MusicType.SORROW, context));
        musicList.add(new Music(R.raw.reflection, MusicType.SORROW, context));
        musicList.add(new Music(R.raw.do_you_never_laugh, MusicType.SORROW, context));
        musicList.add(new Music(R.raw.njol, MusicType.SORROW, context));
        musicList.add(new Music(R.raw.trespasser, MusicType.SORROW, context));
        musicList.add(new Music(R.raw.king_slayer, MusicType.SORROW, context));

        Collections.shuffle(musicList);
    }

    private void loadCalm() {
        musicList.add(new Music(R.raw.secunda, MusicType.CALM, context));
        musicList.add(new Music(R.raw.white_palace, MusicType.CALM, context));
        musicList.add(new Music(R.raw.frostfall, MusicType.CALM, context));
        musicList.add(new Music(R.raw.blinded_forest, MusicType.CALM, context));
        musicList.add(new Music(R.raw.rajan, MusicType.CALM, context));
        musicList.add(new Music(R.raw.viking_sail_home, MusicType.CALM, context));
        Collections.shuffle(musicList);
    }

    private void loadFight() {
        musicList.add(new Music(R.raw.fever_dream, MusicType.FIGHT, context));
        musicList.add(new Music(R.raw.before_lights_out, MusicType.FIGHT, context));
        musicList.add(new Music(R.raw.k2, MusicType.FIGHT, context));
        musicList.add(new Music(R.raw.none_shall_live, MusicType.FIGHT, context));
        musicList.add(new Music(R.raw.scimitar, MusicType.FIGHT, context));
        Collections.shuffle(musicList);
    }


}
