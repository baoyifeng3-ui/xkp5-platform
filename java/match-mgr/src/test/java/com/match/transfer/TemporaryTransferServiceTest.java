package com.match.transfer;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.match.agent.model.AgentCommandFinishedEvent;
import com.match.agent.model.AgentCommandView;
import com.match.agent.persistence.ProcessingAgentMapper;
import com.match.agent.service.AgentCommandService;
import com.match.environment.persistence.TrainingEnvironmentMapper;
import com.match.environment.persistence.TrainingEnvironmentRecord;
import com.match.resource.service.ResourceStorageService;
import org.junit.Test;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.web.multipart.MultipartFile;

import static org.junit.Assert.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

public class TemporaryTransferServiceTest {
    private final TemporaryTransferMapper records = mock(TemporaryTransferMapper.class);
    private final TrainingEnvironmentMapper environments = mock(TrainingEnvironmentMapper.class);
    private final ResourceStorageService storage = mock(ResourceStorageService.class);
    private final AgentCommandService commands = mock(AgentCommandService.class);
    private final TemporaryTransferService service = new TemporaryTransferService(records, environments,
            mock(ProcessingAgentMapper.class), storage, commands, new ObjectMapper());

    private void ownEnvironment() {
        TrainingEnvironmentRecord env = new TrainingEnvironmentRecord();
        env.setUserId(7);
        env.setWorkspaceRelativePath("training/7/unbound");
        when(environments.selectForUpdate("env")).thenReturn(env);
    }

    @Test(expected = IllegalArgumentException.class)
    public void rejectsMissingFilenameBeforeStoring() {
        ownEnvironment();
        service.upload(7, false, "env", mock(MultipartFile.class));
    }

    @Test
    public void failedDispatchCleansStoredFile() {
        ownEnvironment();
        ResourceStorageService.StoredFile stored = mock(ResourceStorageService.StoredFile.class);
        when(stored.getStorageKey()).thenReturn("test-storage-key");
        when(storage.store(any())).thenReturn(stored);
        when(commands.requestEnvironmentCommand(any(), anyString(), anyString(), anyInt(), anyString(), anyString()))
                .thenThrow(new IllegalArgumentException("offline"));
        try {
            service.upload(7, false, "env", new MockMultipartFile("file", "data.csv", "text/csv", new byte[]{1}));
            fail("offline agent should fail dispatch");
        } catch (IllegalStateException expected) {
            verify(storage).delete("test-storage-key");
        }
    }

    @Test
    public void successfulDispatchPreservesFileUntilAgentCompletes() {
        ownEnvironment();
        ResourceStorageService.StoredFile stored = mock(ResourceStorageService.StoredFile.class);
        when(stored.getStorageKey()).thenReturn("test-storage-key");
        when(storage.store(any())).thenReturn(stored);
        AgentCommandView command = new AgentCommandView();
        command.setCommandId("command");
        when(commands.requestEnvironmentCommand(any(), anyString(), anyString(), anyInt(), anyString(), anyString())).thenReturn(command);
        TemporaryTransferRecord record = service.upload(7, false, "env", new MockMultipartFile("file", "data.csv", "text/csv", new byte[]{1}));
        assertEquals("DISPATCHED", record.getState());
        verify(storage, never()).delete(anyString());
        when(records.selectByCommand("command")).thenReturn(record);
        service.done(new AgentCommandFinishedEvent("command", "agent", "TRANSFER_FILE", true, "OK", "ok"));
        assertEquals("SUCCEEDED", record.getState());
        verify(storage).delete("test-storage-key");
    }
}
