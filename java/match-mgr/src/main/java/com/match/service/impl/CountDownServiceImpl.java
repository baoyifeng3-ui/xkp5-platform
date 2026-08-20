package com.match.service.impl;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.match.dto.CountDownActionRequest;
import com.match.dto.CountDownResponse;
import com.match.entity.CountDown;
import com.match.mapper.CountDownMapper;
import com.match.service.CountDownService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Date;

@Service
public class CountDownServiceImpl extends ServiceImpl<CountDownMapper, CountDown> implements CountDownService {
    private static final int COUNT_DOWN_ID = 1;
    private static final String NOT_STARTED = "NOT_STARTED";
    private static final String WAITING_LOGIN = "WAITING_LOGIN";
    private static final String SCHEDULED = "SCHEDULED";
    private static final String RUNNING = "RUNNING";
    private static final String PAUSED = "PAUSED";
    private static final String FINISHED = "FINISHED";
    private static final String ENDED = "ENDED";
    private final CompetitionRoundResetService roundResetService;

    public CountDownServiceImpl(CompetitionRoundResetService roundResetService) {
        this.roundResetService = roundResetService;
    }

    @Override
    public CountDown getCountDown() {
        return ensureCountDown();
    }

    public CountDownResponse snapshot() {
        Date now = new Date();
        CountDown countDown = ensureCountDown();
        String status = normalizeStatus(countDown, now);
        long remaining = remainingSeconds(countDown, now, status);
        Long scheduledStart = millis(countDown.getScheduledStartTime());
        long preStartRemaining = scheduledStart == null
                ? 0L : Math.max(0L, (scheduledStart - now.getTime() + 999L) / 1000L);
        CountDownResponse response = new CountDownResponse();
        response.setServerTime(now.getTime());
        response.setStartTime(countDown.getStartTime() == null ? null : countDown.getStartTime().getTime());
        response.setEndTime(countDown.getCountDownTime() == null ? null : countDown.getCountDownTime().getTime());
        response.setScheduledStartTime(scheduledStart);
        response.setLoginOpenTime(scheduledStart == null ? null
                : scheduledStart - preLoginMinutes(countDown) * 60_000L);
        response.setStatus(status);
        response.setAccessPhase(accessPhase(status));
        response.setRemainingSeconds(remaining);
        response.setPreStartRemainingSeconds(preStartRemaining);
        response.setPreLoginMinutes(preLoginMinutes(countDown));
        response.setDurationMinutes(durationMinutes(countDown));
        return response;
    }

    @Transactional
    public CountDownResponse apply(CountDownActionRequest request, Integer adminId) {
        if (request == null || request.getAction() == null) {
            throw new IllegalArgumentException("缺少计时操作");
        }
        CountDown countDown = ensureCountDown();
        Date now = new Date();
        String action = request.getAction().trim().toUpperCase();
        if ("SCHEDULE".equals(action)) {
            schedule(countDown, request, adminId, now);
        } else if ("START".equals(action)) {
            int minutes = request.getMinutes() == null ? 240 : request.getMinutes();
            validateMinutes(minutes);
            if (FINISHED.equals(normalizeStatus(countDown, now))) {
                roundResetService.reset();
            }
            clearSchedule(countDown);
            setRunning(countDown, now, new Date(now.getTime() + minutes * 60_000L), adminId, true);
        } else if ("EXTEND".equals(action)) {
            int minutes = request.getMinutes() == null ? 30 : request.getMinutes();
            validateMinutes(minutes);
            long base = now.getTime();
            if (RUNNING.equals(normalizeStatus(countDown, now)) && countDown.getCountDownTime() != null
                    && countDown.getCountDownTime().after(now)) {
                base = countDown.getCountDownTime().getTime();
            } else if (PAUSED.equals(countDown.getStatus())) {
                countDown.setRemainingSeconds(value(countDown.getRemainingSeconds()) + minutes * 60L);
                countDown.setUpdatedAt(now);
                countDown.setUpdatedBy(adminId);
                updateById(countDown);
                return snapshot();
            }
            setRunning(countDown, now, new Date(base + minutes * 60_000L), adminId, false);
        } else if ("SET_END".equals(action)) {
            if (request.getEndTime() == null || request.getEndTime() <= now.getTime()) {
                throw new IllegalArgumentException("结束时间必须晚于当前服务器时间");
            }
            clearSchedule(countDown);
            setRunning(countDown, now, new Date(request.getEndTime()), adminId, false);
        } else if ("PAUSE".equals(action)) {
            if (!RUNNING.equals(normalizeStatus(countDown, now))) {
                throw new IllegalArgumentException("当前计时不是进行中状态");
            }
            long remaining = remainingSeconds(countDown, now, RUNNING);
            countDown.setRemainingSeconds(remaining);
            countDown.setCountDownTime(null);
            countDown.setStatus(PAUSED);
            countDown.setUpdatedAt(now);
            countDown.setUpdatedBy(adminId);
            updateById(countDown);
        } else if ("RESUME".equals(action)) {
            if (!PAUSED.equals(countDown.getStatus()) || value(countDown.getRemainingSeconds()) <= 0) {
                throw new IllegalArgumentException("当前计时不是可恢复状态");
            }
            clearSchedule(countDown);
            setRunning(countDown, now,
                    new Date(now.getTime() + value(countDown.getRemainingSeconds()) * 1000L), adminId, false);
        } else if ("END".equals(action)) {
            if (FINISHED.equals(normalizeStatus(countDown, now))) {
                throw new IllegalArgumentException("比赛已经结束，不能重复结束");
            }
            countDown.setCountDownTime(now);
            countDown.setRemainingSeconds(0L);
            countDown.setStatus(FINISHED);
            clearSchedule(countDown);
            countDown.setUpdatedAt(now);
            countDown.setUpdatedBy(adminId);
            updateById(countDown);
        } else {
            throw new IllegalArgumentException("不支持的计时操作");
        }
        return snapshot();
    }

