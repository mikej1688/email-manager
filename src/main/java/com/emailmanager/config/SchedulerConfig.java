package com.emailmanager.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.SchedulingConfigurer;
import org.springframework.scheduling.config.ScheduledTaskRegistrar;
import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler;

/**
 * Configures the scheduler thread pool so that when the application shuts down:
 * - No new scheduled tasks are accepted
 * - In-progress scheduled work is interrupted instead of delaying JVM exit
 */
@Configuration
public class SchedulerConfig implements SchedulingConfigurer {

    @Bean(destroyMethod = "shutdown")
    public ThreadPoolTaskScheduler taskScheduler() {
        ThreadPoolTaskScheduler scheduler = new ThreadPoolTaskScheduler();
        scheduler.setPoolSize(5);
        scheduler.setThreadNamePrefix("email-sync-");
        scheduler.setWaitForTasksToCompleteOnShutdown(false);
        scheduler.setAcceptTasksAfterContextClose(false);
        scheduler.setAwaitTerminationSeconds(0);
        scheduler.setErrorHandler(t -> org.slf4j.LoggerFactory.getLogger(SchedulerConfig.class)
                .error("Unhandled error in scheduled task", t));
        scheduler.initialize();
        return scheduler;
    }

    @Override
    public void configureTasks(ScheduledTaskRegistrar registrar) {
        registrar.setScheduler(taskScheduler());
    }
}
