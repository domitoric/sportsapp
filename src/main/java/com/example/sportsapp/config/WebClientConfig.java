package com.example.sportsapp.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.codec.ClientCodecConfigurer;
import org.springframework.web.reactive.function.client.WebClient;

/**
 * Налаштовує WebClient для звернення до зовнішнього футбольного API.
 */
@Configuration
public class WebClientConfig {

    @Bean
    public WebClient footballWebClient(
            WebClient.Builder builder,
            @Value("${football.api.base-url}") String baseUrl,
            @Value("${football.api.token:}") String token
    ) {
        String normalizedBaseUrl = baseUrl != null && baseUrl.endsWith("/")
                ? baseUrl
                : baseUrl + "/";
        WebClient.Builder configured = builder
                .baseUrl(normalizedBaseUrl)
                .codecs(this::configureCodecs);
        if (token != null && !token.isBlank()) {
            configured.defaultHeader("X-Auth-Token", token);
        }
        return configured.build();
    }

    private void configureCodecs(ClientCodecConfigurer codecs) {
        codecs.defaultCodecs().maxInMemorySize(4 * 1024 * 1024);
    }
}
