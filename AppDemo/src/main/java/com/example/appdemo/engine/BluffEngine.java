package com.example.appdemo.engine;

import com.example.appdemo.model.*;
import com.example.appdemo.service.BluffRuleEngine;
import org.springframework.stereotype.Component;

import java.util.*;
import java.util.stream.Collectors;

@Component("BLUFF")
public class BluffEngine implements CardGameEngine {
    private final GameState gameState = new GameState();
    private transient StateChangeListener listener;
    private final Random random = new Random();
    private int consecutivePassCount = 0; // Đếm số lượt pass liên tiếp để xét round mới

    @Override
    public void setListener(StateChangeListener listener) {
        this.listener = listener;
    }

    private void notifyStateChanged() {
        if (listener != null) listener.onStateChanged();
    }

    @Override
    public GameState getGameState() {
        return this.gameState;
    }

    @Override
    public void startNewGame(int numBots) {
        if (numBots < 1) numBots = 1;
        if (numBots > 3) numBots = 3;

        consecutivePassCount = 0;
        gameState.setGameType("BLUFF");
        gameState.getPlayers().clear();

        Player human = new Player("p1", "Bạn (Player 1)");
        human.setRiskTolerance(1.0);
        gameState.getPlayers().add(human);

        // Gán tính cách cho bot (Aggressive < 1.0, Normal = 1.0, Cautious > 1.0)
        double[] riskTolerances = {0.75, 1.0, 1.30};
        for (int i = 1; i <= numBots; i++) {
            Player bot = new Player("bot" + i, "Máy " + i);
            bot.setRiskTolerance(riskTolerances[(i - 1) % riskTolerances.length]);
            gameState.getPlayers().add(bot);
        }

        // Tạo bộ bài chuẩn (3 -> 15) + 2 lá Joker (rank = 0, suit = 0) = 54 lá
        List<Card> deck = new ArrayList<>();
        for (int r = 3; r <= 15; r++) {
            for (int s = 1; s <= 4; s++) {
                deck.add(new Card(r, s));
            }
        }
        deck.add(new Card(0, 0));
        deck.add(new Card(0, 0));
        Collections.shuffle(deck);

        // Chia bài đều cho các player
        int idx = 0;
        for (Card c : deck) {
            gameState.getPlayers().get(idx % gameState.getPlayers().size()).getHand().add(c);
            idx++;
        }

        // Sắp xếp bài tay cho người chơi
        for (Player p : gameState.getPlayers()) {
            p.getHand().sort(Comparator.comparingInt(Card::getPower));
            p.setHasPassed(false);
            p.updateAction("");
        }

        // Reset state đặc thù Nói Láo trên GameState chung
        gameState.getPenaltyPile().clear();
        gameState.setCurrentClaimedRank(-1);
        gameState.setCurrentExpectedCount(0);
        gameState.setLastBluffMove(null);

        gameState.setCurrentTurnIndex(0);
        gameState.setLastPlayedTurnIndex(0);
        gameState.setGameStarted(true);
        gameState.setGameResult("");
        gameState.setGameMessage("Bắt đầu ván Nói Láo (54 lá)! Úp 1-4 lá theo số gọi vòng.");
        notifyStateChanged();

        if (gameState.getCurrentTurnIndex() != 0) {
            startBotThread();
        }
    }

