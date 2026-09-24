package com.example.appdemo.factory;

import com.example.appdemo.engine.CardGameEngine;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.Map;

@Service
public class GameFactory {

    private final Map<String, CardGameEngine> gameEngines;
    private CardGameEngine activeEngine;

    @Autowired
    public GameFactory(Map<String, CardGameEngine> gameEngines) {
        this.gameEngines = gameEngines;
        this.activeEngine = gameEngines.get("TIEN_LEN");
    }

    public CardGameEngine selectGame(String gameType) {
        if (gameEngines.containsKey(gameType)) {
            this.activeEngine = gameEngines.get(gameType);
        }
        return this.activeEngine;
    }

    public CardGameEngine getActiveEngine() {
        return this.activeEngine;
    }
}