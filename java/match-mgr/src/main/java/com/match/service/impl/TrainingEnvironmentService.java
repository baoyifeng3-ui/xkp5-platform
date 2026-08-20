package com.match.service.impl;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.match.entity.TrainUrl;
import com.match.entity.TrainingNode;
import com.match.entity.TrainingServer;
import com.match.entity.TrainingSlot;
import com.match.entity.User;
import com.match.entity.UserTrainingAssignment;
import com.match.mapper.TrainingNodeMapper;
import com.match.mapper.TrainingServerMapper;
import com.match.mapper.UserTrainingAssignmentMapper;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.net.HttpURLConnection;
import java.net.URL;

@Service
public class TrainingEnvironmentService {
    private final TrainingServerMapper serverMapper;
    private final TrainingNodeMapper nodeMapper;
    private final UserTrainingAssignmentMapper assignmentMapper;
    private final UserServiceImpl userService;

    public TrainingEnvironmentService(TrainingServerMapper serverMapper,
                                      TrainingNodeMapper nodeMapper,
                                      UserTrainingAssignmentMapper assignmentMapper,
                                      UserServiceImpl userService) {
        this.serverMapper = serverMapper;
        this.nodeMapper = nodeMapper;
        this.assignmentMapper = assignmentMapper;
        this.userService = userService;
    }

    public List<TrainingServer> listServers() {
        List<TrainingServer> servers = serverMapper.selectList(
                Wrappers.<TrainingServer>lambdaQuery().orderByAsc(TrainingServer::getTrainingServerId));
        List<UserTrainingAssignment> assignments = assignmentMapper.selectList(null);
        Map<Integer, UserTrainingAssignment> assignmentByNodeSlot = new HashMap<>();
        for (UserTrainingAssignment assignment : assignments) {
            assignmentByNodeSlot.put(slotKey(assignment.getTrainingNodeId(), assignment.getSlotNo()), assignment);
        }

        for (TrainingServer server : servers) {
            List<TrainingNode> nodes = nodeMapper.selectList(Wrappers.<TrainingNode>lambdaQuery()
                    .eq(TrainingNode::getTrainingServerId, server.getTrainingServerId())
                    .orderByAsc(TrainingNode::getNodeNo));
            for (TrainingNode node : nodes) {
                List<TrainingSlot> slots = new ArrayList<>();
                for (int slotNo = 1; slotNo <= 3; slotNo++) {
                    UserTrainingAssignment assignment = assignmentByNodeSlot.get(slotKey(node.getTrainingNodeId(), slotNo));
                    User user = assignment == null ? null : userService.getById(assignment.getUserId());
                    slots.add(new TrainingSlot(slotNo,
                            user == null ? null : user.getUserId(),
                            user == null ? null : user.getUserName()));
                }
                node.setSlots(slots);
            }
            server.setNodes(nodes);
        }
        return servers;
    }

    public List<TrainingServer> heartbeatServers() {
        List<TrainingServer> servers = serverMapper.selectList(
                new QueryWrapper<TrainingServer>().orderByAsc("training_server_id"));
        long checkedAt = System.currentTimeMillis();
        for (TrainingServer server : servers) {
            List<TrainingNode> nodes = nodeMapper.selectList(new QueryWrapper<TrainingNode>()
                    .eq("training_server_id", server.getTrainingServerId())
                    .orderByAsc("node_no"));
            server.setHeartbeatStatus(heartbeatStatus(server, nodes));
            server.setLastHeartbeatAt(checkedAt);
        }
        return servers;
    }

    private String heartbeatStatus(TrainingServer server, List<TrainingNode> nodes) {
        if (!Boolean.TRUE.equals(server.getEnabled())) {
            return "DISABLED";
        }
        for (TrainingNode node : nodes) {
            if (Boolean.TRUE.equals(node.getEnabled()) && probeT100(node.getHost(),
                    portForNode(server.getT100BasePort(), node.getNodeNo()))) {
                return "ONLINE";
            }
        }
        return "OFFLINE";
    }

    boolean probeT100(String host, int port) {
        HttpURLConnection connection = null;
        try {
            connection = (HttpURLConnection) new URL("http", host.trim(), port, "/").openConnection();
            connection.setRequestMethod("GET");
            connection.setConnectTimeout(700);
            connection.setReadTimeout(700);
            int responseCode = connection.getResponseCode();
            return responseCode >= 200 && responseCode < 400;
        } catch (Exception ignored) {
            return false;
        } finally {
            if (connection != null) connection.disconnect();
        }
    }

