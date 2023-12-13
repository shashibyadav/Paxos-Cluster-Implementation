package com.project.four.client;

import com.project.four.interfaces.StoreService;
import com.project.four.server.ProjectEnums.ConnectionType;
import com.project.four.server.ProjectEnums.DataKeys;
import com.project.four.server.ProjectEnums.MethodType;
import com.project.four.server.ProjectEnums.RequestKeys;
import com.project.four.utills.SocketM;
import com.project.four.utills.Utills;

import java.io.*;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.rmi.RemoteException;
import java.rmi.registry.LocateRegistry;
import java.rmi.registry.Registry;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ExecutorService;

/**
 * Client class for communication with a remote server.
 */
public class Client {
	private SocketM socket = null;
	private DatagramSocket udpSocket = null;
	@SuppressWarnings("unused")
	private ExecutorService executor = null;
	private InetAddress iaServer = null;
	@SuppressWarnings("unused")
	private InetAddress iaClient = null;
	private ClientConfig cConfig = null;
	private Registry registry = null;
	private StoreService rpcServer = null;
	// Used to identify each client uniquely
	final private String uniqueId = UUID.randomUUID().toString();

	/**
	 * Constructor for the Client class.
	 * @param cConfig The ClientConfig object containing the configuration settings for the client.
	 * @throws UnknownHostException If the client host specified in the configuration is invalid.
	 */
	public Client(ClientConfig cConfig) throws UnknownHostException {
		this.cConfig = cConfig;
		this.executor = cConfig.getExecutor();
		this.iaServer = InetAddress.getByName(cConfig.getServerHost());
		this.iaClient = InetAddress.getByName(cConfig.getClientHost());
	}

	/**
	 * Logs a message to the console if the "isLogs" configuration variable is true.
	 * @param msg The message to be logged.
	 */
	private void logger(String msg) {
		if (this.cConfig.isLogs()) {
			System.out.println(msg);
		}
	}

	/**
	 * Starts the client and initializes communication channels based on the configured connection type.
	 *
	 * @throws Exception If there's an issue with client startup.
	 */
	public void startClient() throws Exception {
		if (this.cConfig.getType() == ConnectionType.TCP) {
			this.socket = this.socket != null ? this.socket
					: new SocketM(this.cConfig.getServerHost(), this.cConfig.getServerPort());
			this.socket.setKeepAlive(true);
			this.socket.initialize();
		}
		if (this.cConfig.getType() == ConnectionType.UDP) {
			if (this.cConfig.getClientPort() > -1) {
				this.udpSocket = this.udpSocket != null ? this.udpSocket
						: new DatagramSocket(this.cConfig.getClientPort());
			} else {
				this.udpSocket = this.udpSocket != null ? this.udpSocket : new DatagramSocket();
			}
			this.udpSocket.setSoTimeout(this.cConfig.getRequestTimeout());
		}
		if (this.cConfig.getType() == ConnectionType.RPC) {
			int counter = 0;
			if (this.cConfig.getRegistryPort() > -1) {
				counter++;
			}
			if (Utills.isEmptyString(this.cConfig.getRegistryHost())) {
				counter++;
			}
			if (counter == 2) {
				this.registry = LocateRegistry.getRegistry(this.cConfig.getRegistryHost(),
						this.cConfig.getRegistryPort());
			} else if (counter == 1) {
				this.registry = LocateRegistry.getRegistry(this.cConfig.getRegistryPort());
			} else {
				this.registry = LocateRegistry.getRegistry();
			}
			this.rpcServer = (StoreService) this.registry.lookup(this.cConfig.getServiceKey().toString());

		}
		this.logger("Client started");
	}

	/**
	 * Sends an object request using the established communication channel.
	 *
	 * @param request The request to be sent as a map of RequestKeys and objects.
	 * @throws IOException If there's an issue with sending the request.
	 */
	public void sendObjectRequest(Map<RequestKeys, Object> request) throws IOException {
		ObjectOutputStream oos = this.socket.getObjectOutputStream();
		oos.writeObject(request);
		oos.flush();
	}

