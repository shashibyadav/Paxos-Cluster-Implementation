package com.project.four;

public class ClientDebug {

	public static void main(String[] args) throws Exception {
//		args = new String[] {"TCP","localhost","40000"};
//		args = new String[] {"UDP","localhost","40001"};
//		args = new String[] {"RPC","localhost","40002","StoreService","1"};
//		args = new String[] {"RPC","localhost","40002","StoreService","1", "auto"};
		ClientRun.run(args);
	}

}
