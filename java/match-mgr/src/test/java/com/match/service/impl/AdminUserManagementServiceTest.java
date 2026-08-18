package com.match.service.impl;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.match.dto.AdminUserView;
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
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;
import org.apache.ibatis.builder.MapperBuilderAssistant;

import java.security.SecureRandom;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@RunWith(MockitoJUnitRunner.class)
public class AdminUserManagementServiceTest {
    @Mock private UserMapper userMapper;
    @Mock private TeamsUserMapper teamsUserMapper;
    @Mock private TeamsMapper teamsMapper;
    @Mock private AnswerSheetMapper answerSheetMapper;
    @Mock private ScoreMapper scoreMapper;
    @Mock private TrainUrlMapper trainUrlMapper;
    @Mock private UserTrainingAssignmentMapper userTrainingAssignmentMapper;
    @Mock private AnnouncementFieldService announcementFieldService;

    private AdminUserManagementService service;

    @Before
    public void setUp() {
        MapperBuilderAssistant assistant = new MapperBuilderAssistant(new MybatisConfiguration(), "test");
        assistant.setCurrentNamespace("com.match.test");
        TableInfoHelper.initTableInfo(assistant, User.class);
        TableInfoHelper.initTableInfo(assistant, TeamsUser.class);
        TableInfoHelper.initTableInfo(assistant, UserTrainingAssignment.class);
        TableInfoHelper.initTableInfo(assistant, AnswerSheet.class);
        TableInfoHelper.initTableInfo(assistant, Score.class);
        TableInfoHelper.initTableInfo(assistant, TrainUrl.class);
        service = new AdminUserManagementService(userMapper, teamsUserMapper, teamsMapper,
                answerSheetMapper, scoreMapper, trainUrlMapper, userTrainingAssignmentMapper,
                announcementFieldService, new SecureRandom());
        when(announcementFieldService.valuesByUserIds(any())).thenReturn(Collections.emptyMap());
    }

    @Test
    public void createsUsersAfterLargestStandardSequence() {
        when(userMapper.selectList(any())).thenReturn(Arrays.asList(
                user(1, "admin", "admin"),
                user(2, "user3", "old"),
                user(3, "judge99", "old"),
                user(4, "user10", "old")));

        List<AdminUserView> created = service.createBatch(2);

        ArgumentCaptor<User> captor = ArgumentCaptor.forClass(User.class);
        verify(userMapper, org.mockito.Mockito.times(2)).insert(captor.capture());
        assertEquals("user11", captor.getAllValues().get(0).getUserName());
        assertEquals("user12", captor.getAllValues().get(1).getUserName());
        assertEquals(2, created.size());
        for (User value : captor.getAllValues()) {
            assertPasswordPolicy(value.getPassword());
            assertTrue(value.getEnabled());
            assertFalse(value.getIsAdmin());
        }
    }

    @Test
    public void rejectsInvalidBatchSize() {
        try {
            service.createBatch(0);
            fail("数量为零时应拒绝创建");
        } catch (IllegalArgumentException exception) {
            assertTrue(exception.getMessage().contains("1 到 500"));
        }
        verify(userMapper, never()).insert(any(User.class));
    }

    @Test
    public void returnsTeamFieldsUsingHomepageMapping() {
        User participant = user(2, "user1", "Ab1!xy");
        when(userMapper.selectList(any())).thenReturn(Collections.singletonList(participant));
        TeamsUser link = new TeamsUser();
        link.setUserId(2);
        link.setTeamsId(7);
        when(teamsUserMapper.selectList(any())).thenReturn(Collections.singletonList(link));
        Teams team = new Teams();
        team.setTeamsId(7);
        team.setTeamsName("示例学校");
        team.setTeamsTeacher("王老师|李同学");
        when(teamsMapper.selectBatchIds(any())).thenReturn(Collections.singletonList(team));

        AdminUserView view = service.listUsers().get(0);

        assertEquals("Ab1!xy", view.getPassword());
        assertEquals("示例学校", view.getSchoolName());
        assertEquals("李同学", view.getContestantName());
        assertEquals("王老师", view.getTeacherName());
    }

    @Test
    public void returnsPlaceholdersForAdminEvenWithTeam() {
        User admin = user(1, "admin", "admin");
        when(userMapper.selectList(any())).thenReturn(Collections.singletonList(admin));
        TeamsUser link = new TeamsUser();
        link.setUserId(1);
        link.setTeamsId(7);
        when(teamsUserMapper.selectList(any())).thenReturn(Collections.singletonList(link));
        Teams team = new Teams();
        team.setTeamsId(7);
        team.setTeamsName("示例学校");
        team.setTeamsTeacher("王老师|李同学");
        when(teamsMapper.selectBatchIds(any())).thenReturn(Collections.singletonList(team));

        AdminUserView view = service.listUsers().get(0);

        assertEquals("--", view.getSchoolName());
        assertEquals("--", view.getContestantName());
        assertEquals("--", view.getTeacherName());
    }

    @Test
    public void returnsPlaceholdersWithoutTeam() {
        when(userMapper.selectList(any())).thenReturn(Collections.singletonList(user(2, "user1", "Ab1!xy")));
        when(teamsUserMapper.selectList(any())).thenReturn(Collections.emptyList());

        AdminUserView view = service.listUsers().get(0);

        assertEquals("--", view.getSchoolName());
        assertEquals("--", view.getContestantName());
        assertEquals("--", view.getTeacherName());
    }

    @Test
    public void clearsEveryUserExceptNamedAdminAndTheirRelations() {
        User admin = user(1, "admin", "admin");
        User participant = user(2, "user1", "Ab1!xy");
        User extraAdmin = user(3, "judge", "Ab1!xy");
        extraAdmin.setIsAdmin(true);
        when(userMapper.selectList(any())).thenReturn(Arrays.asList(admin, participant, extraAdmin));

        int deleted = service.clearParticipants();

        assertEquals(2, deleted);
        verify(userTrainingAssignmentMapper).delete(any());
        verify(answerSheetMapper).delete(any());
        verify(scoreMapper).delete(any());
        verify(teamsUserMapper).delete(any());
        verify(trainUrlMapper).delete(any());
        verify(userMapper).deleteBatchIds(Arrays.asList(2, 3));
    }

    private User user(int id, String name, String password) {
        User user = new User();
        user.setUserId(id);
        user.setUserName(name);
        user.setPassword(password);
        user.setEnabled(true);
        user.setMustChangePassword(false);
        user.setIsAdmin("admin".equals(name));
        return user;
    }

    private void assertPasswordPolicy(String password) {
        assertEquals(6, password.length());
        assertTrue(password.matches(".*[0-9].*"));
        assertTrue(password.matches(".*[a-z].*"));
        assertTrue(password.matches(".*[A-Z].*"));
        assertTrue(password.matches(".*[!@#$%^&*].*"));
    }
}
