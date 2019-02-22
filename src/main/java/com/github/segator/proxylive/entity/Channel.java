package com.github.segator.proxylive.entity;

import java.io.File;
import java.net.URL;
import java.util.List;
import java.util.Optional;

public class Channel {
    private Integer number;
    private String name; //used by EPG matching
    private String id; //used by EPG matching
    private String epgID;
    private String logoURL;
    private File logoFile;
    private List<String> categories;
    private List<ChannelSource> sources;
    private String ffmpegParameters="";


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

    public String getEpgID() {
        return epgID;
    }

    public void setEpgID(String epgID) {
        this.epgID = epgID;
    }

    public File getLogoFile() {
        return logoFile;
    }

    public void setLogoFile(File logoFile) {
        this.logoFile = logoFile;
    }

    public List<String> getCategories() {
        return categories;
    }

    public void setCategories(List<String> categories) {
        this.categories = categories;
    }

    public List<ChannelSource> getSources() {
        return sources;
    }

    public ChannelSource getSourceByPriority(int priority){
        Optional<ChannelSource> optionalFind = getSources().stream().filter(s -> s.getPriority()==priority).findFirst();
        if(optionalFind.isPresent()){
            return optionalFind.get();
        }else{
            return null;
        }

    }

    public void setSources(List<ChannelSource> sources) {
        this.sources = sources;
    }

    public String getFfmpegParameters() {
        return ffmpegParameters;
    }

    public void setFfmpegParameters(String ffmpegParameters) {
        this.ffmpegParameters = ffmpegParameters;
    }
}


