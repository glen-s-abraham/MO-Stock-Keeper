package com.mushroom.stockkeeper.controller;

import com.mushroom.stockkeeper.dto.OfflineSnapshotDto;
import com.mushroom.stockkeeper.dto.OfflineSyncRequest;
import com.mushroom.stockkeeper.dto.OfflineSyncResponse;
import com.mushroom.stockkeeper.service.OfflineSyncService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/offline")
public class OfflineSyncController {

    private final OfflineSyncService offlineSyncService;

    public OfflineSyncController(OfflineSyncService offlineSyncService) {
        this.offlineSyncService = offlineSyncService;
    }

    @GetMapping("/snapshot")
    public ResponseEntity<OfflineSnapshotDto> getSnapshot() {
        return ResponseEntity.ok(offlineSyncService.buildSnapshot());
    }

    @PostMapping("/sync")
    public ResponseEntity<OfflineSyncResponse> syncMutations(@RequestBody OfflineSyncRequest request) {
        try {
            OfflineSyncResponse response = offlineSyncService.processSync(request);
            return ResponseEntity.ok(response);
        } catch (IllegalArgumentException ex) {
            return ResponseEntity.badRequest().body(
                    new OfflineSyncResponse(false, 0, 0, ex.getMessage(), offlineSyncService.buildSnapshot())
            );
        }
    }
}
