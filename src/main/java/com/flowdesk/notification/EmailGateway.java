package com.flowdesk.notification;

/** Outbound e-mail port. The default adapter only logs; plug in Spring Mail / SES / SendGrid here. */
public interface EmailGateway {

    void send(String to, String subject, String body);
}
