package com.example.readersmusic

import android.content.Context

class MusicLoader(type: MusicType, private val context: Context) {
    private val musicList: ArrayList<Music> = ArrayList()
    private var orderIndex = 0

    init {
        when (type) {
            MusicType.CALM -> loadCalm()
            MusicType.Battle -> loadFight()
            MusicType.SORROW -> loadSorrow()
            MusicType.FEAR -> loadFear()
        }
    }

    val lastMusic: Music
        get() {
            if (orderIndex <= 1) return musicList[0]
            return musicList[--orderIndex]
        }

    val musicInOrder: Music
        get() {
            if (orderIndex >= musicList.size) {
                orderIndex = 0
                musicList.shuffle()
            }
            return musicList[orderIndex++]
        }

    /*
    Unused musics:
        wars of faith
        trials of odin
     */
    private fun loadSorrow() {
        musicList.add(Music(R.raw.chasm, MusicType.SORROW, context))
        musicList.add(Music(R.raw.childhood, MusicType.SORROW, context))
        musicList.add(Music(R.raw.faith, MusicType.SORROW, context))
        musicList.add(Music(R.raw.reflection, MusicType.SORROW, context))
        musicList.add(Music(R.raw.do_you_never_laugh, MusicType.SORROW, context))
        musicList.add(Music(R.raw.njol, MusicType.SORROW, context))
        musicList.add(Music(R.raw.trespasser, MusicType.SORROW, context))
        musicList.add(Music(R.raw.king_slayer, MusicType.SORROW, context))
        musicList.shuffle()
    }

    private fun loadCalm() {
        musicList.add(Music(R.raw.secunda, MusicType.CALM, context))
        musicList.add(Music(R.raw.white_palace, MusicType.CALM, context))
        musicList.add(Music(R.raw.frostfall, MusicType.CALM, context))
        musicList.add(Music(R.raw.blinded_forest, MusicType.CALM, context))
        musicList.add(Music(R.raw.rajan, MusicType.CALM, context))
        musicList.add(Music(R.raw.viking_sail_home, MusicType.CALM, context))
        musicList.shuffle()
    }

    private fun loadFight() {
        musicList.add(Music(R.raw.fever_dream, MusicType.Battle, context))
        musicList.add(Music(R.raw.before_lights_out, MusicType.Battle, context))
        musicList.add(Music(R.raw.k2, MusicType.Battle, context))
        musicList.add(Music(R.raw.none_shall_live, MusicType.Battle, context))
        musicList.add(Music(R.raw.scimitar, MusicType.Battle, context))
        musicList.shuffle()
    }

    private fun loadFear() {
        musicList.add(Music(R.raw.tension, MusicType.FEAR, context))
        //        musicList.add(new Music(R.raw.battle_school, MusicType.FEAR, context));
        musicList.add(Music(R.raw.driving_to_mexico, MusicType.FEAR, context))
        musicList.add(Music(R.raw.into_the_storm, MusicType.FEAR, context))
        musicList.add(Music(R.raw.mordor, MusicType.FEAR, context))
        musicList.add(Music(R.raw.the_hunt, MusicType.FEAR, context))
        musicList.shuffle()
    }
}