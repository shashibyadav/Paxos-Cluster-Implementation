package com.project.four.interfaces;

import java.rmi.Remote;
import java.rmi.RemoteException;

public interface Participant extends Remote {
    // Initiates the preparation phase for the given transaction
    boolean prepare(String transaction) throws RemoteException;
    // Stores or updates the key-value pair in the context of a transaction
    boolean put(String transaction, String key, Object value) throws RemoteException;
    // Deletes the key-value pair associated with the given key within the transaction
    boolean delete(String transaction, String key) throws RemoteException;
    // Commits the changes made during the transaction and returns a commit message
    String commit(String transaction) throws RemoteException;
    // Rolls back the changes made during the transaction
    boolean rollback(String transaction) throws RemoteException;
    // Logs the global state of the participant based on the provided transaction
    void logGlobalState(String transaction) throws RemoteException;
}
