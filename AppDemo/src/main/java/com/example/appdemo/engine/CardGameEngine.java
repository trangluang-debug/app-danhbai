package com.example.appdemo.engine;

import com.example.appdemo.model.Card;
import com.example.appdemo.model.GameState;
import java.util.List;

public interface CardGameEngine {
    void setListener(StateChangeListener listener);
    void startNewGame(int numBots);
    boolean playTurn(String playerId, List<Card> selectedCards);
    boolean playBluffTurn(String playerId, int claimedRank, List<Card> actualCards);
    void passTurn(String playerId);
    GameState getGameState();

    interface StateChangeListener {
        void onStateChanged();
    }
}