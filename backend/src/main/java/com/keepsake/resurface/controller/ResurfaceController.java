package com.keepsake.resurface.controller;

import com.keepsake.resurface.dto.OnThisDayDto;
import com.keepsake.resurface.service.OnThisDayService;
import java.util.UUID;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class ResurfaceController {

    private final OnThisDayService onThisDayService;

    public ResurfaceController(OnThisDayService onThisDayService) {
        this.onThisDayService = onThisDayService;
    }

    @GetMapping("/api/resurface/on-this-day")
    public OnThisDayDto onThisDay(
            @AuthenticationPrincipal UUID viewerId,
            @RequestParam(required = false) String date
    ) {
        return onThisDayService.onThisDay(viewerId, date);
    }
}
