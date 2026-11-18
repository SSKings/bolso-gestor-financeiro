package com.sskings.bolso.common;

import com.sskings.bolso.config.BolsoProperties;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.LocalDate;
import java.time.YearMonth;

/** "Hoje" no fuso do usuário (America/Sao_Paulo por padrão), não no do servidor. */
@Component
public class Today {

    private final Clock clock;

    public Today(Clock clock, BolsoProperties props) {
        this.clock = clock.withZone(props.zone());
    }

    public LocalDate date() {
        return LocalDate.now(clock);
    }

    public YearMonth month() {
        return YearMonth.now(clock);
    }
}
