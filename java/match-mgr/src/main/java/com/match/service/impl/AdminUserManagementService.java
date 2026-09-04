package com.match.service.impl;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.match.dto.AdminUserView;
import com.match.dto.AdminUserRequest;
import com.match.dto.AdminUserBatchRequest;
import com.match.account.service.AccountSlotService;
import com.match.environment.persistence.ProcessingEnvironmentSlotRecord;
import com.match.environment.persistence.TrainingEnvironmentMapper;
import com.match.environment.persistence.ProcessingEnvironmentSlotMapper;
import com.match.agent.persistence.ProcessingAgentMapper;
import com.match.agent.persistence.ProcessingAgentRecord;
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
import com.match.security.UserRole;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.security.SecureRandom;
import java.time.LocalDateTime;
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
    private static final char[] INITIAL_PASSWORD_CHARACTERS =
            "23456789ABCDEFGHJKLMNPQRSTUVWXYZabcdefghijkmnopqrstuvwxyz".toCharArray();

    private final UserMapper userMapper;
    private final TeamsUserMapper teamsUserMapper;
    private final TeamsMapper teamsMapper;
    private final AnswerSheetMapper answerSheetMapper;
    private final ScoreMapper scoreMapper;
    private final TrainUrlMapper trainUrlMapper;
    private final UserTrainingAssignmentMapper userTrainingAssignmentMapper;
    private final AnnouncementFieldService announcementFieldService;
    private final SecureRandom secureRandom;
    private AccountSlotService accountSlotService;
    private TrainingEnvironmentMapper trainingEnvironmentMapper;
    private ProcessingEnvironmentSlotMapper slotMapper;
    private ProcessingAgentMapper processingAgentMapper;

    @Autowired public void setAccountSlotService(AccountSlotService value) { this.accountSlotService = value; }
    @Autowired public void setTrainingEnvironmentMapper(TrainingEnvironmentMapper value) { this.trainingEnvironmentMapper = value; }
    @Autowired public void setSlotMapper(ProcessingEnvironmentSlotMapper value){this.slotMapper=value;}
    @Autowired public void setProcessingAgentMapper(ProcessingAgentMapper value){this.processingAgentMapper=value;}

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
                Wrappers.<User>lambdaQuery().orderByAsc(User::getUserId)).stream()
                .filter(user -> roleOf(user) == UserRole.USER)
                .collect(Collectors.toList());
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
        Map<Integer, Map<String, String>> customValues = announcementFieldService.valuesByUserIdsByName(userIds);
        for (User user : users) {
            AdminUserView view = toView(user, teamsById.get(teamIdByUserId.get(user.getUserId())));
            view.setCustomFields(customValues.getOrDefault(user.getUserId(), new LinkedHashMap<>()));
            if(slotMapper!=null){List<ProcessingEnvironmentSlotRecord> placements=slotMapper.selectByUser(user.getUserId());if(placements.size()==1){ProcessingEnvironmentSlotRecord slot=placements.get(0);view.setSlotId(slot.getSlotId());view.setSlotNumber(slot.getSlotNumber());view.setAgentId(slot.getAgentId());ProcessingAgentRecord agent=processingAgentMapper==null?null:processingAgentMapper.selectForManagement(slot.getAgentId());if(agent!=null){view.setAgentName(agent.getDisplayName());view.setPrimaryIp(agent.getPrimaryIp());view.setPlacementReady(Boolean.TRUE.equals(user.getEnabled())&&Boolean.TRUE.equals(agent.getEnabled())&&agent.getRemovedAt()==null&&agent.getLastSeenAt()!=null&&agent.getLastSeenAt().isAfter(LocalDateTime.now(java.time.ZoneOffset.UTC).minusSeconds(30)));}}}
            if(trainingEnvironmentMapper!=null)view.setEnvironmentCount(trainingEnvironmentMapper.selectCount(Wrappers.<com.match.environment.persistence.TrainingEnvironmentRecord>lambdaQuery().eq(com.match.environment.persistence.TrainingEnvironmentRecord::getUserId,user.getUserId())));
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
            user.setRole(UserRole.USER.name());
            userMapper.insert(user);
            Teams team = createTeam(user.getUserId(), "", "", "");
            created.add(toView(user, team));
        }
        return created;
    }

    @Transactional
    public List<AdminUserView> createBatch(AdminUserBatchRequest request, int actorId) {
        if (request == null) throw new IllegalArgumentException("批量创建参数不能为空");
        validateCount(request.getCount());
        boolean assignSlots = request.getAgentIds() != null && !request.getAgentIds().isEmpty();
        List<ProcessingEnvironmentSlotRecord> freeSlots = !assignSlots
                ? Collections.emptyList() : accountSlotService.lockFreeSlots(request.getAgentIds());
        List<User> existingUsers = userMapper.selectList(
                Wrappers.<User>lambdaQuery().orderByAsc(User::getUserId));
        int nextSequence = nextSequence(existingUsers);
        List<AdminUserView> created = new ArrayList<>();
        for (int index = 0; index < request.getCount(); index++) {
            String initialPassword = generateInitialPassword();
            User user = new User();
            user.setUserName("user" + (nextSequence + index));
            user.setPassword(initialPassword);
            user.setRemark(request.getRemark());
            user.setEnabled(true);
            user.setMustChangePassword(true);
            user.setIsAdmin(false);
            user.setRole(UserRole.USER.name());
            userMapper.insert(user);
            Teams team = createTeam(user.getUserId(), "", "", "");
            announcementFieldService.saveCustomValues(user.getUserId(), request.getCustomFields(), actorId);
            AdminUserView view = toView(user, team);
            view.setInitialPassword(initialPassword);
            if (assignSlots && index < freeSlots.size()) {
                ProcessingEnvironmentSlotRecord slot = freeSlots.get(index);
                accountSlotService.bind(user.getUserId(), slot.getSlotId(), actorId);
                view.setSlotId(slot.getSlotId()); view.setSlotNumber(slot.getSlotNumber());
                view.setAgentId(slot.getAgentId()); view.setPlacementReady(true);
            }
            view.setCustomFields(request.getCustomFields() == null
                    ? new LinkedHashMap<>() : new LinkedHashMap<>(request.getCustomFields()));
            created.add(view);
        }
        return created;
    }

    @Transactional
    public AdminUserView create(AdminUserRequest request) {
        return create(request, 0);
    }

    @Transactional
    public AdminUserView create(AdminUserRequest request, int actorId) {
        validate(request, true);
        User user = new User();
        applyUser(user, request, true);
        try {
            userMapper.insert(user);
        } catch (org.springframework.dao.DuplicateKeyException exception) {
            throw new IllegalArgumentException("用户名已存在");
        }
        if (request.getSlotId() != null && !request.getSlotId().trim().isEmpty())
            accountSlotService.bind(user.getUserId(), request.getSlotId(), actorId);
        Teams team = null;
        if (!isAdmin(user)) {
            team = createTeam(user.getUserId(), request.getSchoolName(),
                    request.getTeacherName(), request.getContestantName());
            announcementFieldService.saveCustomValues(user.getUserId(), request.getCustomFields(), actorId);
        }
        AdminUserView view = toView(user, team);
        view.setCustomFields(request.getCustomFields() == null
                ? new LinkedHashMap<>() : new LinkedHashMap<>(request.getCustomFields()));
        return view;
    }

    @Transactional
    public AdminUserView update(Integer userId, AdminUserRequest request) {
        return update(userId, request, 0);
    }

    @Transactional
    public AdminUserView update(Integer userId, AdminUserRequest request, int actorId) {
        User user = userMapper.selectById(userId);
        if (user == null) throw new IllegalArgumentException("账号不存在");
        if (roleOf(user) != UserRole.USER) {
            throw new IllegalArgumentException("平台管理员账号不能在比赛账号中维护");
        }
        validate(request, false);
        applyUser(user, request, false);
        try {
            userMapper.updateById(user);
        } catch (org.springframework.dao.DuplicateKeyException exception) {
            throw new IllegalArgumentException("用户名已存在");
        }
        if (request.getSlotId() != null && !request.getSlotId().trim().isEmpty()) {
            List<ProcessingEnvironmentSlotRecord> currentSlots = slotMapper == null ? Collections.emptyList() : slotMapper.selectByUserForUpdate(userId);
            String currentSlotId = currentSlots.size() == 1 ? currentSlots.get(0).getSlotId() : null;
            if (!request.getSlotId().equals(currentSlotId)) {
                Integer count = trainingEnvironmentMapper == null ? 0 : trainingEnvironmentMapper.selectCount(Wrappers.<com.match.environment.persistence.TrainingEnvironmentRecord>lambdaQuery().eq(com.match.environment.persistence.TrainingEnvironmentRecord::getUserId, userId));
                if (count != null && count > 0) throw new IllegalArgumentException("账号已有环境，请使用更换槽位迁移功能");
                if (accountSlotService != null) { accountSlotService.unbind(userId); accountSlotService.bind(userId, request.getSlotId(), actorId); }
            }
        }
        Teams team = null;
        if (!isAdmin(user)) {
            if (hasProfileUpdate(request)) {
                team = upsertTeam(userId, request.getSchoolName(), request.getTeacherName(), request.getContestantName());
                announcementFieldService.saveCustomValues(userId, request.getCustomFields(), actorId);
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
                .filter(user -> roleOf(user) == UserRole.USER)
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
        if (Boolean.TRUE.equals(request.getAdmin())) {
            throw new IllegalArgumentException("比赛账号不能授予平台管理员权限");
        }
        normalizeProfile(request.getSchoolName());
        normalizeProfile(request.getContestantName());
        normalizeProfile(request.getTeacherName());
    }

    private void applyUser(User user, AdminUserRequest request, boolean creating) {
        user.setUserName(request.getUserName().trim());
        user.setRemark(request.getRemark());
        if (creating) user.setMustCompleteProfile(Boolean.TRUE.equals(request.getRequireProfile()));
        if (creating || (request.getPassword() != null && !request.getPassword().isEmpty())) {
            user.setPassword(request.getPassword());
        }
        if (creating) {
            user.setEnabled(request.getEnabled() == null || request.getEnabled());
            user.setMustChangePassword(false);
            user.setIsAdmin(false);
            user.setRole(UserRole.USER.name());
        } else {
            if (request.getEnabled() != null) user.setEnabled(request.getEnabled());
        }
    }

    private String normalizeProfile(String value) {
        String normalized = value == null ? "" : value.trim();
        if (normalized.length() > 100) throw new IllegalArgumentException("学校、选手和老师信息不能超过 100 个字符");
        return normalized;
    }

    private boolean isAdmin(User user) {
        return roleOf(user) != UserRole.USER;
    }

    private UserRole roleOf(User user) {
        return UserRole.resolve(user.getRole(), user.getIsAdmin(), user.getUserName());
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

    String generateInitialPassword() {
        StringBuilder password = new StringBuilder(6);
        while (password.length() < 6) {
            password.append(INITIAL_PASSWORD_CHARACTERS[
                    secureRandom.nextInt(INITIAL_PASSWORD_CHARACTERS.length)]);
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
        view.setRemark(user.getRemark());
        view.setEnvironmentCount(0);
        view.setPlacementReady(false);
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

    @Transactional
    public int setEnabled(List<Integer> userIds, boolean enabled) {
        if (userIds == null || userIds.isEmpty()) return 0;
        int updated = 0;
        for (User user : userMapper.selectBatchIds(userIds)) {
            if (roleOf(user) != UserRole.USER) continue;
            user.setEnabled(enabled); userMapper.updateById(user); updated++;
        }
        return updated;
    }

    @Transactional
    public int deleteUsers(List<Integer> userIds) {
        if (userIds == null || userIds.isEmpty()) return 0;
        List<User> users = userMapper.selectBatchIds(userIds).stream()
                .filter(user -> roleOf(user) == UserRole.USER).collect(Collectors.toList());
        for (User user : users) {
            Integer count = trainingEnvironmentMapper.selectCount(
                    Wrappers.<com.match.environment.persistence.TrainingEnvironmentRecord>lambdaQuery()
                            .eq(com.match.environment.persistence.TrainingEnvironmentRecord::getUserId,
                                    user.getUserId()));
            if (count != null && count > 0) throw new IllegalArgumentException("账号存在实训环境，请先删除环境");
        }
        List<Integer> ids = users.stream().map(User::getUserId).collect(Collectors.toList());
        if (ids.isEmpty()) return 0;
        userTrainingAssignmentMapper.delete(Wrappers.<UserTrainingAssignment>lambdaQuery().in(UserTrainingAssignment::getUserId, ids));
        answerSheetMapper.delete(Wrappers.<AnswerSheet>lambdaQuery().in(AnswerSheet::getUserId, ids));
        scoreMapper.delete(Wrappers.<Score>lambdaQuery().in(Score::getUserId, ids));
        teamsUserMapper.delete(Wrappers.<TeamsUser>lambdaQuery().in(TeamsUser::getUserId, ids));
        trainUrlMapper.delete(Wrappers.<TrainUrl>lambdaQuery().in(TrainUrl::getUserId, ids));
        announcementFieldService.deleteValuesForUsers(ids);
        for (Integer id : ids) accountSlotService.unbind(id);
        userMapper.deleteBatchIds(ids);
        return ids.size();
    }

    private String displayValue(String value) {
        return value == null || value.trim().isEmpty() ? "--" : value.trim();
    }
}
