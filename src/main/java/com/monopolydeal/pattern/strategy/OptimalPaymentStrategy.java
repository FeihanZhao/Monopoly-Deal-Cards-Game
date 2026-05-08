package com.monopolydeal.pattern.strategy;

import com.monopolydeal.model.Bank;
import com.monopolydeal.model.Card;
import com.monopolydeal.model.OptimalPaymentCalculator;

import java.util.List;

public class OptimalPaymentStrategy implements PaymentStrategy {
    @Override
    public List<Card> selectPayment(Bank bank, int amount) throws Bank.InsufficientFundsException {
        return OptimalPaymentCalculator.calculate(bank, amount);
    }
}