    @Override
    public boolean playBluffTurn(String playerId, int claimedRank, List<Card> actualCards) {
        if (!gameState.isGameStarted() || !gameState.getGameResult().isEmpty()) return false;

        int currIdx = gameState.getCurrentTurnIndex();
        Player currentPlayer = gameState.getPlayers().get(currIdx);
        if (!currentPlayer.getId().equals(playerId)) return false;

        if (actualCards == null || actualCards.isEmpty() || actualCards.size() > 4) {
            gameState.setGameMessage(currentPlayer.getName() + " chỉ được úp từ 1 đến 4 lá!");
            notifyStateChanged();
            return false;
        }

        int activeRank = gameState.getCurrentClaimedRank();

        // NẾU ROUND ĐÃ CÓ SỐ ĐỊNH HÌNH (> 0), BẮT BUỘC TUÂN THEO SỐ ĐÓ CỦA ROUND:
        if (activeRank > 0) {
            claimedRank = activeRank;
        } else {
            // Nếu chưa có số định hình (mở đầu round/lượt đầu tiên), nhận claimedRank từ người chơi chọn, nếu không hợp lệ thì mặc định 3
            if (claimedRank <= 0) {
                claimedRank = 3;
            }
            gameState.setCurrentClaimedRank(claimedRank);
        }

        int expectedCount = actualCards.size();

        // FIX TRIỆT ĐỂ: Xóa độc lập từng lá một theo index tay bài thay vì removeIf gộp 0,0
        for (Card c : actualCards) {
            for (int i = 0; i < currentPlayer.getHand().size(); i++) {
                Card handCard = currentPlayer.getHand().get(i);
                if (handCard.getRank() == c.getRank() && handCard.getSuit() == c.getSuit()) {
                    currentPlayer.getHand().remove(i);
                    break;
                }
            }
        }

        consecutivePassCount = 0;
        gameState.setCurrentExpectedCount(expectedCount);
        gameState.setLastBluffMove(new BluffMove(playerId, claimedRank, expectedCount, new ArrayList<>(actualCards)));
        gameState.getPenaltyPile().addAll(actualCards);

        currentPlayer.updateAction("Úp " + expectedCount + " lá (gọi số " + formatRankStr(claimedRank) + ")");
        gameState.setGameMessage(currentPlayer.getName() + " úp " + expectedCount + " lá (gọi số " + formatRankStr(claimedRank) + "). Ai bắt bài không?");

        if (currentPlayer.getHand().isEmpty()) {
            gameState.setGameResult("WIN");
            gameState.setGameMessage("🎉 " + currentPlayer.getName() + " đã hết bài và giành chiến thắng!");
            notifyStateChanged();
            return true;
        }

        nextTurn();
        notifyStateChanged();

        if (gameState.getCurrentTurnIndex() != 0 && gameState.getGameResult().isEmpty()) {
            startBotThread();
        }
        return true;
    }

    @Override
    public void passTurn(String playerId) {
        if (!gameState.isGameStarted() || !gameState.getGameResult().isEmpty()) return;
        int currIdx = gameState.getCurrentTurnIndex();
        Player currentPlayer = gameState.getPlayers().get(currIdx);
        if (!currentPlayer.getId().equals(playerId)) return;

        currentPlayer.updateAction("Bỏ lượt");
        consecutivePassCount++;

        int totalPlayers = gameState.getPlayers().size();
        if (consecutivePassCount >= totalPlayers - 1 && gameState.getLastBluffMove() != null) {
            int lastActorIdx = getPlayerIndexById(gameState.getLastBluffMove().getPlayerId());
            startNewRound(lastActorIdx >= 0 ? lastActorIdx : currIdx);
            return;
        }

        gameState.setGameMessage(currentPlayer.getName() + " đã bỏ lượt.");
        nextTurn();
        notifyStateChanged();

        if (gameState.getCurrentTurnIndex() != 0 && gameState.getGameResult().isEmpty()) {
            startBotThread();
        }
    }

    private int getPlayerIndexById(String id) {
        for (int i = 0; i < gameState.getPlayers().size(); i++) {
            if (gameState.getPlayers().get(i).getId().equals(id)) {
                return i;
            }
        }
        return -1;
    }

    private void startNewRound(int starterIndex) {
        consecutivePassCount = 0;
        gameState.setLastBluffMove(null);
        gameState.setCurrentClaimedRank(-1);
        gameState.setCurrentExpectedCount(0);

        int clearedCount = gameState.getPenaltyPile() != null ? gameState.getPenaltyPile().size() : 0;
        gameState.getPenaltyPile().clear();

        for (Player p : gameState.getPlayers()) {
            p.updateAction("");
        }

        int nextIdx = starterIndex % gameState.getPlayers().size();
        Player starterPlayer = gameState.getPlayers().get(nextIdx);
        gameState.setCurrentTurnIndex(nextIdx);
        gameState.setGameMessage("Tất cả đã bỏ lượt! Đã loại bỏ " + clearedCount + " lá bài cũ, bắt đầu ROUND MỚI. Lượt mở round: " + starterPlayer.getName());
        notifyStateChanged();

        if (nextIdx != 0 && gameState.getGameResult().isEmpty()) {
            startBotThread();
        }
    }

