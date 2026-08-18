package com.match.service;

import com.alibaba.fastjson.JSONArray;
import com.match.entity.Teams;
import com.match.entity.TeamsUser;

public interface TeamsService {
    JSONArray  getTeamsList();

    JSONArray  getTeamsScore();


    TeamsUser selectOne(Integer userId);

    Teams getTeamsOne(Integer teamsId);

    int setTeamsOne(Teams teams);
}
