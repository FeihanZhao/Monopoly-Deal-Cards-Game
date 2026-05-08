package com.monopolydeal.pattern.singleton;

import com.monopolydeal.model.*;
import com.monopolydeal.pattern.observer.*;
import java.util.*;

public class GameManager implements GameSubject {
    private static GameManager instance;
    private final List<GameObserver> observers;
    private GameState currentState;

    private GameManager() {
        this.observers = new ArrayList<>();
        this.currentState = new GameState();
    }

    public static synchronized GameManager getInstance() {
        if (instance == null) {
            instance = new GameManager();
        }
        return instance;
    }

    @Override
    public void attach(GameObserver observer) {
        observers.add(observer);
    }

    @Override
    public void detach(GameObserver observer) {
        observers.remove(observer);
    }

    @Override
    public void notifyObservers() {
        for (GameObserver observer : observers) {
            observer.update(currentState);
        }
    }

    public void updateGameState(GameState newState) {
        this.currentState = newState;
        notifyObservers();
    }

    public GameState getCurrentState() {
        return currentState;
    }
}