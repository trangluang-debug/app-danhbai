package com.example.appdemo.model;

import lombok.Data;
import java.util.ArrayList;
import java.util.List;

@Data
public class GameState {
    private String gameType = "TIEN_LEN";
    private List<Player> players = new ArrayList<>();
    private List<Card> lastPlayedCards = new ArrayList<>();
    private int currentTurnIndex = 0;
    private int lastPlayedTurnIndex = 0;
    private boolean gameStarted = false;
    private String gameMessage = "";
    private String gameResult = "";
    private List<Card> penaltyPile = new ArrayList<>(); // Chồng bài phạt chung giữa bàn
    private int currentClaimedRank = -1;               // Rank đang bị khai báo (VD: đánh đôi 3 -> 3)
    private int currentExpectedCount = 0;              // Số lượng cần đánh (VD: 2)
    private BluffMove lastBluffMove;
}