package com.mushroom.stockkeeper.dto;

public class ServerPrintResponse {

    private boolean success;
    private Long batchId;
    private int unitsPrinted;
    private int bytesSent;
    private String message;

    public ServerPrintResponse() {
    }

    public ServerPrintResponse(boolean success, Long batchId, int unitsPrinted, int bytesSent, String message) {
        this.success = success;
        this.batchId = batchId;
        this.unitsPrinted = unitsPrinted;
        this.bytesSent = bytesSent;
        this.message = message;
    }

    public boolean isSuccess() {
        return success;
    }

    public void setSuccess(boolean success) {
        this.success = success;
    }

    public Long getBatchId() {
        return batchId;
    }

    public void setBatchId(Long batchId) {
        this.batchId = batchId;
    }

    public int getUnitsPrinted() {
        return unitsPrinted;
    }

    public void setUnitsPrinted(int unitsPrinted) {
        this.unitsPrinted = unitsPrinted;
    }

    public int getBytesSent() {
        return bytesSent;
    }

    public void setBytesSent(int bytesSent) {
        this.bytesSent = bytesSent;
    }

    public String getMessage() {
        return message;
    }

    public void setMessage(String message) {
        this.message = message;
    }
}
