package com.project.four.server;

public class ProjectEnums {
	public enum ConnectionType {
		UDP,
		TCP,
		RPC,
		ALL,
		PAXOS
	};
	public enum MethodType {
		PUT,
		GET,
		DELETE,
		STOP,
		RUN,
		LOG,
		ELECT,
	}
	public enum RequestKeys {
		type,
		data,
		authId
	}
	public enum ServiceKeys {
		StoreService,
		CoordinatorService,
		AcceptorService,
		LearnerService,
	}
	public enum DataKeys {
		Priority
	}
}