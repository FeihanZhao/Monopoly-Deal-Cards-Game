package com.monopolydeal.pattern.strategy;

import com.monopolydeal.model.Bank;
import com.monopolydeal.model.Card;

import java.util.ArrayList;
import java.util.List;

public class MinCardPaymentStrategy implements PaymentStrategy {
    @Override
    public List<Card> selectPayment(Bank bank, int amount) throws Bank.InsufficientFundsException {
        // Always try to use largest bills first
        List<Card> allCards = new ArrayList<>(bank.getAllMoneyCards());
        allCards.sort((a, b) -> Integer.compare(b.getValue(), a.getValue()));

        List<Card> payment = new ArrayList<>();
        int remaining = amount;

        for (Card card : allCards) {
            if (remaining <= 0) break;
            payment.add(card);
            remaining -= card.getValue();
        }

        if (remaining > 0) {
            throw new Bank.InsufficientFundsException("Insufficient funds");
        }

        return payment;
    }
}
