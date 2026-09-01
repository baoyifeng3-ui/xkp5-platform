package com.match.account.service;

import com.match.account.model.AccountMigrationRequest;
import com.match.account.persistence.AccountEnvironmentMigrationMapper;
import com.match.account.persistence.AccountEnvironmentMigrationRecord;
import com.match.environment.persistence.ContainerTemplateMapper;
import com.match.environment.persistence.ContainerTemplateRecord;
import com.match.environment.persistence.ProcessingEnvironmentSlotMapper;
import com.match.environment.persistence.ProcessingEnvironmentSlotRecord;
import com.match.environment.persistence.TrainingEnvironmentMapper;
import com.match.environment.persistence.TrainingEnvironmentRecord;
import com.match.registry.persistence.ImageDeploymentMapper;
import com.match.registry.persistence.ImageDeploymentRecord;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

@Service
public class AccountEnvironmentMigrationService {
    private final ProcessingEnvironmentSlotMapper slots;private final TrainingEnvironmentMapper environments;private final ContainerTemplateMapper templates;private final ImageDeploymentMapper images;private final AccountEnvironmentMigrationMapper migrations;
    public AccountEnvironmentMigrationService(ProcessingEnvironmentSlotMapper slots,TrainingEnvironmentMapper environments,ContainerTemplateMapper templates,ImageDeploymentMapper images,AccountEnvironmentMigrationMapper migrations){this.slots=slots;this.environments=environments;this.templates=templates;this.images=images;this.migrations=migrations;}

    @Transactional
    public AccountEnvironmentMigrationRecord preflight(AccountMigrationRequest request,int actorId){
        if(request==null||request.getUserId()==null||request.getTargetSlotId()==null)throw new IllegalArgumentException("迁移参数不完整");
        if(migrations.selectActiveByUser(request.getUserId())!=null)throw new IllegalArgumentException("账号已有进行中的迁移");
        List<ProcessingEnvironmentSlotRecord> current=slots.selectByUserForUpdate(request.getUserId());if(current.isEmpty())throw new IllegalArgumentException("账号尚未绑定服务器槽位，不能迁移");if(current.size()!=1)throw new IllegalArgumentException("账号服务器槽位绑定异常");
        ProcessingEnvironmentSlotRecord source=current.get(0),target=slots.selectForUpdate(request.getTargetSlotId());
        if(target==null)throw new IllegalArgumentException("目标槽位不存在");if(target.getUserId()!=null&&!request.getUserId().equals(target.getUserId()))throw new IllegalArgumentException("目标槽位已绑定其他账号");
        List<TrainingEnvironmentRecord> owned=environments.selectUserEnvironmentsForUpdate(request.getUserId());
        for(TrainingEnvironmentRecord environment:owned){verifyTemplate(target.getAgentId(),environment.getAnnotationTemplateId(),environment.getAnnotationTemplateVersion(),"ANNOTATION");verifyTemplate(target.getAgentId(),environment.getEditorTemplateId(),environment.getEditorTemplateVersion(),"EDITOR");}
        LocalDateTime now=LocalDateTime.now();AccountEnvironmentMigrationRecord record=new AccountEnvironmentMigrationRecord();record.setMigrationId(UUID.randomUUID().toString());record.setUserId(request.getUserId());record.setSourceAgentId(source.getAgentId());record.setSourceSlotId(source.getSlotId());record.setTargetAgentId(target.getAgentId());record.setTargetSlotId(target.getSlotId());record.setMigrateData(request.isMigrateData());record.setState("PENDING");record.setStage("PREFLIGHT");record.setRequestedBy(actorId);record.setRequestedAt(now);record.setUpdatedAt(now);migrations.insert(record);return record;
    }
    private void verifyTemplate(String agentId,String templateId,Integer version,String type){if(templateId==null)return;ContainerTemplateRecord template=templates.selectVersion(templateId,version);if(template==null)throw new IllegalArgumentException("环境模板版本不存在");String digest=template.getImageDigest()==null?template.getImageReference():template.getImageDigest();ImageDeploymentRecord deployed=images.selectLatestSucceeded(agentId,type);if(deployed==null||digest==null||!digest.equals(deployed.getTargetDigest()))throw new IllegalArgumentException("请先向目标服务器推送镜像");}
}
