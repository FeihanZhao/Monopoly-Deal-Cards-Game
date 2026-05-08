package com.monopolydeal.pattern.observer;

public interface GameSubject {
    void attach(GameObserver observer);
    void detach(GameObserver observer);
    void notifyObservers();
}
