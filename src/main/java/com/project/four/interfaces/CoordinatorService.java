package com.project.four.interfaces;

import com.project.four.utills.TransactionCommand;

import java.rmi.Remote;
import java.rmi.RemoteException;

public interface CoordinatorService extends Remote {
    // Method for initiating a transaction, returns true if successful
    boolean transaction(TransactionCommand transaction) throws RemoteException;
    // Method for performing a readiness check, returns true if ready
    boolean readyCheck () throws RemoteException;
    // Method for logging the state based on the provided transaction
    void logState (TransactionCommand transaction) throws RemoteException;
    // Method for stopping the cluster remotely
    void stopCluster() throws RemoteException;
}
