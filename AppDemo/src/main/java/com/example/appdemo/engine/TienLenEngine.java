package com.example.appdemo.engine;

import com.example.appdemo.model.*;
import com.example.appdemo.service.RuleEngine;
import org.springframework.stereotype.Component;

import java.util.*;

@Component("TIEN_LEN")
public class TienLenEngine implements CardGameEngine {
    private final GameState gameState = new GameState();
    private transient StateChangeListener listener;
    private boolean isFirstTurnOfGame = true;

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

    private String getCardsTextDescription(List<Card> cards) {
        if (cards == null || cards.isEmpty()) return "";
        Map<Integer, String> rankNames = Map.ofEntries(
                Map.entry(3, "3"), Map.entry(4, "4"), Map.entry(5, "5"), Map.entry(6, "6"),
                Map.entry(7, "7"), Map.entry(8, "8"), Map.entry(9, "9"), Map.entry(10, "10"),
                Map.entry(11, "J"), Map.entry(12, "Q"), Map.entry(13, "K"), Map.entry(14, "A"), Map.entry(15, "2")
        );
        RuleEngine.ComboType type = RuleEngine.getComboType(cards);
        String highestRank = rankNames.get(cards.get(cards.size() - 1).getRank());

        if (type == RuleEngine.ComboType.SINGLE) return highestRank;
        if (type == RuleEngine.ComboType.PAIR) return "Đôi " + highestRank;
        if (type == RuleEngine.ComboType.TRIPLE) return "Xám " + highestRank;
        if (type == RuleEngine.ComboType.QUAD) return "Tứ quý " + highestRank;
        if (type == RuleEngine.ComboType.STRAIGHT) return "Sảnh " + cards.size() + " lá";
        if (type == RuleEngine.ComboType.DOUBLE_STRAIGHT_3) return "3 đôi thông";
        if (type == RuleEngine.ComboType.DOUBLE_STRAIGHT_4) return "4 đôi thông";
        return "Đánh " + cards.size() + " lá";
    }

    @Override
    public void startNewGame(int numBots) {
        if (numBots < 1) numBots = 1;
        if (numBots > 3) numBots = 3;

        gameState.setGameType("TIEN_LEN");
        gameState.getPlayers().clear();
        gameState.getPlayers().add(new Player("p1", "Bạn (Player 1)"));
        for (int i = 1; i <= numBots; i++) {
            gameState.getPlayers().add(new Player("bot" + i, "Máy " + i));
        }

        List<Card> deck = new ArrayList<>();
        for (int r = 3; r <= 15; r++) {
            for (int s = 1; s <= 4; s++) {
                deck.add(new Card(r, s));
            }
        }
        Collections.shuffle(deck);

        for (int i = 0; i < 13; i++) {
            for (Player p : gameState.getPlayers()) {
                p.getHand().add(deck.remove(0));
            }
        }

        for (Player p : gameState.getPlayers()) {
            p.getHand().sort(Comparator.comparingInt(Card::getPower));
            p.setHasPassed(false);
            p.updateAction("");
        }

        this.isFirstTurnOfGame = true;

        // Kiểm tra Tới Trắng ngay sau khi chia bài
        for (Player p : gameState.getPlayers()) {
            RuleEngine.InstantWinType winType = RuleEngine.checkInstantWin(p.getHand());
            if (winType != RuleEngine.InstantWinType.NONE) {
                gameState.setLastPlayedCards(new ArrayList<>());
                gameState.setCurrentTurnIndex(0);
                gameState.setLastPlayedTurnIndex(0);
                gameState.setGameStarted(true);
                gameState.setGameResult(p.isBot() ? "LOSE" : "WIN");
                gameState.setGameMessage("🎉 " + p.getName() + " TỚI TRẮNG (" + winType + ")!");
                notifyStateChanged();
                return;
            }
        }

        // Tìm người giữ 3 Bích (rank 3, suit 1) để đi đầu
        int startIdx = 0;
        for (int i = 0; i < gameState.getPlayers().size(); i++) {
            Player p = gameState.getPlayers().get(i);
            boolean has3Spade = p.getHand().stream().anyMatch(c -> c.getRank() == 3 && c.getSuit() == 1);
            if (has3Spade) {
                startIdx = i;
                break;
            }
        }

        gameState.setLastPlayedCards(new ArrayList<>());
        gameState.setCurrentTurnIndex(startIdx);
        gameState.setLastPlayedTurnIndex(startIdx);
        gameState.setGameStarted(true);
        gameState.setGameResult("");
        Player startingPlayer = gameState.getPlayers().get(startIdx);
        gameState.setGameMessage("Ván bài Tiến Lên mới! " + startingPlayer.getName() + " có 3♠ được đi trước.");
        notifyStateChanged();

        if (startIdx != 0) {
            startBotThread();
        }
    }

