package com.match.attendance.service;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.match.attendance.persistence.*;
import com.match.entity.User;
import com.match.mapper.UserMapper;
import com.match.service.impl.AnnouncementFieldService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

@Service
public class AttendanceService {
    private final AttendanceSessionMapper sessions;
    private final AttendanceRecordMapper records;
    private final UserMapper users;
    private final AnnouncementFieldService fields;
    public AttendanceService(AttendanceSessionMapper sessions, AttendanceRecordMapper records, UserMapper users, AnnouncementFieldService fields) {
        this.sessions=sessions; this.records=records; this.users=users; this.fields=fields;
    }
    @Transactional public AttendanceSessionRecord start() {
        AttendanceSessionRecord active=sessions.selectActive();
        if(active!=null)return active;
        AttendanceSessionRecord row=new AttendanceSessionRecord(); row.setSessionId(UUID.randomUUID().toString()); row.setStatus("ACTIVE"); row.setStartedAt(LocalDateTime.now()); sessions.insert(row); return row;
    }
    @Transactional public void end() { AttendanceSessionRecord row=sessions.selectActive(); if(row!=null)sessions.end(row.getSessionId(),LocalDateTime.now()); }
    @Transactional public Map<String,Object> checkIn(Integer userId) {
        AttendanceSessionRecord session=sessions.selectActive(); if(session==null) throw new IllegalStateException("当前没有进行中的签到");
        if(records.selectByUser(session.getSessionId(),userId)==null){AttendanceRecord row=new AttendanceRecord();row.setSessionId(session.getSessionId());row.setUserId(userId);row.setCheckedAt(LocalDateTime.now());records.insert(row);}
        return status(userId);
    }
    public Map<String,Object> status(Integer userId){
        AttendanceSessionRecord session=sessions.selectActive(); Map<String,Object> out=new LinkedHashMap<>(); out.put("active",session!=null); if(session==null)return out;
        List<User> participants=users.selectList(Wrappers.<User>lambdaQuery().eq(User::getIsAdmin,false).eq(User::getEnabled,true)); List<Integer> checked=records.selectUserIds(session.getSessionId());
        out.put("sessionId",session.getSessionId());out.put("startedAt",session.getStartedAt());out.put("total",participants.size());out.put("checked",checked.size());out.put("checkedIn",userId!=null&&checked.contains(userId));out.put("rows",rows(participants,checked)); return out;
    }
    private List<Map<String,Object>> rows(List<User> participants,List<Integer> checked){
        Map<Integer,Map<String,String>> custom=fields.valuesByUserIdsByName(participants.stream().map(User::getUserId).collect(Collectors.toList()));
        return participants.stream().map(user->{Map<String,Object> row=new LinkedHashMap<>();row.put("userId",user.getUserId());row.put("userName",user.getUserName());String name=custom.getOrDefault(user.getUserId(),Collections.emptyMap()).get("姓名");if(name!=null&&!name.trim().isEmpty())row.put("name",name);row.put("checkedIn",checked.contains(user.getUserId()));return row;}).collect(Collectors.toList());
    }
}
