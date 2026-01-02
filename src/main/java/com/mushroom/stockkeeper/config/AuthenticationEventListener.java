package com.mushroom.stockkeeper.config;

import com.mushroom.stockkeeper.service.AuditService;
import org.springframework.context.event.EventListener;
import org.springframework.security.authentication.event.AbstractAuthenticationFailureEvent;
import org.springframework.security.authentication.event.AuthenticationSuccessEvent;
import org.springframework.stereotype.Component;

@Component
public class AuthenticationEventListener {

    private final AuditService auditService;

    public AuthenticationEventListener(AuditService auditService) {
        this.auditService = auditService;
    }

    @EventListener
    public void onSuccess(AuthenticationSuccessEvent event) {
        String username = event.getAuthentication().getName();
        auditService.log("LOGIN_SUCCESS", "User logged in: " + username);
    }

    @EventListener
    public void onFailure(AbstractAuthenticationFailureEvent event) {
        Object principal = event.getAuthentication().getPrincipal();
        String username = principal instanceof String ? (String) principal : "Unknown";
        String error = event.getException().getMessage();
        auditService.log("LOGIN_FAILURE", "Failed login for: " + username + " - " + error);
    }
}
