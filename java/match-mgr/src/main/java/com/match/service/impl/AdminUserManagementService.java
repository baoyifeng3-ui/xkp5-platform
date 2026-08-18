package com.match.service.impl;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.match.dto.AdminUserView;
import com.match.dto.AdminUserRequest;
import com.match.entity.AnswerSheet;
import com.match.entity.Score;
import com.match.entity.Teams;
import com.match.entity.TeamsUser;
import com.match.entity.TrainUrl;
import com.match.entity.User;
import com.match.entity.UserTrainingAssignment;
import com.match.mapper.AnswerSheetMapper;
import com.match.mapper.ScoreMapper;
import com.match.mapper.TeamsMapper;
import com.match.mapper.TeamsUserMapper;
import com.match.mapper.TrainUrlMapper;
import com.match.mapper.UserMapper;
import com.match.mapper.UserTrainingAssignmentMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.security.SecureRandom;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

@Service
public class AdminUserManagementService {
    static final int MAX_BATCH_SIZE = 500;

    private static final Pattern USER_NAME_PATTERN = Pattern.compile("^user([1-9]\\d*)$");
    private static final char[] DIGITS = "0123456789".toCharArray();
    private static final char[] LOWERCASE = "abcdefghijkmnopqrstuvwxyz".toCharArray();
    private static final char[] UPPERCASE = "ABCDEFGHJKLMNPQRSTUVWXYZ".toCharArray();
    private static final char[] SYMBOLS = "!@#$%^&*".toCharArray();
    private static final char[] ALL_PASSWORD_CHARACTERS =
            "0123456789abcdefghijkmnopqrstuvwxyzABCDEFGHJKLMNPQRSTUVWXYZ!@#$%^&*".toCharArray();

    private final UserMapper userMapper;
    private final TeamsUserMapper teamsUserMapper;
    private final TeamsMapper teamsMapper;
    private final AnswerSheetMapper answerSheetMapper;
    private final ScoreMapper scoreMapper;
    private final TrainUrlMapper trainUrlMapper;
    private final UserTrainingAssignmentMapper userTrainingAssignmentMapper;
    private final AnnouncementFieldService announcementFieldService;
    private final SecureRandom secureRandom;

    @Autowired
    public AdminUserManagementService(UserMapper userMapper,
                                      TeamsUserMapper teamsUserMapper,
                                      TeamsMapper teamsMapper,
                                      AnswerSheetMapper answerSheetMapper,
                                      ScoreMapper scoreMapper,
                                      TrainUrlMapper trainUrlMapper,
                                      UserTrainingAssignmentMapper userTrainingAssignmentMapper,
                                      AnnouncementFieldService announcementFieldService) {
        this(userMapper, teamsUserMapper, teamsMapper, answerSheetMapper, scoreMapper,
                trainUrlMapper, userTrainingAssignmentMapper, announcementFieldService, new SecureRandom());
    }

    AdminUserManagementService(UserMapper userMapper,
                               TeamsUserMapper teamsUserMapper,
                               TeamsMapper teamsMapper,
                               AnswerSheetMapper answerSheetMapper,
                               ScoreMapper scoreMapper,
                               TrainUrlMapper trainUrlMapper,
                               UserTrainingAssignmentMapper userTrainingAssignmentMapper,
                               AnnouncementFieldService announcementFieldService,
                               SecureRandom secureRandom) {
        this.userMapper = userMapper;
        this.teamsUserMapper = teamsUserMapper;
        this.teamsMapper = teamsMapper;
        this.answerSheetMapper = answerSheetMapper;
        this.scoreMapper = scoreMapper;
        this.trainUrlMapper = trainUrlMapper;
        this.userTrainingAssignmentMapper = userTrainingAssignmentMapper;
        this.announcementFieldService = announcementFieldService;
        this.secureRandom = secureRandom;
    }

