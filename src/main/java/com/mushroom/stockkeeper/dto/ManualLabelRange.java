package com.mushroom.stockkeeper.dto;

public class ManualLabelRange {

    private final int startSeq;
    private final int endSeq;

    public ManualLabelRange(int startSeq, int endSeq) {
        if (startSeq < 1) {
            throw new IllegalArgumentException("Start sequence must be at least 1");
        }
        if (endSeq < startSeq) {
            throw new IllegalArgumentException("End sequence must be greater than or equal to start sequence");
        }
        this.startSeq = startSeq;
        this.endSeq = endSeq;
    }

    public int getStartSeq() {
        return startSeq;
    }

    public int getEndSeq() {
        return endSeq;
    }

    public int getCount() {
        return endSeq - startSeq + 1;
    }

    public String getStartCode() {
        return formatCode(startSeq);
    }

    public String getEndCode() {
        return formatCode(endSeq);
    }

    public static String formatCode(int sequence) {
        return String.format("M-%04d", sequence);
    }
}
