package com.project.four;

import com.project.four.interfaces.AcceptorInterface;
import com.project.four.interfaces.LearnerInterface;
import com.project.four.server.PaxosServer;
import com.project.four.server.ProjectEnums.ServiceKeys;
import com.project.four.server.ServerConfig;
import com.project.four.utills.*;

import java.io.IOException;
import java.rmi.NotBoundException;
import java.rmi.RemoteException;
import java.rmi.registry.LocateRegistry;
import java.rmi.registry.Registry;
import java.util.ArrayList;
import java.util.LinkedList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

/**
 * Class responsible for running multiple instances of Paxos servers and setting up communication between them.
 */
public class PaxosServerRun {
    public static void restartServer (int index, ArchConf fullConf, List<PaxosServer> participantList, List<AcceptorInterface> acceptorInterfaces, List<LearnerInterface> learnerInterfaces, Object stopServer) throws IOException, NotBoundException, BusinessException {
        ServerNetConf participantConf = fullConf.getParticipantsConf().get(index);
        ServerConfig sConfig = new ServerConfig();
        sConfig.setNumServers(participantList.size());
        sConfig.setRpcPort(participantConf.getPort());
        sConfig.setServerHost(participantConf.getHost());
        sConfig.setServiceKey(ServiceKeys.StoreService);
        sConfig.setServerId(index);
        sConfig.setLeaderBased(fullConf.isLeaderBased());
        sConfig.setDelay(fullConf.getDelay());
        sConfig.setRequestAttempts(fullConf.getRequestAttempts());
        sConfig.setAcceptRandomErrorProbability(fullConf.getAcceptRandomErrorProbability());
        // Create and initialize a PaxosServer instance
        PaxosServer server = new PaxosServer(sConfig, stopServer);
        server.setServerId(index);
        // find a working node
        Optional<PaxosServer> findNode = participantList.stream().filter(e -> {
            try {
                e.getServerId();
                return true;
            } catch (Exception ex) {
                return false;
            }
        }).findFirst();
        PaxosServer workingNode = null;
        if (findNode.isEmpty()) {
            throw new BusinessException("No nodes are alive");
        }
        workingNode = findNode.get();
        if (fullConf.isLeaderBased()) {
            server.setLeaderAccept(acceptorInterfaces.get(workingNode.getLeaderServerId()));
            server.setLeaderLearn(learnerInterfaces.get(workingNode.getLeaderServerId()));
            server.setLeaderServerId(workingNode.getLeaderServerId());
        }
        participantList.set(index, server);
        // Lookup Acceptor and Learner services via RMI
        Registry registry = LocateRegistry.getRegistry(sConfig.getServerHost(), sConfig.getRpcPort());
        AcceptorInterface acceptorInterface = (AcceptorInterface) registry.lookup(ServiceKeys.AcceptorService.toString());
        LearnerInterface learnerInterface = (LearnerInterface) registry.lookup(ServiceKeys.LearnerService.toString());
        acceptorInterfaces.set(index, acceptorInterface);
        learnerInterfaces.set(index, learnerInterface);

        //Setting interfaces in new Server
        for (int i = 0 ; i < participantList.size() ; i++) {
            if (i == index) {
                server.getAcceptors().add((AcceptorInterface) server);
                server.getLearners().add((LearnerInterface) server);
            } else {
                server.getAcceptors().add(acceptorInterfaces.get(i));
                server.getLearners().add(learnerInterfaces.get(i));
            }
        }

        // Adding new Server to existing network of Nodes
        for (int i = 0 ; i < acceptorInterfaces.size() ; i ++) {
            if (i == index) {
                continue;
            }
            acceptorInterfaces.get(i).addNewNode(acceptorInterface, learnerInterface);
        }
//        for (AcceptorInterface acceptorInterface1 : acceptorInterfaces) {
//
//            acceptorInterface1.addNewNode(acceptorInterface, learnerInterface);
//        }

    }
    /**
     * Runs the Paxos server with the specified configuration.
     *
     * @param args Command line arguments (not used in this method)
     * @throws Exception If an exception occurs during server execution
     */
    public static void run(String[] args) throws Exception {
        // Object used for synchronization and stopping the server
        final Object stopServer = new Object();
        // Read the static configuration for Paxos
        final ArchConf fullConf = PaxosServer.readStaticConf();
        // List to store Paxos server instances
        final List<PaxosServer> participantsList = new ArrayList<PaxosServer>();
        int counter = 0;
        int numServers = fullConf.getParticipantsConf().size();
        // Testing Async Executor
        ExecutorService executors = Executors.newCachedThreadPool();
        List<Future<?>> futureList = new LinkedList<Future<?>>();
        // Starting all participants
        for (ServerNetConf participantConf: fullConf.getParticipantsConf()) {
            ServerConfig sConfig = new ServerConfig();
            sConfig.setNumServers(numServers);
            sConfig.setRpcPort(participantConf.getPort());
            sConfig.setServerHost(participantConf.getHost());
            sConfig.setServiceKey(ServiceKeys.StoreService);
            sConfig.setServerId(counter);
            sConfig.setLeaderBased(fullConf.isLeaderBased());
            sConfig.setDelay(fullConf.getDelay());
            sConfig.setRequestAttempts(fullConf.getRequestAttempts());
            sConfig.setAcceptRandomErrorProbability(fullConf.getAcceptRandomErrorProbability());
            // Create and initialize a PaxosServer instance
            PaxosServer server = new PaxosServer(sConfig, stopServer);
            server.setServerId(counter);
            participantsList.add(server);
            // Set Self as leader in first server as first will be the leader at startup
            if (counter == 0 && fullConf.isLeaderBased()) {
                server.setLeaderAccept((AcceptorInterface) server);
                server.setLeaderLearn((LearnerInterface) server);
                server.setLeaderServerId(0);
            }
            counter++;
        }

        // Starting RMI service for each node
        final List<AcceptorInterface> acceptorInterfaces = new LinkedList<AcceptorInterface>();
        final List<LearnerInterface> learnerInterfaces = new LinkedList<LearnerInterface>();
        for (int i = 0 ; i < participantsList.size() ; i++) {
            PaxosServer server = participantsList.get(i);
            ServerConfig config = server.getsConfig();
            // Lookup Acceptor and Learner services via RMI
            Registry registry = LocateRegistry.getRegistry(config.getServerHost(), config.getRpcPort());
            AcceptorInterface acceptorInterface = (AcceptorInterface) registry.lookup(ServiceKeys.AcceptorService.toString());
            LearnerInterface learnerInterface = (LearnerInterface) registry.lookup(ServiceKeys.LearnerService.toString());
            acceptorInterfaces.add(acceptorInterface);
            learnerInterfaces.add(learnerInterface);
        }

        AcceptorInterface leaderAccept = acceptorInterfaces.get(0);
        LearnerInterface leaderLearner = learnerInterfaces.get(0);
        // Starting RMI network between Paxos nodes
        for (int i = 0 ; i < participantsList.size() ; i++) {
            PaxosServer server = participantsList.get(i);
            for (int j = 0 ; j < participantsList.size() ; j++) {
                if (i == j) {
                    server.getAcceptors().add((AcceptorInterface) server);
                    server.getLearners().add((LearnerInterface) server);
                } else {
                    server.getAcceptors().add(acceptorInterfaces.get(j));
                    server.getLearners().add(learnerInterfaces.get(j));
                }
                // Assigning first server as leader to nodes other than leader itself
                if (i != 0 && fullConf.isLeaderBased()) {
                    server.setLeaderAccept(leaderAccept);
                    server.setLeaderLearn(leaderLearner);
                    server.setLeaderServerId(0);
                }
            }
        }
        // Restart Server thread. This thread will restart a node if it finds it inactive, it runs every e times @delay millisecs.
        if (fullConf.isAllowNodeRestart()) {
            Future<?> futureRestart = executors.submit(new Runnable() {
                @Override
                public void run() {
                    while (!Utills.checkInterrupt()) {
                        try {
                            Thread.sleep(fullConf.getDelay() * 3);
                        } catch (InterruptedException e) {
                            // Ignore
                        }
                        // Check each server status and restart it if found inactive
                        int counter = -1;
                        for (AcceptorInterface acceptorInterface: acceptorInterfaces) {
                            try {
                                counter++;
                                acceptorInterface.getAcceptorId();
                            } catch (RemoteException e) {
                                try {
                                    restartServer(counter, fullConf, participantsList, acceptorInterfaces, learnerInterfaces, stopServer);
                                } catch (Exception ex) {
                                    // Failed to restart server
                                }
                            }
                        }
                    }
                }
            });
            futureList.add(futureRestart);
        }

        // Wait until notified to stop the server
        synchronized (stopServer) {
            stopServer.wait();
        }
        // Stopping all Threads
        for (Future<?> future: futureList) {
            future.cancel(true);
        }
        executors.shutdownNow();
        while (!executors.isTerminated()) {}
        // Stop all Paxos servers
        for (PaxosServer server: participantsList) {
            try {
                // allowing 1000 milliseconds to each server to gracefully shutdown
                server.stopServerAsync(true, 1000);
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
    }
}
