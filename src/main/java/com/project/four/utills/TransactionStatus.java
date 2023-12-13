package com.project.four.utills;

public class TransactionStatus {
    private String transactionId;
    private long stamp;
    private long startTimestamp;
    public TransactionStatus(){}
    public TransactionStatus(String transactionId, long stamp, long startTimestamp){
        this.transactionId = transactionId;
        this.stamp = stamp;
        this.startTimestamp = startTimestamp;
    }

    public String getTransactionId() {
        return transactionId;
    }

    public void setTransactionId(String transactionId) {
        this.transactionId = transactionId;
    }

    public long getStamp() {
        return stamp;
    }

    public void setStamp(long stamp) {
        this.stamp = stamp;
    }

    public long getStartTimestamp() {
        return startTimestamp;
    }

    public void setStartTimestamp(long startTimestamp) {
        this.startTimestamp = startTimestamp;
    }
}
