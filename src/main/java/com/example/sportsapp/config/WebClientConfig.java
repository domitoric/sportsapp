package com.example.sportsapp.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.codec.ClientCodecConfigurer;
import org.springframework.web.reactive.function.client.WebClient;

/**
 * Configures the WebClient used to call the external football API.
 */
@Configuration
public class WebClientConfig {

    /**
     * Creates the WebClient used for all outbound calls to the external football API.
     */
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

    /**
     * Raises the in-memory buffer so larger API payloads can be decoded safely.
     */
    private void configureCodecs(ClientCodecConfigurer codecs) {
        codecs.defaultCodecs().maxInMemorySize(4 * 1024 * 1024);
    }
}
