package com.project.four.server;

import com.project.four.interfaces.AcceptorInterface;
import com.project.four.interfaces.LearnerInterface;
import com.project.four.interfaces.ProposerInterface;
import com.project.four.interfaces.StoreService;
import com.project.four.server.ProjectEnums.ServiceKeys;
import com.project.four.server.ProjectEnums.MethodType;
import com.project.four.utills.*;
import org.json.simple.JSONObject;
import org.json.simple.parser.JSONParser;
import org.json.simple.parser.ParseException;

import java.io.FileReader;
import java.io.IOException;
import java.rmi.RemoteException;
import java.rmi.registry.LocateRegistry;
import java.rmi.server.UnicastRemoteObject;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

/**
 * PaxosServer class that extends Server and implements ProposerInterface, AcceptorInterface, LearnerInterface, and StoreService.
 */
public class PaxosServer extends Server implements ProposerInterface, AcceptorInterface, LearnerInterface, StoreService {
    // Fields to maintain the state and configuration of the Paxos server
    private final List<AcceptorInterface> acceptors = new LinkedList<AcceptorInterface>();
    private final List<LearnerInterface> learners = new LinkedList<LearnerInterface>();
    private int numServers = 0;
    private int serverId = -1;
    private Map<String, Operation> acceptedPaxosKeys = new ConcurrentHashMap<String, Operation>();
    private Map<Operation, Integer> learnerMap = new ConcurrentHashMap<Operation, Integer>();
    private long lastPreparedProposalId;
    private boolean logs = false;
    private AcceptorInterface leaderAccept = null;
    private LearnerInterface leaderLearn = null;
    private long delay = 0;
    private ExecutorService learnExecutor = Executors.newSingleThreadExecutor();
    private int leaderServerId = -1;
    private boolean leaderBased = false;


    /**
     * Reads static configuration from the 'config.json' file.
     *
     * @return ArchConf object containing configuration details.
     */
    public static ArchConf readStaticConf() {
        // Read configuration from the 'config.json' file and construct ArchConf object
        FileReader fileReader = null;
        JSONObject config = null;
        try {
            fileReader = new FileReader("./config.json");
            config = (JSONObject) new JSONParser().parse(fileReader);
        } catch (IOException | ParseException e) {
            throw new RuntimeException(e);
        }
        String host = "";
        int port;
        List<String> serviceNames;

        List<ServerNetConf> participantsConfs = new ArrayList<ServerNetConf>();
        List<Object> addressList = (List<Object>) ((JSONObject) config.get("nodes")).get("addressList");
        for (Object temp : addressList) {
            JSONObject temp1 = (JSONObject) temp;
            host = temp1.get("host").toString();
            port = (int)(long) temp1.get("port");
            serviceNames = new ArrayList<String>();
            for (Object temp3 : ((List<String>) temp1.get("serviceNameList"))) {
                serviceNames.add(temp3.toString());
            }
            participantsConfs.add(new ServerNetConf(host, port, serviceNames));
        }
        ArchConf fullConf = new ArchConf();
        boolean electLeader = (boolean) config.get("electLeader");
        float acceptRandomErrorProbability = (float)(double) config.get("acceptRandomErrorProbability");
        fullConf.setParticipantsConf(participantsConfs);
        fullConf.setLeaderBased(electLeader);
        fullConf.setAcceptRandomErrorProbability(acceptRandomErrorProbability);
        fullConf.setDelay((long)config.get("delay"));
        fullConf.setAllowNodeRestart((boolean) config.get("allowNodeRestart"));
        return fullConf;
    }

    /**
     * Generates random exceptions based on a configured probability.
     *
     * @throws RemoteBusinessException If a random error is generated
     */
    private void generateRandomExceptions () throws RemoteBusinessException {
        Random random = new Random();
        float randomProbability = random.nextFloat();
        if (randomProbability < this.sConfig.getAcceptRandomErrorProbability()) {
            throw new RemoteBusinessException("Random error generated at " + System.currentTimeMillis());
        }
    }