    public List<AdminUserView> listUsers() {
        List<User> users = userMapper.selectList(
                Wrappers.<User>lambdaQuery().orderByAsc(User::getUserId));
        if (users.isEmpty()) {
            return Collections.emptyList();
        }

        List<Integer> userIds = users.stream().map(User::getUserId).collect(Collectors.toList());
        List<TeamsUser> links = teamsUserMapper.selectList(
                Wrappers.<TeamsUser>lambdaQuery()
                        .in(TeamsUser::getUserId, userIds)
                        .orderByAsc(TeamsUser::getTeamsId));
        Map<Integer, Integer> teamIdByUserId = new LinkedHashMap<>();
        for (TeamsUser link : links) {
            teamIdByUserId.putIfAbsent(link.getUserId(), link.getTeamsId());
        }

        Map<Integer, Teams> teamsById = new HashMap<>();
        if (!teamIdByUserId.isEmpty()) {
            for (Teams team : teamsMapper.selectBatchIds(teamIdByUserId.values())) {
                teamsById.put(team.getTeamsId(), team);
            }
        }

        List<AdminUserView> result = new ArrayList<>();
        Map<Integer, Map<String, String>> customValues = announcementFieldService.valuesByUserIds(userIds);
        for (User user : users) {
            AdminUserView view = toView(user, teamsById.get(teamIdByUserId.get(user.getUserId())));
            view.setCustomFields(customValues.getOrDefault(user.getUserId(), new LinkedHashMap<>()));
            result.add(view);
        }
        return result;
    }

    @Transactional
    public List<AdminUserView> createBatch(Integer count) {
        validateCount(count);
        List<User> existingUsers = userMapper.selectList(
                Wrappers.<User>lambdaQuery().orderByAsc(User::getUserId));
        int nextSequence = nextSequence(existingUsers);
        if ((long) nextSequence + count - 1 > Integer.MAX_VALUE) {
            throw new IllegalArgumentException("账号序号超出支持范围");
        }

        List<AdminUserView> created = new ArrayList<>();
        for (int index = 0; index < count; index++) {
            User user = new User();
            user.setUserName("user" + (nextSequence + index));
            user.setPassword(generatePassword());
            user.setEnabled(true);
            user.setMustChangePassword(false);
            user.setIsAdmin(false);
            userMapper.insert(user);
            Teams team = createTeam(user.getUserId(), "", "", "");
            created.add(toView(user, team));
        }
        return created;
    }

    @Transactional
    public AdminUserView create(AdminUserRequest request) {
        validate(request, true);
        User user = new User();
        applyUser(user, request, true);
        try {
            userMapper.insert(user);
        } catch (org.springframework.dao.DuplicateKeyException exception) {
            throw new IllegalArgumentException("用户名已存在");
        }
        Teams team = null;
        if (!isAdmin(user)) {
            team = createTeam(user.getUserId(), request.getSchoolName(),
                    request.getTeacherName(), request.getContestantName());
            announcementFieldService.saveCustomValues(user.getUserId(), request.getCustomFields());
        }
        AdminUserView view = toView(user, team);
        view.setCustomFields(request.getCustomFields() == null
                ? new LinkedHashMap<>() : new LinkedHashMap<>(request.getCustomFields()));
        return view;
    }

    @Transactional
    public AdminUserView update(Integer userId, AdminUserRequest request) {
        User user = userMapper.selectById(userId);
        if (user == null) throw new IllegalArgumentException("账号不存在");
        if ("admin".equalsIgnoreCase(user.getUserName())) {
            throw new IllegalArgumentException("管理员账号请在修改密码页面维护");
        }
        validate(request, false);
        applyUser(user, request, false);
        try {
            userMapper.updateById(user);
        } catch (org.springframework.dao.DuplicateKeyException exception) {
            throw new IllegalArgumentException("用户名已存在");
        }
        Teams team = null;
        if (!isAdmin(user)) {
            if (hasProfileUpdate(request)) {
                team = upsertTeam(userId, request.getSchoolName(), request.getTeacherName(), request.getContestantName());
                announcementFieldService.saveCustomValues(userId, request.getCustomFields());
            } else {
                team = teamForUser(userId);
            }
        }
        AdminUserView view = toView(user, team);
        view.setCustomFields(request.getCustomFields() == null
                ? new LinkedHashMap<>() : new LinkedHashMap<>(request.getCustomFields()));
        return view;
    }

