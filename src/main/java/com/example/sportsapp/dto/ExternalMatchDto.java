package com.example.sportsapp.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.ArrayList;
import java.util.List;
import lombok.Getter;
import lombok.Setter;

/**
 * DTO used to deserialize raw match data from an external source.
 */
@Getter
@Setter
@JsonIgnoreProperties(ignoreUnknown = true)
public class ExternalMatchDto {

    @JsonProperty("results")
    private List<ExternalMatchItem> matches = new ArrayList<>();

    /**
     * Single item from the raw external matches response.
     */
    @Getter
    @Setter
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class ExternalMatchItem {
        @JsonProperty("idEvent")
        private Long id;

        @JsonProperty("dateEvent")
        private String dateEvent;

        @JsonProperty("idHomeTeam")
        private Long homeTeamId;

        @JsonProperty("idAwayTeam")
        private Long awayTeamId;

        @JsonProperty("strHomeTeam")
        private String homeTeamName;

        @JsonProperty("strAwayTeam")
        private String awayTeamName;

        @JsonProperty("intHomeScore")
        private String scoreHome;

        @JsonProperty("intAwayScore")
        private String scoreAway;
    }
}
