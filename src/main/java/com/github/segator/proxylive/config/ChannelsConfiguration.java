package com.github.segator.proxylive.config;

public class ChannelsConfiguration {
    private GitSource git;
    private String url;
    private long refresh;
    private String type;

    public String getType() {
        return type;
    }

    public void setType(String type) throws Exception {
        if(!type.equals("json") && !type.equals("tvheadend")){
           throw new Exception("Invalid type");
        }
        this.type = type;
    }

    public String getUrl() {
        return url;
    }

    public void setUrl(String url) {
        this.url = url;
    }

    public long getRefresh() {
        return refresh;
    }

    public void setRefresh(long refresh) {
        this.refresh = refresh;
    }

    public GitSource getGit() {
        return git;
    }

    public void setGit(GitSource git) {
        this.git = git;
    }
}
