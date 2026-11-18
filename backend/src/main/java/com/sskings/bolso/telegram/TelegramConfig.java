package com.sskings.bolso.telegram;

import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.web.client.RestClient;

import java.time.Clock;
import java.time.Duration;

/**
 * O bot só existe com bolso.telegram.enabled=true; configuração inválida impede o startup (melhor do que um bot meio
 * ligado). As propriedades ficam sempre disponíveis: o app mostra se o Telegram está configurado.
 */
@Configuration
@EnableConfigurationProperties(TelegramProperties.class)
public class TelegramConfig {

    @Slf4j
    @Configuration
    @EnableScheduling
    @ConditionalOnProperty(prefix = "bolso.telegram", name = "enabled", havingValue = "true")
    static class Enabled {

        Enabled(TelegramProperties props) {
            var problems = props.problems();
            if (!problems.isEmpty()) throw new IllegalStateException("Invalid Telegram configuration:\n - " + String.join("\n - ", problems));
        }

        @Bean
        TelegramClient telegramClient(RestClient.Builder builder, TelegramProperties props) {
            var factory = new SimpleClientHttpRequestFactory();
            factory.setConnectTimeout(Duration.ofSeconds(10));
            factory.setReadTimeout(Duration.ofSeconds(props.pollTimeoutSeconds() + 15L));   // maior que a espera do long polling
            return new TelegramClient(builder.requestFactory(factory), props);
        }

        @Bean
        TelegramBot telegramBot(TelegramClient client, TelegramCommands commands, Clock clock) {
            return new TelegramBot(client, commands, clock);
        }

        @Bean
        TelegramPoller telegramPoller(TelegramClient client, TelegramBot bot) {
            return new TelegramPoller(client, bot);
        }

        @Bean
        ReminderJob telegramReminderJob(TelegramReminders reminders, TelegramClient client) {
            return new ReminderJob(reminders, client);
        }
    }

    /** De hora em hora, das 8h às 21h: se a API estava fora às 8h, o lembrete do dia sai na próxima rodada. */
    @Slf4j
    static class ReminderJob {
        private final TelegramReminders reminders;
        private final TelegramClient client;

        ReminderJob(TelegramReminders reminders, TelegramClient client) {
            this.reminders = reminders;
            this.client = client;
        }

        @Scheduled(cron = "0 0 8-21 * * *", zone = "America/Sao_Paulo")
        void run() {
            for (TelegramReminders.Reminder r : reminders.pending()) {
                try {
                    client.sendMessage(r.chatId(), r.text());
                    reminders.markSent(r.linkId());
                } catch (RuntimeException e) {
                    log.warn("Telegram: lembrete não enviado ({})", e.getMessage());
                }
            }
        }
    }
}
