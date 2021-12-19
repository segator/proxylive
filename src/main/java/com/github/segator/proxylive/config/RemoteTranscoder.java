package com.github.segator.proxylive.config;

public class RemoteTranscoder {
    private String endpoint;
    private String profile;

    public static RemoteTranscoder CreateFrom(RemoteTranscoder remoteTranscoder) {
        RemoteTranscoder rt= new RemoteTranscoder();
        rt.setEndpoint(remoteTranscoder.getEndpoint());
        rt.setProfile(remoteTranscoder.getProfile());
        return rt;
    }
    public String getEndpoint() {
        return endpoint;
    }

    public void setEndpoint(String endpoint) {
        this.endpoint = endpoint;
    }

    public String getProfile() {
        return profile;
    }

    public void setProfile(String profile) {
        this.profile = profile;
    }

    @Override
    public String toString() {
        return "RemoteTranscoder{" +
                "endpoint='" + endpoint + '\'' +
                ", profile='" + profile + '\'' +
                '}';
    }
}
