package com.project.four.implementation;

import com.project.four.interfaces.CoordinatorService;
import com.project.four.interfaces.Participant;
import com.project.four.interfaces.StoreService;
import com.project.four.server.CoordinatorServer;
import com.project.four.server.ProjectEnums;
import com.project.four.server.Server;
import com.project.four.server.ServerConfig;
import com.project.four.utills.*;

import java.io.IOException;
import java.rmi.NotBoundException;
import java.rmi.RemoteException;
import java.rmi.registry.LocateRegistry;
import java.rmi.registry.Registry;
import java.rmi.server.UnicastRemoteObject;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.locks.StampedLock;

public class StoreServiceImpl extends UnicastRemoteObject implements StoreService, Participant {

    private Map<String, Object> store = null;
    private Server hostServer = null;
    private Logger logWriter = null;
    private boolean isLogs = false;
    private boolean isReadAllowed = true;
    private boolean isWriteAllowed = true;
    private boolean transactionInProgress = false;
    private Map<String, List<TransactionAction>> history = new ConcurrentHashMap<String, List<TransactionAction>>();
    private Map<String, TransactionStatus> inprogressTransactions = new ConcurrentHashMap<String, TransactionStatus>();
    private StampedLock lock = new StampedLock();
    private Registry ccorRegis = null;
    private CoordinatorService coordinator = null;
    private ServerConfig sConfig = null;

    /**
     * Constructor for StoreServiceImpl.
     *
     * @param server The host server for the store.
     * @throws RemoteException If there's an issue with remote object creation.
     */
    public StoreServiceImpl(Server server) throws RemoteException {
        super();
        this.configSetup(server, null, null);
    }

    /**
     * Constructor for StoreServiceImpl with an initial store.
     *
     * @param server The host server for the store.
     * @param store  Initial key-value pairs for the store.
     * @throws RemoteException If there's an issue with remote object creation.
     */
    public StoreServiceImpl(Server server, Map<String, Object> store) throws RemoteException {
        super();
        this.configSetup(server, store, null);
    }

    /**
     * Constructor for StoreServiceImpl with an initial store and a logger.
     *
     * @param server    The host server for the store.
     * @param store     Initial key-value pairs for the store.
     * @param logWriter Logger for logging messages.
     * @throws RemoteException If there's an issue with remote object creation.
     */
    public StoreServiceImpl(Server server, Map<String, Object> store, Logger logWriter) throws RemoteException {
        super();
        this.configSetup(server, store, logWriter);
    }

    /**
     * Connects to the coordinator service if configured.
     *
     * @throws RemoteException    If there's an issue with the remote operation.
     * @throws NotBoundException   If the coordinator service is not bound.
     */
    private void connectCoordinator () throws RemoteException, NotBoundException {
        if (this.sConfig.isHasCoordinator()) {
            ArchConf conf = CoordinatorServer.readStaticConf();
            ServerNetConf coorConf = conf.getCoordinatorConf();
            this.ccorRegis = LocateRegistry.getRegistry(coorConf.getHost(), coorConf.getPort());
            this.coordinator = (CoordinatorService) this.ccorRegis.lookup("CoordinatorService");
            this.logWriter.logger("Coordinator Connected");
//            System.out.println(this.coordinator.readyCheck());
        }
    }

    /**
     * Performs initial configuration setup.
     *
     * @param server    The host server for the store.
     * @param store     Initial key-value pairs for the store.
     * @param logWriter Logger for logging messages.
     */
    private void configSetup(Server server, Map<String, Object> store, Logger logWriter) {
        this.store = store == null ? new ConcurrentHashMap<String, Object>() : store;
        this.hostServer = server;
        try {
            this.logWriter = (logWriter != null) ? logWriter : new Logger();
            this.sConfig = server.getsConfig();
            this.connectCoordinator();
        } catch (IOException | NotBoundException e) {
            e.printStackTrace();
        }
    }

    /**
     * Common logging method for logging messages.
     */
    private void commonLogs() {
//        @SuppressWarnings("deprecation")
//        long threadId = Thread.currentThread().getId();
//        this.logWriter.logger("Thread :- " + threadId + " is running");
    }

    /**
     * Waits if read, write, or transaction operations are not allowed.
     */
    private void waitIfNecessary () {
        if (!isReadAllowed || !isWriteAllowed || transactionInProgress) {
            try {
                this.store.wait();
            } catch (InterruptedException e) {
                throw new RuntimeException(e);
            }
        }
    }

