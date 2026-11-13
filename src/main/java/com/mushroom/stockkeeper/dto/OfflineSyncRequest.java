package com.mushroom.stockkeeper.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.ArrayList;
import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class OfflineSyncRequest {
    private List<OfflineMutationDto> mutations = new ArrayList<>();
}
