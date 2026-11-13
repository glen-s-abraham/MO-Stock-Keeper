package com.mushroom.stockkeeper.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.mushroom.stockkeeper.dto.OfflineMutationDto;
import com.mushroom.stockkeeper.dto.OfflineSnapshotDto;
import com.mushroom.stockkeeper.dto.OfflineSyncRequest;
import com.mushroom.stockkeeper.dto.OfflineSyncResponse;
import com.mushroom.stockkeeper.service.OfflineSyncService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;
import java.util.Map;

import static org.hamcrest.Matchers.containsString;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
class OfflineSyncControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockBean
    private OfflineSyncService offlineSyncService;

    @Test
    @WithMockUser(roles = "ADMIN")
    void getSnapshot_ShouldReturnOfflineSnapshotJson() throws Exception {
        OfflineSnapshotDto snapshot = new OfflineSnapshotDto();
        snapshot.setTimestamp("2026-11-13T12:00:00Z");
        snapshot.setManualLabelLastSequence(15);
        snapshot.setSettings(Map.of("company_name", "MO Farms"));

        when(offlineSyncService.buildSnapshot()).thenReturn(snapshot);

        mockMvc.perform(get("/api/offline/snapshot"))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.manualLabelLastSequence").value(15))
                .andExpect(jsonPath("$.settings.company_name").value("MO Farms"));
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void syncMutations_ShouldProcessOutboxAndReturnUpdatedSnapshot() throws Exception {
        OfflineSnapshotDto snapshot = new OfflineSnapshotDto();
        snapshot.setManualLabelLastSequence(25);
        OfflineSyncResponse syncResponse = new OfflineSyncResponse(
                true, 1, 0, "Synced 1 offline change(s) successfully (0 duplicate(s) skipped).", snapshot
        );

        when(offlineSyncService.processSync(any(OfflineSyncRequest.class))).thenReturn(syncResponse);

        OfflineSyncRequest req = new OfflineSyncRequest(List.of(
                new OfflineMutationDto(
                        "mut-101",
                        "CREATE_BATCH",
                        "2026-11-13T12:00:00Z",
                        Map.of("productId", "1", "quantity", "10", "batchCode", "B-20261113-101")
                )
        ));

        mockMvc.perform(post("/api/offline/sync")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req))
                        .with(csrf()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.appliedCount").value(1))
                .andExpect(jsonPath("$.snapshot.manualLabelLastSequence").value(25));
    }

    @Test
    @WithMockUser(roles = "PACKER")
    void offlinePrintView_ShouldRenderOfflinePrintTemplate() throws Exception {
        mockMvc.perform(get("/batches/offline-print"))
                .andExpect(status().isOk())
                .andExpect(view().name("batches/offline-print"))
                .andExpect(content().string(containsString("Offline Print Sheet (Queued for Server Sync)")));
    }
}
