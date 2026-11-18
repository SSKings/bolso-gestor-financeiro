package com.sskings.bolso.transaction;

public enum TransactionType { INCOME, EXPENSE, TRANSFER,
    /** Pagamento de fatura: só pela rota da fatura do cartão (CardService.pay). */
    CARD_PAYMENT }