	/**
	 * Reads and processes an object request received over UDP.
	 *
	 * @param wait Indicates whether to wait for a response or not.
	 * @return A map of data received from the server, or null if no data is available.
	 * @throws IOException            If there's an issue with reading the request.
	 * @throws ClassNotFoundException If there's an issue with deserializing the received object.
	 */
	@SuppressWarnings("unchecked")
	public Map<String, Object> readObjectRequestUDP(boolean wait) throws IOException, ClassNotFoundException {
		try {
			byte[] dataBucket = new byte[this.cConfig.getUdpPacketLength()];
			DatagramPacket buffer = new DatagramPacket(dataBucket, this.cConfig.getUdpPacketLength());
			this.udpSocket.receive(buffer);
			ByteArrayInputStream bais = new ByteArrayInputStream(buffer.getData());
			ObjectInputStream ois = new ObjectInputStream(bais);
			if (wait) {
				Map<RequestKeys, Object> response = (Map<RequestKeys, Object>) ois.readObject();
				Map<String, Object> data = Utills.getRequestData(response);
				return data;
			} else {
				if (ois.available() > 0) {
					Map<RequestKeys, Object> response = (Map<RequestKeys, Object>) ois.readObject();
					Map<String, Object> data = Utills.getRequestData(response);
					return data;
				}
			}
		} catch (ClassNotFoundException e) {
			e.printStackTrace();
		} catch (IOException e) {
			e.printStackTrace();
		}
		return null;
	}

	/**
	 * Sends an object request using the UDP communication channel.
	 *
	 * @param request The request to be sent as a map of RequestKeys and objects.
	 * @throws IOException If there's an issue with sending the request.
	 */
	public void sendObjectRequestUDP(Map<RequestKeys, Object> request) throws IOException {
		ByteArrayOutputStream baos = new ByteArrayOutputStream(this.cConfig.getUdpPacketLength());
		ObjectOutputStream oos = new ObjectOutputStream(baos);
		oos.writeObject(request);
		byte[] data = baos.toByteArray();
		DatagramPacket buffer = new DatagramPacket(data, data.length, this.iaServer, this.cConfig.getServerPort());
		this.udpSocket.send(buffer);
	}

	/**
	 * Reads and processes an object request received over the established communication channel.
	 *
	 * @param wait Indicates whether to wait for a response or not.
	 * @return A map of data received from the server, or null if no data is available.
	 * @throws IOException            If there's an issue with reading the request.
	 * @throws ClassNotFoundException If there's an issue with deserializing the received object.
	 */
	@SuppressWarnings("unchecked")
	public Map<String, Object> readObjectRequest(boolean wait) throws IOException, ClassNotFoundException {
		try {
			ObjectInputStream ois = this.socket.getObjectInputStream();
			if (wait) {
				Map<RequestKeys, Object> response = (Map<RequestKeys, Object>) ois.readObject();
				Map<String, Object> data = Utills.getRequestData(response);
				return data;
			} else {
				if (ois.available() > 0) {
					Map<RequestKeys, Object> response = (Map<RequestKeys, Object>) ois.readObject();
					Map<String, Object> data = Utills.getRequestData(response);
					return data;
				}
			}
		} catch (ClassNotFoundException e) {
			e.printStackTrace();
		} catch (IOException e) {
			e.printStackTrace();
		}
		return null;
	}

	/**
	 * Sends a request to put data with the specified key and object value to the server.
	 *
	 * @param key  The key for the data to be stored.
	 * @param data The data to be stored.
	 * @throws IOException If there's an issue with sending the request.
	 */
	public void putData(String key, Object data) throws IOException {
		if (this.cConfig.getType() == ConnectionType.RPC) {
			try {
				this.rpcServer.putData(key, data, this.uniqueId);
			} catch (RemoteException e) {
				e.printStackTrace();
			}
		} else {
			Map<String, Object> payload = new HashMap<String, Object>();
			payload.put(key, data);
			Map<RequestKeys, Object> request = Utills.generateRequest(MethodType.PUT, payload, this.uniqueId);
			if (this.cConfig.getType() == ConnectionType.TCP) {
				this.sendObjectRequest(request);
			} else if (this.cConfig.getType() == ConnectionType.UDP) {
				this.sendObjectRequestUDP(request);
			}
		}
	}

	/**
	 * Sends a request to get data with the specified key from the server.
	 *
	 * @param key The key for the data to retrieve.
	 * @throws IOException            If there's an issue with sending the request.
	 * @throws ClassNotFoundException If there's an issue with deserializing the received object.
	 */
	public void getData(String key) throws IOException, ClassNotFoundException {
		Object result = null;
		if (this.cConfig.getType() == ConnectionType.RPC) {
			result = this.rpcServer.getData(key, this.uniqueId);
		} else {
			Map<String, Object> payload = new HashMap<String, Object>();
			payload.put(key, true);
			Map<RequestKeys, Object> request = Utills.generateRequest(MethodType.GET, payload, this.uniqueId);
			if (this.cConfig.getType() == ConnectionType.TCP) {
				this.sendObjectRequest(request);
				result = this.readObjectRequest(true);
			} else if (this.cConfig.getType() == ConnectionType.UDP) {
				this.sendObjectRequestUDP(request);
				result = this.readObjectRequestUDP(true);
			}
		}
		System.out.println(result + " - " + System.currentTimeMillis());
	}

