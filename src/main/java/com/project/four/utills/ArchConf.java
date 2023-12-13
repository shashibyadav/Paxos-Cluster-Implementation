package com.project.four.utills;

import java.util.List;

/**
 * The ArchConf class represents the architectural configuration of the distributed system.
 * It includes configuration details for the coordinator server and a list of participant servers.
 */
public class ArchConf {
    private ServerNetConf coordinatorConf;
    private List<ServerNetConf> participantsConf;
    private boolean leaderBased;
    private float acceptRandomErrorProbability;
    private int requestAttempts = 5;
    private long delay = 0;
    private boolean allowNodeRestart = false;

    public boolean isAllowNodeRestart() {
        return allowNodeRestart;
    }

    public void setAllowNodeRestart(boolean allowNodeRestart) {
        this.allowNodeRestart = allowNodeRestart;
    }

    public long getDelay() {
        return delay;
    }

    public void setDelay(long delay) {
        this.delay = delay;
    }

    public int getRequestAttempts() {
        return requestAttempts;
    }

    public void setRequestAttempts(int requestAttempts) {
        this.requestAttempts = requestAttempts;
    }

    public float getAcceptRandomErrorProbability() {
        return acceptRandomErrorProbability;
    }

    public void setAcceptRandomErrorProbability(float acceptRandomErrorProbability) {
        this.acceptRandomErrorProbability = acceptRandomErrorProbability;
    }

    public boolean isLeaderBased() {
        return leaderBased;
    }

    public void setLeaderBased(boolean leaderBased) {
        this.leaderBased = leaderBased;
    }

    /**
     * Gets the configuration for the coordinator server.
     *
     * @return The ServerNetConf object representing coordinator server configuration.
     */
    public ServerNetConf getCoordinatorConf() {
        return coordinatorConf;
    }

    /**
     * Sets the configuration for the coordinator server.
     *
     * @param coordinatorConf The ServerNetConf object representing coordinator server configuration.
     */
    public void setCoordinatorConf(ServerNetConf coordinatorConf) {
        this.coordinatorConf = coordinatorConf;
    }

    public List<ServerNetConf> getParticipantsConf() {
        return participantsConf;
    }

    public void setParticipantsConf(List<ServerNetConf> participantsConf) {
        this.participantsConf = participantsConf;
    }
}