    @Transactional
    public TrainingServer saveServer(TrainingServer input) {
        validateServer(input);
        TrainingServer server;
        if (input.getTrainingServerId() == null) {
            server = input;
            server.setEnabled(input.getEnabled() == null || input.getEnabled());
            serverMapper.insert(server);
        } else {
            server = serverMapper.selectById(input.getTrainingServerId());
            if (server == null) {
                throw new IllegalArgumentException("训练服务器不存在");
            }
            server.setServerName(input.getServerName().trim());
            server.setVscodeBasePort(input.getVscodeBasePort());
            server.setCvatBasePort(input.getCvatBasePort());
            server.setT100BasePort(input.getT100BasePort());
            server.setEnabled(input.getEnabled() == null || input.getEnabled());
            serverMapper.updateById(server);
        }

        for (TrainingNode inputNode : input.getNodes()) {
            TrainingNode node = nodeMapper.selectOne(Wrappers.<TrainingNode>lambdaQuery()
                    .eq(TrainingNode::getTrainingServerId, server.getTrainingServerId())
                    .eq(TrainingNode::getNodeNo, inputNode.getNodeNo()));
            if (node == null) {
                node = new TrainingNode();
                node.setTrainingServerId(server.getTrainingServerId());
                node.setNodeNo(inputNode.getNodeNo());
                node.setHost(inputNode.getHost().trim());
                node.setEnabled(inputNode.getEnabled() == null || inputNode.getEnabled());
                nodeMapper.insert(node);
            } else {
                node.setHost(inputNode.getHost().trim());
                node.setEnabled(inputNode.getEnabled() == null || inputNode.getEnabled());
                nodeMapper.updateById(node);
            }
        }
        return server;
    }

    @Transactional
    public void deleteServer(Integer trainingServerId) {
        if (trainingServerId == null) {
            throw new IllegalArgumentException("训练服务器不存在");
        }
        TrainingServer server = serverMapper.selectById(trainingServerId);
        if (server == null) {
            throw new IllegalArgumentException("训练服务器不存在");
        }

        List<TrainingNode> nodes = nodeMapper.selectList(Wrappers.<TrainingNode>lambdaQuery()
                .eq(TrainingNode::getTrainingServerId, trainingServerId));
        for (TrainingNode node : nodes) {
            Integer assignmentCount = assignmentMapper.selectCount(Wrappers.<UserTrainingAssignment>lambdaQuery()
                    .eq(UserTrainingAssignment::getTrainingNodeId, node.getTrainingNodeId()));
            if (assignmentCount != null && assignmentCount > 0) {
                throw new IllegalArgumentException("该服务器仍有账号分配，请先取消分配");
            }
        }
        serverMapper.deleteById(trainingServerId);
    }

    @Transactional
    public void assign(Integer userId, Integer nodeId) {
        if (userId == null || nodeId == null) {
            throw new IllegalArgumentException("请选择有效的用户和端口组");
        }
        User user = userService.getById(userId);
        if (user == null || !Boolean.TRUE.equals(user.getEnabled())) {
            throw new IllegalArgumentException("用户不存在或已停用");
        }
        if ("admin".equals(user.getUserName())) {
            throw new IllegalArgumentException("管理员不分配训练端口");
        }
        TrainingNode node = nodeMapper.selectById(nodeId);
        if (node == null || !Boolean.TRUE.equals(node.getEnabled())) {
            throw new IllegalArgumentException("训练节点不存在或已停用");
        }
        TrainingServer server = serverMapper.selectById(node.getTrainingServerId());
        if (server == null || !Boolean.TRUE.equals(server.getEnabled())) {
            throw new IllegalArgumentException("训练服务器不存在或已停用");
        }

        List<UserTrainingAssignment> occupiedSlots = assignmentMapper.selectList(
                Wrappers.<UserTrainingAssignment>lambdaQuery()
                        .eq(UserTrainingAssignment::getTrainingNodeId, nodeId));
        for (UserTrainingAssignment occupied : occupiedSlots) {
            if (!occupied.getUserId().equals(userId)) {
                throw new IllegalArgumentException("该端口组已有端口分配给其他用户");
            }
        }

        assignmentMapper.delete(Wrappers.<UserTrainingAssignment>lambdaQuery()
                .eq(UserTrainingAssignment::getUserId, userId));
        try {
            for (int slotNo = 1; slotNo <= 3; slotNo++) {
                UserTrainingAssignment assignment = new UserTrainingAssignment();
                assignment.setUserId(userId);
                assignment.setTrainingNodeId(nodeId);
                assignment.setSlotNo(slotNo);
                assignmentMapper.insert(assignment);
            }
        } catch (DuplicateKeyException exception) {
            throw new IllegalArgumentException("该端口组已被分配，请刷新后重试");
        }
    }