    /**
     * Performs authorization check.
     *
     * @param authId The authorization identifier.
     * @throws RemoteBusinessException If not authorized.
     */
    private void authCheck(String authId) throws RemoteBusinessException {
        if (Utills.isEmptyString(authId)) {
            throw new RemoteBusinessException("Not Authorised");
        }
    }

    /**
     * Put data into the store with the given key and value, subject to authorization checks.
     *
     * @param key    The key to store the data under.
     * @param value  The data to be stored.
     * @param authId The authorization identifier.
     * @throws RemoteException If there's an issue with the remote operation.
     */
    @Override
    public void putData(String key, Object value, String authId) throws RemoteException {
        this.authCheck(authId);
        this.commonLogs();
        this.waitIfNecessary();
        String storeKey = this.hostServer.consStoreKeyRe(authId, key);
        if (this.store.containsKey(storeKey)) {
            this.logWriter.logger("Key :- " + storeKey + " already exists in the store");
            throw new RemoteBusinessException("Key :- " + key + " already exists in the store");
        }
        if (this.sConfig.isHasCoordinator()) {
            TransactionCommand transactionCommand = new TransactionCommand();
            transactionCommand.getActions().add(new TransactionAction(storeKey, value, ProjectEnums.MethodType.PUT));
            boolean result = this.coordinator.transaction(transactionCommand);
//            System.out.println(result);
        } else {
            this.store.put(storeKey, value);
        }
    }

    /**
     * Retrieve data from the store using the specified key, subject to authorization checks.
     *
     * @param key    The key to retrieve data for.
     * @param authId The authorization identifier.
     * @return The data associated with the key or null if not found.
     * @throws RemoteException If there's an issue with the remote operation.
     */
    @Override
    public Object getData(String key, String authId) throws RemoteException {
        this.authCheck(authId);
        this.commonLogs();
        this.waitIfNecessary();
        String storeKey = this.hostServer.consStoreKeyRe(authId, key);
        if (this.store.containsKey(storeKey)) {
            if (this.sConfig.isHasCoordinator()) {
                long stamp = this.lock.tryOptimisticRead();
                if (this.lock.validate(stamp)) {
                    return this.store.get(storeKey);
                }
            } else {
                return this.store.get(storeKey);
            }
        }
        return null;
    }

    /**
     * Delete data from the store using the specified key, subject to authorization checks.
     *
     * @param key    The key to delete data for.
     * @param authId The authorization identifier.
     * @throws RemoteException If there's an issue with the remote operation.
     */
    @Override
    public void deleteData(String key, String authId) throws RemoteException {
        this.authCheck(authId);
        this.commonLogs();
        this.waitIfNecessary();
        String storeKey = this.hostServer.consStoreKeyRe(authId, key);
        if (this.store.containsKey(storeKey)) {
            if (this.sConfig.isHasCoordinator()) {
                TransactionCommand transactionCommand = new TransactionCommand();
                transactionCommand.getActions().add(new TransactionAction(storeKey, null, ProjectEnums.MethodType.DELETE));
                this.coordinator.transaction(transactionCommand);
            } else {
                this.store.remove(storeKey);
            }
        }
    }

    /**
     * Stop the server asynchronously, subject to authorization checks.
     *
     * @param authId The authorization identifier.
     * @param force  Whether to forcefully stop the server.
     * @throws RemoteException If there's an issue with the remote operation.
     */
    @Override
    public void stopServer(String authId, boolean force) throws RemoteException {
        this.authCheck(authId);
        this.commonLogs();
        if (this.sConfig.isHasCoordinator()) {
            Object stopServer = this.hostServer.getClusterSemaphores();
            synchronized (stopServer) {
             stopServer.notifyAll();
            }
        } else {
            this.hostServer.stopServerAsync(force);
        }
    }

    /**
     * Initiates the global state for the store.
     *
     * @throws RemoteException If there's an issue with the remote operation.
     */
    @Override
    public void initiateGlobalState(String name) throws RemoteException {
        if (this.sConfig.isHasCoordinator()) {
            TransactionCommand transactionCommand = new TransactionCommand();
            this.coordinator.logState(transactionCommand);
        }
    }

    @Override
    public void askToStopASpecificServer(int index) throws RemoteException {

    }

    /**
     * Checks if the transaction is valid and in progress.
     *
     * @param transactionId The ID of the transaction.
     * @return True if the transaction is valid and in progress; false otherwise.
     */
    private boolean checkTransaction(String transactionId) {
        if (!history.containsKey(transactionId)) {
            return false;
        }
        if (!this.lock.isWriteLocked()) {
            return false;
        }
        return true;
    }

