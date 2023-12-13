package com.project.four.server;

import com.project.four.implementation.StoreServiceImpl;
import com.project.four.interfaces.StoreService;
import com.project.four.server.ProjectEnums.ConnectionType;
import com.project.four.server.ProjectEnums.DataKeys;
import com.project.four.server.ProjectEnums.MethodType;
import com.project.four.server.ProjectEnums.RequestKeys;
import com.project.four.utills.*;

import java.io.*;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.rmi.RemoteException;
import java.rmi.registry.LocateRegistry;
import java.rmi.registry.Registry;
import java.rmi.server.UnicastRemoteObject;
import java.util.*;
import java.util.concurrent.*;

/**
 * The Server class represents a server in a client-server system.
 */
public class Server {
    private ServerSocketM tcpSocket = null;
    private DatagramSocket udpSocket = null;
    protected ExecutorService executor = null;
    protected ServerConfig sConfig = null;
    protected Map<String, Object> config = new ConcurrentHashMap<String, Object>();
    protected List<Future<?>> futureList = new LinkedList<Future<?>>();
    private Set<SocketM> tcpSocketSet = new CopyOnWriteArraySet<SocketM>();
    private Set<SocketM> udpSocketSet = new CopyOnWriteArraySet<SocketM>();
    protected Map<String, Object> store = new ConcurrentHashMap<String, Object>();
    private Queue<DatagramPacket> udpDatagramPackQ = new ConcurrentLinkedQueue<DatagramPacket>();
    protected Logger logWriter = null;
    private StoreServiceImpl serverRPC = null;
    protected StoreService stubRPC = null;
    protected Registry registryRPC = null;
    protected Object clusterSemaphores = null;

    /**
     * Constructor for the Server class.
     *
     * @param sConfig The ServerConfig object containing the configuration settings for the server.
     * @throws IOException If there is an error setting up the server.
     */
    public Server(ServerConfig sConfig) throws IOException {
        this.executor = sConfig.getExecutor();
        this.sConfig = sConfig;
        this.configSetup();
    }

    /**
     * Configure server on setup
     *
     * @throws IOException
     */
    private void configSetup() throws IOException {
        this.config.put("serverStarted", false);
        if (this.sConfig.isHasCoordinator()) {
            this.logWriter = new Logger("Server_"+this.sConfig.getRpcPort()+"_"+System.currentTimeMillis()+".txt");
        } else {
            this.logWriter = new Logger(null);
        }
        this.logWriter.setLogs(this.sConfig.isLogs());
    }

    /**
     * Log the given msg to given log writer
     *
     * @param msg
     */
    public void logger(String msg) {
        this.logWriter.logger(msg);
    }

    /**
     * Log the given exception to given log writer
     *
     * @param e
     */
    public void loggerEx(Exception e) {
        this.logWriter.loggerEx(e);
    }


