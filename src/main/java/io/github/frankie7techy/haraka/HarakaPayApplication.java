package io.github.frankie7techy.haraka;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * HarakaPay: a mobile-money style wallet and P2P transfer API built on a
 * double-entry ledger, with idempotent request handling and JWT auth.
 */
@SpringBootApplication
@EnableScheduling
@ConfigurationPropertiesScan
public class HarakaPayApplication {

    public static void main(String[] args) {
        SpringApplication.run(HarakaPayApplication.class, args);
    }
}
