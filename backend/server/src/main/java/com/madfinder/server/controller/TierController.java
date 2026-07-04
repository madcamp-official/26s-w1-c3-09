package com.madfinder.server.controller;

import com.madfinder.server.dto.TierSaveRequest;
import com.madfinder.server.dto.TierSaveResponse;
import com.madfinder.server.service.TierService;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

/** PUT /api/tiers — 티어표 저장 (유저당 1세트 전체 덮어쓰기, 2페이지). */
@RestController
public class TierController {

    private final TierService tierService;

    public TierController(TierService tierService) {
        this.tierService = tierService;
    }

    @PutMapping("/api/tiers")
    public TierSaveResponse save(@RequestBody TierSaveRequest request) {
        return tierService.save(request);
    }
}
