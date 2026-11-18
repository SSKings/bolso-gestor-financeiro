package com.sskings.bolso.support;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.concurrent.atomic.AtomicReference;

/** Relógio dos testes: começa na hora real e só anda quando o teste manda (bloqueio, expiração de token/convite). */
public class MutableClock extends Clock {

    private final AtomicReference<Instant> now;
    private final ZoneId zone;

    public MutableClock() {
        this(new AtomicReference<>(Instant.now()), ZoneOffset.UTC);
    }

    private MutableClock(AtomicReference<Instant> now, ZoneId zone) {
        this.now = now;
        this.zone = zone;
    }

    public void advance(Duration d) {
        now.updateAndGet(i -> i.plus(d));
    }

    public void reset() {
        now.set(Instant.now());
    }

    @Override public ZoneId getZone() { return zone; }
    @Override public Clock withZone(ZoneId z) { return new MutableClock(now, z); }   // mesmo instante, outro fuso
    @Override public Instant instant() { return now.get(); }
}
