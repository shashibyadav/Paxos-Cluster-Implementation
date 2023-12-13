package com.project.four.implementation;

import com.project.four.interfaces.CoordinatorService;
import com.project.four.interfaces.Participant;
import com.project.four.server.ProjectEnums;
import com.project.four.server.ProjectEnums.ServiceKeys;
import com.project.four.utills.RemoteBusinessException;
import com.project.four.utills.ServerNetConf;
import com.project.four.utills.TransactionAction;
import com.project.four.utills.TransactionCommand;

import java.rmi.RemoteException;
import java.rmi.registry.LocateRegistry;
import java.rmi.registry.Registry;
import java.rmi.server.UnicastRemoteObject;
import java.util.LinkedList;
import java.util.List;
import java.util.concurrent.ExecutorService;

/**
 * Implementation of the CoordinatorService interface for coordinating distributed transactions.
 */
public class CoordinatorServiceImpl extends UnicastRemoteObject implements CoordinatorService {

    private List<Participant> participants;
    private ExecutorService executorService;
    private boolean serverStarted = false;
    private List<ServerNetConf> participantConfList = null;

    /**
     * Constructor for CoordinatorServiceImpl.
     *
     * @param participantList List of ServerNetConf objects representing participant configurations.
     * @throws RemoteException If there's an issue with remote object creation.
     */
    public CoordinatorServiceImpl(List<ServerNetConf> participantList) throws RemoteException {
        super();
        this.participantConfList = participantList;
    }

    /**
     * Connects to participants using their configurations.
     *
     * @throws RemoteException If there's an issue with connecting to participants.
     */
    public void connectParticipant() throws RemoteException {
        this.participants = new LinkedList<Participant>();
        for (ServerNetConf conf: this.participantConfList) {
            Registry registry = LocateRegistry.getRegistry(conf.getHost(), conf.getPort());
            for (ServiceKeys serviceName: conf.getServiceNames()) {
                Participant participant = null;
                try {
                    participant = (Participant) registry.lookup(serviceName.name());;
                    participants.add(participant);
                } catch (Exception e) {
                    throw new RemoteException("Unable to connect to participant", e);
                }
            }
        }
    }

    /**
     * Executes a distributed transaction.
     *
     * @param transaction The transaction to be executed.
     * @return True if the transaction is successful, false otherwise.
     * @throws RemoteException If there's an issue with the remote execution of the transaction.
     */
    @Override
    public boolean transaction(TransactionCommand transaction) throws RemoteException {
        try {
            for (Participant participant: this.participants) {
                if (!participant.prepare(transaction.getTransactionId())) {
                    throw new RemoteBusinessException("Not ready for a transaction");
                }
            }
            for (TransactionAction action: transaction.getActions()) {
                for (Participant participant: this.participants) {
                    if (action.getMethod() == ProjectEnums.MethodType.PUT) {
                        participant.put(transaction.getTransactionId(), action.getKey(), action.getValue());
                    }
                    if (action.getMethod() == ProjectEnums.MethodType.DELETE) {
                        participant.delete(transaction.getTransactionId(), action.getKey());
                    }
                }
            }
            for (Participant participant: this.participants) {
                participant.commit(transaction.getTransactionId());
            }
            return true;
        } catch (Exception ex) {
            for (Participant participant: this.participants) {
                participant.rollback(transaction.getTransactionId());
            }
            throw ex;
        }
    }

    /**
     * Checks if participants are ready for a transaction.
     *
     * @return True if participants are ready, false otherwise.
     * @throws RemoteException If there's an issue with the remote check.
     */
    @Override
    public boolean readyCheck() throws RemoteException {
        return true;
    }

    /**
     * Logs the global state of participants for a transaction.
     *
     * @param transaction The transaction for which to log the global state.
     * @throws RemoteException If there's an issue with the remote log operation.
     */
    @Override
    public void logState(TransactionCommand transaction) throws RemoteException {
        try {
            for (Participant participant: this.participants) {
                if (!participant.prepare(transaction.getTransactionId())) {
                    throw new RemoteBusinessException("Not ready for a transaction");
                }
            }
            for (Participant participant: this.participants) {
                participant.logGlobalState(transaction.getTransactionId());
            }
            for (Participant participant: this.participants) {
                participant.commit(transaction.getTransactionId());
            }
        } catch (Exception ex) {
            for (Participant participant: this.participants) {
                participant.rollback(transaction.getTransactionId());
            }
            throw ex;
        }
    }

    /**
     * Stops the cluster, including stopping participants and self.
     *
     * @throws RemoteException If there's an issue with the remote stop operation.
     */
    @Override
    public void stopCluster() throws RemoteException {
        // Stop participants
        for (Participant participant: this.participants) {

        }

        // Stop self

    }
}
