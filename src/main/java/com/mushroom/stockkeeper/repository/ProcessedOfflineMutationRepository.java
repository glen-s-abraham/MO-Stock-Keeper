package com.mushroom.stockkeeper.repository;

import com.mushroom.stockkeeper.model.ProcessedOfflineMutation;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface ProcessedOfflineMutationRepository extends JpaRepository<ProcessedOfflineMutation, Long> {

    boolean existsByClientMutationId(String clientMutationId);
}
