package com.project.four.utills;

import java.io.Serializable;
import java.util.LinkedList;
import java.util.List;
import java.util.UUID;

public class TransactionCommand implements Serializable {

    private String transactionId = null;
    private List<TransactionAction> actions = null;
    private boolean failed = false;
    private Exception exFailed = null;
    public TransactionCommand() {
        this.transactionId = UUID.randomUUID().toString();
        this.actions = new LinkedList<TransactionAction>();
    }
    public TransactionCommand (String transactionId, List<TransactionAction> actions) {
        this.transactionId = Utills.isEmptyString(transactionId) ? UUID.randomUUID().toString() : transactionId;
        this.actions = actions;
    }
    public String getTransactionId() {
        return transactionId;
    }

    public void setTransactionId(String transactionId) {
        this.transactionId = transactionId;
    }

    public List<TransactionAction> getActions() {
        return actions;
    }

    public void setActions(List<TransactionAction> actions) {
        this.actions = actions;
    }

    public boolean isFailed() {
        return failed;
    }

    public void setFailed(boolean failed) {
        this.failed = failed;
    }

    public Exception getExFailed() {
        return exFailed;
    }

    public void setExFailed(Exception exFailed) {
        this.exFailed = exFailed;
    }
}
