package com.mushroom.stockkeeper.dto;

import java.util.List;

public class BatchReturnRequest {
    private List<ReturnItemRequest> items;

    public List<ReturnItemRequest> getItems() {
        return items;
    }

    public void setItems(List<ReturnItemRequest> items) {
        this.items = items;
    }
}
