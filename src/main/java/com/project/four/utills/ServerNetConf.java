package com.project.four.utills;

import com.project.four.server.ProjectEnums.ServiceKeys;

import java.util.LinkedList;
import java.util.List;
public class ServerNetConf {
    private String host;
    private int port;
    private List<ServiceKeys> serviceNames;

    public ServerNetConf (String host, int port, List<String> serviceNames) {
        this.host = host;
        this.port = port;
        this.serviceNames = new LinkedList<ServiceKeys>();
        for (String name: serviceNames) {
            this.serviceNames.add(ServiceKeys.valueOf(name));
        }
    }

    public String getHost() {
        return host;
    }

    public void setHost(String host) {
        this.host = host;
    }

    public int getPort() {
        return port;
    }

    public void setPort(int port) {
        this.port = port;
    }

    public List<ServiceKeys> getServiceNames() {
        return serviceNames;
    }

    public void setServiceNames(List<ServiceKeys> serviceNames) {
        this.serviceNames = serviceNames;
    }
}
