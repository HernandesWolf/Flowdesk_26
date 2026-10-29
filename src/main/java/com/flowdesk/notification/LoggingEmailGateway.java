package com.flowdesk.notification;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

@Component
public class LoggingEmailGateway implements EmailGateway {

    private static final Logger log = LoggerFactory.getLogger(LoggingEmailGateway.class);

    @Override
    public void send(String to, String subject, String body) {
        log.info("[email] to={} subject=\"{}\"", to, subject);
    }
}
