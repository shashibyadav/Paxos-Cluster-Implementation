package com.project.four.server;

import com.project.four.implementation.CoordinatorServiceImpl;
import com.project.four.interfaces.CoordinatorService;
import com.project.four.utills.ArchConf;
import com.project.four.utills.ServerNetConf;
import org.json.simple.JSONObject;
import org.json.simple.parser.JSONParser;
import org.json.simple.parser.ParseException;

import java.io.FileReader;
import java.io.IOException;
import java.rmi.AlreadyBoundException;
import java.rmi.NotBoundException;
import java.rmi.RemoteException;
import java.rmi.registry.LocateRegistry;
import java.rmi.registry.Registry;
import java.rmi.server.UnicastRemoteObject;
import java.util.ArrayList;
import java.util.List;

/**
 * The CoordinatorServer class orchestrates the setup and management of the distributed system's coordinator server.
 * It includes methods for reading configuration, starting and stopping the server, and managing the RMI registry.
 */
public class CoordinatorServer {

    private ArchConf fullConfig = null;
    private CoordinatorServiceImpl coordinatorService = null;
    private Registry coordinatorRegistry = null;

    /**
     * Reads the static configuration from the "config.json" file and initializes the fullConfig member variable.
     */
    public static ArchConf readStaticConf() {
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
        List<String> serviceNames = new ArrayList<String>();
        JSONObject coordinatorConfgJ = (JSONObject) config.get("coordinatorServer");
        host = coordinatorConfgJ.get("host").toString();
        port = (int)(long)(coordinatorConfgJ.get("port"));
        serviceNames.add(coordinatorConfgJ.get("serviceName").toString());
        ServerNetConf coordinatorConfig = new ServerNetConf(host, port, serviceNames);

        // Creating participants config list
        List<ServerNetConf> participantsConfs = new ArrayList<ServerNetConf>();
        List<Object> addressList = (List<Object>) ((JSONObject) config.get("participants")).get("addressList");
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
        fullConf.setCoordinatorConf(coordinatorConfig);
        fullConf.setParticipantsConf(participantsConfs);
        return fullConf;
    }

    /**
     * Reads the static configuration and initializes the fullConfig member variable.
     */
    private void readConf() {
        this.fullConfig = CoordinatorServer.readStaticConf();
    }

    /**
     * Starts the coordinator server by creating and binding the RMI registry and initializing the coordinator service.
     *
     * @throws RemoteException        if there is a problem with remote communication during the setup.
     * @throws AlreadyBoundException   if the registry is already bound to the specified name.
     */
    private void startCoordinatorServer() throws RemoteException, AlreadyBoundException {
        ServerNetConf coordinatorConf = this.fullConfig.getCoordinatorConf();
        this.coordinatorService = new CoordinatorServiceImpl(this.fullConfig.getParticipantsConf());
        CoordinatorService coordinator = (CoordinatorService) this.coordinatorService;
        this.coordinatorRegistry = LocateRegistry.createRegistry(coordinatorConf.getPort());
        this.coordinatorRegistry.bind(coordinatorConf.getServiceNames().get(0).name(), coordinator);
    }

    /**
     * Starts the entire server by reading the configuration and starting the coordinator server.
     *
     * @throws RemoteException        if there is a problem with remote communication during the setup.
     * @throws AlreadyBoundException   if the registry is already bound to the specified name.
     */
    public void startServer() throws RemoteException, AlreadyBoundException {
        // Reading Config File
        this.readConf();

        // Start Server
        this.startCoordinatorServer();

    }


    /**
     * Stops the server by unbinding the coordinator service and unexporting objects.
     *
     * @throws RemoteException      if there is a problem with remote communication during the shutdown.
     * @throws NotBoundException     if the registry does not contain an entry for the specified name.
     */
    public void stopServer() throws RemoteException, NotBoundException {
        this.coordinatorRegistry.unbind(ProjectEnums.ServiceKeys.CoordinatorService.toString());
        UnicastRemoteObject.unexportObject((CoordinatorService) this.coordinatorService, true);
        UnicastRemoteObject.unexportObject(this.coordinatorRegistry, true);
    }

    // Getter and Setter methods for member variables
    public ArchConf getFullConfig() {
        return fullConfig;
    }

    public void setFullConfig(ArchConf fullConfig) {
        this.fullConfig = fullConfig;
    }

    public CoordinatorServiceImpl getCoordinatorService() {
        return coordinatorService;
    }

    public void setCoordinatorService(CoordinatorServiceImpl coordinatorService) {
        this.coordinatorService = coordinatorService;
    }
}
