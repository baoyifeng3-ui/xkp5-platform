package com.match.service.impl;

import cn.dev33.satoken.stp.StpUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.match.entity.TrainUrl;
import com.match.mapper.TrainUrlMapper;
import com.match.service.TrainUrlService;
import org.springframework.stereotype.Service;

/**
 * <p>
 *  服务实现类
 * </p>
 *
 * @author zhaoyu
 * @since 2022-03-22
 */
@Service
public class TrainUrlServiceImpl extends ServiceImpl<TrainUrlMapper, TrainUrl> implements TrainUrlService {

    private final TrainingEnvironmentService trainingEnvironmentService;

    public TrainUrlServiceImpl(TrainingEnvironmentService trainingEnvironmentService) {
        this.trainingEnvironmentService = trainingEnvironmentService;
    }

    public TrainUrl getByUser() {
        return trainingEnvironmentService.getForUser(StpUtil.getLoginIdAsInt());
    }

}
