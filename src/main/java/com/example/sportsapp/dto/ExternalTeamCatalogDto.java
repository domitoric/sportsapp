package com.example.sportsapp.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.ArrayList;
import java.util.List;
import lombok.Getter;
import lombok.Setter;

/**
 * DTO для десеріалізації сирих даних каталогу команд із зовнішнього джерела.
 */
@Getter
@Setter
@JsonIgnoreProperties(ignoreUnknown = true)
public class ExternalTeamCatalogDto {

    @JsonProperty("teams")
    private List<ExternalTeamItem> teams = new ArrayList<>();

    /**
     * Елемент сирої відповіді із зовнішнього API команд.
     */
    @Getter
    @Setter
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class ExternalTeamItem {
        @JsonProperty("idTeam")
        private Long id;

        @JsonProperty("strTeam")
        private String name;
    }

    @Getter
    @Setter
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class ExternalSquadPlayer {
        @JsonProperty("idPlayer")
        private Long id;

        @JsonProperty("strPlayer")
        private String name;

        @JsonProperty("strPosition")
        private String position;
    }
}
