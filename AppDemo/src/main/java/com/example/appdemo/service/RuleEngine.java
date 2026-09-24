package com.example.appdemo.service;

import com.example.appdemo.model.Card;
import java.util.*;

public class RuleEngine {

    public enum ComboType {
        INVALID, SINGLE, PAIR, TRIPLE, QUAD, STRAIGHT, DOUBLE_STRAIGHT_3, DOUBLE_STRAIGHT_4
    }

    public enum InstantWinType {
        NONE, DRAGON_STRAIGHT, FOUR_TWOS, FIVE_DOUBLE_STRAIGHT, SIX_PAIRS, SAME_COLOR
    }

    public static ComboType getComboType(List<Card> cards) {
        if (cards == null || cards.isEmpty()) return ComboType.INVALID;
        int size = cards.size();

        if (size == 1) return ComboType.SINGLE;

        boolean allSameRank = true;
        for (int i = 1; i < size; i++) {
            if (cards.get(i).getRank() != cards.get(0).getRank()) {
                allSameRank = false;
                break;
            }
        }

        if (allSameRank) {
            if (size == 2) return ComboType.PAIR;
            if (size == 3) return ComboType.TRIPLE;
            if (size == 4) return ComboType.QUAD;
            return ComboType.INVALID;
        }

        List<Card> sorted = cards.stream()
                .sorted(Comparator.comparingInt(Card::getRank))
                .toList();

        if (size >= 3) {
            boolean hasTwo = sorted.stream().anyMatch(c -> c.getRank() == 15);
            if (!hasTwo) {
                boolean isStraight = true;
                for (int i = 0; i < size - 1; i++) {
                    if (sorted.get(i + 1).getRank() - sorted.get(i).getRank() != 1) {
                        isStraight = false;
                        break;
                    }
                }
                if (isStraight) return ComboType.STRAIGHT;
            }
        }

        if (size == 6 && isNDoubleStraight(sorted, 3)) {
            return ComboType.DOUBLE_STRAIGHT_3;
        }

        if (size == 8 && isNDoubleStraight(sorted, 4)) {
            return ComboType.DOUBLE_STRAIGHT_4;
        }

        return ComboType.INVALID;
    }

    private static boolean isNDoubleStraight(List<Card> sortedCards, int pairCount) {
        for (int i = 0; i < pairCount * 2; i += 2) {
            if (sortedCards.get(i).getRank() != sortedCards.get(i + 1).getRank()) {
                return false;
            }
        }
        for (int i = 0; i < pairCount - 1; i++) {
            int currentPairRank = sortedCards.get(i * 2).getRank();
            int nextPairRank = sortedCards.get((i + 1) * 2).getRank();
            if (currentPairRank == 15 || nextPairRank == 15 || (nextPairRank - currentPairRank != 1)) {
                return false;
            }
        }
        return true;
    }

    public static boolean canPlay(List<Card> newCards, List<Card> lastCards, boolean isFirstTurnOfGame) {
        ComboType newType = getComboType(newCards);
        if (newType == ComboType.INVALID) return false;

        if (isFirstTurnOfGame) {
            boolean has3Spade = newCards.stream()
                    .anyMatch(c -> c.getRank() == 3 && c.getSuit() == 1);
            if (!has3Spade) return false;
        }

        if (lastCards == null || lastCards.isEmpty()) return true;

        ComboType lastType = getComboType(lastCards);

        if (newType == ComboType.DOUBLE_STRAIGHT_4) {
            if (lastType == ComboType.SINGLE && lastCards.get(0).getRank() == 15) return true;
            if (lastType == ComboType.PAIR && lastCards.get(0).getRank() == 15) return true;
            if (lastType == ComboType.DOUBLE_STRAIGHT_3) return true;
            if (lastType == ComboType.QUAD) return true;
            if (lastType == ComboType.DOUBLE_STRAIGHT_4) {
                return getHighestPower(newCards) > getHighestPower(lastCards);
            }
            return false;
        }

        if (lastType == ComboType.SINGLE && lastCards.get(0).getRank() == 15) {
            if (newType == ComboType.DOUBLE_STRAIGHT_3 || newType == ComboType.QUAD) {
                return true;
            }
        }

        if (lastType == ComboType.PAIR && lastCards.get(0).getRank() == 15 && newType == ComboType.QUAD) {
            return true;
        }

        if (lastType == ComboType.DOUBLE_STRAIGHT_3 && newType == ComboType.QUAD) {
            return true;
        }

        if (newType != lastType || newCards.size() != lastCards.size()) return false;

        return getHighestPower(newCards) > getHighestPower(lastCards);
    }

    private static int getHighestPower(List<Card> cards) {
        return cards.stream().mapToInt(Card::getPower).max().orElse(0);
    }

    public static InstantWinType checkInstantWin(List<Card> hand) {
        if (hand == null || hand.size() < 13) return InstantWinType.NONE;

        List<Card> sortedHand = hand.stream()
                .sorted(Comparator.comparingInt(Card::getRank))
                .toList();

        if (isDragonStraight(sortedHand)) return InstantWinType.DRAGON_STRAIGHT;

        long countTwos = sortedHand.stream().filter(c -> c.getRank() == 15).count();
        if (countTwos == 4) return InstantWinType.FOUR_TWOS;

        Map<Integer, Integer> rankCounts = new HashMap<>();
        for (Card c : sortedHand) {
            rankCounts.put(c.getRank(), rankCounts.getOrDefault(c.getRank(), 0) + 1);
        }

        int totalPairs = 0;
        List<Integer> pairRanks = new ArrayList<>();
        for (Map.Entry<Integer, Integer> entry : rankCounts.entrySet()) {
            int pairs = entry.getValue() / 2;
            totalPairs += pairs;
            if (pairs > 0) pairRanks.add(entry.getKey());
        }

        if (isFiveDoubleStraight(pairRanks)) return InstantWinType.FIVE_DOUBLE_STRAIGHT;
        if (totalPairs >= 6) return InstantWinType.SIX_PAIRS;
        if (isSameColor(hand)) return InstantWinType.SAME_COLOR;

        return InstantWinType.NONE;
    }

    private static boolean isDragonStraight(List<Card> sortedHand) {
        Set<Integer> uniqueRanks = new HashSet<>();
        for (Card c : sortedHand) {
            if (c.getRank() >= 3 && c.getRank() <= 14) uniqueRanks.add(c.getRank());
        }
        return uniqueRanks.size() == 12;
    }

    private static boolean isFiveDoubleStraight(List<Integer> pairRanks) {
        if (pairRanks.size() < 5) return false;
        List<Integer> sortedPairRanks = pairRanks.stream()
                .filter(r -> r < 15)
                .distinct()
                .sorted()
                .toList();
        if (sortedPairRanks.size() < 5) return false;
        int consecutive = 1;
        for (int i = 0; i < sortedPairRanks.size() - 1; i++) {
            if (sortedPairRanks.get(i + 1) - sortedPairRanks.get(i) == 1) {
                consecutive++;
                if (consecutive >= 5) return true;
            } else {
                consecutive = 1;
            }
        }
        return false;
    }

    private static boolean isSameColor(List<Card> hand) {
        boolean allBlack = hand.stream().allMatch(c -> c.getSuit() == 1 || c.getSuit() == 2);
        boolean allRed = hand.stream().allMatch(c -> c.getSuit() == 3 || c.getSuit() == 4);
        return allBlack || allRed;
    }
}