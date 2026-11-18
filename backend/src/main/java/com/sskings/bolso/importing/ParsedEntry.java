package com.sskings.bolso.importing;

import java.math.BigDecimal;
import java.time.LocalDate;

/** Uma linha do extrato. Valor com sinal (negativo = saída); externalId identifica a linha para não duplicar. */
public record ParsedEntry(LocalDate date, BigDecimal amount, String description, String externalId) {}