    /**
     * Performs authorization check. Throws an exception if the authorization ID is empty.
     *
     * @param authId Authorization ID to be checked.
     * @throws RemoteBusinessException if the authorization ID is empty.
     */
    private void authCheck(String authId) throws RemoteBusinessException {
        if (Utills.isEmptyString(authId)) {
            throw new RemoteBusinessException("Not Authorised");
        }
    }

    /**
     * Starts the Paxos server, exporting necessary RMI objects, and binding to the registry.
     *
     * @throws IOException if an I/O error occurs.
     */
    @Override
    public void startServer() throws IOException {
        this.logs = this.sConfig.isLogs();
        this.stubRPC = (StoreService) UnicastRemoteObject.exportObject((StoreService) this, 0);
        this.registryRPC = LocateRegistry.createRegistry(this.sConfig.getRpcPort());
        this.registryRPC.rebind(this.sConfig.getServiceKey().toString(), this);
        this.registryRPC.rebind(ServiceKeys.AcceptorService.toString(), this);
        this.registryRPC.rebind(ServiceKeys.LearnerService.toString(), this);
        final PaxosServer self = this;
        // starting cleanup thread
        Future<?> futureCleanUp = this.executor.submit(new Runnable() {
            @Override
            public void run() {
                self.logger("CleanUp thread started");
                try {
                    // Delay Starting
                    if (delay > 0) {
                        Thread.sleep(delay);
                    }
                } catch (InterruptedException e) {
                    // Ignore
                }
                while ((boolean) config.get("serverStarted") && !Utills.checkInterrupt()) {
                    try {
                        // Check all servers
                        int index = -1;
                        try {
                            for (AcceptorInterface acceptorInterface: self.getAcceptors()) {
                                index++;
                                if (acceptorInterface != null) {
                                    acceptorInterface.getAcceptorId();
                                }
                            }
                        } catch (Exception e) {
                            if (index > -1) {
                                self.handleParticipantServerFail(index);
                            }
                        }
                    } catch (Exception e) {
                        self.loggerEx(e);
                    }
                }
            }
        });
        this.futureList.add(futureCleanUp);
        if (this.leaderBased) {
            // Starting leader election thread
            Future<?> futureLeaderThread = this.executor.submit(new Runnable() {
                @Override
                public void run() {
                    self.logger("Election thread started");
                    while ((boolean) config.get("serverStarted") && !Utills.checkInterrupt()) {
                        try {
                            // Check leader alive
                            if (delay > 0) {
                                Thread.sleep(delay);
                            }
                            try {
                                self.getLeaderAccept().getAcceptorId();
                            } catch (Exception e) {
                                self.handleElectLeader();
                            }
                        } catch (InterruptedException e) {
                            // Ignore
                        }  catch (Exception e) {
                            self.loggerEx(e);
                        }
                    }
                }
            });
            this.futureList.add(futureLeaderThread);
        }
        this.config.put("serverStarted", true);
        this.logger("Server - " + this.serverId + " started");
    }

//    private int checkAliveNodes () {
//        try {
//            for (AcceptorInterface acceptorInterface: this.acceptors) {
//
//            }
//        } catch (Exception e) {
//
//        }
//    }