	/**
	 * Starts the server by initializing the necessary components based on the configured connection types (TCP, UDP, and/or RPC).
	 * This method sets up the server's TCP and UDP sockets and launches threads to handle incoming connections and requests.
	 * If configured, it also starts an RPC service.
	 *
	 * @throws IOException If there is an issue creating the server sockets or RPC service.
	 */
    public void startServer() throws IOException {
        if (this.tcpSocket == null
                && (this.sConfig.getType() == ConnectionType.TCP || this.sConfig.getType() == ConnectionType.ALL)) {
            this.tcpSocket = new ServerSocketM(this.sConfig.getTcpPort(),
                    InetAddress.getByName(this.sConfig.getServerHost()));
        }
        if (this.udpSocket == null
                && (this.sConfig.getType() == ConnectionType.UDP || this.sConfig.getType() == ConnectionType.ALL)) {
            this.udpSocket = new DatagramSocket(this.sConfig.getUdpPort(),
                    InetAddress.getByName(this.sConfig.getServerHost()));
            this.udpSocket.setSoTimeout(this.sConfig.getRequestTimeout());
        }
        if (this.serverRPC == null
                && (this.sConfig.getType() == ConnectionType.RPC || this.sConfig.getType() == ConnectionType.ALL)) {
            this.serverRPC = new StoreServiceImpl(this, this.store, this.logWriter);
            this.serverRPC.setLogs(this.sConfig.isLogs());
//            this.stubRPC = (StoreService) UnicastRemoteObject.exportObject((StoreService) this.serverRPC, 0);
            this.registryRPC = LocateRegistry.createRegistry(this.sConfig.getRpcPort());
            this.registryRPC.rebind(this.sConfig.getServiceKey().toString(), this.serverRPC);
            this.logger("RPC started");
        }

        final Map<String, Object> config = this.config;
        final ServerSocketM tcpServerSocket = this.tcpSocket;
        final DatagramSocket udpServerSocket = this.udpSocket;
        final Set<SocketM> tcpSocketSet = this.tcpSocketSet;
        final Set<SocketM> udpSocketSet = this.udpSocketSet;
        final Map<String, Object> store = this.store;
        final Server self = this;
        this.config.put("serverStarted", true);
        // This thread does cleanup actions like removing closed connections from memory and can be used to perform background tasks
        Future<?> futureCleanUp = this.executor.submit(new Runnable() {

            @Override
            public void run() {
                self.logger("CleanUp thread started");
                while ((boolean) config.get("serverStarted") && !Utills.checkInterrupt()) {
                    try {
                        for (SocketM socket : tcpSocketSet) {
                            if (socket.isClosed()) {
                                tcpSocketSet.remove(socket);
                            }
                        }
                        for (SocketM socket : udpSocketSet) {
                            if (socket.isClosed()) {
                                tcpSocketSet.remove(socket);
                            }
                        }
                        Thread.sleep(5000);
                    } catch (InterruptedException e) {
                        if ((boolean) config.get("serverStarted")) {
                            self.loggerEx(e);
                        }
                    }
                }
            }
        });
        this.futureList.add(futureCleanUp);
        if (this.sConfig.getType() == ConnectionType.TCP || this.sConfig.getType() == ConnectionType.ALL) {
            // This Thread is used for accepting new TCP connections and storing it in the memory
            Future<?> futureTCP = this.executor.submit(new Runnable() {

                @Override
                public void run() {
                    self.logger("TCP accept Thread started");
                    while ((boolean) config.get("serverStarted") && !Utills.checkInterrupt()) {
                        SocketM sSocket = null;
                        try {
                            self.logger("waiting for client");
                            sSocket = tcpServerSocket.accept();
                            sSocket.initialize();
                            sSocket.setKeepAlive(true);
                            tcpSocketSet.add(sSocket);
                        } catch (Exception ex) {
                            if ((boolean) config.get("serverStarted")) {
                                self.loggerEx(ex);
                            }
                        }
                        Thread.yield();
                    }
                }

            });

            // This is the deamon thread for TCP requests, it processes all the requests by the clients.
            Future<?> futureTCPProcess = this.executor.submit(new Runnable() {

                @Override
                public void run() {
                    self.logger("TCP process thread started");
                    while ((boolean) config.get("serverStarted") && !Utills.checkInterrupt()) {
                        for (SocketM socket : tcpSocketSet) {
                            try {
                                if (socket.isConnected() && !socket.isClosed()
                                        && socket.getInputStream().available() > 0) {
                                    Server.tcpProcessRequest(socket, self, store);
                                }
                            } catch (ClassNotFoundException | IOException | BusinessException e) {
                                if ((boolean) config.get("serverStarted")) {
                                    self.loggerEx(e);
                                }
                            }
                        }
                        Thread.yield();
                    }

                }
            });
            this.futureList.add(futureTCP);
            this.futureList.add(futureTCPProcess);

        }
        if (this.sConfig.getType() == ConnectionType.UDP || this.sConfig.getType() == ConnectionType.ALL) {
            // This Thread is used for reading new messages from UDP port and storing it in the memory
            Future<?> futureUDP = this.executor.submit(new Runnable() {

                @Override
                public void run() {
                    self.logger("UDP accept thread started");
                    while ((boolean) config.get("serverStarted") && !Utills.checkInterrupt()) {
                        try {
                            if (udpServerSocket.isClosed()) {
                                throw new BusinessException("UDP socket is Closed");
                            }
                            Server.readUDPPacket(udpServerSocket, self);
                        } catch (IOException | BusinessException e) {
                            if ((boolean) config.get("serverStarted")) {
                                self.loggerEx(e);
                            }
                        }
                        Thread.yield();
                    }
                }

            });
            // This is the deamon thread for UDP requests, it processes all the messages in the queue.
            Future<?> futureUDPProcess = this.executor.submit(new Runnable() {

                @Override
                public void run() {
                    self.logger("UDP process thread started");
                    while ((boolean) config.get("serverStarted") && !Utills.checkInterrupt()) {
                        try {
                            if (!self.udpDatagramPackQ.isEmpty()) {
                                DatagramPacket dp = self.udpDatagramPackQ.poll();
                                Server.udpProcessRequest(dp, self, store);
                            }
                        } catch (ClassNotFoundException | IOException | BusinessException e) {
                            if ((boolean) config.get("serverStarted")) {
                                self.loggerEx(e);
                            }
                        }
                        Thread.yield();
                    }
                }
            });
            this.futureList.add(futureUDP);
            this.futureList.add(futureUDPProcess);
        }
        this.logger("Server started");
    }

