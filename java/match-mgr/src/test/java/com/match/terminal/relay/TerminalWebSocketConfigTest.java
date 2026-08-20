package com.match.terminal.relay;

import com.match.agent.service.AgentCredentialService;
import com.match.terminal.service.TerminalSessionService;
import org.junit.Test;
import org.springframework.web.socket.config.annotation.WebSocketHandlerRegistration;
import org.springframework.web.socket.config.annotation.WebSocketHandlerRegistry;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.socket.server.standard.ServletServerContainerFactoryBean;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler;

import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.Executor;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.fail;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.never;

public class TerminalWebSocketConfigTest {
    @Test
    public void registersExactEndpointsWithExplicitConfiguredOrigins() {
        TerminalSessionService sessions = mock(TerminalSessionService.class);
        AgentCredentialService credentials = mock(AgentCredentialService.class);
        TerminalWebSocketHandler handler = mock(TerminalWebSocketHandler.class);
        TerminalWebSocketConfig config = new TerminalWebSocketConfig(sessions, credentials,
                "https://management.example,https://backup.example");
        WebSocketHandlerRegistry registry = mock(WebSocketHandlerRegistry.class);
        WebSocketHandlerRegistration browser = mock(WebSocketHandlerRegistration.class);
        WebSocketHandlerRegistration agent = mock(WebSocketHandlerRegistration.class);
        when(registry.addHandler(handler, TerminalWebSocketConfig.BROWSER_PATH)).thenReturn(browser);
        when(registry.addHandler(handler, TerminalWebSocketConfig.AGENT_PATH)).thenReturn(agent);
        when(browser.addInterceptors(any())).thenReturn(browser);
        when(agent.addInterceptors(any())).thenReturn(agent);

        config.registerWebSocketHandlers(registry, handler);

        verify(registry).addHandler(handler, "/terminal/v1/browser/{sessionId}");
        verify(registry).addHandler(handler, "/terminal/v1/agent/{sessionId}");
        verify(browser).setAllowedOrigins(
                "https://management.example", "https://backup.example");
        verify(agent).setAllowedOrigins(
                "https://management.example", "https://backup.example");
    }

    @Test
    public void exposesContainerLimitsAndRejectsWildcardOriginConfiguration() {
        assertEquals(64 * 1024, TerminalWebSocketConfig.BINARY_BUFFER_BYTES);
        assertEquals(4 * 1024, TerminalWebSocketConfig.TEXT_BUFFER_BYTES);
        TerminalWebSocketConfig valid = new TerminalWebSocketConfig(
                mock(TerminalSessionService.class), mock(AgentCredentialService.class),
                "https://management.example");
        ServletServerContainerFactoryBean container = valid.terminalWebSocketContainer();
        assertEquals(Integer.valueOf(64 * 1024),
                ReflectionTestUtils.getField(container, "maxBinaryMessageBufferSize"));
        assertEquals(Integer.valueOf(4 * 1024),
                ReflectionTestUtils.getField(container, "maxTextMessageBufferSize"));
        try {
            new TerminalWebSocketConfig(mock(TerminalSessionService.class),
                    mock(AgentCredentialService.class), "*");
        } catch (IllegalArgumentException expected) {
            return;
        }
        fail("wildcard terminal origin was accepted");
    }

    @Test
    public void sharedPeriodicSchedulerHasExplicitShutdownLifecycle() throws Exception {
        TerminalWebSocketConfig config = new TerminalWebSocketConfig(
                mock(TerminalSessionService.class), mock(AgentCredentialService.class),
                "https://management.example");
        ScheduledExecutorService scheduler = config.terminalRelayScheduler();
        ThreadPoolTaskExecutor writers = config.terminalWriterExecutor();
        writers.initialize();
        CountDownLatch writerRan = new CountDownLatch(1);
        writers.execute(writerRan::countDown);
        assertEquals(true, writerRan.await(5, TimeUnit.SECONDS));
        config.terminalRelayCoordinator(writers, scheduler);
        assertEquals(false, scheduler.isShutdown());
        assertEquals(1, ((ScheduledThreadPoolExecutor) scheduler).getQueue().size());

        scheduler.shutdownNow();
        writers.shutdown();

        assertEquals(true, scheduler.awaitTermination(5, TimeUnit.SECONDS));
        assertEquals(true, scheduler.isShutdown());
        assertEquals(true, writers.getThreadPoolExecutor().isShutdown());
    }

    @Test
    public void relayMaintenanceUsesFixedDelayWithoutCatchUpScheduling() {
        TerminalWebSocketConfig config = new TerminalWebSocketConfig(
                mock(TerminalSessionService.class), mock(AgentCredentialService.class),
                "https://management.example");
        ScheduledExecutorService scheduler = mock(ScheduledExecutorService.class);

        config.terminalRelayCoordinator((Executor) Runnable::run, scheduler);

        verify(scheduler).scheduleWithFixedDelay(any(Runnable.class),
                org.mockito.ArgumentMatchers.eq(250L), org.mockito.ArgumentMatchers.eq(250L),
                org.mockito.ArgumentMatchers.eq(TimeUnit.MILLISECONDS));
        verify(scheduler, never()).scheduleAtFixedRate(any(Runnable.class),
                org.mockito.ArgumentMatchers.anyLong(), org.mockito.ArgumentMatchers.anyLong(),
                any(TimeUnit.class));
    }

    @Test
    public void applicationTaskSchedulerIsConcreteAndBounded() {
        TerminalWebSocketConfig config = new TerminalWebSocketConfig(
                mock(TerminalSessionService.class), mock(AgentCredentialService.class),
                "https://management.example");

        ThreadPoolTaskScheduler scheduler = config.taskScheduler();
        assertEquals(true, scheduler.getScheduledExecutor() != null);

        scheduler.shutdown();
    }
}
