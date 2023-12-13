package com.project.four.interfaces;

import com.project.four.server.ProjectEnums.MethodType;

import java.rmi.Remote;
import java.rmi.RemoteException;

/**
 * The LearnerInterface represents a remote interface that defines
 * the learning process in the Paxos consensus algorithm. It contains
 * the learning method to acknowledge an accepted proposal.
 */
public interface LearnerInterface extends Remote {
  int getLearnerId() throws RemoteException;
  /**
   * The learn method is used to inform the Learner of an accepted proposal.
   *
   * @param proposalId The unique identifier for the proposal.
   * @param acceptedValue The value that has been accepted.
   * @throws RemoteException If a remote invocation error occurs.
   */
  void learn(long proposalId, Object acceptedValue) throws RemoteException;

  /**
   * Logs the global state of the learner.
   *
   * @throws RemoteException If a remote communication error occurs.
   */
  void logGlobalState() throws RemoteException;

  /**
   * Updates the learner's state based on the specified operation.
   *
   * @param type  The type of update operation (PUT, DELETE, LOG).
   * @param key   The key associated with the update.
   * @param value The value to be updated.
   * @throws RemoteException If a remote communication error occurs.
   */
  void updateStore(MethodType type, String key, Object value) throws RemoteException;

  /**
   * Informs the learner of a leader update in the system.
   *
   * @param type  The type of update operation (e.g., ADD, REMOVE).
   * @param key   The key associated with the update.
   * @param value The value to be updated.
   * @throws RemoteException If a remote communication error occurs.
   */
  void leaderUpdate (MethodType type, String key, Object value) throws RemoteException;
  void addNewLearner (LearnerInterface learnerInterface) throws RemoteException;
}
