package com.sskings.bolso.common;

/** Normalização de texto digitado: tira espaços das pontas e junta espaços repetidos. */
public final class Texts {

    private Texts() {}

    public static String clean(String s) {
        return s == null ? null : s.strip().replaceAll("\\s+", " ");
    }
}