    @Transactional
    public int clearParticipants() {
        List<Integer> userIds = userMapper.selectList(Wrappers.<User>lambdaQuery()).stream()
                .filter(user -> !"admin".equalsIgnoreCase(user.getUserName()))
                .map(User::getUserId)
                .collect(Collectors.toList());
        if (userIds.isEmpty()) {
            return 0;
        }

        userTrainingAssignmentMapper.delete(Wrappers.<UserTrainingAssignment>lambdaQuery()
                .in(UserTrainingAssignment::getUserId, userIds));
        answerSheetMapper.delete(Wrappers.<AnswerSheet>lambdaQuery()
                .in(AnswerSheet::getUserId, userIds));
        scoreMapper.delete(Wrappers.<Score>lambdaQuery().in(Score::getUserId, userIds));
        teamsUserMapper.delete(Wrappers.<TeamsUser>lambdaQuery().in(TeamsUser::getUserId, userIds));
        trainUrlMapper.delete(Wrappers.<TrainUrl>lambdaQuery().in(TrainUrl::getUserId, userIds));
        announcementFieldService.deleteValuesForUsers(userIds);
        userMapper.deleteBatchIds(userIds);
        return userIds.size();
    }

    private Teams upsertTeam(Integer userId, String schoolName, String teacherName, String contestantName) {
        Teams team = teamForUser(userId);
        if (team == null) return createTeam(userId, schoolName, teacherName, contestantName);
        team.setTeamsName(normalizeProfile(schoolName));
        team.setTeamsTeacher(normalizeProfile(teacherName) + "|" + normalizeProfile(contestantName));
        teamsMapper.updateById(team);
        return team;
    }

    private Teams teamForUser(Integer userId) {
        TeamsUser link = teamsUserMapper.selectOne(Wrappers.<TeamsUser>lambdaQuery()
                .eq(TeamsUser::getUserId, userId).last("LIMIT 1"));
        return link == null ? null : teamsMapper.selectById(link.getTeamsId());
    }

    private boolean hasProfileUpdate(AdminUserRequest request) {
        return request.getSchoolName() != null || request.getTeacherName() != null
                || request.getContestantName() != null || request.getCustomFields() != null;
    }

    private Teams createTeam(Integer userId, String schoolName, String teacherName, String contestantName) {
        Teams team = new Teams();
        team.setTeamsName(normalizeProfile(schoolName));
        team.setTeamsTeacher(normalizeProfile(teacherName) + "|" + normalizeProfile(contestantName));
        team.setSpeedProgress(0);
        teamsMapper.insert(team);
        TeamsUser link = new TeamsUser();
        link.setTeamsId(team.getTeamsId());
        link.setUserId(userId);
        teamsUserMapper.insert(link);
        return team;
    }

    private void validate(AdminUserRequest request, boolean passwordRequired) {
        if (request == null || request.getUserName() == null || request.getUserName().trim().isEmpty()) {
            throw new IllegalArgumentException("用户名不能为空");
        }
        if (request.getUserName().trim().length() > 50) throw new IllegalArgumentException("用户名不能超过 50 个字符");
        if (passwordRequired && (request.getPassword() == null || request.getPassword().length() < 4)) {
            throw new IllegalArgumentException("密码至少需要 4 个字符");
        }
        if (request.getPassword() != null && !request.getPassword().isEmpty() && request.getPassword().length() < 4) {
            throw new IllegalArgumentException("密码至少需要 4 个字符");
        }
        normalizeProfile(request.getSchoolName());
        normalizeProfile(request.getContestantName());
        normalizeProfile(request.getTeacherName());
    }

