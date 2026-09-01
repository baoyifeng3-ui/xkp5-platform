package com.match.course.web;

import com.match.course.model.CourseProgressRequest;
import com.match.course.service.CourseLearningService;
import com.match.course.persistence.CourseChapterMapper;
import com.match.security.RoleGuard;
import com.match.util.result.Response;
import com.match.util.result.ResponseResult;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/user/courses")
public class UserCourseController {
    private final RoleGuard roleGuard;
    private final CourseLearningService learningService;
    private CourseChapterMapper chapterMapper;
    private com.match.environment.service.ActiveClassSessionService classSessionService;

    public UserCourseController(RoleGuard roleGuard, CourseLearningService learningService) {
        this.roleGuard = roleGuard;
        this.learningService = learningService;
    }
    @org.springframework.beans.factory.annotation.Autowired public void setChapterMapper(CourseChapterMapper mapper){this.chapterMapper=mapper;}
    @org.springframework.beans.factory.annotation.Autowired public void setClassSessionService(com.match.environment.service.ActiveClassSessionService value){this.classSessionService=value;}

    @GetMapping("/{courseId}/chapters") public ResponseResult<Object> chapters(@PathVariable String courseId){roleGuard.requireUser();return Response.makeOKRsp(chapterMapper.selectByCourse(courseId));}

    @GetMapping
    public ResponseResult<Object> list() {
        roleGuard.requireUser();
        java.util.List<com.match.course.model.CourseView> courses=learningService.visibleCourses();
        com.match.environment.persistence.ActiveClassSessionRecord session=classSessionService==null?null:classSessionService.current();
        if(session!=null&&Boolean.TRUE.equals(session.getActive())&&session.getCourseId()!=null)courses=courses.stream().filter(item->session.getCourseId().equals(item.getCourse().getCourseId())).collect(java.util.stream.Collectors.toList());
        return Response.makeOKRsp(courses);
    }

    @PostMapping("/progress")
    public ResponseResult<Object> progress(@RequestBody CourseProgressRequest request) {
        Integer userId = roleGuard.requireUser().getUserId();
        learningService.recordProgress(userId, request.getResourceId(), request.getProgressKind(),
                request.getProgressValue(), request.getCompleted(), request.getCurrentValue(),
                request.getTotalValue(), request.getLastPosition());
        return Response.makeOKRsp(null);
    }

    @GetMapping("/progress")
    public ResponseResult<Object> progress() {
        roleGuard.requireUser();
        return Response.makeOKRsp(learningService.progress(roleGuard.requireUser().getUserId()));
    }

    @GetMapping("/resources/{resourceId}/preview-url")
    public ResponseResult<Object> previewUrl(@org.springframework.web.bind.annotation.PathVariable String resourceId) {
        roleGuard.requireUser();
        return Response.makeOKRsp(learningService.previewUrl(resourceId));
    }

    @GetMapping("/{courseId}/cover-url")
    public ResponseResult<Object> coverUrl(@PathVariable String courseId) {
        roleGuard.requireUser();
        return Response.makeOKRsp(learningService.coverUrl(courseId));
    }
}