    private CountDown ensureCountDown() {
        CountDown countDown = getById(COUNT_DOWN_ID);
        if (countDown != null) {
            if (countDown.getStatus() == null) {
                countDown.setStatus(NOT_STARTED);
            }
            if (countDown.getRemainingSeconds() == null) {
                countDown.setRemainingSeconds(0L);
            }
            if (countDown.getPreLoginMinutes() == null) countDown.setPreLoginMinutes(30);
            if (countDown.getDurationMinutes() == null) countDown.setDurationMinutes(240);
            return countDown;
        }
        countDown = new CountDown();
        countDown.setCountDownId(COUNT_DOWN_ID);
        countDown.setStatus(NOT_STARTED);
        countDown.setRemainingSeconds(0L);
        countDown.setPreLoginMinutes(30);
        countDown.setDurationMinutes(240);
        save(countDown);
        return countDown;
    }

    private void setRunning(CountDown countDown, Date start, Date end, Integer adminId, boolean resetStart) {
        if (resetStart || countDown.getStartTime() == null) {
            countDown.setStartTime(start);
        }
        countDown.setCountDownTime(end);
        countDown.setRemainingSeconds(Math.max(0L, (end.getTime() - start.getTime()) / 1000L));
        countDown.setStatus(RUNNING);
        countDown.setUpdatedAt(start);
        countDown.setUpdatedBy(adminId);
        updateById(countDown);
    }

    private long remainingSeconds(CountDown countDown, Date now, String status) {
        if (RUNNING.equals(status) && countDown.getCountDownTime() != null) {
            return Math.max(0L, (countDown.getCountDownTime().getTime() - now.getTime() + 999L) / 1000L);
        }
        if (PAUSED.equals(countDown.getStatus())) {
            return Math.max(0L, value(countDown.getRemainingSeconds()));
        }
        return 0L;
    }

    private String normalizeStatus(CountDown countDown, Date now) {
        if (ENDED.equals(countDown.getStatus())) {
            return FINISHED;
        }
        if (PAUSED.equals(countDown.getStatus()) || FINISHED.equals(countDown.getStatus())) {
            return countDown.getStatus();
        }
        if (countDown.getScheduledStartTime() != null) {
            long start = countDown.getScheduledStartTime().getTime();
            long loginOpen = start - preLoginMinutes(countDown) * 60_000L;
            if (now.getTime() < loginOpen) return WAITING_LOGIN;
            if (now.getTime() < start) return SCHEDULED;
            if (countDown.getCountDownTime() != null && now.before(countDown.getCountDownTime())) return RUNNING;
            return FINISHED;
        }
        if (RUNNING.equals(countDown.getStatus()) && remainingSeconds(countDown, now, RUNNING) == 0) {
            return FINISHED;
        }
        return countDown.getStatus() == null ? NOT_STARTED : countDown.getStatus();
    }

    private void schedule(CountDown countDown, CountDownActionRequest request, Integer adminId, Date now) {
        if (request.getStartTime() == null || request.getStartTime() <= now.getTime()) {
            throw new IllegalArgumentException("正式开始时间必须晚于当前服务器时间");
        }
        int preLogin = request.getPreLoginMinutes() == null ? 30 : request.getPreLoginMinutes();
        int duration = request.getDurationMinutes() == null ? 240 : request.getDurationMinutes();
        if (preLogin < 0 || preLogin > 7 * 24 * 60) {
            throw new IllegalArgumentException("提前登录分钟数必须在 0 到 10080 之间");
        }
        validateMinutes(duration);
        Date scheduledStart = new Date(request.getStartTime());
        countDown.setScheduledStartTime(scheduledStart);
        countDown.setStartTime(scheduledStart);
        countDown.setPreLoginMinutes(preLogin);
        countDown.setDurationMinutes(duration);
        countDown.setCountDownTime(new Date(scheduledStart.getTime() + duration * 60_000L));
        countDown.setRemainingSeconds(duration * 60L);
        countDown.setStatus(SCHEDULED);
        countDown.setUpdatedAt(now);
        countDown.setUpdatedBy(adminId);
        updateById(countDown);
    }

    private void clearSchedule(CountDown countDown) {
        countDown.setScheduledStartTime(null);
    }

    private int preLoginMinutes(CountDown countDown) {
        return countDown.getPreLoginMinutes() == null ? 30 : countDown.getPreLoginMinutes();
    }

    private int durationMinutes(CountDown countDown) {
        return countDown.getDurationMinutes() == null ? 240 : countDown.getDurationMinutes();
    }

    private Long millis(Date value) {
        return value == null ? null : value.getTime();
    }

    private String accessPhase(String status) {
        if (WAITING_LOGIN.equals(status)) return "BEFORE_LOGIN";
        if (SCHEDULED.equals(status)) return "PRE_START";
        if (RUNNING.equals(status) || PAUSED.equals(status)) return "ACTIVE";
        if (FINISHED.equals(status)) return "FINISHED";
        return "UNSCHEDULED";
    }

    private long value(Long value) {
        return value == null ? 0L : value;
    }

    private void validateMinutes(Integer minutes) {
        if (minutes == null || minutes < 1 || minutes > 7 * 24 * 60) {
            throw new IllegalArgumentException("分钟数必须在 1 到 10080 之间");
        }
    }
}