    private void applyUser(User user, AdminUserRequest request, boolean creating) {
        user.setUserName(request.getUserName().trim());
        if (creating || (request.getPassword() != null && !request.getPassword().isEmpty())) {
            user.setPassword(request.getPassword());
        }
        if (creating) {
            user.setEnabled(request.getEnabled() == null || request.getEnabled());
            user.setMustChangePassword(false);
            user.setIsAdmin(Boolean.TRUE.equals(request.getAdmin()));
        } else {
            if (request.getEnabled() != null) user.setEnabled(request.getEnabled());
            if (request.getAdmin() != null) user.setIsAdmin(request.getAdmin());
        }
    }

    private String normalizeProfile(String value) {
        String normalized = value == null ? "" : value.trim();
        if (normalized.length() > 100) throw new IllegalArgumentException("学校、选手和老师信息不能超过 100 个字符");
        return normalized;
    }

    private boolean isAdmin(User user) {
        return Boolean.TRUE.equals(user.getIsAdmin()) || "admin".equalsIgnoreCase(user.getUserName());
    }

    String generatePassword() {
        List<Character> characters = new ArrayList<>();
        characters.add(randomCharacter(DIGITS));
        characters.add(randomCharacter(LOWERCASE));
        characters.add(randomCharacter(UPPERCASE));
        characters.add(randomCharacter(SYMBOLS));
        while (characters.size() < 6) {
            characters.add(randomCharacter(ALL_PASSWORD_CHARACTERS));
        }
        Collections.shuffle(characters, secureRandom);

        StringBuilder password = new StringBuilder(6);
        for (Character character : characters) {
            password.append(character);
        }
        return password.toString();
    }

    private char randomCharacter(char[] characters) {
        return characters[secureRandom.nextInt(characters.length)];
    }

    private int nextSequence(List<User> users) {
        int maximum = 0;
        for (User user : users) {
            Matcher matcher = USER_NAME_PATTERN.matcher(String.valueOf(user.getUserName()));
            if (matcher.matches()) {
                try {
                    maximum = Math.max(maximum, Integer.parseInt(matcher.group(1)));
                } catch (NumberFormatException ignored) {
                    // Values beyond the supported integer range do not affect valid generated names.
                }
            }
        }
        if (maximum == Integer.MAX_VALUE) {
            throw new IllegalArgumentException("账号序号超出支持范围");
        }
        return maximum + 1;
    }

    private void validateCount(Integer count) {
        if (count == null || count < 1 || count > MAX_BATCH_SIZE) {
            throw new IllegalArgumentException("新增数量必须在 1 到 " + MAX_BATCH_SIZE + " 之间");
        }
    }

    private AdminUserView toView(User user, Teams team) {
        AdminUserView view = new AdminUserView();
        view.setUserId(user.getUserId());
        view.setUserName(user.getUserName());
        view.setPassword(user.getPassword());
        view.setEnabled(user.getEnabled());
        view.setMustChangePassword(user.getMustChangePassword());
        view.setIsAdmin(user.getIsAdmin());
        boolean admin = Boolean.TRUE.equals(user.getIsAdmin())
                || "admin".equalsIgnoreCase(user.getUserName());
        Teams displayedTeam = admin ? null : team;
        view.setSchoolName(displayedTeam == null ? "--" : displayValue(displayedTeam.getTeamsName()));

        String[] people = displayedTeam == null || displayedTeam.getTeamsTeacher() == null
                ? new String[0]
                : displayedTeam.getTeamsTeacher().split("\\|", -1);
        view.setTeacherName(people.length > 0 ? displayValue(people[0]) : "--");
        view.setContestantName(people.length > 1 ? displayValue(people[1]) : "--");
        return view;
    }

    private String displayValue(String value) {
        return value == null || value.trim().isEmpty() ? "--" : value.trim();
    }
}
