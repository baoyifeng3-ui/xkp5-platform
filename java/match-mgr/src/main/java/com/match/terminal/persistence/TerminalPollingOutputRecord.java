package com.match.terminal.persistence;

import lombok.Data;
import java.time.LocalDateTime;

@Data
public class TerminalPollingOutputRecord {
    private String sessionId;
    private Long outputCursor;
    private byte[] outputData;
    private LocalDateTime createdAt;
}
