package com.match.registry.service;

import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.TimeUnit;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

public class RegistryImportToolTest {
    @Rule
    public TemporaryFolder temp = new TemporaryFolder();

    @Test
    public void timeoutKillsProcessAndClosesBothOutputStreamsWithoutUnboundedJoin() throws Exception {
        FakeProcess process = new FakeProcess(false, false);
        RegistryImportTool tool = tool(process);

        long started = System.nanoTime();
        try {
            tool.importArchive(request());
            fail();
        } catch (RegistryImportTool.ImportException e) {
            assertEquals("IMPORT_TIMEOUT", e.getCode());
        }

        assertTrue(process.destroyed);
        assertTrue(process.stdout.closed);
        assertTrue(process.stderr.closed);
        assertTrue(TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - started) < 2000);
    }

    @Test
    public void interruptionKillsProcessClosesStreamsAndPreservesInterrupt() throws Exception {
        FakeProcess process = new FakeProcess(true, false);
        RegistryImportTool tool = tool(process);
        try {
            tool.importArchive(request());
            fail();
        } catch (RegistryImportTool.ImportException e) {
            assertEquals("IMPORT_INTERRUPTED", e.getCode());
            assertTrue(Thread.currentThread().isInterrupted());
        } finally {
            Thread.interrupted();
        }
        assertTrue(process.destroyed);
        assertTrue(process.stdout.closed);
        assertTrue(process.stderr.closed);
    }

    @Test
    public void reportsEachCopiedBlobOnceFromSkopeoOutput() throws Exception {
        String first = "Copying blob sha256:aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa\n";
        String duplicate = "Copying blob sha256:aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa\r";
        String second = "Copying blob sha256:bbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbb\n";
        FakeProcess process = new FakeProcess(false, true, first + duplicate + second, "");
        List<Integer> copied = new ArrayList<>();

        RegistryImportTool.ImportResult result = tool(process, true).importArchive(request(), copied::add);

        assertEquals("sha256:cccccccccccccccccccccccccccccccccccccccccccccccccccccccccccccccc", result.getDigest());
        assertEquals(Arrays.asList(1, 2), copied);
    }

    private RegistryImportTool tool(FakeProcess process) throws Exception {
        return tool(process, false);
    }

    private RegistryImportTool tool(FakeProcess process, boolean createDigest) throws Exception {
        Path root = temp.newFolder("import").toPath();
        Path ca = temp.newFile("ca.crt").toPath();
        Path username = temp.newFile("username").toPath();
        Path password = temp.newFile("password").toPath();
        Files.write(username, "writer".getBytes(StandardCharsets.UTF_8));
        Files.write(password, "secret".getBytes(StandardCharsets.UTF_8));
        return new RegistryImportTool("skopeo", "https://registry:5000", ca, username,
                password, root, 1, command -> {
                    if (createDigest) {
                        int index = command.indexOf("--digestfile");
                        Files.write(java.nio.file.Paths.get(command.get(index + 1)),
                                "sha256:cccccccccccccccccccccccccccccccccccccccccccccccccccccccccccccccc"
                                        .getBytes(StandardCharsets.US_ASCII));
                    }
                    return process;
                }, 10, 10);
    }

    private RegistryImportTool.ImportRequest request() throws Exception {
        Path archive = temp.newFile("archive.tar").toPath();
        return new RegistryImportTool.ImportRequest(archive, "courses/vision", "1.0.0");
    }

    private static final class TrackingInputStream extends ByteArrayInputStream {
        private boolean closed;

        private TrackingInputStream() {
            this("");
        }

        private TrackingInputStream(String content) {
            super(content.getBytes(StandardCharsets.UTF_8));
        }

        @Override
        public void close() throws IOException {
            closed = true;
            super.close();
        }
    }

    private static final class FakeProcess extends Process {
        private final boolean interrupt;
        private final boolean completes;
        private final TrackingInputStream stdout;
        private final TrackingInputStream stderr;
        private boolean destroyed;

        private FakeProcess(boolean interrupt, boolean completes) {
            this(interrupt, completes, "", "");
        }

        private FakeProcess(boolean interrupt, boolean completes, String stdout, String stderr) {
            this.interrupt = interrupt;
            this.completes = completes;
            this.stdout = new TrackingInputStream(stdout);
            this.stderr = new TrackingInputStream(stderr);
        }

        @Override public OutputStream getOutputStream() { return new ByteArrayOutputStream(); }
        @Override public InputStream getInputStream() { return stdout; }
        @Override public InputStream getErrorStream() { return stderr; }
        @Override public int waitFor() { return 0; }
        @Override public boolean waitFor(long timeout, TimeUnit unit) throws InterruptedException {
            if (interrupt) throw new InterruptedException("test");
            return completes;
        }
        @Override public int exitValue() { return 0; }
        @Override public void destroy() { destroyed = true; }
        @Override public Process destroyForcibly() { destroyed = true; return this; }
    }
}
