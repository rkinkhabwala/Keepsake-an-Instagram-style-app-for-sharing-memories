package com.keepsake.notification.config;

import com.keepsake.auth.service.InvalidAccessTokenException;
import com.keepsake.auth.service.JwtService;
import com.keepsake.notification.service.NotificationService;
import java.security.Principal;
import java.util.UUID;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.MessagingException;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.messaging.support.ChannelInterceptor;
import org.springframework.messaging.support.MessageHeaderAccessor;
import org.springframework.stereotype.Component;

/**
 * Authenticates the STOMP CONNECT frame with the same access token as the REST API ({@code Authorization: Bearer}
 * header), and allows only subscribing to one's own notification queue. Clients cannot send messages.
 */
@Component
public class StompAuthentication implements ChannelInterceptor {

    static final String NOTIFICATIONS = "/user" + NotificationService.QUEUE;

    private record UserPrincipal(String getName) implements Principal {
    }

    private final JwtService jwtService;

    public StompAuthentication(JwtService jwtService) {
        this.jwtService = jwtService;
    }

    @Override
    public Message<?> preSend(Message<?> message, MessageChannel channel) {
        StompHeaderAccessor accessor = MessageHeaderAccessor.getAccessor(message, StompHeaderAccessor.class);
        if (accessor == null || accessor.getCommand() == null) {
            return message;
        }
        switch (accessor.getCommand()) {
            case CONNECT, STOMP -> accessor.setUser(new UserPrincipal(authenticate(
                    accessor.getFirstNativeHeader("Authorization")).toString()));
            case SUBSCRIBE -> {
                if (accessor.getUser() == null || !NOTIFICATIONS.equals(accessor.getDestination())) {
                    throw new MessagingException("You can only subscribe to " + NOTIFICATIONS);
                }
            }
            case SEND -> throw new MessagingException("Sending is not supported");
            default -> {
            }
        }
        return message;
    }

    private UUID authenticate(String header) {
        if (header == null || !header.startsWith("Bearer ")) {
            throw new MessagingException("Missing access token");
        }
        try {
            return jwtService.verifyAccessToken(header.substring("Bearer ".length()));
        } catch (InvalidAccessTokenException invalid) {
            throw new MessagingException("Invalid access token");
        }
    }
}
