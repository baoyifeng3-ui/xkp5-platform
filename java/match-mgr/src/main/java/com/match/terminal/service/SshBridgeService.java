package com.match.terminal.service;

import com.match.agent.persistence.ProcessingAgentMapper;
import com.match.agent.persistence.ProcessingAgentRecord;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;
import java.util.*;

@Service
public class SshBridgeService {
    private final ProcessingAgentMapper agents; private final RestTemplate http=new RestTemplate(); private final String base; private final String token; private final String sshUser;
    public SshBridgeService(ProcessingAgentMapper agents,@Value("${match.ssh-bridge.url:http://192.168.65.254:19245}")String base,
                            @Value("${match.ssh-bridge.token:xkp5-development-ssh-bridge}")String token,
                            @Value("${match.ssh-bridge.user:root}")String sshUser){this.agents=agents;this.base=base;this.token=token;this.sshUser=sshUser;}
    public Map<String,Object> create(String agentId){ProcessingAgentRecord a=agents.selectForManagement(agentId);if(a==null||!Boolean.TRUE.equals(a.getEnabled())||a.getRemovedAt()!=null)throw new IllegalArgumentException("处理服务器不可用");Map<String,String>b=new HashMap<>();b.put("host",a.getPrimaryIp());b.put("user",sshUser);return exchange(HttpMethod.POST,"/sessions",b,Map.class);}
    public String publicKey(){Map<String,Object> result=exchange(HttpMethod.GET,"/public-key",null,Map.class);Object value=result==null?null:result.get("publicKey");if(value==null||!String.valueOf(value).startsWith("ssh-"))throw new IllegalStateException("管理服务器 SSH 公钥不可用");return String.valueOf(value).trim();}
    public Map<String,Object> output(String id,long cursor){return exchange(HttpMethod.GET,"/sessions/"+id+"/output?cursor="+cursor,null,Map.class);}
    public void input(String id,String data){Map<String,String>b=new HashMap<>();b.put("data",data);exchange(HttpMethod.POST,"/sessions/"+id+"/input",b,Void.class);}
    public void close(String id){exchange(HttpMethod.DELETE,"/sessions/"+id,null,Void.class);}
    private <T>T exchange(HttpMethod method,String path,Object body,Class<T> type){HttpHeaders h=new HttpHeaders();h.set("X-Bridge-Token",token);h.setContentType(MediaType.APPLICATION_JSON);return http.exchange(base+path,method,new HttpEntity<>(body,h),type).getBody();}
}
