package com.flowdesk.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.annotation.EnableScheduling;

/** Enables asynchronous event listeners (notifications) and the scheduled SLA monitor. */
@Configuration
@EnableAsync
@EnableScheduling
public class AsyncConfig {}
