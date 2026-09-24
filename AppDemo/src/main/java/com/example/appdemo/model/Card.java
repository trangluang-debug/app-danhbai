package com.example.appdemo.model;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class Card {
    private int rank; // 3 -> 15 (3 -> A, 2)
    private int suit; // 1: Bích, 2: Chuồng, 3: Rô, 4: Cơ

    public int getPower() {
        return (rank * 4) + suit;
    }
}