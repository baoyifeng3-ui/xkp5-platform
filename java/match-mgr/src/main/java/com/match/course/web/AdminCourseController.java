package com.match.course.web;

import com.match.course.model.CourseResourceRequest;
import com.match.course.model.CourseUpsertRequest;
import com.match.course.model.CourseChapterRequest;
import com.match.course.model.CourseResourceBindingRequest;
import com.match.course.model.CourseResourceMetadataRequest;
import com.match.course.service.CourseAuthoringService;
import com.match.course.service.CourseLearningService;
import com.match.entity.User;
import com.match.security.RoleGuard;
import com.match.util.result.Response;
import com.match.util.result.ResponseResult;
import com.match.util.dfs.FastDFSClient;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.bind.annotation.RequestPart;
import java.util.Collections;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/admin/courses")
public class AdminCourseController {
    private final RoleGuard roleGuard;
    private final CourseAuthoringService authoringService;
    private CourseLearningService learningService;

    public AdminCourseController(RoleGuard roleGuard, CourseAuthoringService authoringService) {
        this.roleGuard = roleGuard;
        this.authoringService = authoringService;
    }

    @GetMapping
    public ResponseResult<Object> list() {
        roleGuard.requireAnyAdmin();
        return Response.makeOKRsp(authoringService.list());
    }

    @PostMapping
    public ResponseResult<Object> create(@RequestBody CourseUpsertRequest request) {
        User actor = roleGuard.requireAnyAdmin();
        return Response.makeOKRsp(authoringService.create(request, actor.getUserId()));
    }

    @PutMapping("/{courseId}")
    public ResponseResult<Object> update(@PathVariable String courseId,
                                         @RequestBody CourseUpsertRequest request) {
        roleGuard.requireAnyAdmin();
        return Response.makeOKRsp(authoringService.update(courseId, request));
    }

    @PostMapping("/{courseId}/enabled")
    public ResponseResult<Object> setEnabled(@PathVariable String courseId,
                                             @RequestParam boolean enabled) {
        roleGuard.requireAnyAdmin();
        return Response.makeOKRsp(authoringService.setEnabled(courseId, enabled));
    }

    @PostMapping("/content-images")
    public ResponseResult<Object> uploadContentImage(@RequestPart("file") MultipartFile file) {
        roleGuard.requireAnyAdmin();
        if(file==null||file.isEmpty())throw new IllegalArgumentException("课程图片不能为空");
        if(file.getSize()>5L*1024*1024)throw new IllegalArgumentException("课程图片不能超过 5 MiB");
        if(file.getContentType()==null||!file.getContentType().startsWith("image/"))throw new IllegalArgumentException("仅支持图片文件");
        String path=FastDFSClient.uploadFile(file);
        if(path==null||path.trim().isEmpty())throw new IllegalArgumentException("课程图片上传失败");
        return Response.makeOKRsp(Collections.singletonMap("url",FastDFSClient.getResAccessUrl(path)));
    }
    @org.springframework.beans.factory.annotation.Autowired public void setLearningService(CourseLearningService service){this.learningService=service;}

    @DeleteMapping("/{courseId}")
    public ResponseResult<Object> delete(@PathVariable String courseId) {
        roleGuard.requireAnyAdmin();
        authoringService.delete(courseId);
        return Response.makeOKRsp(null);
    }

    @GetMapping("/{courseId}/chapters") public ResponseResult<Object> chapters(@PathVariable String courseId){roleGuard.requireAnyAdmin();return Response.makeOKRsp(authoringService.chapters(courseId));}
    @PostMapping("/{courseId}/chapters") public ResponseResult<Object> createChapter(@PathVariable String courseId,@RequestBody CourseChapterRequest request){User actor=roleGuard.requireAnyAdmin();return Response.makeOKRsp(authoringService.createChapter(courseId,request,actor.getUserId()));}
    @PutMapping("/{courseId}/chapters/{chapterId}") public ResponseResult<Object> updateChapter(@PathVariable String courseId,@PathVariable String chapterId,@RequestBody CourseChapterRequest request){roleGuard.requireAnyAdmin();return Response.makeOKRsp(authoringService.updateChapter(courseId,chapterId,request));}
    @DeleteMapping("/{courseId}/chapters/{chapterId}") public ResponseResult<Object> deleteChapter(@PathVariable String courseId,@PathVariable String chapterId){roleGuard.requireAnyAdmin();authoringService.deleteChapter(courseId,chapterId);return Response.makeOKRsp(null);}
    @PutMapping("/{courseId}/resources/{fileId}/binding") public ResponseResult<Object> bindResource(@PathVariable String courseId,@PathVariable String fileId,@RequestBody CourseResourceBindingRequest request){roleGuard.requireAnyAdmin();return Response.makeOKRsp(authoringService.bindResource(courseId,fileId,request));}
    @PutMapping("/{courseId}/resources/{fileId}/metadata") public ResponseResult<Object> updateResourceMetadata(@PathVariable String courseId,@PathVariable String fileId,@RequestBody CourseResourceMetadataRequest request){roleGuard.requireAnyAdmin();return Response.makeOKRsp(authoringService.updateResourceMetadata(courseId,fileId,request));}
    @GetMapping("/resources/{resourceId}/preview-url") public ResponseResult<Object> preview(@PathVariable String resourceId){roleGuard.requireAnyAdmin();return Response.makeOKRsp(learningService.previewUrl(resourceId));}
    @GetMapping("/{courseId}/cover-url") public ResponseResult<Object> cover(@PathVariable String courseId){roleGuard.requireAnyAdmin();return Response.makeOKRsp(learningService.coverUrl(courseId));}

    @PostMapping("/{courseId}/resources")
    public ResponseResult<Object> addResource(@PathVariable String courseId,
                                              @RequestBody CourseResourceRequest request) {
        User actor = roleGuard.requireAnyAdmin();
        return Response.makeOKRsp(authoringService.addResource(courseId, request, actor.getUserId()));
    }

    @PostMapping("/resources/{resourceId}/enabled")
    public ResponseResult<Object> setResourceEnabled(@PathVariable String resourceId,
                                                     @RequestParam boolean enabled) {
        roleGuard.requireAnyAdmin();
        return Response.makeOKRsp(authoringService.setResourceEnabled(resourceId, enabled));
    }
}
