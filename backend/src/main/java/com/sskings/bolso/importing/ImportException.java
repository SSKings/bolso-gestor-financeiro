package com.sskings.bolso.importing;

/** Arquivo que não dá para importar (mensagem estável, em inglês, como as demais da API). */
public class ImportException extends RuntimeException {
    public ImportException(String message) {
        super(message);
    }
}
