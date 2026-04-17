package com.emailmanager;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * Main application class for the Email Manager system.
 * This application manages multiple email accounts with AI-powered
 * classification.
 */
@SpringBootApplication
@EnableScheduling
public class EmailManagerApplication {

    public static void main(String[] args) {
        SpringApplication.run(EmailManagerApplication.class, args);
    }
}
