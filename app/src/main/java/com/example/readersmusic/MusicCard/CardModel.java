package com.example.readersmusic.MusicCard;

import com.example.readersmusic.MusicType;

public class CardModel {
    private int cardImage;
    private String cardName;
    private MusicType type;

    public CardModel(int cardImage, String cardName, MusicType type) {
        this.cardImage = cardImage;
        this.cardName = cardName;
        this.type = type;
    }

    public int getCardImage() {
        return cardImage;
    }

    public String getCardName() {
        return cardName;
    }

    public MusicType getType() {
        return type;
    }
}
