package com.sskings.bolso;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.security.autoconfigure.UserDetailsServiceAutoConfiguration;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;

/**
 * Sem UserDetailsServiceAutoConfiguration: o Spring criaria um usuário "user" com senha gerada (e escrita no log).
 * O login do Bolso é só o do AuthService/JWT.
 */
@SpringBootApplication(exclude = UserDetailsServiceAutoConfiguration.class)
@ConfigurationPropertiesScan
public class BolsoApplication {

    public static void main(String[] args) {
        SpringApplication.run(BolsoApplication.class, args);
    }
}
