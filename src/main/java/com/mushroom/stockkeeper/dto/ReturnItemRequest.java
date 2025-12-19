package com.mushroom.stockkeeper.dto;

public class ReturnItemRequest {
    private String uuid;
    private String reason;

    public String getUuid() {
        return uuid;
    }

    public void setUuid(String uuid) {
        this.uuid = uuid;
    }

    public String getReason() {
        return reason;
    }

    public void setReason(String reason) {
        this.reason = reason;
    }
}
