package com.monopolydeal.model;

/**
 * Game constants class (utility class, not instantiable)
 *
 * Centralizes all adjustable game rule parameters and network configuration for Monopoly Deal.
 * All fields are public static final, allowing compile-time inlining optimization.
 */
public final class GameConstants {
    /** Private constructor to prevent external instantiation */
    private GameConstants() {}

    // ==================== Player-related Constants ====================

    /** Minimum number of players (game cannot start below this) */
    public static final int MIN_PLAYERS = 2;
    /** Maximum number of players (room capacity limit) */
    public static final int MAX_PLAYERS = 5;
    /** Initial hand size at the start of the game */
    public static final int INITIAL_HAND_SIZE = 5;
    /** Number of cards drawn automatically per turn */
    public static final int DRAW_COUNT = 2;
    /** Extra cards drawn when hand is empty at the start of a turn */
    public static final int EMPTY_HAND_DRAW_COUNT = 5;
    /** Maximum number of cards that can be played per turn */
    public static final int MAX_PLAYS_PER_TURN = 3;
    /** Hand size limit (must discard if exceeding this at end of turn) */
    public static final int MAX_HAND_SIZE = 7;

    // ==================== Time-related Constants ====================

    /** Turn time limit in seconds, auto-ends turn on timeout */
    public static final int TURN_TIMEOUT_SECONDS = 30;
    /** Timeout warning lead time in seconds, warns the player N seconds before turn ends */
    public static final int TIMEOUT_WARNING_SECONDS = 10;
    /** Discard phase countdown in seconds */
    public static final int DISCARD_TIMEOUT_SECONDS = 15;
    /** Time allowed to decide whether to play Just Say No, in seconds */
    public static final int JUST_SAY_NO_TIMEOUT_SECONDS = 5;

    // ==================== Building Limits ====================

    /** Maximum number of houses per complete property set */
    public static final int MAX_HOUSES_PER_SET = 1;
    /** Maximum number of hotels per complete property set */
    public static final int MAX_HOTELS_PER_SET = 1;

    // ==================== Win Condition ====================

    /** Number of complete property sets required to win (collect 3 full sets of different colors) */
    public static final int WINNING_COMPLETE_SETS = 3;

    // ==================== Action Card Amount Constants ====================

    /** "Birthday" action card: all other players each pay 2M to the current player */
    public static final int BIRTHDAY_AMOUNT = 2;
    /** "Debt Collector" action card: a specified player pays 5M to the current player */
    public static final int DEBT_COLLECTOR_AMOUNT = 5;

    // ==================== Network Configuration Constants ====================

    /** Default server listening port */
    public static final int SERVER_PORT = 8888;
    /** Default client connection host address */
    public static final String DEFAULT_HOST = "localhost";

    // ==================== Money Denominations ====================

    /** All possible face values for money cards (unit: M/millions) */
    public static final int[] MONEY_DENOMINATIONS = {1, 2, 3, 4, 5, 10};
}
