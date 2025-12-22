package com.mushroom.stockkeeper.service;

import com.mushroom.stockkeeper.model.AuditLog;
import com.mushroom.stockkeeper.repository.AuditLogRepository;
import org.springframework.stereotype.Service;
import org.springframework.security.core.context.SecurityContextHolder;

@Service
public class AuditService {

    private final AuditLogRepository auditLogRepository;

    public AuditService(AuditLogRepository auditLogRepository) {
        this.auditLogRepository = auditLogRepository;
    }

    public void log(String action, String details) {
        try {
            AuditLog log = new AuditLog();
            log.setAction(action);
            log.setDetails(details);

            // Get Username
            var auth = SecurityContextHolder.getContext().getAuthentication();
            if (auth != null) {
                log.setUsername(auth.getName());
            } else {
                log.setUsername("SYSTEM");
            }

            auditLogRepository.save(log);
        } catch (Exception e) {
            // Audit logging should not break the app
            System.err.println("Failed to write audit log: " + e.getMessage());
        }
    }
}
