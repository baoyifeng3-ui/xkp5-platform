package com.match.dto;

import lombok.Data;
import java.util.Map;

@Data
public class CompetitionAnnouncementRequest {
    private Map<String, String> values;
}
