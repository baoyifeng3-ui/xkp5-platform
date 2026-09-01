package com.match.environment.service;

import com.match.account.model.EligibleAccountView;
import com.match.account.service.AccountSlotService;
import com.match.environment.model.CreateAccountsEnvironmentRequest;
import com.match.environment.model.CreateTrainingEnvironmentRequest;
import org.junit.Before;
import org.junit.Test;
import org.mockito.ArgumentCaptor;

import java.util.Arrays;

import static org.junit.Assert.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

public class AccountEnvironmentCreationServiceTest {
    private AccountSlotService placements;
    private TrainingEnvironmentService environments;
    private AccountEnvironmentCreationService service;

    @Before public void setUp(){placements=mock(AccountSlotService.class);environments=mock(TrainingEnvironmentService.class);service=new AccountEnvironmentCreationService(placements,environments);}

    @Test public void createResolvesAgentAndSlotFromAccountBinding(){
        EligibleAccountView placement=new EligibleAccountView();placement.setUserId(3);placement.setAgentId("agent-1");placement.setSlotId("slot-2");placement.setSlotNumber(2);
        when(placements.requireReadyPlacement(3)).thenReturn(placement);
        CreateAccountsEnvironmentRequest request=new CreateAccountsEnvironmentRequest();request.setEnvironmentName("课程环境");request.setEnvironmentType("COURSE");request.setUserIds(Arrays.asList(3));
        service.create(request,1,"ADMIN");
        ArgumentCaptor<CreateTrainingEnvironmentRequest> captor=ArgumentCaptor.forClass(CreateTrainingEnvironmentRequest.class);
        verify(environments).create(captor.capture(),org.mockito.ArgumentMatchers.eq(1),org.mockito.ArgumentMatchers.eq("ADMIN"));
        assertEquals("agent-1",captor.getValue().getAgentId());assertEquals(Integer.valueOf(2),captor.getValue().getSlotNumber());assertEquals(Integer.valueOf(3),captor.getValue().getUserId());
    }
}