    /**
     * Solution for homework 1, this function reverses and reverses the case of the given String in the request.
     *
     * @param request
     * @return
     */
    private String run(Map<String, Object> request) {
        String data = (String) request.get(MethodType.RUN.toString());
        char[] charArr = data.toCharArray();
        StringBuilder sb = new StringBuilder();
        for (int i = charArr.length - 1; i >= 0; i--) {
            char c = data.charAt(i);
            if (!Character.isAlphabetic(c)) {
                sb.append(c);
                continue;
            }
            if (Character.isUpperCase(c)) {
                sb.append(Character.toLowerCase(c));
            } else if (Character.isLowerCase(c)) {
                sb.append(Character.toUpperCase(c));
            }
        }
        return sb.toString();
    }

    /**
     * This method constructs key for storage unique to each client such that there is no collision of keys from concurrent clients in the memory.
     *
     * @param authId
     * @param key
     * @return
     * @throws BusinessException
     */
    public String consStoreKey(String authId, String key) throws BusinessException {
        if (Utills.isEmptyString(authId)) {
            throw new BusinessException("Not Authorised");
        }
        if (Utills.isEmptyString(key)) {
            throw new BusinessException("Not key provided");
        }
        return authId + "$-$" + key;
    }

    /**
     * This method deconstructs the storage key to client key to maintain transparency
     *
     * @param key
     * @return
     */
    public String destructStoreKey(String key) {
        return key.split("$-$")[1];
    }

    /**
     * This method constructs key for storage unique to each client such that there is no collision of keys from concurrent clients in the memory for RPC.
     *
     * @param authId
     * @param key
     * @return
     * @throws RemoteBusinessException
     */
    public String consStoreKeyRe(String authId, String key) throws RemoteBusinessException {
        if (Utills.isEmptyString(authId)) {
            throw new RemoteBusinessException("Not Authorised");
        }
        if (Utills.isEmptyString(key)) {
            throw new RemoteBusinessException("Not key provided");
        }
        return authId + "$-$" + key;
    }

    /**
     * This method is used by TCP process thread and contains logic to process TCP requests
     *
     * @param socket
     * @param self
     * @param store
     * @throws ClassNotFoundException
     * @throws IOException
     * @throws BusinessException
     */
    private static void tcpProcessRequest(SocketM socket, Server self, Map<String, Object> store)
            throws ClassNotFoundException, IOException, BusinessException {
        Map<RequestKeys, Object> data = Server.readInput(socket);
        MethodType rKey = (MethodType) data.get(RequestKeys.type);
        String authId = (String) data.get(RequestKeys.authId);
        if (Utills.isEmptyString(authId)) {
            throw new BusinessException("Not Authorised");
        }
        Map<String, Object> rData = Utills.getRequestData(data);
        Map<String, Object> result = new HashMap<String, Object>();
        Map<RequestKeys, Object> response = null;
        switch (rKey) {
            case PUT:
                for (String key : rData.keySet()) {
                    String storeKey = self.consStoreKey(authId, key);
                    store.put(storeKey, rData.get(key));
                }
                break;
            case GET:
                for (String key : rData.keySet()) {
                    String storeKey = self.consStoreKey(authId, key);
                    result.put(key, store.get(storeKey));
                }
                response = Utills.generateRequest(rKey, result, authId);
                Server.sendOutput(socket, response);
                break;
            case DELETE:
                for (String key : rData.keySet()) {
                    String storeKey = self.consStoreKey(authId, key);
                    store.remove(storeKey);
                }
                break;
            case STOP:
                Boolean force = (Boolean) rData.get(DataKeys.Priority.toString());
                self.stopServer(force);
                break;
            case RUN:
                String tempData = self.run(rData);
                result.put(MethodType.RUN.toString(), tempData);
                response = Utills.generateRequest(rKey, result, authId);
                Server.sendOutput(socket, response);
                break;
            default:
                break;
        }
    }

