package com.monopolydeal.model;

public final class GameConstants {
    private GameConstants() {}

    public static final int MIN_PLAYERS = 2;
    public static final int MAX_PLAYERS = 5;
    public static final int INITIAL_HAND_SIZE = 5;
    public static final int DRAW_COUNT = 3;
    public static final int EMPTY_HAND_DRAW_COUNT = 5;
    public static final int MAX_PLAYS_PER_TURN = 3;
    public static final int MAX_HAND_SIZE = 7;
    public static final int TURN_TIMEOUT_SECONDS = 30;
    public static final int TIMEOUT_WARNING_SECONDS = 10;
    public static final int WINNING_COMPLETE_SETS = 3;
    public static final int BIRTHDAY_AMOUNT = 2;
    public static final int DEBT_COLLECTOR_AMOUNT = 5;
    public static final int JUST_SAY_NO_TIMEOUT_SECONDS = 5;
    public static final int SERVER_PORT = 8888;
    public static final String DEFAULT_HOST = "localhost";
    public static final int[] MONEY_DENOMINATIONS = {1, 2, 3, 4, 5, 10};
}