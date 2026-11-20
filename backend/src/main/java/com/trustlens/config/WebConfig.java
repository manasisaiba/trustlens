package com.trustlens.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import java.util.concurrent.Executor;

@Configuration
public class WebConfig implements WebMvcConfigurer {
    private final AppProperties props;

    public WebConfig(AppProperties props) {
        this.props = props;
    }

    @Override
    public void addCorsMappings(CorsRegistry registry) {
        registry.addMapping("/api/**")
                .allowedOrigins(props.allowedOrigins().split(","))
                .allowedMethods("GET", "POST", "PATCH", "DELETE");
    }

    /** Runs whole audits (one thread per audit). */
    @Bean(name = "auditExecutor")
    public Executor auditExecutor() {
        ThreadPoolTaskExecutor ex = new ThreadPoolTaskExecutor();
        ex.setCorePoolSize(2);
        ex.setMaxPoolSize(4);
        ex.setQueueCapacity(50);
        ex.setThreadNamePrefix("audit-");
        ex.initialize();
        return ex;
    }

    /** Runs individual persona journeys in parallel inside an audit. */
    @Bean(name = "journeyExecutor")
    public ThreadPoolTaskExecutor journeyExecutor() {
        ThreadPoolTaskExecutor ex = new ThreadPoolTaskExecutor();
        ex.setCorePoolSize(Math.max(1, props.parallelJourneys()));
        ex.setMaxPoolSize(Math.max(1, props.parallelJourneys()) * 2);
        ex.setQueueCapacity(100);
        ex.setThreadNamePrefix("journey-");
        ex.initialize();
        return ex;
    }
}