    public void challenge(String challengerId) {
        if (!gameState.isGameStarted() || !gameState.getGameResult().isEmpty()) return;

        List<Card> pile = gameState.getPenaltyPile();
        BluffMove lastMove = gameState.getLastBluffMove();
        if (lastMove == null) {
            gameState.setGameMessage("Chưa có nước đi nào để kiểm tra!");
            notifyStateChanged();
            return;
        }

        boolean isRealLiar = !BluffRuleEngine.validateBluffClaim(
                lastMove.getActualCards(),
                lastMove.getClaimedRank(),
                lastMove.getExpectedCount()
        );

        Player challenger = gameState.getPlayers().stream()
                .filter(p -> p.getId().equals(challengerId)).findFirst().orElse(null);
        Player actor = gameState.getPlayers().stream()
                .filter(p -> p.getId().equals(lastMove.getPlayerId())).findFirst().orElse(null);

        int nextStarterIndex = -1;

        if (isRealLiar) {
            if (actor != null) {
                actor.getHand().addAll(pile);
                actor.getHand().sort(Comparator.comparingInt(Card::getPower));
            }
            nextStarterIndex = getPlayerIndexById(challengerId);
            gameState.setGameMessage((challenger != null ? challenger.getName() : "Ai đó") + " bắt chuẩn! " +
                    (actor != null ? actor.getName() : "Người chơi") + " nói láo/lẫn bài khác, nhặt " + pile.size() + " lá phạt. " +
                    (challenger != null ? challenger.getName() : "Người bắt") + " mở vòng mới.");
        } else {
            if (challenger != null) {
                challenger.getHand().addAll(pile);
                challenger.getHand().sort(Comparator.comparingInt(Card::getPower));
            }
            nextStarterIndex = getPlayerIndexById(lastMove.getPlayerId());
            gameState.setGameMessage((challenger != null ? challenger.getName() : "Ai đó") + " bắt hụt! " +
                    (actor != null ? actor.getName() : "Người chơi") + " nói thật chuẩn xác. " +
                    (challenger != null ? challenger.getName() : "Người check") + " nhặt phạt. " +
                    (actor != null ? actor.getName() : "Bạn") + " mở vòng mới.");
        }

        pile.clear();
        gameState.setLastBluffMove(null);
        gameState.setCurrentClaimedRank(-1);
        gameState.setCurrentExpectedCount(0);
        consecutivePassCount = 0;

        for (Player p : gameState.getPlayers()) {
            p.updateAction("");
        }

        int targetIdx = (nextStarterIndex >= 0) ? nextStarterIndex : 0;
        gameState.setCurrentTurnIndex(targetIdx);
        notifyStateChanged();

        if (targetIdx != 0 && gameState.getGameResult().isEmpty()) {
            startBotThread();
        }
    }

    @Override
    public boolean playTurn(String playerId, List<Card> selectedCards) {
        int activeRank = gameState.getCurrentClaimedRank();
        int finalRank = (activeRank > 0) ? activeRank : 3;
        return playBluffTurn(playerId, finalRank, selectedCards);
    }

    private void nextTurn() {
        int nextIndex = (gameState.getCurrentTurnIndex() + 1) % gameState.getPlayers().size();
        gameState.setCurrentTurnIndex(nextIndex);
        gameState.getPlayers().get(nextIndex).updateAction("");
    }

    private void startBotThread() {
        new Thread(() -> {
            try { Thread.sleep(1300); } catch (InterruptedException ignored) {}
            synchronized (this) {
                processBotTurn();
            }
        }).start();
    }

