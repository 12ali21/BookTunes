package com.example.readersmusic;

public class CardModel {
    private int cardImage;
    private String cardName;

    public CardModel(int cardImage, String cardName) {
        this.cardImage = cardImage;
        this.cardName = cardName;
    }

    public int getCardImage() {
        return cardImage;
    }

    public void setCardImage(int cardImage) {
        this.cardImage = cardImage;
    }

    public String getCardName() {
        return cardName;
    }

    public void setCardName(String cardName) {
        this.cardName = cardName;
    }
}
