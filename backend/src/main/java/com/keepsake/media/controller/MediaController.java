package com.keepsake.media.controller;

import com.keepsake.media.dto.UploadMetadataDto;
import com.keepsake.media.dto.UploadUrlDto;
import com.keepsake.media.dto.UploadUrlRequest;
import com.keepsake.media.service.MediaUploadService;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/media")
public class MediaController {

    private final MediaUploadService uploadService;

    public MediaController(MediaUploadService uploadService) {
        this.uploadService = uploadService;
    }

    @PostMapping("/upload-urls")
    @ResponseStatus(HttpStatus.CREATED)
    public List<UploadUrlDto> uploadUrls(
            @AuthenticationPrincipal UUID viewerId,
            @RequestBody List<UploadUrlRequest> items
    ) {
        return uploadService.createUploadUrls(viewerId, items);
    }

    @GetMapping("/uploads/{uploadId}/metadata")
    public UploadMetadataDto metadata(@AuthenticationPrincipal UUID viewerId, @PathVariable UUID uploadId) {
        return uploadService.metadata(viewerId, uploadId);
    }
}
