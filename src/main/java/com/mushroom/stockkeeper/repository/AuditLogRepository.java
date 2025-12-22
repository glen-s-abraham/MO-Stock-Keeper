package com.mushroom.stockkeeper.repository;

import com.mushroom.stockkeeper.model.AuditLog;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AuditLogRepository extends JpaRepository<AuditLog, Long> {
}
