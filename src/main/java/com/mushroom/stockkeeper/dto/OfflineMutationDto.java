package com.mushroom.stockkeeper.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.LinkedHashMap;
import java.util.Map;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class OfflineMutationDto {
    private String clientMutationId;
    private String mutationType;
    private String createdAt;
    private Map<String, String> payload = new LinkedHashMap<>();
}
