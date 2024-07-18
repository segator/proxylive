package com.github.segator.proxylive.entity;

import lombok.Data;

import java.io.File;
import java.util.Comparator;
import java.util.List;

@Data
public class Channel {
    private Integer number;
    private String name; //used by EPG matching
    private String id; //used by EPG matching
    private String epgID;
    private String logoURL;
    private File logoFile;
    private String encryptionKey;
    private List<String> categories;
    private List<ChannelSource> sources;
    private String ffmpegParameters="";
    public Channel(){

    }


    public ChannelSource getSourceByPriority(int priority){
        Object[] orderedSources = getSources().stream().sorted(Comparator.comparing(ChannelSource::getPriority)).toArray();
        if(priority> orderedSources.length){
            return null;
        }else {
            return (ChannelSource) orderedSources[priority-1];
        }
    }
}


