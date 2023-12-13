package com.project.four.utills;

import com.google.common.base.Objects;
import com.project.four.server.ProjectEnums.MethodType;

import java.io.Serializable;

/**
 * Represents an operation to be executed in the Paxos algorithm.
 * Each operation consists of a type, key, value, authentication ID, and a proposal ID.
 */
public class Operation implements Serializable {
    MethodType type;
    String key;
    Object value;
    String authId;
    long proposalId;

    @Override
    public int hashCode () {
        return Objects.hashCode(key, (String)value);
    }

    @Override
    public boolean equals (Object ops) {
        try {
            Operation temp = (Operation) ops;
            String currentValue = (String) this.value;
            String newValue = (String) temp.value;
            if (this.key.equals(temp.key) && currentValue.equals(newValue)) {
                return true;
            } else {
                return false;
            }
        } catch (Exception e) {
            return false;
        }
    }

    /**
     * Default constructor for creating an operation with a type, key, value, and authentication ID.
     *
     * @param type   Type of the operation (e.g., PUT, DELETE, LOG).
     * @param key    Key associated with the operation.
     * @param value  Value associated with the operation (used in PUT operations).
     * @param authId Authentication ID associated with the operation.
     */
    public Operation(MethodType type, String key, Object value, String authId) {
        this.type = type;
        this.key = key;
        this.value = value;
        this.authId = authId;
    }

    /**
     * Constructor for creating an operation with a type, key, and value (authentication ID set to null).
     *
     * @param type  Type of the operation (e.g., PUT, DELETE, LOG).
     * @param key   Key associated with the operation.
     * @param value Value associated with the operation (used in PUT operations).
     */
    public Operation(MethodType type, String key, Object value) {
        this(type, key, value, null);
    }

    /**
     * Constructor for creating an operation with a type, key, and authentication ID (value set to null).
     *
     * @param type  Type of the operation (e.g., PUT, DELETE, LOG).
     * @param key   Key associated with the operation.
     * @param authId Authentication ID associated with the operation.
     */
    public Operation(MethodType type, String key, String authId) {
        this(type, key, null, authId);
    }

    public MethodType getType() {
        return type;
    }

    public void setType(MethodType type) {
        this.type = type;
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

    public String getAuthId() {
        return authId;
    }

    public void setAuthId(String authId) {
        this.authId = authId;
    }

    public long getProposalId() {
        return proposalId;
    }

    public void setProposalId(long proposalId) {
        this.proposalId = proposalId;
    }
}
