package com.project.four.interfaces;

import java.rmi.Remote;
import java.rmi.RemoteException;

/**
 * The StoreService interface defines remote methods for interacting with a key-value store.
 * It extends the Remote interface, allowing it to be used as a remote service.
 *
 * @throws RemoteException if there is a problem with the remote communication.
 */
public interface StoreService extends Remote {
    /**
     * Stores data in the key-value store.
     *
     * @param key    The key under which the data will be stored.
     * @param value  The data to be stored.
     * @param authId The authorization ID associated with the operation.
     * @throws RemoteException if there is a problem with the remote communication.
     */
    void putData(String key, Object value, String authId) throws RemoteException;

    /**
     * Retrieves data from the key-value store.
     *
     * @param key    The key for which data is requested.
     * @param authId The authorization ID associated with the operation.
     * @return The data associated with the specified key, or null if the key is not found.
     * @throws RemoteException if there is a problem with the remote communication.
     */
    Object getData(String key, String authId) throws RemoteException;

    /**
     * Deletes data from the key-value store.
     *
     * @param key    The key for which data should be deleted.
     * @param authId The authorization ID associated with the operation.
     * @throws RemoteException if there is a problem with the remote communication.
     */
    void deleteData(String key, String authId) throws RemoteException;

    /**
     * Stops the server with an optional force flag.
     *
     * @param authId The authorization ID associated with the operation.
     * @param force  If true, forcefully stop the server; if false, stop gracefully.
     * @throws RemoteException if there is a problem with the remote communication.
     */
    void stopServer(String authId, boolean force) throws RemoteException;

    /**
     * Initiates the global state for the key-value store.
     *
     * @throws RemoteException if there is a problem with the remote communication.
     */

    void initiateGlobalState(String name) throws RemoteException;
    void askToStopASpecificServer(int index) throws RemoteException;
}
