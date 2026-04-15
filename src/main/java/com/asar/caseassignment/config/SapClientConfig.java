package com.asar.caseassignment.config;

import java.time.Duration;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.web.client.RestTemplateBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.client.RestTemplate;

@Configuration
public class SapClientConfig {

    @Bean(name = "sapRestTemplate")
    public RestTemplate sapRestTemplate(
            RestTemplateBuilder builder,
            @Value("${sap.username}") String username,
            @Value("${sap.password}") String password
    ) {
        return builder
                .basicAuthentication(username, password) // <-- this fixes 401 if creds are right
                .setConnectTimeout(Duration.ofSeconds(30))
                .setReadTimeout(Duration.ofSeconds(60))
                .build();
    }
}