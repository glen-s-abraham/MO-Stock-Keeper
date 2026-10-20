package com.mushroom.stockkeeper.dto;

public class PrintServerStatusResponse {

    private boolean available;
    private String printServerUrl;
    private String message;
    private boolean sameMachineAsPrintServer;
    private boolean hideServerPrintOnWorkstation = true;
    private boolean hideClientPrintOnRemote = true;

    public PrintServerStatusResponse() {
    }

    public PrintServerStatusResponse(boolean available, String printServerUrl, String message) {
        this(available, printServerUrl, message, false);
    }

    public PrintServerStatusResponse(boolean available, String printServerUrl, String message, boolean sameMachineAsPrintServer) {
        this.available = available;
        this.printServerUrl = printServerUrl;
        this.message = message;
        this.sameMachineAsPrintServer = sameMachineAsPrintServer;
    }

    public boolean isAvailable() {
        return available;
    }

    public void setAvailable(boolean available) {
        this.available = available;
    }

    public String getPrintServerUrl() {
        return printServerUrl;
    }

    public void setPrintServerUrl(String printServerUrl) {
        this.printServerUrl = printServerUrl;
    }

    public String getMessage() {
        return message;
    }

    public void setMessage(String message) {
        this.message = message;
    }

    public boolean isSameMachineAsPrintServer() {
        return sameMachineAsPrintServer;
    }

    public void setSameMachineAsPrintServer(boolean sameMachineAsPrintServer) {
        this.sameMachineAsPrintServer = sameMachineAsPrintServer;
    }

    public boolean isHideServerPrintOnWorkstation() {
        return hideServerPrintOnWorkstation;
    }

    public void setHideServerPrintOnWorkstation(boolean hideServerPrintOnWorkstation) {
        this.hideServerPrintOnWorkstation = hideServerPrintOnWorkstation;
    }

    public boolean isHideClientPrintOnRemote() {
        return hideClientPrintOnRemote;
    }

    public void setHideClientPrintOnRemote(boolean hideClientPrintOnRemote) {
        this.hideClientPrintOnRemote = hideClientPrintOnRemote;
    }
}
