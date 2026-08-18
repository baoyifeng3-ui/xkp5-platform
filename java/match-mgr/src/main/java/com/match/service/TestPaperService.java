package com.match.service;

import com.alibaba.fastjson.JSONArray;
import com.match.entity.Subject;
import com.match.entity.User;

public interface TestPaperService{
    JSONArray  getSubject(User user,Subject subject);


}
