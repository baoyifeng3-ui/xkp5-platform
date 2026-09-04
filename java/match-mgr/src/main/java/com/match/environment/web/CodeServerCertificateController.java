package com.match.environment.web;

import com.match.security.RoleGuard;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.FileSystemResource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

@RestController
@RequestMapping("/user/code-server")
public class CodeServerCertificateController {
    private final RoleGuard roleGuard;
    private final Path rootCaFile;

    public CodeServerCertificateController(RoleGuard roleGuard,
                                           @Value("${xkp.code-server.root-ca-file:/etc/xkp/code-server-tls/rootCA.pem}") String rootCaFile) {
        this.roleGuard = roleGuard;
        this.rootCaFile = Paths.get(rootCaFile).toAbsolutePath().normalize();
    }

    @GetMapping("/root-ca.pem")
    public ResponseEntity<FileSystemResource> rootCa() {
        roleGuard.requireUser();
        if (!Files.isRegularFile(rootCaFile)) throw new IllegalStateException("平台根证书不可用");
        return ResponseEntity.ok().contentType(MediaType.parseMediaType("application/x-pem-file"))
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"rootCA.pem\"")
                .body(new FileSystemResource(rootCaFile));
    }
}