    @Override
    public boolean playTurn(String playerId, List<Card> selectedCards) {
        if (!gameState.isGameStarted() || !gameState.getGameResult().isEmpty()) return false;

        Player currentPlayer = gameState.getPlayers().get(gameState.getCurrentTurnIndex());
        if (!currentPlayer.getId().equals(playerId)) return false;

        if (currentPlayer.isHasPassed()) {
            gameState.setGameMessage("Bạn đã bỏ lượt trong vòng này!");
            notifyStateChanged();
            return false;
        }

        if (RuleEngine.canPlay(selectedCards, gameState.getLastPlayedCards(), this.isFirstTurnOfGame)) {
            for (Card c : selectedCards) {
                currentPlayer.getHand().removeIf(card -> card.getRank() == c.getRank() && card.getSuit() == c.getSuit());
            }

            gameState.setLastPlayedCards(new ArrayList<>(selectedCards));
            gameState.setLastPlayedTurnIndex(gameState.getCurrentTurnIndex());
            currentPlayer.updateAction(getCardsTextDescription(selectedCards));
            this.isFirstTurnOfGame = false;

            if (currentPlayer.getHand().isEmpty()) {
                gameState.setGameResult("WIN");
                gameState.setGameMessage("🎉 Bạn đã hết bài và giành CHIẾN THẮNG!");
                notifyStateChanged();
                return true;
            }

            nextTurn();
            return true;
        } else {
            if (this.isFirstTurnOfGame) {
                gameState.setGameMessage("Lượt đầu tiên bắt buộc phải đánh bài chứa 3♠!");
                notifyStateChanged();
            }
        }
        return false;
    }

    @Override
    public boolean playBluffTurn(String playerId, int claimedRank, List<Card> actualCards) {
        return false;
    }

    @Override
    public void passTurn(String playerId) {
        if (!gameState.isGameStarted() || !gameState.getGameResult().isEmpty()) return;

        Player currentPlayer = gameState.getPlayers().get(gameState.getCurrentTurnIndex());
        if (currentPlayer.getId().equals(playerId)) {
            if (gameState.getLastPlayedCards().isEmpty()) {
                gameState.setGameMessage("Bạn đang giữ vòng, không thể bỏ lượt!");
                notifyStateChanged();
                return;
            }
            if (this.isFirstTurnOfGame) {
                gameState.setGameMessage("Lượt đầu tiên có 3♠ không được bỏ lượt!");
                notifyStateChanged();
                return;
            }

            currentPlayer.setHasPassed(true);
            currentPlayer.updateAction("Bỏ lượt");
            gameState.setGameMessage(currentPlayer.getName() + " đã bỏ lượt.");
            nextTurn();
        }
    }

    private void nextTurn() {
        if (checkRoundOver()) {
            gameState.setCurrentTurnIndex(gameState.getLastPlayedTurnIndex());
            resetRound();
            notifyStateChanged();

            if (gameState.getCurrentTurnIndex() != 0 && gameState.getGameResult().isEmpty()) {
                startBotThread();
            } else if (gameState.getCurrentTurnIndex() == 0) {
                gameState.setGameMessage("Tất cả đã bỏ lượt! Bạn thắng vòng này và được ra bài mới.");
                notifyStateChanged();
            }
            return;
        }

        int nextIndex = (gameState.getCurrentTurnIndex() + 1) % gameState.getPlayers().size();
        while (gameState.getPlayers().get(nextIndex).isHasPassed()) {
            nextIndex = (nextIndex + 1) % gameState.getPlayers().size();
        }

        gameState.setCurrentTurnIndex(nextIndex);
        notifyStateChanged();

        if (gameState.getCurrentTurnIndex() != 0 && gameState.getGameResult().isEmpty()) {
            startBotThread();
        } else if (gameState.getCurrentTurnIndex() == 0) {
            gameState.setGameMessage("Đến lượt bạn đánh!");
            notifyStateChanged();
        }
    }

