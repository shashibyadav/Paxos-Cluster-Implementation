package com.project.four.utills;

import java.io.IOException;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.SocketException;
import java.net.SocketImpl;

public class ServerSocketM extends ServerSocket {

	public ServerSocketM(int port) throws IOException {
		super(port);
	}
	public ServerSocketM (int port, InetAddress address) throws IOException {
		super(port, 0, address);
	}
	public ServerSocketM(int port, int backlog, InetAddress address) throws IOException {
		super (port, backlog, address);
	}
	
	@Override
	public SocketM accept () throws IOException {
		if (isClosed())
            throw new SocketException("Socket is closed");
        if (!isBound())
            throw new SocketException("Socket is not bound yet");
        SocketM s = new SocketM((SocketImpl) null);
        implAccept(s);
        return s;
	}

}