    /**
     * Compatibility overload for callers that still submit the former single-slot shape.
     */
    @Deprecated
    public void assign(Integer userId, Integer nodeId, Integer slotNo) {
        if (slotNo == null || slotNo < 1 || slotNo > 3) {
            throw new IllegalArgumentException("请选择有效的端口组");
        }
        assign(userId, nodeId);
    }

    public void unassign(Integer userId) {
        assignmentMapper.delete(Wrappers.<UserTrainingAssignment>lambdaQuery()
                .eq(UserTrainingAssignment::getUserId, userId));
    }

    public TrainUrl getForUser(Integer userId) {
        List<UserTrainingAssignment> assignments = assignmentMapper.selectList(
                Wrappers.<UserTrainingAssignment>lambdaQuery()
                        .eq(UserTrainingAssignment::getUserId, userId));
        if (assignments == null || assignments.isEmpty()) {
            return null;
        }
        UserTrainingAssignment assignment = assignments.get(0);
        TrainingNode node = nodeMapper.selectById(assignment.getTrainingNodeId());
        TrainingServer server = node == null ? null : serverMapper.selectById(node.getTrainingServerId());
        if (node == null || server == null || !Boolean.TRUE.equals(node.getEnabled()) || !Boolean.TRUE.equals(server.getEnabled())) {
            return null;
        }
        TrainUrl url = new TrainUrl();
        url.setUserId(userId);
        for (UserTrainingAssignment item : assignments) {
            if (!node.getTrainingNodeId().equals(item.getTrainingNodeId())) {
                continue;
            }
            if (item.getSlotNo() == 1) {
                url.setVscodeUrl(node.getHost() + ":" + portForNode(server.getVscodeBasePort(), node.getNodeNo()));
            } else if (item.getSlotNo() == 2) {
                url.setCvatUrl(node.getHost() + ":" + portForNode(server.getCvatBasePort(), node.getNodeNo()));
            } else if (item.getSlotNo() == 3) {
                url.setT100Url(node.getHost() + ":" + portForNode(server.getT100BasePort(), node.getNodeNo()));
            }
        }
        return url;
    }

    private void validateServer(TrainingServer server) {
        if (server.getServerName() == null || server.getServerName().trim().isEmpty()) {
            throw new IllegalArgumentException("服务器名称不能为空");
        }
        validatePort(server.getVscodeBasePort(), "VSCode");
        validatePort(server.getCvatBasePort(), "CVAT");
        validatePort(server.getT100BasePort(), "T100");
        if (server.getNodes() == null || server.getNodes().size() != 4) {
            throw new IllegalArgumentException("每台训练服务器必须配置 4 个节点");
        }
        Set<Integer> nodeNumbers = new HashSet<>();
        for (TrainingNode node : server.getNodes()) {
            if (node.getNodeNo() == null || node.getNodeNo() < 1 || node.getNodeNo() > 4 || !nodeNumbers.add(node.getNodeNo())) {
                throw new IllegalArgumentException("节点编号必须是互不重复的 1 至 4");
            }
            if (node.getHost() == null || node.getHost().trim().isEmpty()) {
                throw new IllegalArgumentException("节点 IP 不能为空");
            }
        }
    }

    private void validatePort(Integer port, String serviceName) {
        if (port == null || port < 1 || port > 65531) {
            throw new IllegalArgumentException(serviceName + " 端口无效");
        }
    }

    private int portForNode(Integer basePort, Integer nodeNo) {
        return basePort + nodeNo;
    }

    private int slotKey(Integer nodeId, Integer slotNo) {
        return nodeId * 10 + slotNo;
    }
}
