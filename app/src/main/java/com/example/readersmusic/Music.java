package com.example.readersmusic;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.media.MediaDataSource;
import android.media.MediaMetadataRetriever;
import android.net.Uri;

import java.io.IOException;

public class Music {
    private final int source;
    private  String name;
    private final MusicType type;
    private String artist;
    private Bitmap cover;

    public int getSource() {
        return source;
    }

    public Bitmap getCover() {
        return cover;
    }

    public Music(int source, MusicType type, Context context) {
        this.source = source;
        this.type = type;
        extractDetails(context);
    }

    private void extractDetails(Context context){
        MediaMetadataRetriever metadataRetriever = new MediaMetadataRetriever();
        metadataRetriever.setDataSource(context, Uri.parse("android.resource://com.example.readersmusic/" + source));

        artist = metadataRetriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_ARTIST);
        name = metadataRetriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_TITLE);
        byte[] coverBytes = metadataRetriever.getEmbeddedPicture();
        if(coverBytes!=null){
            cover = BitmapFactory.decodeByteArray(coverBytes, 0, coverBytes.length);
        } else {
            cover = null;
        }
        try {
            metadataRetriever.release();
        } catch (IOException e) {
        }
    }

    public String getName() {
        return name;
    }

    public MusicType getType() {
        return type;
    }

    public String getArtist() {
        return artist;
    }
}
