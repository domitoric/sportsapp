package com.example.sportsapp.dto;

import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * DTO форми для додавання зовнішнього гравця до локального відстеження.
 */
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TrackPlayerRequest {

    @NotNull
    private Long externalPlayerId;

}
