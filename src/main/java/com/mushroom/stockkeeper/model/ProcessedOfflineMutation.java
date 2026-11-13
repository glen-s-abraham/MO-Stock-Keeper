package com.mushroom.stockkeeper.model;

import jakarta.persistence.*;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Entity
@Table(name = "sk_processed_offline_mutations")
@Data
@NoArgsConstructor
public class ProcessedOfflineMutation {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true, length = 100)
    private String clientMutationId;

    @Column(nullable = false, length = 60)
    private String mutationType;

    @Column(nullable = false)
    private LocalDateTime processedAt = LocalDateTime.now();

    @Column(length = 255)
    private String summary;

    public ProcessedOfflineMutation(String clientMutationId, String mutationType, String summary) {
        this.clientMutationId = clientMutationId;
        this.mutationType = mutationType;
        this.summary = summary;
        this.processedAt = LocalDateTime.now();
    }
}
