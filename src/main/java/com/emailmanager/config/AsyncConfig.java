package com.emailmanager.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableAsync;

@Configuration
@EnableAsync
public class AsyncConfig {
    // Uses Spring's default SimpleAsyncTaskExecutor for @Async methods.
    // Replace with a custom ThreadPoolTaskExecutor if audit log volume warrants it.
}