    /**
     * Checks the liveliness of the leader by invoking the getAcceptorId() method on the leader's AcceptorInterface.
     *
     * @return true if the leader is alive, false otherwise
     */
    private boolean checkLeaderAlive () {
        try {
            this.getLeaderAccept().getAcceptorId();
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    /**
     * Retrieves the AcceptorInterface at the specified index, handling self-reference appropriately.
     *
     * @param index The index of the AcceptorInterface to retrieve
     * @return The AcceptorInterface at the specified index
     */
    private synchronized AcceptorInterface getIndexAccept (int index) {
        if (index == this.serverId) {
            return (AcceptorInterface) this;
        } else {
            return this.acceptors.get(index);
        }
    }


    /**
     * Retrieves the LearnerInterface at the specified index, handling self-reference appropriately.
     *
     * @param index The index of the LearnerInterface to retrieve
     * @return The LearnerInterface at the specified index
     */
    private synchronized LearnerInterface getIndexLearn (int index) {
        if (index == this.serverId) {
            return (LearnerInterface) this;
        } else {
            return this.learners.get(index);
        }
    }

    /**
     * Sets the newly elected leader and handles the transition appropriately.
     *
     * @param leaderId The server ID of the newly elected leader
     */
    private synchronized void setNewElectedLeader (int leaderId) {
        this.leaderAccept = this.getIndexAccept(leaderId);
        this.leaderLearn = this.getIndexLearn(leaderId);
        this.handleParticipantServerFail(this.leaderServerId);
        this.leaderServerId = leaderId;
    }

    /**
     * Handles the election of a new leader by initiating an election operation.
     */
    private synchronized void handleElectLeader() {
        // Reducing the number of competition for being leader
//        int
//        if () {
//
//        }
        // Decrease the count of servers as one of them is dead
        Operation operation = new Operation(MethodType.ELECT, MethodType.ELECT.name(), String.valueOf(this.serverId), "internal");
            try {
                this.proposeOperation(operation);
            } catch (RemoteException e) {
                //Ignore You were not elected as leader
            }
    }

    /**
     * Handles the failure of a participant server. (To be implemented)
     *
     * @param index Index of the failed server in the list of acceptors.
     */
    private synchronized void handleParticipantServerFail (int index) {
        this.acceptors.set(index, null);
        this.learners.set(index, null);
        this.numServers = (int) this.acceptors.stream().filter(e -> e != null).count();
    }

    /**
     * Constructs a PaxosServer instance with the specified ServerConfig and clusterSemaphore.
     *
     * @param sConfig          The ServerConfig containing server-specific configuration settings
     * @param clusterSemaphore An object used for synchronization within the server cluster
     * @throws IOException If an I/O error occurs during server initialization
     */
    public PaxosServer(ServerConfig sConfig, Object clusterSemaphore) throws IOException {
        super(sConfig);
        this.numServers = sConfig.getNumServers();
        this.serverId = sConfig.getServerId();
        this.leaderBased = sConfig.isLeaderBased();
        this.clusterSemaphores = clusterSemaphore;
        this.delay = sConfig.getDelay();
        this.startServer();
    }

    /**
     * Constructs a unique key for storage based on the authorization ID and key.
     *
     * @param authId Authorization ID.
     * @param key    Key for storage.
     * @return Concatenation of authorization ID and key.
     * @throws BusinessException if authorization ID is empty.
     */
    public String consStoreKey(String authId, String key) throws BusinessException {
        if (Utills.isEmptyString(authId)) {
            throw new BusinessException("Not Authorised");
        }
        if (Utills.isEmptyString(key)) {
            throw new BusinessException("Not key provided");
        }
//        return authId + "$-$" + key;
        return key;
    }

    /**
     * Constructs a store key for storage, catching and rethrowing BusinessException as RemoteBusinessException.
     *
     * @param authId Authorization ID.
     * @param key    Key for storage.
     * @return Concatenation of authorization ID and key.
     * @throws RemoteBusinessException if authorization ID is empty.
     */
    public String consStoreKeyRe(String authId, String key) throws RemoteBusinessException {
        try {
            return this.consStoreKey(authId, key);
        } catch (BusinessException e) {
            throw new RemoteBusinessException(e.getMessage());
        }
    }

    /**
     * Setter method to set the leader acceptor.
     *
     * @param leaderAccept Leader acceptor to be set.
     */
    public void setLeaderAccept(AcceptorInterface leaderAccept) {
        this.leaderAccept = leaderAccept;
    }

    /**
     * Setter method to set the leader learner.
     *
     * @param leaderLearn Leader learner to be set.
     */
    public void setLeaderLearn(LearnerInterface leaderLearn) {
        this.leaderLearn = leaderLearn;
    }

    public List<AcceptorInterface> getAcceptors() {
        return acceptors;
    }

    public List<LearnerInterface> getLearners() {
        return learners;
    }

    /**
     * Updates the server's state as the leader and propagates the update to all learners.
     *
     * @param type  The type of update operation (e.g., ADD, REMOVE)
     * @param key   The key associated with the update
     * @param value The value to be updated
     * @throws RemoteException If a remote communication error occurs
     */
    @Override
    public synchronized void leaderUpdate (MethodType type, String key, Object value) throws RemoteException{
        try {
            this.updateStore(type, key, value);
        } catch (Exception e) {
            // Ignore
        }
        for (LearnerInterface learnerInterface: this.learners) {
            try {
                learnerInterface.updateStore(type, key, value);
            } catch (Exception e) {
                // Ignore
            }
        }
    }

    @Override
    public synchronized void addNewLearner(LearnerInterface learnerInterface) throws RemoteException {
        this.learners.set(learnerInterface.getLearnerId(), learnerInterface);
        this.numServers = (int) this.learners.stream().filter(e -> e != null).count();
    }

    /**
     * Proposes a PUT operation to the Paxos algorithm.
     *
     * @param key    Key for the operation.
     * @param value  Value for the operation.
     * @param authId Authorization ID for the operation.
     * @throws RemoteException if a remote error occurs.
     */
    @Override
    public void putData(String key, Object value, String authId) throws RemoteException {
        this.authCheck(authId);
        if (this.leaderBased) {
            this.leaderUpdateWrapper(MethodType.PUT, key, value);
        } else {
            this.proposeOperation(new Operation(MethodType.PUT, key, value, authId));
        }
    }

    /**
     * Retrieves data for the given key using the Paxos algorithm.
     *
     * @param key    Key to retrieve data for.
     * @param authId Authorization ID for the operation.
     * @return Retrieved data.
     * @throws RemoteException if a remote error occurs.
     */
    @Override
    public Object getData(String key, String authId) throws RemoteException {
        this.authCheck(authId);
        String storeKey = this.consStoreKeyRe(authId, key);
        if (this.store.containsKey(storeKey)) {
            return this.store.get(storeKey);
        }
        return null;
    }

    /**
     * Wrapper method for leader updates, ensuring the update is sent to the current leader.
     * Retries the operation with a delay if the leader is not alive.
     *
     * @param type  The type of update operation (e.g., ADD, REMOVE)
     * @param key   The key associated with the update
     * @param value The value to be updated
     * @throws RemoteException If a remote communication error occurs
     */
    private void leaderUpdateWrapper (MethodType type, String key, Object value) throws RemoteException{
        boolean continueLoop = true;
        int counter = 0;
        // Continue Trying after every delay amount if leader is not alive
        while (continueLoop) {
            if (counter > this.sConfig.getRequestAttempts()) {
                throw new RemoteBusinessException("Update failed");
            }
            try {
                counter++;
                this.leaderLearn.leaderUpdate(type, key, value);
                continueLoop = false;
            } catch (Exception e) {
                try {
                    continueLoop = true;
                    Thread.sleep(this.delay);
                } catch (InterruptedException ex) {
                    // Ignore
                }
            }
        }
    }

    /**
     * Proposes a DELETE operation to the Paxos algorithm.
     *
     * @param key    Key for the operation.
     * @param authId Authorization ID for the operation.
     * @throws RemoteException if a remote error occurs.
     */
    @Override
    public void deleteData(String key, String authId) throws RemoteException {
        this.authCheck(authId);
        if (this.leaderBased) {
            this.leaderUpdateWrapper(MethodType.DELETE, key, null);
        } else {
            this.proposeOperation(new Operation(MethodType.DELETE, key, "DELETE", authId));
        }
    }

    /**
     * Stops the Paxos server, notifying other servers if necessary.
     *
     * @param authId Authorization ID for the operation.
     * @param force  Flag indicating whether to force-stop the server.
     * @throws RemoteException if a remote error occurs.
     */
    @Override
    public void stopServer(String authId, boolean force) throws RemoteException {
        this.authCheck(authId);
        Object semaphore = this.clusterSemaphores;
        synchronized (semaphore) {
            semaphore.notifyAll();
        }
    }

    /**
     * Logs the global state of the server.
     */
    @Override
    public void logGlobalState () {
        this.logWriter.logger("Current State:- " + this.store.toString());
    }

    /**
     * Updates the server's state based on the specified operation.
     *
     * @param type  The type of update operation (PUT, DELETE, LOG)
     * @param key   The key associated with the update
     * @param value The value to be updated
     * @throws RemoteException If a remote communication error occurs
     */
    @Override
    public void updateStore(MethodType type, String key, Object value) throws RemoteException {
        switch (type) {
            case PUT -> this.store.put(key, value);
            case DELETE -> this.store.remove(key);
            case LOG -> this.logWriter.logger(value + " -> Current Store:- " + this.store.toString() + " ; Leader server Id:- " + this.leaderServerId + " ; Server view of connections :- " + this.acceptors + " ; Timestamp:- " + System.currentTimeMillis());
        }
    }

    /**
     * Initiates the global state with a given name using a LOG operation.
     *
     * @param name Name for the global state initiation.
     * @throws RemoteException if a remote error occurs.
     */
    @Override
    public void initiateGlobalState(String name) throws RemoteException {
        if (this.leaderBased) {
            this.leaderUpdateWrapper(MethodType.LOG, "logGlobalState", name);
        } else {
            Operation logOperation = new Operation(MethodType.LOG, "logGlobalState", name, "internal");
            this.proposeOperation(logOperation);
        }
    }

    /**
     * Requests a specific server to stop asynchronously.
     *
     * @param index The index of the server to be stopped
     * @throws RemoteException If a remote communication error occurs
     */
    @Override
    public synchronized void askToStopASpecificServer(int index) throws RemoteException {
        if (index == this.serverId) {
            this.stopServerAsync(false);
        } else {
            AcceptorInterface acceptorInterface = this.getIndexAccept(index);
            acceptorInterface.askStopServerAsync(false, -1);
        }
    }

    /**
     * Initiates an asynchronous request to stop the server with optional force and sleep parameters.
     *
     * @param force Whether to force stop the server
     * @param sleep The duration to sleep before stopping the server
     * @throws RemoteException If a remote communication error occurs
     */
    @Override
    public void askStopServerAsync (boolean force, long sleep) throws RemoteException{
        this.stopServerAsync(force, sleep);
    }

    @Override
    public synchronized void addNewAcceptor(AcceptorInterface acceptorInterface) throws RemoteException {
        this.acceptors.set(acceptorInterface.getAcceptorId(), acceptorInterface);
        this.numServers = (int) this.acceptors.stream().filter(e -> e != null).count();
    }

    @Override
    public synchronized void addNewNode(AcceptorInterface acceptorInterface, LearnerInterface learnerInterface) throws RemoteException {
        this.acceptors.set(acceptorInterface.getAcceptorId(), acceptorInterface);
        this.learners.set(learnerInterface.getLearnerId(), learnerInterface);
        this.numServers = (int) this.acceptors.stream().filter(e -> e != null).count();
    }

    /**
     * Generates a unique proposal ID.
     *
     * @return Unique proposal ID.
     */
    private long generateProposalId() {
        // Placeholder code to generate a unique proposal ID
        long currentTime = System.currentTimeMillis();
        currentTime = Long.valueOf(String.valueOf(currentTime) + this.serverId);
        return currentTime;
    }

    /**
     * Proposes a value using the Paxos algorithm.
     *
     * @param proposalId    Proposal ID for the operation.
     * @param proposalValue Proposal value to be proposed.
     * @throws RemoteException          if a remote error occurs.
     * @throws RemoteBusinessException if the proposal is rejected.
     */
    @Override
    public synchronized void propose(long proposalId, Object proposalValue) throws RemoteException {
        int serverSize = this.numServers;
        int threshold = (int) (serverSize / 2);
        // for self server handling
        List<List<Object>> results = new LinkedList<List<Object>>();
        List<Object> result = null;
        for (AcceptorInterface acceptorInterface: this.acceptors) {
            try {
                result = acceptorInterface.prepareWrapper(proposalId, proposalValue);
                results.add(result);
            } catch (Exception e) {
                // Ignore If there is exception
            }
        }
        // Checking majority
        if (results.size() > threshold) {
            // Checking promise results
            Operation paramOps = (Operation) proposalValue;
            long highestProposalId = proposalId;
            for (List<Object> list : results) {
                if (list.size() == 3) {
                    long paramProposalId = (long)list.get(0);
                    long acceptedProposalId = (long)list.get(1);
                    Operation acceptedOperation = (Operation) list.get(2);
                    if (acceptedProposalId > highestProposalId) {
                        highestProposalId = acceptedProposalId;
                        paramOps.setValue(acceptedOperation.getValue());
                    }
                }
            }
        } else {
            throw new RemoteBusinessException("Restart prepare majority did not agree");
        }
    }
    private String consAcceptedKkeys (Operation operation) {
        return operation.getKey();
    }

    /**
     * Wrapper method for the prepare phase, ensuring the proposal has a higher priority before proceeding.
     *
     * @param proposalId     The proposal ID for the prepare phase
     * @param proposalValue  The value associated with the proposal
     * @return A list of objects representing the result of the prepare phase
     * @throws RemoteException       If a remote communication error occurs
     * @throws RemoteBusinessException If a lower-priority proposal is detected, prompting a transaction restart
     */
    @Override
    public List<Object> prepareWrapper (long proposalId, Object proposalValue) throws RemoteException {
        if (proposalId > this.lastPreparedProposalId) {
            return this.prepare(proposalId, proposalValue);
        } else {
            throw new RemoteBusinessException("Lower Priority Proposed, Restart Transaction");
        }
    }

    /**
     * Prepares for a proposal in the Paxos algorithm.
     *
     * @param proposalId    Proposal ID for the operation.
     * @param proposalValue Proposal value to be prepared.
     * @return List of prepare results.
     * @throws RemoteException          if a remote error occurs.
     * @throws RemoteBusinessException if the proposal is rejected.
     */
    private synchronized List<Object> prepare(long proposalId, Object proposalValue) throws RemoteException {
        this.lastPreparedProposalId = proposalId;
        List<Object> result = new LinkedList<Object>();
        result.add(proposalId);
        // Checking if this acceptor has accepted a value for same key previously
        Operation operation = (Operation) proposalValue;
        if (this.acceptedPaxosKeys.containsKey(this.consAcceptedKkeys(operation))) {
            Operation acceptedValue = this.acceptedPaxosKeys.get(this.consAcceptedKkeys(operation));
            result.add(acceptedValue.getProposalId());
            result.add(acceptedValue);
        }
        return result;
    }

    /**
     * Proposes an operation using the Paxos algorithm, including the prepare and accept phases.
     *
     * @param operation Operation to be proposed.
     * @throws RemoteException          if a remote error occurs.
     * @throws RemoteBusinessException if the proposal is rejected.
     */
    private void proposeOperation(Operation operation) throws RemoteException {
        long proposalId;
        boolean proposeSuccess = false;
        int serverSize = this.numServers;
        int threshold = (int) (serverSize / 2);

        // TODO propose retry attempt implementation
//        while (!proposeSuccess && !Utills.checkInterrupt()) {
        proposalId = this.generateProposalId();
        try {
            operation.setProposalId(proposalId);
            this.propose(proposalId, operation);
            proposeSuccess = true;
        } catch (Exception e) {
            // Ignore
        }
//        }

        // Accept phase
        if (proposeSuccess) {
            List<List<Object>> results = new LinkedList<List<Object>>();
            List<Object> result = null;
            // Call all acceptors
            for (AcceptorInterface acceptorInterface: this.acceptors) {
                try {
                    result = acceptorInterface.acceptWrapper(proposalId, operation);
                    results.add(result);
                } catch (Exception e) {
                    // Ignore if any acceptor fails
                }
            }
            if (results.size() > threshold) {
                Operation consensusValue = (Operation) results.get(0).get(1);
                operation.setValue(consensusValue.getValue());
            } else {
                // Notify user about transaction failure and user will try again with updated values
                throw new RemoteBusinessException("transaction failed");
            }
        }

    }

    /**
     * Asynchronously calls the learn method on all learners for a given proposal ID and proposed value.
     *
     * @param proposalId    Proposal ID associated with the value.
     * @param proposedValue Value proposed to be learned.
     */
    private void callLearners (long proposalId, Operation proposedValue) {
        // executing learning on other servers asynchronously to avoid deadlock
        final List<LearnerInterface> learners = this.learners;
        final PaxosServer self = this;
        // Submit a task to the learnExecutor to call the learn method on all learners
        Future<?> future = this.learnExecutor.submit(new Runnable() {
            @Override
            public void run() {
                for (LearnerInterface learnerInterface: learners) {
                    try {
                        // Call the learn method on all other learners
                        learnerInterface.learn(proposalId, proposedValue);
                    } catch (Exception e) {
//                        self.loggerEx(e);
                    }
                }
            }
        });
        // Add the future to the list of futures for tracking
        this.futureList.add(future);
    }

    /**
     * Accepts a proposal in the Paxos algorithm.
     *
     * @param proposalId    Proposal ID for the operation.
     * @param proposalValue Proposal value to be accepted.
     * @return List of accept results.
     * @throws RemoteException          if a remote error occurs.
     * @throws RemoteBusinessException if the proposal is rejected.
     */
    private synchronized List<Object> accept(long proposalId, Object proposalValue) throws RemoteException {
        this.generateRandomExceptions();
        final Operation temp = (Operation) proposalValue;
        this.acceptedPaxosKeys.put(this.consAcceptedKkeys(temp), temp);
        List<Object> result = new LinkedList<Object>();
        result.add(proposalId);
        result.add(temp);
        this.callLearners(proposalId, temp);
        return result;
    }

    /**
     * Wrapper method for the accept phase, ensuring the proposal has an equal or higher priority before proceeding.
     *
     * @param proposalId     The proposal ID for the accept phase
     * @param proposalValue  The value associated with the proposal
     * @return A list of objects representing the result of the accept phase
     * @throws RemoteException       If a remote communication error occurs
     * @throws RemoteBusinessException If a lower-priority proposal ID is detected in the Accept-Request phase
     */
    @Override
    public List<Object> acceptWrapper (long proposalId, Object proposalValue) throws RemoteException {
        if (proposalId >= this.lastPreparedProposalId) {
            return this.accept(proposalId, proposalValue);
        } else {
            throw new RemoteBusinessException("Proposal ID lower in Accept-Request phase");
        }
    }

    /**
     * Resets the learner map for a given proposal ID and operation.
     *
     * @param proposalId Proposal ID to reset.
     * @param operation  Operation to reset.
     */
    private void reset(long proposalId, Operation operation) {
        this.learnerMap.remove(operation);
    }

    /**
     * Learns and processes the accepted value in the Paxos algorithm.
     *
     * @param proposalId    Proposal ID for the accepted value.
     * @param acceptedValue Accepted value to be learned.
     * @throws RemoteException          if a remote error occurs.
     * @throws RemoteBusinessException if learning fails.
     */
    @Override
    public synchronized void learn(long proposalId, Object acceptedValue) throws RemoteException {
        Operation operation = (Operation) acceptedValue;
//        if (this.acceptedPaxosKeys.containsKey(this.consAcceptedKkeys(operation))) {
            if (!this.learnerMap.containsKey(operation)) {
                this.learnerMap.put(operation, 0);
            }
            int count = this.learnerMap.get(operation) + 1;
            this.learnerMap.put(operation, count);
            int threshold = (int)this.numServers / 2;
            if (count > threshold) {
                try {
                    this.applyOperation(operation);
                    this.reset(proposalId, operation);
                } catch (BusinessException e) {
                    throw new RemoteBusinessException(e.getMessage());
                }
            }
//        } else {
//            throw new RemoteBusinessException("Transaction not accepted");
//        }
    }

    /**
     * Applies the proposed operation to the server's state.
     *
     * @param operation Operation to be applied.
     * @throws BusinessException if an illegal argument is encountered.
     */
    private void applyOperation(Operation operation) throws BusinessException {
        if (operation == null) return;
        String storeKey = this.consStoreKey(operation.getAuthId(), operation.getKey());
        switch (operation.getType()) {
            case PUT -> this.store.put(storeKey, operation.getValue());
            case DELETE -> this.store.remove(storeKey);
            case LOG -> this.logWriter.logger(operation.getValue() + " -> Current Store:- " + this.store.toString() + " ; Leader server Id:- " + this.leaderServerId + " ; Server view of connections :- " + this.acceptors + " ; Timestamp:- " + System.currentTimeMillis());
            case ELECT -> {
                int leaderId = Integer.parseInt((String)operation.getValue());
                this.setNewElectedLeader(leaderId);
                this.logWriter.logger("Server " + leaderId + " elected as leader");
            }
            default -> throw new BusinessException("Illegal Argument Exception");
        }
    }

    /**
     * Stops the server asynchronously with an optional delay.
     *
     * @param force Flag indicating whether to force-stop the server.
     * @param sleep Delay before stopping the server.
     */
    public void stopServerAsync (boolean force, long sleep) {
        final PaxosServer self = this;
        this.executor.submit(new Runnable() {
            @Override
            public void run() {
                if (sleep > 0) {
                    try {
                        Thread.sleep(sleep);
                    } catch (InterruptedException e) {
                        // Ignore
                    }
                }
                self.stopServer(force);
            }
        });
    }

    /**
     * Stops the Paxos server, performing necessary cleanup.
     *
     * @param force Flag indicating whether to force-stop the server.
     * @return True if the server is successfully stopped, false otherwise.
     */
    @Override
    public boolean stopServer(boolean force) {
        try {
            this.logger("Server Stop Called");
            this.config.put("serverStarted", false);
            this.registryRPC.unbind(this.sConfig.getServiceKey().toString());
            UnicastRemoteObject.unexportObject((StoreService) this, true);
            UnicastRemoteObject.unexportObject(this.registryRPC, true);
            this.logger("Child thread interrupt called");
            for (Future<?> future : this.futureList) {
                future.cancel(true);
            }
            if (force) {
                this.executor.shutdownNow();
                this.learnExecutor.shutdownNow();
            } else {
                this.executor.shutdown();
                this.learnExecutor.shutdown();
            }
            while (!this.executor.isTerminated() && !this.learnExecutor.isTerminated() && !Thread.interrupted()) {
            }
            this.logger("Server Stopped");
            this.logWriter.close();
            return true;
        } catch (Exception e) {
            e.printStackTrace();
            this.loggerEx(e);
        }
        return false;
    }

    public int getServerId() {
        return serverId;
    }

    public void setServerId(int serverId) {
        this.serverId = serverId;
    }

    public boolean isLogs() {
        return logs;
    }

    public void setLogs(boolean logs) {
        this.logs = logs;
    }

    public AcceptorInterface getLeaderAccept() {
        return leaderAccept;
    }

    public LearnerInterface getLeaderLearn() {
        return leaderLearn;
    }

    @Override
    public int getAcceptorId () {
        return this.serverId;
    }
    @Override
    public int getLearnerId () {
        return this.serverId;
    }

    public long getDelay() {
        return delay;
    }

    public void setDelay(int delay) {
        this.delay = delay;
    }
    public void setLeaderServerId(int leaderServerId) {
        this.leaderServerId = leaderServerId;
    }

    public int getLeaderServerId() {
        return leaderServerId;
    }
}