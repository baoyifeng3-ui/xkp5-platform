package com.match.agent.model;

public final class AgentCommandFinishedEvent {
    private final String commandId;
    private final String agentId;
    private final String commandType;
    private final boolean success;
    private final String resultCode;
    private final String resultMessage;
    private final String resultJson;

    public AgentCommandFinishedEvent(String commandId, String agentId, String commandType,
                                     boolean success, String resultCode, String resultMessage) {
        this(commandId, agentId, commandType, success, resultCode, resultMessage, null);
    }
    public AgentCommandFinishedEvent(String commandId, String agentId, String commandType,
                                     boolean success, String resultCode, String resultMessage,
                                     String resultJson) {
        this.commandId = commandId;
        this.agentId = agentId;
        this.commandType = commandType;
        this.success = success;
        this.resultCode = resultCode;
        this.resultMessage = resultMessage;
        this.resultJson = resultJson;
    }

    public String getCommandId() { return commandId; }
    public String getAgentId() { return agentId; }
    public String getCommandType() { return commandType; }
    public boolean isSuccess() { return success; }
    public String getResultCode() { return resultCode; }
    public String getResultMessage() { return resultMessage; }
    public String getResultJson() { return resultJson; }
}