    /**
     * Prepares the store for a transaction.
     *
     * @param transaction The ID of the transaction.
     * @return True if the store is prepared for the transaction; false otherwise.
     * @throws RemoteException If there's an issue with the remote operation.
     */
    @Override
    public boolean prepare(String transaction) throws RemoteException {
        long stamp = -1;
        try {
            stamp = this.lock.writeLock();
            this.history.put(transaction, new LinkedList<TransactionAction>());
            TransactionStatus ts = new TransactionStatus(transaction, stamp, System.currentTimeMillis());
            this.inprogressTransactions.put(transaction, ts);
            return true;
        } catch (Exception ex) {
            if (this.lock.isWriteLocked() && stamp == -1) {
                this.lock.unlockWrite(stamp);
            }
            return false;
        }
    }

    /**
     * Puts data into the store as part of a transaction.
     *
     * @param transaction The ID of the transaction.
     * @param key         The key for the data.
     * @param value       The value of the data.
     * @return True if the operation is successful; false otherwise.
     * @throws RemoteException If there's an issue with the remote operation.
     */
    @Override
    public boolean put(String transaction, String key, Object value) throws RemoteException {
        if (this.checkTransaction(transaction)) {
            Object prevValue = null;
            if (this.store.containsKey(key)) {
                prevValue = this.store.get(key);
            }
            this.store.put(key, value);
            TransactionAction action = new TransactionAction(key, value, ProjectEnums.MethodType.PUT, prevValue);
            this.history.get(transaction).add(action);
            return true;
        } else {
            return false;
        }
    }

    /**
     * Deletes data from the store as part of a transaction.
     *
     * @param transaction The ID of the transaction.
     * @param key         The key for the data.
     * @return True if the operation is successful; false otherwise.
     * @throws RemoteException If there's an issue with the remote operation.
     */
    @Override
    public boolean delete(String transaction, String key) throws RemoteException {
        if (this.checkTransaction(transaction)) {
            Object deletedValue = this.store.get(key);
            this.store.remove(key);
            TransactionAction action = new TransactionAction(key, deletedValue, ProjectEnums.MethodType.DELETE);
            this.history.get(transaction).add(action);
            return true;
        } else {
            return false;
        }
    }

    /**
     * Commits a transaction in the store.
     *
     * @param transaction The ID of the transaction.
     * @return The ID of the committed transaction.
     * @throws RemoteException If there's an issue with the remote operation.
     */
    @Override
    public String commit(String transaction) throws RemoteException {
        if (this.checkTransaction(transaction)) {
            this.history.remove(transaction);
            long stamp = this.inprogressTransactions.get(transaction).getStamp();
            this.inprogressTransactions.remove(transaction);
            this.lock.unlockWrite(stamp);
            return transaction;
        } else {
            throw new RemoteBusinessException("Invalid transaction");
        }
    }

    /**
     * Rolls back a transaction in the store.
     *
     * @param transaction The ID of the transaction.
     * @return True if the rollback is successful; false otherwise.
     * @throws RemoteException If there's an issue with the remote operation.
     */
    @Override
    public boolean rollback(String transaction) throws RemoteException {
        if (this.checkTransaction(transaction)) {
            List<TransactionAction> his = this.history.get(transaction);
            for (TransactionAction action: his) {
                if (action.getMethod() == ProjectEnums.MethodType.DELETE) {
                    this.store.put(action.getKey(), action.getValue());
                } else if (action.getMethod() == ProjectEnums.MethodType.PUT) {
                    this.store.put(action.getKey(), action.getPrevValue());
                }
            }
            this.history.remove(transaction);
            long stamp = this.inprogressTransactions.get(transaction).getStamp();
            this.inprogressTransactions.remove(transaction);
            this.lock.unlockWrite(stamp);
            return true;
        }
        return false;
    }

    // Override the logGlobalState method as part of the implemented interface
    @Override
    public void logGlobalState(String transaction) throws RemoteException {
        if (this.checkTransaction(transaction)) {
            this.logWriter.logger("Current State:- " + this.store.toString());
        }
    }

    public CoordinatorService getCoordinator() {
        return coordinator;
    }

    public void setCoordinator(CoordinatorService coordinator) {
        this.coordinator = coordinator;
    }

    /**
     * Get the current state of logging in the service.
     *
     * @return true if logging is enabled; false otherwise.
     */
    public boolean isLogs() {
        return isLogs;
    }

    /**
     * Set the logging state of the service.
     *
     * @param isLogs true to enable logging; false to disable.
     */
    public void setLogs(boolean isLogs) {
        this.isLogs = isLogs;
    }

}
