package com.project.four.utills;

import com.project.four.server.ProjectEnums.MethodType;

import java.io.Serializable;

public class TransactionAction implements Serializable {
    private String key = null;
    private Object value = null;
    private MethodType method = null;
    private Object prevValue = null;

    public TransactionAction(){}
    public TransactionAction(String key, Object value, MethodType method){
        this.key = key;
        this.value = value;
        this.method = method;
    }
    public TransactionAction(String key, Object value, MethodType method, Object prevValue) {
        this.key = key;
        this.value = value;
        this.method = method;
        this.prevValue = prevValue;
    }

    public String getKey() {
        return key;
    }

    public void setKey(String key) {
        this.key = key;
    }

    public Object getValue() {
        return value;
    }

    public void setValue(Object value) {
        this.value = value;
    }

    public MethodType getMethod() {
        return method;
    }

    public void setMethod(MethodType method) {
        this.method = method;
    }

    public Object getPrevValue() {
        return prevValue;
    }

    public void setPrevValue(Object prevValue) {
        this.prevValue = prevValue;
    }
}
