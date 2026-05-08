package com.monopolydeal.pattern.observer;

import com.monopolydeal.model.GameState;

public interface GameObserver {
    void update(GameState gameState);
}