    private void startBotThread() {
        new Thread(() -> {
            try { Thread.sleep(1000); } catch (InterruptedException ignored) {}
            processBotTurn();
        }).start();
    }

    private boolean checkRoundOver() {
        int activePlayers = 0;
        for (Player p : gameState.getPlayers()) {
            if (!p.isHasPassed()) activePlayers++;
        }
        return activePlayers <= 1;
    }

    private void resetRound() {
        gameState.setLastPlayedCards(new ArrayList<>());
        for (Player p : gameState.getPlayers()) {
            p.setHasPassed(false);
            p.updateAction("");
        }
        gameState.setGameMessage("Vòng mới! " + gameState.getPlayers().get(gameState.getCurrentTurnIndex()).getName() + " thắng vòng.");
    }

    private synchronized void processBotTurn() {
        if (gameState.getCurrentTurnIndex() == 0 || !gameState.getGameResult().isEmpty()) return;

        Player bot = gameState.getPlayers().get(gameState.getCurrentTurnIndex());
        if (bot.isHasPassed()) {
            nextTurn();
            return;
        }

        List<Card> botHand = bot.getHand();
        List<Card> move = findValidMoveForBot(botHand, gameState.getLastPlayedCards());

        if (!move.isEmpty()) {
            for (Card c : move) {
                botHand.removeIf(card -> card.getRank() == c.getRank() && card.getSuit() == c.getSuit());
            }

            gameState.setLastPlayedCards(new ArrayList<>(move));
            gameState.setLastPlayedTurnIndex(gameState.getCurrentTurnIndex());
            bot.updateAction(getCardsTextDescription(move));
            this.isFirstTurnOfGame = false;

            if (botHand.isEmpty()) {
                gameState.setGameResult("LOSE");
                gameState.setGameMessage("☠️ " + bot.getName() + " đã hết bài! Bạn đã THUA!");
                notifyStateChanged();
                return;
            } else {
                gameState.setGameMessage(bot.getName() + " vừa đánh " + move.size() + " lá bài.");
            }
        } else {
            bot.setHasPassed(true);
            bot.updateAction("Bỏ lượt");
            gameState.setGameMessage(bot.getName() + " bỏ lượt.");
        }

        nextTurn();
    }

    private List<Card> findValidMoveForBot(List<Card> hand, List<Card> tableCards) {
        if (this.isFirstTurnOfGame) {
            Card spade3 = hand.stream().filter(c -> c.getRank() == 3 && c.getSuit() == 1).findFirst().orElse(null);
            if (spade3 != null) {
                List<Card> move = Collections.singletonList(spade3);
                if (RuleEngine.canPlay(move, tableCards, this.isFirstTurnOfGame)) return move;
            }
        }

        if (tableCards == null || tableCards.isEmpty()) {
            return Collections.singletonList(hand.get(0));
        }

        RuleEngine.ComboType tableType = RuleEngine.getComboType(tableCards);

        if (tableType == RuleEngine.ComboType.SINGLE) {
            for (Card card : hand) {
                List<Card> singleMove = Collections.singletonList(card);
                if (RuleEngine.canPlay(singleMove, tableCards, this.isFirstTurnOfGame)) return singleMove;
            }
        }

        if (tableType == RuleEngine.ComboType.PAIR) {
            for (int i = 0; i < hand.size() - 1; i++) {
                if (hand.get(i).getRank() == hand.get(i + 1).getRank()) {
                    List<Card> pairMove = Arrays.asList(hand.get(i), hand.get(i + 1));
                    if (RuleEngine.canPlay(pairMove, tableCards, this.isFirstTurnOfGame)) return pairMove;
                }
            }
        }

        return new ArrayList<>();
    }
}