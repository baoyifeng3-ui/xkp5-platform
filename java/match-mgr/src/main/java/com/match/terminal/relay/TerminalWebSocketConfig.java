package com.match.terminal.relay;

import com.match.agent.service.AgentCredentialService;
import com.match.terminal.service.TerminalSessionService;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.web.socket.config.annotation.EnableWebSocket;
import org.springframework.web.socket.config.annotation.WebSocketConfigurer;
import org.springframework.web.socket.config.annotation.WebSocketHandlerRegistration;
import org.springframework.web.socket.config.annotation.WebSocketHandlerRegistry;
import org.springframework.web.socket.server.standard.ServletServerContainerFactoryBean;

import java.util.Arrays;
import java.util.concurrent.Executor;

@Configuration
@EnableWebSocket
public class TerminalWebSocketConfig implements WebSocketConfigurer {
    public static final String BROWSER_PATH = "/terminal/v1/browser/{sessionId}";
    public static final String AGENT_PATH = "/terminal/v1/agent/{sessionId}";
    public static final int BINARY_BUFFER_BYTES = 64 * 1024;
    public static final int TEXT_BUFFER_BYTES = 4 * 1024;

    private final TerminalSessionService sessions;
    private final AgentCredentialService credentials;
    private final String[] allowedOrigins;

    public TerminalWebSocketConfig(TerminalSessionService sessions,
                                   AgentCredentialService credentials,
                                   @Value("${match.allowed-origins}") String allowedOrigins) {
        this.sessions = sessions;
        this.credentials = credentials;
        this.allowedOrigins = parseOrigins(allowedOrigins);
        TerminalHandshakeInterceptor.browser(sessions, this.allowedOrigins);
    }

    @Bean(name = "terminalWriterExecutor")
    public ThreadPoolTaskExecutor terminalWriterExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(4);
        executor.setMaxPoolSize(4);
        executor.setQueueCapacity(1024);
        executor.setAllowCoreThreadTimeOut(true);
        executor.setKeepAliveSeconds(30);
        executor.setThreadNamePrefix("terminal-relay-");
        executor.setWaitForTasksToCompleteOnShutdown(false);
        return executor;
    }

    @Bean
    public TerminalRelayCoordinator terminalRelayCoordinator(
            @Qualifier("terminalWriterExecutor") Executor executor) {
        return new TerminalRelayCoordinator(sessions, executor, System::nanoTime);
    }

    @Bean
    public TerminalWebSocketHandler terminalWebSocketHandler(TerminalRelayCoordinator coordinator) {
        return new TerminalWebSocketHandler(coordinator);
    }

    @Bean
    public ServletServerContainerFactoryBean terminalWebSocketContainer() {
        ServletServerContainerFactoryBean container = new ServletServerContainerFactoryBean();
        container.setMaxBinaryMessageBufferSize(BINARY_BUFFER_BYTES);
        container.setMaxTextMessageBufferSize(TEXT_BUFFER_BYTES);
        return container;
    }

    @Override
    public void registerWebSocketHandlers(WebSocketHandlerRegistry registry) {
        registerWebSocketHandlers(registry,
                terminalWebSocketHandler(terminalRelayCoordinator(terminalWriterExecutor())));
    }

    void registerWebSocketHandlers(WebSocketHandlerRegistry registry,
                                   TerminalWebSocketHandler handler) {
        WebSocketHandlerRegistration browser = registry.addHandler(handler, BROWSER_PATH);
        browser.addInterceptors(TerminalHandshakeInterceptor.browser(sessions, allowedOrigins));
        browser.setAllowedOrigins(Arrays.copyOf(allowedOrigins, allowedOrigins.length));

        WebSocketHandlerRegistration agent = registry.addHandler(handler, AGENT_PATH);
        agent.addInterceptors(TerminalHandshakeInterceptor.agent(credentials, sessions));
        agent.setAllowedOrigins(Arrays.copyOf(allowedOrigins, allowedOrigins.length));
    }

    private String[] parseOrigins(String configured) {
        if (configured == null) {
            throw new IllegalArgumentException("Terminal browser origins are required");
        }
        String[] values = Arrays.stream(configured.split(",", -1))
                .map(String::trim)
                .toArray(String[]::new);
        if (values.length == 0 || Arrays.stream(values).anyMatch(String::isEmpty)) {
            throw new IllegalArgumentException("Terminal browser origins are required");
        }
        return values;
    }
}
