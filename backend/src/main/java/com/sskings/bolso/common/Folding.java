package com.sskings.bolso.common;

import java.text.Normalizer;
import java.util.Locale;

/** Texto para comparação: sem acento, minúsculo, espaços juntos ("Padaria São  João" → "padaria sao joao"). */
public final class Folding {

    private Folding() {}

    public static String fold(String s) {
        if (s == null) return "";
        String noAccents = Normalizer.normalize(s, Normalizer.Form.NFD).replaceAll("\\p{M}", "");
        return noAccents.toLowerCase(Locale.ROOT).strip().replaceAll("\\s+", " ");
    }
}
