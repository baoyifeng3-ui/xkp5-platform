package com.match.service.impl;

import com.alibaba.fastjson.JSONArray;
import com.alibaba.fastjson.JSONObject;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.match.entity.*;
import com.match.mapper.*;
import com.match.service.TeamsService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.Date;
import java.util.List;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.stream.Collectors;

@Service
public class TeamsServiceImpl implements TeamsService {
    @Autowired
    private TeamsMapper teamsMapper;
    @Autowired
    private UserMapper userMapper;

    @Autowired
    private TeamsUserMapper teamsUserMapper;
    @Autowired
    private TestPaperMapper testPaperMapper;

    @Autowired
    private ScoreMapper scoreMapper;
    @Autowired
    private AnnouncementFieldService announcementFieldService;
    @Autowired
    private SystemSettingService systemSettingService;

    public Map<String, Object> getHomepageData() {
        List<User> participants = userMapper.selectList(null).stream()
                .filter(user -> !Boolean.TRUE.equals(user.getIsAdmin()))
                .filter(user -> !"admin".equalsIgnoreCase(user.getUserName()))
                .filter(user -> !Boolean.FALSE.equals(user.getEnabled()))
                .sorted(Comparator.comparing(User::getUserId))
                .collect(Collectors.toList());
        List<Integer> userIds = participants.stream().map(User::getUserId).collect(Collectors.toList());
        Map<Integer, Teams> teamsByUser = teamsByUser(userIds);
        Map<Integer, Map<String, String>> customValues = announcementFieldService.valuesByUserIds(userIds);

        List<Map<String, Object>> rows = new ArrayList<>();
        List<Map<String, Object>> progress = new ArrayList<>();
        String paper = systemSettingService.getActivePaper();
        int total = paper.isEmpty() ? 0 : testPaperMapper.selectCount(
                new QueryWrapper<Subject>().eq("test_paper_type", paper));
        for (int index = 0; index < participants.size(); index++) {
            User user = participants.get(index);
            Teams team = teamsByUser.get(user.getUserId());
            String[] people = people(team);
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("userId", user.getUserId());
            row.put("userName", user.getUserName());
            row.put("sequence", index + 1);
            row.put("schoolName", display(team == null ? null : team.getTeamsName()));
            row.put("teacherName", display(people[0]));
            row.put("contestantName", display(people[1]));
            row.putAll(customValues.getOrDefault(user.getUserId(), new LinkedHashMap<>()));
            rows.add(row);

            int present = team == null || team.getSpeedProgress() == null ? 0 : team.getSpeedProgress();
            present = Math.min(Math.max(present, 0), total);
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("userId", user.getUserId());
            item.put("userName", user.getUserName());
            item.put("present", present);
            item.put("all", total);
            item.put("percentage", total == 0 ? 0 : Math.round(present * 100f / total));
            progress.add(item);
        }

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("fields", announcementFieldService.enabledFields());
        result.put("rows", rows);
        result.put("progress", progress);
        return result;
    }

    private Map<Integer, Teams> teamsByUser(List<Integer> userIds) {
        Map<Integer, Teams> result = new LinkedHashMap<>();
        if (userIds.isEmpty()) return result;
        List<TeamsUser> links = teamsUserMapper.selectList(new QueryWrapper<TeamsUser>().in("user_id", userIds));
        Map<Integer, Integer> teamIdByUser = new LinkedHashMap<>();
        for (TeamsUser link : links) teamIdByUser.putIfAbsent(link.getUserId(), link.getTeamsId());
        if (teamIdByUser.isEmpty()) return result;
        Map<Integer, Teams> teams = teamsMapper.selectBatchIds(teamIdByUser.values()).stream()
                .collect(Collectors.toMap(Teams::getTeamsId, value -> value));
        teamIdByUser.forEach((userId, teamId) -> result.put(userId, teams.get(teamId)));
        return result;
    }

