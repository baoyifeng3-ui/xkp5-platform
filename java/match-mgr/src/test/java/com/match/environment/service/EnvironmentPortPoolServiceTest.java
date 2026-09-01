package com.match.environment.service;

import com.match.environment.persistence.EnvironmentPortAllocationMapper;
import com.match.environment.persistence.EnvironmentPortAllocationRecord;
import com.match.environment.persistence.EnvironmentPortPoolMapper;
import com.match.environment.persistence.EnvironmentPortPoolRecord;
import org.junit.Before;
import org.junit.Test;

import java.util.Arrays;
import java.util.Collections;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.fail;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

public class EnvironmentPortPoolServiceTest {
    private EnvironmentPortPoolMapper pools;
    private EnvironmentPortAllocationMapper allocations;
    private EnvironmentPortPoolService service;

    @Before public void setUp() {
        pools=mock(EnvironmentPortPoolMapper.class);allocations=mock(EnvironmentPortAllocationMapper.class);
        service=new EnvironmentPortPoolService(pools,allocations);
    }

    @Test public void allocationUsesFirstFreePort() {
        when(pools.selectForUpdate("agent-1","COURSE","ANNOTATION")).thenReturn(pool(20000,20499));
        when(allocations.selectUsedPortsForUpdate("agent-1",20000,20499,"tcp"))
                .thenReturn(Arrays.asList(20000,20002));
        EnvironmentPortAllocationRecord result=service.allocate("env-1","slot-1","agent-1",
                "COURSE","ANNOTATION","ANNOTATION",8080,"tcp");
        assertEquals(Integer.valueOf(20001),result.getHostPort());
        assertEquals("env-1",result.getEnvironmentId());
        verify(allocations).insert(any(EnvironmentPortAllocationRecord.class));
    }

    @Test public void exhaustedPoolReturnsSpecificError() {
        when(pools.selectForUpdate("agent-1","COURSE","T100")).thenReturn(pool(21500,21501));
        when(allocations.selectUsedPortsForUpdate("agent-1",21500,21501,"tcp"))
                .thenReturn(Arrays.asList(21500,21501));
        try { service.allocate("env-1","slot-1","agent-1","COURSE","T100","EDITOR",5000,"tcp");fail(); }
        catch(IllegalArgumentException error){assertEquals("T100 端口池已用尽",error.getMessage());}
    }

    @Test public void releaseDeletesOnlyEnvironmentAllocations() {
        service.releaseEnvironment("env-1");
        verify(allocations).deleteByEnvironment("env-1");
    }

    private EnvironmentPortPoolRecord pool(int start,int end){EnvironmentPortPoolRecord value=new EnvironmentPortPoolRecord();value.setRangeStart(start);value.setRangeEnd(end);return value;}
}
