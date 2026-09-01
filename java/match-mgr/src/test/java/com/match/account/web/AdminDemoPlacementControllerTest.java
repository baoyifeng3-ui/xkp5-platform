package com.match.account.web;

import com.match.account.service.AccountSlotService;
import com.match.entity.User;
import com.match.environment.persistence.ProcessingEnvironmentSlotMapper;
import com.match.environment.persistence.ProcessingEnvironmentSlotRecord;
import com.match.security.RoleGuard;
import org.junit.Test;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

import static org.junit.Assert.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

public class AdminDemoPlacementControllerTest {
    @Test
    public void bindsCurrentBusinessAdminToTeachingSlot() {
        RoleGuard roles = mock(RoleGuard.class);
        AccountSlotService service = mock(AccountSlotService.class);
        ProcessingEnvironmentSlotMapper slots = mock(ProcessingEnvironmentSlotMapper.class);
        User admin = new User(); admin.setUserId(9);
        ProcessingEnvironmentSlotRecord bound = new ProcessingEnvironmentSlotRecord();
        bound.setSlotId("slot-1"); bound.setUserId(9);
        when(roles.requireBusinessAdmin()).thenReturn(admin);
        when(service.bind(9, "slot-1", 9)).thenReturn(bound);
        Map<String, String> request = new HashMap<>(); request.put("slotId", "slot-1");

        Object data = new AdminDemoPlacementController(roles, service, slots).bind(request).getData();

        assertEquals(bound, data);
        verify(service).bind(9, "slot-1", 9);
    }

    @Test
    public void listsCurrentPlacementAndAllSlots() {
        RoleGuard roles = mock(RoleGuard.class);
        AccountSlotService service = mock(AccountSlotService.class);
        ProcessingEnvironmentSlotMapper slots = mock(ProcessingEnvironmentSlotMapper.class);
        User admin = new User(); admin.setUserId(9);
        when(roles.requireBusinessAdmin()).thenReturn(admin);
        when(slots.selectByUser(9)).thenReturn(Collections.emptyList());
        when(slots.selectAll()).thenReturn(Collections.emptyList());

        Map<?, ?> data = (Map<?, ?>) new AdminDemoPlacementController(roles, service, slots).get().getData();

        assertEquals(Collections.emptyList(), data.get("current"));
        assertEquals(Collections.emptyList(), data.get("slots"));
    }
}
