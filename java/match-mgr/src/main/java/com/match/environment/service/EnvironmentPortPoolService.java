package com.match.environment.service;

import com.match.environment.persistence.EnvironmentPortAllocationMapper;
import com.match.environment.persistence.EnvironmentPortAllocationRecord;
import com.match.environment.persistence.EnvironmentPortPoolMapper;
import com.match.environment.persistence.EnvironmentPortPoolRecord;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.ArrayList;
import java.util.Map;
import java.util.LinkedHashMap;

@Service
public class EnvironmentPortPoolService {
    private final EnvironmentPortPoolMapper pools;
    private final EnvironmentPortAllocationMapper allocations;
    public EnvironmentPortPoolService(EnvironmentPortPoolMapper pools,EnvironmentPortAllocationMapper allocations){this.pools=pools;this.allocations=allocations;}

    @Transactional
    public EnvironmentPortAllocationRecord allocate(String environmentId,String slotId,String agentId,
                                                     String environmentType,String serviceType,String componentType,
                                                     int containerPort,String protocol){
        EnvironmentPortPoolRecord pool=pools.selectForUpdate(agentId,environmentType,serviceType);
        if(pool==null)throw new IllegalArgumentException(serviceType+" 端口池未配置");
        List<Integer> values=allocations.selectUsedPortsForUpdate(agentId,pool.getRangeStart(),pool.getRangeEnd(),protocol);
        Set<Integer> used=new HashSet<>(values);
        Integer selected=null;for(int port=pool.getRangeStart();port<=pool.getRangeEnd();port++){if(!used.contains(port)){selected=port;break;}}
        if(selected==null)throw new IllegalArgumentException(serviceType+" 端口池已用尽");
        EnvironmentPortAllocationRecord result=new EnvironmentPortAllocationRecord();result.setAllocationId(UUID.randomUUID().toString());
        result.setEnvironmentId(environmentId);result.setSlotId(slotId);result.setAgentId(agentId);result.setComponentType(componentType);
        result.setContainerPort(containerPort);result.setHostPort(selected);result.setProtocol(protocol);result.setCreatedAt(LocalDateTime.now());
        allocations.insert(result);return result;
    }

    @Transactional public void releaseEnvironment(String environmentId){allocations.deleteByEnvironment(environmentId);}

    @Transactional(readOnly=true) public List<Map<String,Object>> list(String agentId){List<Map<String,Object>> result=new ArrayList<>();for(EnvironmentPortPoolRecord pool:pools.selectByAgent(agentId)){int total=pool.getRangeEnd()-pool.getRangeStart()+1,used=allocations.countInRange(agentId,pool.getRangeStart(),pool.getRangeEnd());Map<String,Object> row=new LinkedHashMap<>();row.put("poolId",pool.getPoolId());row.put("environmentType",pool.getEnvironmentType());row.put("serviceType",pool.getServiceType());row.put("rangeStart",pool.getRangeStart());row.put("rangeEnd",pool.getRangeEnd());row.put("total",total);row.put("used",used);row.put("remaining",Math.max(0,total-used));result.add(row);}return result;}
    @Transactional public EnvironmentPortPoolRecord update(String poolId,int start,int end,int actorId){if(start<1024||end>65535||start>end)throw new IllegalArgumentException("端口池范围无效");EnvironmentPortPoolRecord target=pools.selectById(poolId);if(target==null)throw new IllegalArgumentException("端口池不存在");for(EnvironmentPortPoolRecord other:pools.selectByAgent(target.getAgentId()))if(!poolId.equals(other.getPoolId())&&start<=other.getRangeEnd()&&end>=other.getRangeStart())throw new IllegalArgumentException("端口池范围与其他服务重叠");if(allocations.countInRange(target.getAgentId(),target.getRangeStart(),target.getRangeEnd())>0&&(start>target.getRangeStart()||end<target.getRangeEnd()))throw new IllegalArgumentException("端口池已有分配，不能缩小范围");target.setRangeStart(start);target.setRangeEnd(end);target.setUpdatedBy(actorId);target.setUpdatedAt(LocalDateTime.now());pools.updateById(target);return target;}
}