    private synchronized void processBotTurn() {
        if (gameState.getCurrentTurnIndex() == 0 || !gameState.getGameResult().isEmpty()) return;
        if (!gameState.isGameStarted()) return;

        Player bot = gameState.getPlayers().get(gameState.getCurrentTurnIndex());
        if (bot.getHand().isEmpty()) {
            passTurn(bot.getId());
            return;
        }

        BluffMove lastMove = gameState.getLastBluffMove();
        if (lastMove != null && !lastMove.getPlayerId().equals(bot.getId())) {
            if (decideToChallenge(bot, lastMove)) {
                challenge(bot.getId());
                return;
            }
        }

        List<Card> botHand = bot.getHand();
        if (botHand.isEmpty()) {
            passTurn(bot.getId());
            return;
        }

        int currentRank = gameState.getCurrentClaimedRank();
        if (currentRank <= 0) {
            List<Card> sortedHand = botHand.stream()
                    .sorted(Comparator.comparingInt(Card::getPower))
                    .collect(Collectors.toList());
            currentRank = sortedHand.get(0).getRank();
            if (currentRank == 0 && sortedHand.size() > 1) {
                currentRank = sortedHand.get(1).getRank();
            }
            if (currentRank <= 0) currentRank = 3;
        }

        List<Card> matchingCards = new ArrayList<>();
        for (Card c : botHand) {
            if (c.getRank() == currentRank || c.getRank() == 0) {
                matchingCards.add(c);
            }
        }

        if (matchingCards.isEmpty()) {
            double baseBluffChance = 0.20;
            double personalityModifier = (1.0 - bot.getRiskTolerance()) * 0.3;
            int pileSize = gameState.getPenaltyPile() != null ? gameState.getPenaltyPile().size() : 0;
            double safetyModifier = (bot.getHand().size() < 5 || pileSize > 8) ? -0.25 : 0.0;
            double finalBluffChance = Math.max(0.02, Math.min(0.60, baseBluffChance + personalityModifier + safetyModifier));

            if (random.nextDouble() > finalBluffChance) {
                passTurn(bot.getId());
            } else {
                int count = Math.min(2, botHand.size());
                List<Card> bluffCards = new ArrayList<>(botHand.subList(0, count));
                playBluffTurn(bot.getId(), currentRank, bluffCards);
            }
        } else {
            int maxCount = Math.min(4, matchingCards.size());
            int countToPlay = 1 + random.nextInt(maxCount);
            List<Card> cardsToPlay = new ArrayList<>(matchingCards.subList(0, countToPlay));
            playBluffTurn(bot.getId(), currentRank, cardsToPlay);
        }
    }

    private boolean decideToChallenge(Player bot, BluffMove lastMove) {
        if (lastMove == null) return false;
        List<Card> pile = gameState.getPenaltyPile();
        int pileSize = pile != null ? pile.size() : 0;

        double confidence = 0.35;
        confidence += (lastMove.getExpectedCount() - 1) * 0.12;

        long matchingCountInHand = bot.getHand().stream()
                .filter(c -> c.getRank() == lastMove.getClaimedRank() || c.getRank() == 0)
                .count();

        if (lastMove.getExpectedCount() >= 3 && matchingCountInHand >= 2) {
            confidence += 0.45;
        }

        double baseThreshold = 0.70;
        double penaltyRiskFactor = Math.min(1.8, 1.0 + (pileSize * 0.045));

        int botHandSize = bot.getHand().size();
        double survivalAdjustment = 0.0;
        if (botHandSize < 5 && pileSize > 4) {
            survivalAdjustment += 0.35;
        } else if (botHandSize > 10 && pileSize <= 5) {
            survivalAdjustment -= 0.15;
        }

        double rawThreshold = (baseThreshold * penaltyRiskFactor) + survivalAdjustment;
        double dynamicThreshold = Math.max(0.3, rawThreshold * bot.getRiskTolerance());

        return confidence >= dynamicThreshold;
    }

    private String formatRankStr(int rank) {
        switch (rank) {
            case 11: return "J";
            case 12: return "Q";
            case 13: return "K";
            case 14: return "A";
            case 15: return "2";
            default: return String.valueOf(rank);
        }
    }
}