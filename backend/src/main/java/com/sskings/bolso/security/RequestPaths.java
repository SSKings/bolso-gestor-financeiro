package com.sskings.bolso.security;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.web.util.UrlPathHelper;

/**
 * Caminho da requisição como o Spring MVC o enxerga: decodificado ({@code %66} vira {@code f}), sem
 * {@code ;parâmetros} e sem o context path. Filtro que decide por caminho (limite, trava de ferramenta) tem que usar
 * ESTE caminho, nunca {@code getRequestURI()} cru: {@code /api/auth/%66orgot-password} chega ao controller de
 * {@code /forgot-password}, mas não é igual a ele como texto (lição da revisão 6 do ShopEasy).
 * <p>Barras e pontos codificados ({@code %2F}, {@code %2E}), {@code //} e {@code ..} o firewall do Spring Security já
 * recusa antes de qualquer filtro.
 */
public final class RequestPaths {

    private static final UrlPathHelper HELPER = new UrlPathHelper();   // decodifica e tira ";..." (padrões)

    private RequestPaths() {}

    public static String of(HttpServletRequest request) {
        return HELPER.getPathWithinApplication(request);
    }
}
