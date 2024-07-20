package com.github.segator.proxylive.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.segator.proxylive.config.ProxyLiveConfiguration;
import com.github.segator.proxylive.entity.Channel;
import com.github.segator.proxylive.entity.ChannelSource;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import org.apache.commons.io.FileUtils;
import org.apache.commons.io.IOUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Scheduled;


import java.io.*;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class ChannelM3u8Service implements ChannelService {
    private final Logger logger = LoggerFactory.getLogger(ChannelM3u8Service.class);
    @Autowired
    private ProxyLiveConfiguration config;

    private List<Channel> channels;
    private File tempLogoFilePath;
    private long lastUpdate = 0;
    private ObjectMapper objectMapper = new ObjectMapper();
    private Pattern urlTvgPattern = Pattern.compile("url-tvg=\"([^\"]+)\"");
    private Pattern channelPattern = Pattern.compile("tvg-id=\"(.*?)\".*tvg-name=\"(.*?)\".*tvg-logo=\"(.*?)\".*group-title=\"(.*?)\"");
    private Pattern kodiLicenseKeyPattern = Pattern.compile("^#KODIPROP:inputstream\\.adaptive\\.license_key=\\{.*\\}$");
    private Pattern kodiStreamHeadersPattern = Pattern.compile("^#KODIPROP:inputstream\\.adaptive\\.stream_headers=.*$");

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
        connection.disconnect();
        parseM3U8(lines);
    }

    private void parseM3U8(List<String> lines) throws JsonProcessingException {
        List<Channel> channels = new ArrayList<>();
        Channel currentChannel = null;
        Integer channelNumber = 0;
        for (String line : lines) {
            if (line.startsWith("#EXTM3U")){
                Matcher matcher = urlTvgPattern.matcher(line);
                if(matcher.find()){
                    String epg = matcher.group(1);
                }
            }
            else if (line.startsWith("#EXTINF:")) {
                currentChannel = new Channel();
                Matcher matcher = channelPattern.matcher(line);
                if (matcher.find()) {
                    channelNumber++;
                    currentChannel.setNumber(channelNumber);
                    currentChannel.setId(matcher.group(1));
                    currentChannel.setName(matcher.group(2));
                    currentChannel.setLogoURL(matcher.group(3));
                    currentChannel.setCategories(Collections.singletonList(matcher.group(4)));
                }
            } else if (line.startsWith("#KODIPROP")) {
                Matcher kodiLicenseMatcher = kodiLicenseKeyPattern.matcher(line);
                if (kodiLicenseMatcher.find()) {
                    String jsonString = line.substring(line.indexOf('=') + 1).trim();
                    JsonNode jsonNode = objectMapper.readTree(jsonString);
                    JsonNode keysNode = jsonNode.path("keys");
                    if (keysNode.isArray() && !keysNode.isEmpty()) {
                        JsonNode firstKey = keysNode.get(0);
                        currentChannel.setEncryptionKey(toHex(firstKey.get("k").asText()));
                    }
                }

                Matcher kodiStreamHeadersMatcher = kodiStreamHeadersPattern.matcher(line);
                if (kodiStreamHeadersMatcher.find()) {
                    String headers = line.substring(line.indexOf('=') + 1).trim();
                    currentChannel.getSourceHeaders().putAll(getQueryMap(headers));
                }
            }else if (line.startsWith("#EXTVLCOPT")){
                String headers = line.substring(11).trim();
                currentChannel.getSourceHeaders().putAll(getQueryMap(headers));
            } else if (line.startsWith("http")) {
                if (currentChannel != null) {
                    currentChannel.setSources(Collections.singletonList(new ChannelSource(1, line, "ffmpeg")));
                    channels.add(currentChannel);
                }
            }
        }

        this.channels = channels;
    }

    private void downloadChannelLogos() throws IOException {
        Path piconsPath = Files.createTempDirectory("proxylivePicons");

        for (Channel channel : channels) {
            String logoURL = channel.getLogoURL();
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
            //downloadChannelLogos();
            lastUpdate = new Date().getTime();
        }
    }

    private static String toHex(String base64String) {
        byte[] bytes = Base64.getDecoder().decode(base64String);
        StringBuilder hexString = new StringBuilder();
        for (byte b : bytes) {
            String hex = Integer.toHexString(0xff & b);
            if (hex.length() == 1) {
                hexString.append('0');
            }
            hexString.append(hex);
        }
        return hexString.toString();
    }
    public static Map<String, String> getQueryMap(String query) {
        Map<String, String> map = new HashMap<>();

        String[] pairs = query.split("&");
        for (String pair : pairs) {
            String[] keyValue = pair.split("=");
            if (keyValue.length == 2) {
                map.put(keyValue[0], keyValue[1]);
            } else if (keyValue.length == 1) {
                // Handle cases where the value might be missing
                map.put(keyValue[0], "");
            }
        }

        return map;
    }
}