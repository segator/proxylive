package com.github.segator.proxylive.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.segator.proxylive.ProxyLiveUtils;
import com.github.segator.proxylive.config.GitSource;
import com.github.segator.proxylive.config.ProxyLiveConfiguration;
import com.github.segator.proxylive.entity.Channel;
import com.github.segator.proxylive.entity.ChannelSource;
import org.apache.commons.io.FileUtils;
import org.apache.commons.io.IOUtils;
import org.eclipse.jgit.api.*;
import org.eclipse.jgit.revwalk.RevCommit;
import org.eclipse.jgit.revwalk.RevWalk;
import org.eclipse.jgit.transport.TrackingRefUpdate;
import org.eclipse.jgit.transport.UsernamePasswordCredentialsProvider;
import org.json.simple.JSONArray;
import org.json.simple.JSONObject;
import org.json.simple.parser.JSONParser;
import org.json.simple.parser.ParseException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Scheduled;

import javax.annotation.PostConstruct;
import javax.annotation.PreDestroy;
import java.io.*;
import java.net.HttpURLConnection;
import java.net.URISyntaxException;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;

public class ChannelM3u8Service implements ChannelService {
    private final Logger logger = LoggerFactory.getLogger(ChannelM3U8Service.class);
    @Autowired
    private ProxyLiveConfiguration config;

    private List<Channel> channels;
    private File tempLogoFilePath;
    private long lastUpdate = 0;

    @Override
    public List<Channel> getChannelList() {
        return channels;
    }

    @Override
    public Channel getChannelByID(String channelID) {
        return channels.stream().filter(ch -> ch.getId().equals(channelID)).findFirst().orElse(null);
    }

    private void getM3U8Data() throws IOException {
        URL url = new URL(config.getSource().getM3u8URL());
        HttpURLConnection connection = (HttpURLConnection) url.openConnection();
        connection.setRequestMethod("GET");
        connection.connect();

        if (connection.getResponseCode() != 200) {
            throw new IOException("Error retrieving M3U8 file: " + connection.getResponseMessage());
        }

        List<String> lines = IOUtils.readLines(connection.getInputStream(), "UTF-8");
        parseM3U8(lines);
        connection.disconnect();
    }

    private void parseM3U8(List<String> lines) {
        List<Channel> channels = new ArrayList<>();
        Channel currentChannel = null;

        for (String line : lines) {
            if (line.startsWith("#EXTINF:")) {
                currentChannel = new Channel();
                Matcher matcher = Pattern.compile("tvg-id=\"(.*?)\".*tvg-name=\"(.*?)\".*tvg-logo=\"(.*?)\".*group-title=\"(.*?)\"").matcher(line);
                if (matcher.find()) {
                    currentChannel.setId(matcher.group(1));
                    currentChannel.setName(matcher.group(2));
                    currentChannel.setLogo(matcher.group(3));
                    currentChannel.setCategories(Collections.singletonList(matcher.group(4)));
                }
            } else if (line.startsWith("http")) {
                if (currentChannel != null) {
                    currentChannel.setSources(Collections.singletonList(new ChannelSource(1, line, "raw")));
                    channels.add(currentChannel);
                }
            }
        }

        this.channels = channels;
    }

    private void downloadChannelLogos() throws IOException {
        Path piconsPath = Files.createTempDirectory("proxylivePicons");

        for (Channel channel : channels) {
            String logoURL = channel.getLogo();
            if (logoURL != null && !logoURL.isEmpty()) {
                try {
                    HttpURLConnection connection = (HttpURLConnection) new URL(logoURL).openConnection();
                    if (connection.getResponseCode() != 404) {
                        File logoFile = Paths.get(piconsPath.toString(), channel.getId() + ".png").toFile();
                        channel.setLogoFile(logoFile);
                        try (FileOutputStream fos = new FileOutputStream(logoFile)) {
                            IOUtils.copy(connection.getInputStream(), fos);
                        }
                        connection.disconnect();
                    }
                } catch (Exception e) {
                    logger.error("Failed to download logo for channel: " + channel.getId(), e);
                }
            }
        }

        if (tempLogoFilePath != null && tempLogoFilePath.exists()) {
            FileUtils.deleteDirectory(tempLogoFilePath);
        }
        tempLogoFilePath = piconsPath.toFile();
    }

    @PreDestroy
    private void cleanup() throws IOException {
        logger.debug("Cleaning picons directory");
        if (tempLogoFilePath != null && tempLogoFilePath.exists()) {
            FileUtils.deleteDirectory(tempLogoFilePath);
        }
    }

    @Scheduled(fixedDelay = 60 * 1000) // Every Minute
    @PostConstruct
    public void getDataFromM3U8() throws Exception {
        if (new Date().getTime() - lastUpdate > (config.getSource().getEpg().getRefresh() * 1000)) {
            getM3U8Data();
            downloadChannelLogos();
            lastUpdate = new Date().getTime();
        }
    }
}