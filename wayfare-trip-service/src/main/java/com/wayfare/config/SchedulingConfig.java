package com.wayfare.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;

/** Turns on {@code @Scheduled}; the outbox relay is the only user. */
@Configuration
@EnableScheduling
public class SchedulingConfig {
}
