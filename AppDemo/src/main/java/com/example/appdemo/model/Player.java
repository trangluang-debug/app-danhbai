package com.example.appdemo.model;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import java.util.ArrayList;
import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class Player {
    private String id;
    private String name;
    private boolean isBot;
    private List<Card> hand = new ArrayList<>();
    private boolean hasPassed = false;
    private String lastAction = "";
    private double riskTolerance = 1.0; // Thêm dòng này

    // Constructor tương thích với code cũ (tự động bật isBot nếu không phải p1)
    public Player(String id, String name) {
        this.id = id;
        this.name = name;
        this.isBot = id != null && !id.equals("p1");
        this.riskTolerance = 1.0;
    }

    public void updateAction(String action) {
        this.lastAction = action;
    }
}