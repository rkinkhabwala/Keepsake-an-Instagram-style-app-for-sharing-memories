package com.keepsake.user.service;

import com.keepsake.media.service.MediaProcessingService;
import java.util.List;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionalEventListener;

@Component
public class AvatarProcessingListener {

    private final MediaProcessingService processingService;
    private final UserService userService;

    public AvatarProcessingListener(MediaProcessingService processingService, UserService userService) {
        this.processingService = processingService;
        this.userService = userService;
    }

    @TransactionalEventListener
    public void onSubmitted(AvatarSubmitted event) {
        processingService.processAll(List.of(event.uploadId())).whenComplete((allReady, error) -> {
            if (Boolean.TRUE.equals(allReady)) {
                userService.applyAvatar(event.userId(), event.uploadId());
            }
        });
    }
}
