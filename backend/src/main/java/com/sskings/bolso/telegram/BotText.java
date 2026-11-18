package com.sskings.bolso.telegram;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;
import java.util.Locale;

/** Formatação das respostas (HTML do Telegram): todo texto vindo do usuário passa por {@link #esc}. */
final class BotText {

    private BotText() {}

    static String esc(String s) {
        return s == null ? "" : s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;");
    }

    static String brl(BigDecimal v) {
        DecimalFormat f = new DecimalFormat("#,##0.00", DecimalFormatSymbols.getInstance(Locale.forLanguageTag("pt-BR")));
        String s = f.format(v.abs().setScale(2, RoundingMode.HALF_UP));
        return (v.signum() < 0 ? "−R$ " : "R$ ") + s;
    }
}
