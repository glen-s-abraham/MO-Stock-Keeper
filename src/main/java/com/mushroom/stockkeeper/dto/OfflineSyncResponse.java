package com.mushroom.stockkeeper.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class OfflineSyncResponse {
    private boolean success;
    private int appliedCount;
    private int skippedDuplicateCount;
    private String message;
    private OfflineSnapshotDto snapshot;
}
