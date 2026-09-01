package com.match.mode.web;

import com.match.mode.model.PlatformModeView;
import com.match.mode.persistence.CompetitionCredentialRecord;
import com.match.mode.service.CompetitionCredentialService;
import com.match.mode.service.PlatformModeService;
import com.match.security.RoleGuard;
import com.match.util.result.Response;
import com.match.util.result.ResponseResult;
import org.springframework.web.bind.annotation.*;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@RestController @RequestMapping("/admin/competition-credentials")
public class CompetitionCredentialController {
    private final RoleGuard roles;private final PlatformModeService modes;private final CompetitionCredentialService credentials;
    public CompetitionCredentialController(RoleGuard roles,PlatformModeService modes,CompetitionCredentialService credentials){this.roles=roles;this.modes=modes;this.credentials=credentials;}
    @GetMapping public ResponseResult<Object> list(){roles.requireBusinessAdmin();PlatformModeView mode=modes.current();if(!PlatformModeService.COMPETITION.equals(mode.getMode()))return Response.makeOKRsp(new ArrayList<>());List<Map<String,Object>> result=new ArrayList<>();for(CompetitionCredentialRecord record:credentials.list(mode.getGeneration()))result.add(view(record));return Response.makeOKRsp(result);}
    @PostMapping("/{userId}/regenerate") public ResponseResult<Object> regenerate(@PathVariable Integer userId){roles.requireBusinessAdmin();PlatformModeView mode=modes.current();requireCompetition(mode);return Response.makeOKRsp(view(credentials.regenerate(mode.getGeneration(),userId,null)));}
    @PutMapping("/{userId}") public ResponseResult<Object> update(@PathVariable Integer userId,@RequestBody Map<String,String> request){roles.requireBusinessAdmin();PlatformModeView mode=modes.current();requireCompetition(mode);return Response.makeOKRsp(view(credentials.regenerate(mode.getGeneration(),userId,request==null?null:request.get("password"))));}
    private Map<String,Object> view(CompetitionCredentialRecord record){Map<String,Object> value=new LinkedHashMap<>();value.put("userId",record.getUserId());value.put("password",credentials.plain(record));value.put("updatedAt",record.getUpdatedAt());return value;}
    private void requireCompetition(PlatformModeView mode){if(!PlatformModeService.COMPETITION.equals(mode.getMode()))throw new IllegalArgumentException("平台当前不是比赛模式");}
}
