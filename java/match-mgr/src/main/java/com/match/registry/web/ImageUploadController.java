package com.match.registry.web;

import com.match.registry.dto.ImageUploadCreateRequest;
import com.match.registry.dto.ImageUploadChunkView;
import com.match.registry.service.ImageUploadService;
import com.match.security.RoleGuard;
import com.match.util.result.Response;
import com.match.util.result.ResponseResult;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/admin/image-uploads")
public class ImageUploadController {
    private final RoleGuard roleGuard;
    private final ImageUploadService service;
    public ImageUploadController(RoleGuard roleGuard, ImageUploadService service) { this.roleGuard = roleGuard; this.service = service; }
    @PostMapping public ResponseResult<Object> create(@RequestBody ImageUploadCreateRequest request) { com.match.entity.User u = roleGuard.requireSuperAdmin(); return Response.makeOKRsp(service.create("SUPER_ADMIN", request, u.getUserId())); }
    @PutMapping("/{uploadId}/chunks/{chunkIndex}") public ResponseResult<Object> chunk(@PathVariable String uploadId, @PathVariable int chunkIndex, @RequestParam("file") MultipartFile file, @RequestHeader(value="X-Chunk-SHA256", required=false) String checksum) { com.match.entity.User u = roleGuard.requireSuperAdmin(); return Response.makeOKRsp(service.putChunk("SUPER_ADMIN", uploadId, chunkIndex, file, checksum)); }
    @PostMapping("/{uploadId}/complete") public ResponseResult<Object> complete(@PathVariable String uploadId) { roleGuard.requireSuperAdmin(); return Response.makeOKRsp(service.requestCompletion("SUPER_ADMIN", uploadId)); }
    @PostMapping("/{uploadId}/cancel") public ResponseResult<Object> cancel(@PathVariable String uploadId) { roleGuard.requireSuperAdmin(); return Response.makeOKRsp(service.cancel("SUPER_ADMIN", uploadId)); }
    @GetMapping("/{uploadId}") public ResponseResult<Object> status(@PathVariable String uploadId) { com.match.entity.User u = roleGuard.requireAnyAdmin(); return Response.makeOKRsp(service.status(roleGuard.roleOf(u).name(), uploadId)); }
    @GetMapping("/artifact/{artifactId}") public ResponseResult<Object> statusByArtifact(@PathVariable String artifactId) { roleGuard.requireSuperAdmin(); return Response.makeOKRsp(service.statusByArtifact("SUPER_ADMIN", artifactId)); }
}
