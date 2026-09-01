package com.match.terminal.service;

import com.match.agent.model.AgentCommandView;
import com.match.agent.persistence.ProcessingAgentMapper;
import com.match.agent.persistence.ProcessingAgentRecord;
import com.match.agent.service.AgentCommandService;
import com.match.entity.User;
import com.match.terminal.model.TerminalPollingInputRequest;
import com.match.terminal.model.TerminalPollingOutputView;
import com.match.terminal.persistence.TerminalPollingOutputMapper;
import com.match.terminal.persistence.TerminalPollingOutputRecord;
import com.match.terminal.persistence.TerminalSessionMapper;
import com.match.terminal.persistence.TerminalSessionRecord;
import org.springframework.stereotype.Service;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

@Service
public class TerminalPollingService {
    private final TerminalSessionMapper sessions; private final TerminalPollingOutputMapper outputs;
    private final ProcessingAgentMapper agents; private final AgentCommandService commands;
    public TerminalPollingService(TerminalSessionMapper sessions, TerminalPollingOutputMapper outputs,
                                  ProcessingAgentMapper agents, AgentCommandService commands) {
        this.sessions=sessions; this.outputs=outputs; this.agents=agents; this.commands=commands;
    }
    public TerminalPollingOutputView output(String sessionId,long cursor) {
        if(cursor<0) throw new IllegalArgumentException("输出游标不能为负数");
        TerminalSessionRecord session=require(sessionId); List<TerminalPollingOutputRecord> rows=outputs.selectAfter(sessionId,cursor);
        List<String> chunks=new ArrayList<>(); long next=cursor;
        for(TerminalPollingOutputRecord row:rows){chunks.add(new String(row.getOutputData(),StandardCharsets.UTF_8));next=row.getOutputCursor();}
        TerminalPollingOutputView view=new TerminalPollingOutputView();view.setCursor(cursor);view.setNextCursor(next);
        view.setReset(false);view.setState(session.getState());view.setChunks(chunks);return view;
    }
    public AgentCommandView input(String sessionId, User actor, TerminalPollingInputRequest request) {
        TerminalSessionRecord session=require(sessionId);String data=request==null?null:request.getData();
        if(data==null||data.isEmpty()||data.getBytes(StandardCharsets.UTF_8).length>4096)throw new IllegalArgumentException("终端输入不能为空且不能超过 4 KiB");
        ProcessingAgentRecord agent=agents.selectForManagement(session.getAgentId());
        return commands.requestTerminalInput(agent,sessionId,data,actor.getUserId());
    }
    private TerminalSessionRecord require(String id){TerminalSessionRecord r=sessions.selectById(id);if(r==null)throw new IllegalArgumentException("终端会话不存在");return r;}
}
