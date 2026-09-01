package com.match.account.service;

import com.match.account.model.EligibleAccountView;
import com.match.environment.persistence.ProcessingEnvironmentSlotMapper;
import com.match.environment.persistence.ProcessingEnvironmentSlotRecord;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Collections;

@Service
public class AccountSlotService {
    private final ProcessingEnvironmentSlotMapper slots;

    public AccountSlotService(ProcessingEnvironmentSlotMapper slots) {
        this.slots = slots;
    }

    @Transactional
    public ProcessingEnvironmentSlotRecord bind(int userId, String slotId, int actorId) {
        ProcessingEnvironmentSlotRecord target = slots.selectForUpdate(slotId);
        if (target == null) throw new IllegalArgumentException("服务器槽位不存在");
        if (target.getUserId() != null && target.getUserId() != userId) {
            throw new IllegalArgumentException("槽位已绑定其他账号");
        }
        List<ProcessingEnvironmentSlotRecord> current = slots.selectByUserForUpdate(userId);
        if (!current.isEmpty() && !slotId.equals(current.get(0).getSlotId())) {
            throw new IllegalArgumentException("账号已绑定其他服务器槽位");
        }
        if (target.getUserId() == null
                && slots.bindIfUnbound(slotId, userId, LocalDateTime.now()) != 1) {
            throw new IllegalArgumentException("槽位绑定状态已变化");
        }
        return slots.selectForUpdate(slotId);
    }

    @Transactional(readOnly = true)
    public List<EligibleAccountView> eligible() {
        return slots.selectEligibleAccounts();
    }

    @Transactional(readOnly = true)
    public EligibleAccountView requireReadyPlacement(int userId) {
        EligibleAccountView placement = slots.selectEligibleAccount(userId);
        if (placement == null) throw new IllegalArgumentException("账号未绑定可用的处理服务器槽位");
        return placement;
    }

    @Transactional
    public List<ProcessingEnvironmentSlotRecord> lockFreeSlots(List<String> agentIds) {
        if (agentIds == null || agentIds.isEmpty()) return Collections.emptyList();
        return slots.selectFreeSlotsForAgentsForUpdate(agentIds);
    }

    @Transactional
    public void unbind(int userId) {
        List<ProcessingEnvironmentSlotRecord> current = slots.selectByUserForUpdate(userId);
        for (ProcessingEnvironmentSlotRecord slot : current) {
            slots.unbindIfBoundTo(slot.getSlotId(), userId, LocalDateTime.now());
        }
    }
}
