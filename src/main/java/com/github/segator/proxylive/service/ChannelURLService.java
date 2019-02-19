package com.github.segator.proxylive.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.segator.proxylive.config.GitSource;
import com.github.segator.proxylive.config.ProxyLiveConfiguration;
import com.github.segator.proxylive.entity.Channel;
import org.apache.commons.io.FileUtils;
import org.apache.commons.io.IOUtils;
import org.eclipse.jgit.api.*;
import org.eclipse.jgit.lib.Ref;
import org.eclipse.jgit.revwalk.RevCommit;
import org.eclipse.jgit.revwalk.RevWalk;
import org.eclipse.jgit.transport.TrackingRefUpdate;
import org.eclipse.jgit.transport.UsernamePasswordCredentialsProvider;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Scheduled;

import javax.annotation.PostConstruct;
import javax.annotation.PreDestroy;
import java.io.*;

import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;

public class ChannelURLService implements ChannelService {

    @Autowired
    private ProxyLiveConfiguration config;

    private List<Channel> channels;
    //private File tmpGitPath;
    private Git git;
    private File tempLogoFilePath;
    private long lastUpdate=0;


    @Override
    public List<Channel> getChannelList() {
        return channels;
    }

    @Override
    public Channel getChannelByID(String channelID) {
        Optional<Channel> channelOptional = channels.stream().filter(ch -> ch.getId().equals(channelID)).findFirst();
        if(channelOptional.isPresent()){
            return channelOptional.get();
        }else{
            return null;
        }
    }

    private void setGitCredentials( TransportCommand gitCommand){
        GitSource gitSource = config.getSource().getChannels().getGit();
        if(gitSource.getUser()!=null) {
            gitCommand.setCredentialsProvider(new UsernamePasswordCredentialsProvider(gitSource.getUser(), gitSource.getPass()));
        }
    }


    @Scheduled(fixedDelay = 60 * 1000) //Every Minute
    @PostConstruct
    public void getChannelInfo() throws Exception {
        if(new Date().getTime()-lastUpdate>+(config.getSource().getChannels().getRefresh()*1000)) {
            ObjectMapper objectMapper = new ObjectMapper();
            List<Channel> channels=null;
            //URL
            if(config.getSource().getChannels().getUrl()!=null) {
                URL channelURL = new URL(config.getSource().getChannels().getUrl());
                BufferedReader in = new BufferedReader(new InputStreamReader(channelURL.openStream()));
                channels = objectMapper.readValue(in, new TypeReference<List<Channel>>() {
                });
                in.close();
            //Git
            }else if(config.getSource().getChannels().getGit()!=null){
                //not initialized yet
                if(git==null){
                    System.out.println("Loading Channels from: "+config.getSource().getChannels().getGit().getRepository());
                    File tmpGitPath = Files.createTempDirectory("proxyliveGit").toFile();
                    GitSource gitSource = config.getSource().getChannels().getGit();
                    CloneCommand cloneCommand = Git.cloneRepository();
                    cloneCommand.setURI(gitSource.getRepository());
                    cloneCommand.setDirectory(tmpGitPath);
                    cloneCommand.setCloneAllBranches(true);
                    cloneCommand.setBranch(gitSource.getBranch());
                    setGitCredentials(cloneCommand);
                    git = cloneCommand.call();
                    PullCommand pull = git.pull().setRemote("origin");
                    setGitCredentials(pull);
                    pull.call();
                }else {
                    PullCommand pullCommand = git.pull().setRemote("origin");
                    setGitCredentials(pullCommand);
                    PullResult pulLResult = pullCommand.call();
                    if(!pulLResult.isSuccessful()){
                        throw new Exception("error when pulling");
                    }
                    Collection<TrackingRefUpdate> commitsUpdated = pulLResult.getFetchResult().getTrackingRefUpdates();
                    if (commitsUpdated.size() > 0) {
                        System.out.println("Channels modified, updating..");
                        RevWalk walk = new RevWalk(git.getRepository());
                        for (TrackingRefUpdate commitRef : commitsUpdated) {
                            RevCommit commit = walk.parseCommit(commitRef.getNewObjectId());
                            System.out.println("(" + commit.getAuthorIdent().getName() + "<" + commit.getAuthorIdent().getEmailAddress() + ">) " + commit.getFullMessage());
                        }
                    }else{
                        //no changes, don't need to update
                        return;
                    }
                }
                File channelsFile = new File(git.getRepository().getDirectory().getParentFile(), "channels.json");
                if(!channelsFile.exists()){
                    throw new Exception("channels.json doesn't exist on git repository");
                }
                channels = objectMapper.readValue(channelsFile, new TypeReference<List<Channel>>() {
                });
            }
            readPicons(channels);
            this.channels = channels;
            System.out.println("Channels loaded");
            lastUpdate = new Date().getTime();
        }
    }

    private void readPicons(List<Channel> channels) throws IOException {
        Path piconsPath = Files.createTempDirectory("proxylivePicons");
        for (Channel channel: channels) {
            if(channel.getLogoURL()!=null){
                URL logoURL = new URL(channel.getLogoURL());
                InputStream is = logoURL.openStream();
                File logoFile = Paths.get(piconsPath.toString(),channel.getId()+".png").toFile();
                channel.setLogoFile(logoFile);
                FileOutputStream fos = new FileOutputStream(logoFile);
                IOUtils.copy(is, fos);
                fos.flush();
                is.close();
                fos.close();
            //logoFile
            }else{
                if(channel.getLogoFile()!=null){
                    //if sources got from git then load picon from git
                    if(git!=null){
                        channel.setLogoFile(new File(git.getRepository().getDirectory().getParentFile(),channel.getLogoFile().toString()));
                    }
                }

            }
        }
        tempLogoFilePath = piconsPath.toFile();
    }

    @PreDestroy
    private void cleanup() throws IOException {
        System.out.println("cleaning picons directory");
        if (tempLogoFilePath != null && tempLogoFilePath.exists()) {
            FileUtils.deleteDirectory(tempLogoFilePath);
        }
        if(git!=null && git.getRepository().getDirectory().getParentFile().exists()){
            FileUtils.deleteDirectory(git.getRepository().getDirectory().getParentFile());
        }
    }
}
