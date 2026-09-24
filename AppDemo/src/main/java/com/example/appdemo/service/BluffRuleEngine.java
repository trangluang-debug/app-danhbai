package com.example.appdemo.service;

import com.example.appdemo.model.Card;
import java.util.List;

public class BluffRuleEngine {

    // Quy ước: Joker có rank = 0
    public static boolean isJokerCard(Card card) {
        return card != null && card.getRank() == 0;
    }

    /**
     * Kiểm tra xem bài thực tế có khớp lời khai báo hay dùng Joker hợp lệ không.
     */
    public static boolean validateBluffClaim(List<Card> actualCards, int claimedRank, int expectedCount) {
        if (actualCards == null || actualCards.size() != expectedCount) {
            return false;
        }

        for (Card card : actualCards) {
            boolean matchesClaim = (card.getRank() == claimedRank) || isJokerCard(card);
            if (!matchesClaim) {
                return false; // Phát hiện lá khác không phải mục tiêu/Joker -> Đánh láo!
            }
        }
        return true;
    }
}