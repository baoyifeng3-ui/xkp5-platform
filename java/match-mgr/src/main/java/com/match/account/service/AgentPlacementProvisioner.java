package com.match.account.service;

import com.match.environment.persistence.EnvironmentPortPoolMapper;
import com.match.environment.persistence.EnvironmentPortPoolRecord;
import com.match.environment.persistence.ProcessingEnvironmentSlotMapper;
import com.match.environment.persistence.ProcessingEnvironmentSlotRecord;
import org.springframework.stereotype.Service;
import java.time.LocalDateTime;
import java.util.*;

@Service public class AgentPlacementProvisioner {
    @org.springframework.beans.factory.annotation.Autowired private com.match.mode.persistence.ProcessingAgentModeMapper modes;
    private final ProcessingEnvironmentSlotMapper slots;private final EnvironmentPortPoolMapper pools;
    public AgentPlacementProvisioner(ProcessingEnvironmentSlotMapper slots,EnvironmentPortPoolMapper pools){this.slots=slots;this.pools=pools;}
    public void ensure(String agentId){if(modes!=null)modes.ensureTraining(agentId);LocalDateTime now=LocalDateTime.now();Set<Integer> existing=new HashSet<>();for(ProcessingEnvironmentSlotRecord row:slots.selectByAgentForUpdate(agentId))existing.add(row.getSlotNumber());for(int number=1;number<=4;number++)if(!existing.contains(number)){ProcessingEnvironmentSlotRecord row=new ProcessingEnvironmentSlotRecord();row.setSlotId(UUID.randomUUID().toString());row.setAgentId(agentId);row.setSlotNumber(number);row.setCreatedBy(0);row.setCreatedAt(now);row.setUpdatedAt(now);slots.insert(row);}if(pools.selectByAgent(agentId).isEmpty()){String[][] definitions={{"COURSE","ANNOTATION","20000","20499"},{"COURSE","VSCODE","20500","20999"},{"COURSE","JUPYTER","21000","21499"},{"COURSE","T100","21500","21999"},{"COMPETITION","ANNOTATION","22000","22499"},{"COMPETITION","VSCODE","22500","22999"},{"COMPETITION","JUPYTER","23000","23499"},{"COMPETITION","T100","23500","23999"}};for(String[] d:definitions){EnvironmentPortPoolRecord row=new EnvironmentPortPoolRecord();row.setPoolId(UUID.randomUUID().toString());row.setAgentId(agentId);row.setEnvironmentType(d[0]);row.setServiceType(d[1]);row.setRangeStart(Integer.valueOf(d[2]));row.setRangeEnd(Integer.valueOf(d[3]));row.setUpdatedBy(0);row.setUpdatedAt(now);pools.insert(row);}}}
}
