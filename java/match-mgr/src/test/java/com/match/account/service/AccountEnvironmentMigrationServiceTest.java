package com.match.account.service;

import com.match.account.model.AccountMigrationRequest;
import com.match.account.persistence.AccountEnvironmentMigrationMapper;
import com.match.environment.persistence.ContainerTemplateMapper;
import com.match.environment.persistence.ContainerTemplateRecord;
import com.match.environment.persistence.ProcessingEnvironmentSlotMapper;
import com.match.environment.persistence.ProcessingEnvironmentSlotRecord;
import com.match.environment.persistence.TrainingEnvironmentMapper;
import com.match.environment.persistence.TrainingEnvironmentRecord;
import com.match.registry.persistence.ImageDeploymentMapper;
import org.junit.Before;
import org.junit.Test;

import java.util.Collections;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.fail;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

public class AccountEnvironmentMigrationServiceTest {
    private ProcessingEnvironmentSlotMapper slots; private TrainingEnvironmentMapper environments;
    private ContainerTemplateMapper templates; private ImageDeploymentMapper images;
    private AccountEnvironmentMigrationMapper migrations; private AccountEnvironmentMigrationService service;
    @Before public void setUp(){slots=mock(ProcessingEnvironmentSlotMapper.class);environments=mock(TrainingEnvironmentMapper.class);templates=mock(ContainerTemplateMapper.class);images=mock(ImageDeploymentMapper.class);migrations=mock(AccountEnvironmentMigrationMapper.class);service=new AccountEnvironmentMigrationService(slots,environments,templates,images,migrations);}

    @Test public void requestDefaultsToNoDataMigration(){AccountMigrationRequest request=new AccountMigrationRequest();assertFalse(request.isMigrateData());}

    @Test public void missingTargetImageStopsBeforeBindingChange(){
        when(slots.selectByUserForUpdate(3)).thenReturn(Collections.singletonList(slot("old","agent-old",1,3)));
        when(slots.selectForUpdate("new")).thenReturn(slot("new","agent-new",2,null));
        TrainingEnvironmentRecord environment=new TrainingEnvironmentRecord();environment.setUserId(3);environment.setAnnotationTemplateId("template-1");environment.setAnnotationTemplateVersion(1);
        when(environments.selectUserEnvironmentsForUpdate(3)).thenReturn(Collections.singletonList(environment));
        ContainerTemplateRecord template=new ContainerTemplateRecord();template.setComponentType("ANNOTATION");template.setImageReference("sha256:aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa");
        when(templates.selectVersion("template-1",1)).thenReturn(template);
        when(images.selectLatestSucceeded("agent-new","ANNOTATION")).thenReturn(null);
        AccountMigrationRequest request=new AccountMigrationRequest();request.setUserId(3);request.setTargetSlotId("new");
        try{service.preflight(request,1);fail();}catch(IllegalArgumentException error){assertEquals("请先向目标服务器推送镜像",error.getMessage());}
        verify(slots,never()).unbindIfBoundTo(org.mockito.ArgumentMatchers.anyString(),org.mockito.ArgumentMatchers.anyInt(),org.mockito.ArgumentMatchers.any());
    }

    private ProcessingEnvironmentSlotRecord slot(String id,String agent,int number,Integer user){ProcessingEnvironmentSlotRecord value=new ProcessingEnvironmentSlotRecord();value.setSlotId(id);value.setAgentId(agent);value.setSlotNumber(number);value.setUserId(user);return value;}
}