    /**
     * This method is used by UDP process thread and contains logic to process TCP requests
     *
     * @param packet
     * @param self
     * @param store
     * @throws ClassNotFoundException
     * @throws IOException
     * @throws BusinessException
     */
    private static void udpProcessRequest(DatagramPacket packet, Server self, Map<String, Object> store)
            throws ClassNotFoundException, IOException, BusinessException {
        Map<RequestKeys, Object> data = Server.readUDPInput(packet, self);
        MethodType rKey = (MethodType) data.get(RequestKeys.type);
        String authId = (String) data.get(RequestKeys.authId);
        if (Utills.isEmptyString(authId)) {
            throw new BusinessException("Not Authorised");
        }
        Map<String, Object> rData = Utills.getRequestData(data);
        Map<String, Object> result = new HashMap<String, Object>();
        Map<RequestKeys, Object> response = null;
        switch (rKey) {
            case PUT:
                for (String key : rData.keySet()) {
                    String storeKey = self.consStoreKey(authId, key);
                    store.put(storeKey, rData.get(key));
                }
                break;
            case GET:
                for (String key : rData.keySet()) {
                    String storeKey = self.consStoreKey(authId, key);
                    result.put(key, store.get(storeKey));
                }
                response = Utills.generateRequest(rKey, result, authId);
                Server.sendUDPOutput(packet, self, response);
                break;
            case DELETE:
                for (String key : rData.keySet()) {
                    String storeKey = self.consStoreKey(authId, key);
                    store.remove(storeKey);
                }
                break;
            case STOP:
                Boolean force = (Boolean) rData.get(DataKeys.Priority.toString());
                self.stopServer(force);
                break;
            case RUN:
                String tempData = self.run(rData);
                result.put(MethodType.RUN.toString(), tempData);
                response = Utills.generateRequest(rKey, result, authId);
                Server.sendUDPOutput(packet, self, response);
                break;
            default:
                break;
        }
    }

    /**
     * Reads an incoming UDP packet and extracts the request data from it.
     *
     * @param packet The DatagramPacket containing the UDP packet data.
     * @param self   The Server instance that handles the request processing.
     * @return A Map containing the request data with RequestKeys as keys and corresponding values.
     * @throws IOException            If an I/O error occurs while reading the UDP packet data.
     * @throws ClassNotFoundException If the object within the UDP packet cannot be deserialized.
     */
    @SuppressWarnings("unchecked")
    private static Map<RequestKeys, Object> readUDPInput(DatagramPacket packet, Server self)
            throws IOException, ClassNotFoundException {
        ByteArrayInputStream bais = new ByteArrayInputStream(packet.getData());
        ObjectInputStream ois = new ObjectInputStream(bais);
        Map<RequestKeys, Object> request = (Map<RequestKeys, Object>) ois.readObject();
        return request;

    }

    /**
     * Reads an incoming UDP packet from the specified DatagramSocket and adds it to the UDP packet queue.
     *
     * @param socket The DatagramSocket from which to read the UDP packet.
     * @param self   The Server instance that handles the packet processing.
     * @throws IOException If an I/O error occurs while reading the UDP packet data.
     */
    private static void readUDPPacket(DatagramSocket socket, Server self) throws IOException {
        byte[] data = new byte[self.sConfig.getUdpPacketLength()];
        DatagramPacket buffer = new DatagramPacket(data, self.sConfig.getUdpPacketLength());
        socket.receive(buffer);
        self.udpDatagramPackQ.add(buffer);
    }

    /**
     * Sends a response via UDP using the provided DatagramPacket, Server instance, and request data.
     *
     * @param packet  The DatagramPacket to which the response will be sent.
     * @param self    The Server instance handling the response.
     * @param request The request data to be sent as a response.
     * @throws IOException If an I/O error occurs during the UDP response sending process.
     */
    private static void sendUDPOutput(DatagramPacket packet, Server self, Map<RequestKeys, Object> request)
            throws IOException {
        ByteArrayOutputStream baos = new ByteArrayOutputStream(self.sConfig.getUdpPacketLength());
        ObjectOutputStream oos = new ObjectOutputStream(baos);
        oos.writeObject(request);
        byte[] data = baos.toByteArray();
        InetAddress ia = InetAddress.getByName(self.sConfig.getClientHost());
        int clientPort = self.sConfig.getClientUDPPort();
        if (packet != null) {
            ia = packet.getAddress();
            clientPort = packet.getPort();
        }
        DatagramPacket buffer = new DatagramPacket(data, data.length, ia, clientPort);
        self.udpSocket.send(buffer);
    }

