package com.project.four;

public class ServerDebug {

	public static void main(String[] args) throws Exception {
//		args = new String[] {"TCP","localhost","40000"};
//		args = new String[] {"UDP","localhost","40000"};
//		args = new String[] {"RPC", "localhost", "40005", "StoreService"};
//		args = new String[] {"ALL", "localhost", "40000", "40001", "40002", "StoreService"};
		ServerRun.run(args);
	}

}
