package com.match.terminal.model;

import lombok.Data;

import java.util.List;

@Data
public class TerminalPollingOutputView {
    private long cursor;
    private long nextCursor;
    private boolean reset;
    private String state;
    private List<String> chunks;
}
