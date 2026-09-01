package com.match.terminal.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.match.agent.model.AgentCommandFinishedEvent;
import com.match.agent.service.AgentCommandService;
import com.match.terminal.persistence.TerminalPollingOutputMapper;
import com.match.terminal.persistence.TerminalPollingOutputRecord;
import com.match.terminal.persistence.TerminalSessionMapper;
import com.match.terminal.persistence.TerminalSessionRecord;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;

@Component
public class TerminalPollingResultListener {
    private final TerminalSessionMapper sessions;private final TerminalPollingOutputMapper outputs;private final ObjectMapper json;
    public TerminalPollingResultListener(TerminalSessionMapper sessions,TerminalPollingOutputMapper outputs,ObjectMapper json){this.sessions=sessions;this.outputs=outputs;this.json=json;}
    @EventListener @Transactional
    public void completed(AgentCommandFinishedEvent event) throws Exception {
        if(!AgentCommandService.EXECUTE_TERMINAL_INPUT.equals(event.getCommandType())||event.getResultJson()==null)return;
        JsonNode details=json.readTree(event.getResultJson());String sessionId=details.path("sessionId").asText(null);if(sessionId==null)return;
        String output=details.path("output").asText("");if(!event.isSuccess())output+=System.lineSeparator()+event.getResultMessage()+System.lineSeparator();
        TerminalSessionRecord session=sessions.selectById(sessionId);if(session==null)return;
        long previous=session.getPollingOutputCursor()==null?0:session.getPollingOutputCursor(),next=previous+1;LocalDateTime now=LocalDateTime.now();
        if(sessions.advanceOutputCursor(sessionId,previous,next,now)!=1)throw new IllegalStateException("终端输出游标冲突");
        TerminalPollingOutputRecord row=new TerminalPollingOutputRecord();row.setSessionId(sessionId);row.setOutputCursor(next);row.setOutputData(output.getBytes(StandardCharsets.UTF_8));row.setCreatedAt(now);outputs.insert(row);
    }
}