    /**
     * Reads and deserializes a Map of RequestKeys and Object from the provided SocketM instance.
     *
     * @param socket The SocketM instance from which the input will be read.
     * @return A deserialized Map of RequestKeys and Object containing the received input data.
     * @throws IOException            If an I/O error occurs while reading from the input stream.
     * @throws ClassNotFoundException If a class is not found during deserialization.
     */
    @SuppressWarnings("unchecked")
    private static Map<RequestKeys, Object> readInput(SocketM socket) throws IOException, ClassNotFoundException {
        ObjectInputStream ois = socket.getObjectInputStream();
        Map<RequestKeys, Object> response = (Map<RequestKeys, Object>) ois.readObject();
        return response;
    }

    /**
     * Serializes and sends a Map of RequestKeys and Object to the provided SocketM instance.
     *
     * @param socket  The SocketM instance to which the output will be sent.
     * @param request A Map of RequestKeys and Object to be serialized and sent.
     * @throws IOException If an I/O error occurs while writing to the output stream.
     */
    private static void sendOutput(SocketM socket, Map<RequestKeys, Object> request) throws IOException {
        ObjectOutputStream oos = socket.getObjectOutputStream();
        oos.writeObject(request);
        oos.flush();
    }

    /**
     * Stops the server asynchronously with an option to force the shutdown.
     *
     * @param force If true, forces the server to shut down immediately; if false, attempts to gracefully shut down.
     */
    public void stopServerAsync(boolean force) {
        final Server self = this;
        this.executor.submit(new Runnable() {

            @Override
            public void run() {
                self.stopServer(force);
            }

        });
    }

    /**
     * Stops the server.
     *
     * @param force If true, forces the server to shut down immediately; if false, attempts to gracefully shut down.
     * @return True if the server was successfully stopped, false otherwise.
     */
    public boolean stopServer(boolean force) {
        try {
            this.logger("Server Stop Called");
            this.config.put("serverStarted", false);
            for (SocketM socket : tcpSocketSet) {
                if (!socket.isClosed()) {
                    socket.close();
                }
            }
            for (SocketM socket : udpSocketSet) {
                if (!socket.isClosed()) {
                    socket.close();
                }
            }
            if (this.tcpSocket != null && !this.tcpSocket.isClosed()) {
                this.tcpSocket.close();
            }
            if (this.udpSocket != null && !this.udpSocket.isClosed()) {
                this.udpSocket.close();
            }
            for (Future<?> future : this.futureList) {
                future.cancel(true);
            }
            this.logger("Child thread interrupt called");
            if (force) {
                this.executor.shutdown();
            } else {
                this.executor.shutdownNow();
            }
            while (!this.executor.isTerminated() && !Thread.interrupted()) {
            }
            if (this.sConfig.getType() == ConnectionType.RPC || this.sConfig.getType() == ConnectionType.ALL) {
                this.registryRPC.unbind(this.sConfig.getServiceKey().toString());
                UnicastRemoteObject.unexportObject((StoreService) this.serverRPC, true);
                UnicastRemoteObject.unexportObject(this.registryRPC, true);
            }
            this.logWriter.close();
            this.logger("Server Stopped");
//			if (this.sConfig.getType() == ConnectionType.RPC) {
//				Runtime runtime = Runtime.getRuntime();
//				runtime.exit(0);
//			}
            return true;
        } catch (Exception e) {
            e.printStackTrace();
        }
        return false;
    }

    public ServerConfig getsConfig() {
        return sConfig;
    }

    public void setsConfig(ServerConfig sConfig) {
        this.sConfig = sConfig;
    }

    public Object getClusterSemaphores() {
        return clusterSemaphores;
    }

    public void setClusterSemaphores(Object clusterSemaphores) {
        this.clusterSemaphores = clusterSemaphores;
    }

    public StoreService getStoreObject () {
        return this.serverRPC;
    }


    /*
     * Code from now on is to give access to methods available to public rpc server
     * */
}