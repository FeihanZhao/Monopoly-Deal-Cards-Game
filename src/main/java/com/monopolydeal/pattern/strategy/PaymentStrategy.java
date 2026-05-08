package com.monopolydeal.pattern.strategy;

import com.monopolydeal.model.*;
import java.util.*;

public interface PaymentStrategy {
    List<Card> selectPayment(Bank bank, int amount) throws Bank.InsufficientFundsException;
}