    private String[] people(Teams team) {
        String[] parts = team == null || team.getTeamsTeacher() == null
                ? new String[0] : team.getTeamsTeacher().split("\\|", -1);
        return new String[]{parts.length > 0 ? parts[0] : "", parts.length > 1 ? parts[1] : ""};
    }

    private String display(String value) {
        return value == null || value.trim().isEmpty() ? "--" : value.trim();
    }
    @Override
    public JSONArray  getTeamsList() {
       List<Teams> teamsList = teamsMapper.selectList(null);

        JSONArray jsonArray = new JSONArray();


        for (Teams teams:
                teamsList) {
            JSONObject jsonObject= new JSONObject();
            QueryWrapper<TeamsUser> wrapper = new QueryWrapper<>();
            wrapper.eq("teams_id",teams.getTeamsId());

            List<TeamsUser> teamsUsers = teamsUserMapper.selectList(wrapper);
            List<User> list = new ArrayList<>();
            jsonObject.put("teams",teams);




            for (TeamsUser teamsUser:
                    teamsUsers) {
                User user = userMapper.selectById(teamsUser.getUserId());
                list.add(user);

            }

            jsonObject.put("user",list);

            QueryWrapper<Subject> Subjectwrapper = new QueryWrapper<>();
            Subjectwrapper.eq("test_paper_type","A");

            List<Subject> subjects = testPaperMapper.selectList(Subjectwrapper);

            JSONObject jindu = new JSONObject();
            Integer speedProgress = teams.getSpeedProgress();
            float x = speedProgress;
            int c = subjects.size();
            float v = x / c * 100;
            jindu.put("percentage",v+"%");
            jindu.put("present",speedProgress);
            jindu.put("all",c);
            jsonObject.put("speed",jindu);
            jsonArray.add(jsonObject);

        }

        return jsonArray;




    }





    @Override
    public JSONArray  getTeamsScore() {
        List<Teams> teamsList = teamsMapper.selectList(null);

        JSONArray jsonArray = new JSONArray();


        for (Teams teams:
                teamsList) {
            JSONObject jsonObject= new JSONObject();
            QueryWrapper<TeamsUser> wrapper = new QueryWrapper<>();
            wrapper.eq("teams_id",teams.getTeamsId());

            List<TeamsUser> teamsUsers = teamsUserMapper.selectList(wrapper);

            jsonObject.put("teams",teams);




            Date createTime = null;

            Double cj = null;
            for (TeamsUser teamsUser:
                    teamsUsers) {
                QueryWrapper<Score> wra = new QueryWrapper<>();
                wra.eq("user_id",teamsUser.getUserId());
//                Score score = scoreMapper.selectOne(wra);
                List<Score> list = scoreMapper.selectList(wra).stream()
                        .sorted(Comparator.comparing(Score::getCreateTime, Comparator.reverseOrder()))
                        .collect(Collectors.toList());
                if (list.size() > 0 ){
                    Score score = list.get(0);
                    cj=score.getScore();
                    createTime=score.getCreateTime();
                }

            }

            jsonObject.put("score",cj);
            jsonObject.put("submissionTime",createTime);

            jsonArray.add(jsonObject);

        }

        return jsonArray;



    }


    @Override
    public TeamsUser selectOne(Integer userId) {
        QueryWrapper<TeamsUser> wrapper = new QueryWrapper<>();
        wrapper.eq("user_id",userId);
        TeamsUser teamsUser = teamsUserMapper.selectOne(wrapper);
        return teamsUser;
    }

    @Override
    public Teams getTeamsOne(Integer teamsId) {

        QueryWrapper<Teams> wrapper = new QueryWrapper<>();
        wrapper.eq("teams_id",teamsId);
        Teams teams = teamsMapper.selectOne(wrapper);
        return teams;
    }

    @Override
    public int setTeamsOne(Teams teams) {
        int i = teamsMapper.updateById(teams);
        return i;
    }
}