	/**
	 * Sends a request to run a specific operation on the server.
	 *
	 * @param text The text associated with the operation.
	 * @return The result of the operation.
	 * @throws IOException            If there's an issue with sending the request.
	 * @throws ClassNotFoundException If there's an issue with deserializing the received object.
	 */
	public String runData(String text) throws ClassNotFoundException, IOException {
		Map<String, Object> payload = new HashMap<String, Object>();
		payload.put(MethodType.RUN.toString(), text);
		Map<RequestKeys, Object> request = Utills.generateRequest(MethodType.RUN, payload, this.uniqueId);
		Map<String, Object> result = null;
		if (this.cConfig.getType() == ConnectionType.TCP) {
			this.sendObjectRequest(request);
			result = this.readObjectRequest(true);
		} else if (this.cConfig.getType() == ConnectionType.UDP) {
			this.sendObjectRequestUDP(request);
			result = this.readObjectRequestUDP(true);
		}
		return (String) result.get(MethodType.RUN.toString());

	}

	/**
	 * Sends a request to delete data with the specified key from the server.
	 *
	 * @param key The key for the data to delete.
	 * @throws IOException If there's an issue with sending the request.
	 */
	public void deleteData(String key) throws IOException {
		if (this.cConfig.getType() == ConnectionType.RPC) {
			this.rpcServer.deleteData(key, this.uniqueId);
		} else {
			Map<String, Object> payload = new HashMap<String, Object>();
			payload.put(key, true);
			Map<RequestKeys, Object> request = Utills.generateRequest(MethodType.DELETE, payload, this.uniqueId);
			if (this.cConfig.getType() == ConnectionType.UDP) {
				this.sendObjectRequestUDP(request);
			} else if (this.cConfig.getType() == ConnectionType.TCP) {
				this.sendObjectRequest(request);
			}
		}
	}

	/**
	 * Reads and processes data received from the server.
	 *
	 * @throws IOException            If there's an issue with reading the request.
	 * @throws ClassNotFoundException If there's an issue with deserializing the received object.
	 */
	public void readData() throws IOException, ClassNotFoundException {
		Map<String, Object> data = this.readObjectRequest(false);
		this.logger(data.toString());
	}

	/**
	 * Stops the client and closes the communication channels.
	 *
	 * @throws Exception If there's an issue with client shutdown.
	 */
	public void stopClient() throws Exception {
		if (this.socket != null && !this.socket.isClosed()) {
			try {
				this.socket.close();
			} catch (IOException e) {
				e.printStackTrace();
			}
		}
		if (this.udpSocket != null && !this.udpSocket.isClosed()) {
			this.udpSocket.close();
		}
		this.logger("Client Stopped");
	}

	/**
	 * Sends a request to stop the server operation.
	 *
	 * @param force Indicates whether to forcefully stop the server.
	 * @throws IOException If there's an issue with sending the request.
	 */
	public void stopServerCall(boolean force) throws IOException {
		if (this.cConfig.getType() == ConnectionType.RPC) {
			this.rpcServer.stopServer(this.uniqueId, force);
		} else {
			Map<String, Object> payload = new HashMap<String, Object>();
			payload.put(DataKeys.Priority.toString(), force);
			Map<RequestKeys, Object> request = Utills.generateRequest(MethodType.STOP, payload, this.uniqueId);
			if (this.cConfig.getType() == ConnectionType.TCP) {
				this.sendObjectRequest(request);
			} else if (this.cConfig.getType() == ConnectionType.UDP) {
				this.sendObjectRequestUDP(request);
			}
		}
	}

	/**
	 * Initiates the global state on the server.
	 *
	 * @throws IOException If there's an issue with sending the request.
	 */
	public void initiateGlobalState (String name) throws IOException{
		this.rpcServer.initiateGlobalState(name);
	}

	public void askToStopASpecificServer (int index) throws IOException{
		this.rpcServer.askToStopASpecificServer(index);
	}
}