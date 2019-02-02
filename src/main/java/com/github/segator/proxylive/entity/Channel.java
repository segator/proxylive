package com.github.segator.proxylive.entity;

import java.io.File;
import java.net.URL;
import java.util.List;

public class Channel {
    private Integer number;
    private String name; //used by EPG matching
    private String id; //used by EPG matching
    private String logoURL;
    private File logoFile;
    private List<ChannelCategory> categories;
    private List<ChannelSource> sources;


    public Integer getNumber() {
        return number;
    }

    public void setNumber(Integer number) {
        this.number = number;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getLogoURL() {
        return logoURL;
    }

    public void setLogoURL(String logoURL) {
        this.logoURL = logoURL;
    }

    public File getLogoFile() {
        return logoFile;
    }

    public void setLogoFile(File logoFile) {
        this.logoFile = logoFile;
    }

    public List<ChannelCategory> getCategories() {
        return categories;
    }

    public void setCategories(List<ChannelCategory> categories) {
        this.categories = categories;
    }

    public List<ChannelSource> getSources() {
        return sources;
    }

    public void setSources(List<ChannelSource> sources) {
        this.sources = sources;
    }
}


