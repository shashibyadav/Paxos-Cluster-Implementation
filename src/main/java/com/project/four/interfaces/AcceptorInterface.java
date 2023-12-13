package com.project.four.interfaces;

import java.rmi.Remote;
import java.rmi.RemoteException;
import java.util.List;

/**
 * The AcceptorInterface defines the remote methods to be implemented by the acceptors in the Paxos
 * consensus algorithm. It includes methods for preparing and accepting proposals.
 */
public interface AcceptorInterface extends Remote {

  int getAcceptorId() throws RemoteException;
  /**
   * Prepares the acceptor to receive a proposal with a given proposal ID.
   *
   * @param proposalId The unique ID of the proposal.
   * @return An integer response indicating the status or decision related to the proposal.
   * @throws RemoteException If a remote communication error occurs.
   */
  List<Object> prepareWrapper (long proposalId, Object proposalValue) throws RemoteException;
//  List<Object> prepare(long proposalId, Object proposalValue) throws RemoteException;

  /**
   * Accepts or rejects a proposal with the given proposal ID and value.
   *
   * @param proposalId    The unique ID of the proposal.
   * @param proposalValue The value of the proposal.
   * @return A boolean indicating whether the proposal was accepted (true) or rejected (false).
   * @throws RemoteException If a remote communication error occurs.
   */
  List<Object> acceptWrapper (long proposalId, Object proposalValue) throws RemoteException;
//  List<Object> accept(long proposalId, Object proposalValue) throws RemoteException;

  /**
   * Initiates an asynchronous request to stop the server with optional force and sleep parameters.
   *
   * @param force Whether to force stop the server.
   * @param sleep The duration to sleep before stopping the server.
   * @throws RemoteException If a remote communication error occurs.
   */
  void askStopServerAsync (boolean force, long sleep) throws RemoteException;
  void addNewAcceptor (AcceptorInterface acceptorInterface) throws RemoteException;
  void addNewNode (AcceptorInterface acceptorInterface, LearnerInterface learnerInterface) throws RemoteException;
}
