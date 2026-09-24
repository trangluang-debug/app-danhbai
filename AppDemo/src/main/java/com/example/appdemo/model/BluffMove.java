package com.example.appdemo.model;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class BluffMove {
    private String playerId;
    private int claimedRank;       // Khai báo là đánh số mấy (VD: 3)
    private int expectedCount;     // Số lượng cần đánh (VD: 2)
    private List<Card> actualCards;// Bài thực tế úp xuống
}