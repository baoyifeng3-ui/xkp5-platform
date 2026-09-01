package com.match.account.service;

import com.match.account.model.EligibleAccountView;
import com.match.environment.persistence.ProcessingEnvironmentSlotMapper;
import com.match.environment.persistence.ProcessingEnvironmentSlotRecord;
import org.junit.Before;
import org.junit.Test;

import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.Collections;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.fail;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

public class AccountSlotServiceTest {
    private ProcessingEnvironmentSlotMapper slots;
    private AccountSlotService service;

    @Before
    public void setUp() {
        slots = mock(ProcessingEnvironmentSlotMapper.class);
        service = new AccountSlotService(slots);
    }

    @Test
    public void bindRejectsSlotOwnedByAnotherAccount() {
        when(slots.selectForUpdate("slot-1")).thenReturn(slot("slot-1", "agent-1", 1, 7));
        expectFailure(() -> service.bind(8, "slot-1", 1), "槽位已绑定其他账号");
    }

    @Test
    public void bindRejectsSecondPlacementForAccount() {
        ProcessingEnvironmentSlotRecord target = slot("slot-2", "agent-1", 2, null);
        when(slots.selectForUpdate("slot-2")).thenReturn(target);
        when(slots.selectByUserForUpdate(8)).thenReturn(
                Collections.singletonList(slot("slot-1", "agent-1", 1, 8)));
        expectFailure(() -> service.bind(8, "slot-2", 1), "账号已绑定其他服务器槽位");
    }

    @Test
    public void bindClaimsFreeSlot() {
        ProcessingEnvironmentSlotRecord target = slot("slot-2", "agent-1", 2, null);
        ProcessingEnvironmentSlotRecord bound = slot("slot-2", "agent-1", 2, 8);
        when(slots.selectForUpdate("slot-2")).thenReturn(target, bound);
        when(slots.selectByUserForUpdate(8)).thenReturn(Collections.emptyList());
        when(slots.bindIfUnbound(eq("slot-2"), eq(8), any(LocalDateTime.class))).thenReturn(1);
        assertSame(bound, service.bind(8, "slot-2", 1));
        verify(slots).bindIfUnbound(eq("slot-2"), eq(8), any(LocalDateTime.class));
    }

    @Test
    public void eligibleReturnsMapperValidatedPlacements() {
        EligibleAccountView first = new EligibleAccountView(); first.setUserId(3);
        EligibleAccountView second = new EligibleAccountView(); second.setUserId(4);
        when(slots.selectEligibleAccounts()).thenReturn(Arrays.asList(first, second));
        assertEquals(Arrays.asList(first, second), service.eligible());
    }

    private ProcessingEnvironmentSlotRecord slot(String id, String agentId, int number, Integer userId) {
        ProcessingEnvironmentSlotRecord record = new ProcessingEnvironmentSlotRecord();
        record.setSlotId(id); record.setAgentId(agentId); record.setSlotNumber(number); record.setUserId(userId);
        return record;
    }

    private void expectFailure(Runnable action, String message) {
        try { action.run(); fail("expected failure"); }
        catch (IllegalArgumentException error) { assertEquals(message, error.getMessage()); }
    }
}
