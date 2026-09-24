package com.example.appdemo.controller;

import com.example.appdemo.engine.BluffEngine;
import com.example.appdemo.engine.CardGameEngine;
import com.example.appdemo.factory.GameFactory;
import com.example.appdemo.model.Card;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Controller;

import jakarta.annotation.PostConstruct;
import java.util.List;
import java.util.Map;

@Controller
public class GameController {

    @Autowired
    private GameFactory gameFactory;

    @Autowired
    private SimpMessagingTemplate messagingTemplate;

    @PostConstruct
    public void init() {
        registerListener(gameFactory.getActiveEngine());
    }

    private void registerListener(CardGameEngine engine) {
        if (engine != null) {
            engine.setListener(this::broadcastState);
        }
    }

    @MessageMapping("/select-game")
    public void selectGame(Map<String, String> payload) {
        String gameType = (payload != null) ? payload.getOrDefault("gameType", "TIEN_LEN") : "TIEN_LEN";
        CardGameEngine selectedEngine = gameFactory.selectGame(gameType);
        if (selectedEngine != null && selectedEngine.getGameState() != null) {
            selectedEngine.getGameState().setGameType(gameType);
        }
        registerListener(selectedEngine);
        broadcastState();
    }

    @MessageMapping("/start")
    public void startGame(Map<String, Integer> payload) {
        int numBots = (payload != null) ? payload.getOrDefault("numBots", 3) : 3;
        CardGameEngine engine = gameFactory.getActiveEngine();
        if (engine != null) {
            engine.startNewGame(numBots);
        }
    }

    @MessageMapping("/play")
    public void playTurn(@Payload ActionRequest request) {
        if (request != null && request.getPlayerId() != null && request.getCards() != null) {
            CardGameEngine engine = gameFactory.getActiveEngine();
            if (engine instanceof BluffEngine) {
                int rankClaim = (request.getClaimedRank() != null) ? request.getClaimedRank() : 3;
                ((BluffEngine) engine).playBluffTurn(request.getPlayerId(), rankClaim, request.getCards());
            } else {
                engine.playTurn(request.getPlayerId(), request.getCards());
            }
            broadcastState();
        }
    }

    @MessageMapping("/pass")
    public void passTurn(@Payload(required = false) ActionRequest request) {
        String playerId = (request != null && request.getPlayerId() != null) ? request.getPlayerId() : "p1";
        gameFactory.getActiveEngine().passTurn(playerId);
        broadcastState();
    }

    @MessageMapping("/challenge")
    public void challengeBluff(@Payload(required = false) ActionRequest request) {
        String playerId = (request != null && request.getPlayerId() != null) ? request.getPlayerId() : "p1";
        CardGameEngine engine = gameFactory.getActiveEngine();
        if (engine instanceof BluffEngine) {
            ((BluffEngine) engine).challenge(playerId);
            broadcastState();
        }
    }

    private void broadcastState() {
        if (gameFactory.getActiveEngine() != null) {
            messagingTemplate.convertAndSend("/topic/game-state", gameFactory.getActiveEngine().getGameState());
        }
    }

    // DTO nhận dữ liệu từ client hỗ trợ cả Tiến Lên và Nói Láo
    public static class ActionRequest {
        private String playerId;
        private List<Card> cards;
        private Integer claimedRank; // Giá trị khai báo khi chơi Nói Láo (VD: 3, 4, ...)

        public String getPlayerId() { return playerId; }
        public void setPlayerId(String playerId) { this.playerId = playerId; }

        public List<Card> getCards() { return cards; }
        public void setCards(List<Card> cards) { this.cards = cards; }

        public Integer getClaimedRank() { return claimedRank; }
        public void setClaimedRank(Integer claimedRank) { this.claimedRank = claimedRank; }
    }
}