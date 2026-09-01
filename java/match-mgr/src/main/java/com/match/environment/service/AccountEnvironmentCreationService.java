package com.match.environment.service;

import com.match.account.model.EligibleAccountView;
import com.match.account.service.AccountSlotService;
import com.match.environment.model.CreateAccountsEnvironmentRequest;
import com.match.environment.model.CreateTrainingEnvironmentRequest;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
public class AccountEnvironmentCreationService {
    private final AccountSlotService placements;
    private final TrainingEnvironmentService environments;
    public AccountEnvironmentCreationService(AccountSlotService placements,TrainingEnvironmentService environments){this.placements=placements;this.environments=environments;}

    public List<Map<String,Object>> create(CreateAccountsEnvironmentRequest request,int actorId,String actorRole){
        if(request==null||request.getUserIds()==null||request.getUserIds().isEmpty())throw new IllegalArgumentException("请选择关联账号");
        List<Map<String,Object>> results=new ArrayList<>();
        for(Integer userId:request.getUserIds()){
            Map<String,Object> result=new LinkedHashMap<>();result.put("userId",userId);
            try{
                EligibleAccountView placement=placements.requireReadyPlacement(userId);
                CreateTrainingEnvironmentRequest target=toTarget(request,placement);
                result.put("success",true);result.put("operation",environments.create(target,actorId,actorRole));
            }catch(RuntimeException error){result.put("success",false);result.put("message",error.getMessage());}
            results.add(result);
        }
        return results;
    }

    private CreateTrainingEnvironmentRequest toTarget(CreateAccountsEnvironmentRequest source,EligibleAccountView placement){
        CreateTrainingEnvironmentRequest target=new CreateTrainingEnvironmentRequest();
        target.setEnvironmentName(source.getEnvironmentName());target.setRemark(source.getRemark());target.setEnvironmentType(source.getEnvironmentType());
        target.setUserId(placement.getUserId());target.setAgentId(placement.getAgentId());target.setSlotNumber(placement.getSlotNumber());target.setCourseId(source.getCourseId());
        target.setAnnotationTemplateId(source.getAnnotationTemplateId());target.setAnnotationTemplateVersion(source.getAnnotationTemplateVersion());
        target.setEditorTemplateId(source.getEditorTemplateId());target.setEditorTemplateVersion(source.getEditorTemplateVersion());return target;
    }
}
