package com.match.service.impl;

import com.match.entity.TrainUrl;
import com.match.entity.TrainingNode;
import com.match.entity.TrainingServer;
import com.match.entity.UserTrainingAssignment;
import com.match.mapper.TrainingNodeMapper;
import com.match.mapper.TrainingServerMapper;
import com.match.mapper.UserTrainingAssignmentMapper;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;

import java.util.Arrays;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@RunWith(MockitoJUnitRunner.class)
public class TrainingEnvironmentServiceTest {
    @Mock
    private TrainingServerMapper serverMapper;
    @Mock
    private TrainingNodeMapper nodeMapper;
    @Mock
    private UserTrainingAssignmentMapper assignmentMapper;
    @Mock
    private UserServiceImpl userService;

    private TrainingEnvironmentService service;

    @Before
    public void setUp() {
        service = new TrainingEnvironmentService(serverMapper, nodeMapper, assignmentMapper, userService);
    }

    @Test
    public void returnsOnlyAssignedEnvironmentUrl() {
        UserTrainingAssignment assignment = new UserTrainingAssignment();
        assignment.setUserId(7);
        assignment.setTrainingNodeId(11);
        assignment.setSlotNo(2);
        UserTrainingAssignment code = new UserTrainingAssignment();
        code.setUserId(7);
        code.setTrainingNodeId(11);
        code.setSlotNo(1);
        UserTrainingAssignment inference = new UserTrainingAssignment();
        inference.setUserId(7);
        inference.setTrainingNodeId(11);
        inference.setSlotNo(3);
        when(assignmentMapper.selectList(any())).thenReturn(Arrays.asList(code, assignment, inference));

        TrainingNode node = new TrainingNode();
        node.setTrainingNodeId(11);
        node.setTrainingServerId(3);
        node.setNodeNo(3);
        node.setHost("10.20.0.11");
        node.setEnabled(true);
        when(nodeMapper.selectById(11)).thenReturn(node);

        TrainingServer server = new TrainingServer();
        server.setTrainingServerId(3);
        server.setVscodeBasePort(9090);
        server.setCvatBasePort(8080);
        server.setT100BasePort(5000);
        server.setEnabled(true);
        when(serverMapper.selectById(3)).thenReturn(server);

        TrainUrl url = service.getForUser(7);

        assertEquals("10.20.0.11:9093", url.getVscodeUrl());
        assertEquals("10.20.0.11:8083", url.getCvatUrl());
        assertEquals("10.20.0.11:5003", url.getT100Url());
    }

    @Test
    public void rejectsLegacyFourthSlotAssignment() {
        try {
            service.assign(7, 11, 4);
            fail("应拒绝第 4 槽位");
        } catch (IllegalArgumentException exception) {
            assertTrue(exception.getMessage().contains("端口"));
        }
    }

    @Test
    public void deletesServerWithoutAssignments() {
        TrainingServer server = new TrainingServer();
        server.setTrainingServerId(3);
        TrainingNode node = new TrainingNode();
        node.setTrainingNodeId(11);
        node.setTrainingServerId(3);
        when(serverMapper.selectById(3)).thenReturn(server);
        when(nodeMapper.selectList(any())).thenReturn(Arrays.asList(node));
        when(assignmentMapper.selectCount(any())).thenReturn(0);

        service.deleteServer(3);

        verify(serverMapper).deleteById(3);
    }

    @Test
    public void rejectsDeletingServerWithAssignments() {
        TrainingServer server = new TrainingServer();
        server.setTrainingServerId(3);
        TrainingNode node = new TrainingNode();
        node.setTrainingNodeId(11);
        node.setTrainingServerId(3);
        when(serverMapper.selectById(3)).thenReturn(server);
        when(nodeMapper.selectList(any())).thenReturn(Arrays.asList(node));
        when(assignmentMapper.selectCount(any())).thenReturn(1);

        try {
            service.deleteServer(3);
            fail("有账号分配时应拒绝删除");
        } catch (IllegalArgumentException exception) {
            assertTrue(exception.getMessage().contains("账号分配"));
        }

        verify(serverMapper, never()).deleteById(3);
    }

    @Test
    public void rejectsDeletingMissingServer() {
        when(serverMapper.selectById(3)).thenReturn(null);

        try {
            service.deleteServer(3);
            fail("服务器不存在时应拒绝删除");
        } catch (IllegalArgumentException exception) {
            assertTrue(exception.getMessage().contains("服务器不存在"));
        }

        verify(serverMapper, never()).deleteById(3);
    }

    @Test
    public void heartbeatMarksDisabledServerWithoutProbing() {
        TrainingServer server = new TrainingServer();
        server.setTrainingServerId(3);
        server.setEnabled(false);
        TrainingNode node = new TrainingNode();
        node.setTrainingServerId(3);
        node.setNodeNo(1);
        node.setHost("216.0.0.1");
        when(serverMapper.selectList(any())).thenReturn(Arrays.asList(server));
        when(nodeMapper.selectList(any())).thenReturn(Arrays.asList(node));

        service.heartbeatServers();

        assertEquals("DISABLED", server.getHeartbeatStatus());
    }
}
