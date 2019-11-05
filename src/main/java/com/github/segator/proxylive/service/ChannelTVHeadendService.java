package com.github.segator.proxylive.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.github.segator.proxylive.config.ProxyLiveConfiguration;
import com.github.segator.proxylive.entity.Channel;
import com.github.segator.proxylive.entity.ChannelSource;
import com.github.segator.proxylive.stream.UDPInputStream;
import org.apache.commons.io.FileUtils;
import org.apache.commons.io.IOUtils;
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
import java.net.MalformedURLException;
import java.net.ProtocolException;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;

public class ChannelTVHeadendService implements ChannelService {
    private final Logger logger = LoggerFactory.getLogger(ChannelTVHeadendService.class);
    @Autowired
    private ProxyLiveConfiguration config;


    private JSONArray cachedChannelList;
    private JSONArray cachedChannelTags;
    private List<Channel> channels;
    private File tempLogoFilePath;
    private long lastUpdate=0;


    @Override
    public List<Channel> getChannelList() {
        return channels;
    }

    @Override
    public Channel getChannelByID(String channelID)
    {
        Optional<Channel> channelOptional = channels.stream().filter(ch -> ch.getId().equals(channelID)).findFirst();
        if(channelOptional.isPresent()){
            return channelOptional.get();
        }else{
            return null;
        }
    }


    private void getTvheadendData() throws ProtocolException, IOException, MalformedURLException, ParseException {
            cachedChannelList = (JSONArray) getTvheadendResponse("api/channel/grid?start=0&limit=5000").get("entries");
            cachedChannelTags = (JSONArray) getTvheadendResponse("api/channeltag/list").get("entries");
    }

    private List<Channel> buildChannels() throws Exception {
        logger.info("Updating Channel Info");
        List<Channel> channels=new ArrayList();
        Path piconsPath = Files.createTempDirectory("proxylivePicons");
        for (Object ochannel : cachedChannelList) {
            Channel channel = new Channel();
            channels.add(channel);
            JSONObject channelObject = (JSONObject) ochannel;
            //channel ID
            channel.setId((String)channelObject.get("uuid"));
            //tvheadend channel match ID with EPG extracted from tvh
            channel.setEpgID(channel.getId());
            logger.debug("Updating Channel:"+channel.getId());

            //Channel number
            String channelNumber="";
            if(channelObject.get("number")!=null){
                channel.setNumber(new Integer(channelObject.get("number").toString()));
            }


            //Channel Name
            channel.setName((String)channelObject.get("name"));

            //Channel categories
            JSONArray categories = (JSONArray)channelObject.get("tags");
            List<String> categoriesNames=new ArrayList();
            for (Object oCategory:categories) {
                String category =(String)oCategory;
                categoriesNames.add(getCategoryName(cachedChannelTags,category));
            }
            channel.setCategories(categoriesNames);

            String iconURL = (String)channelObject.get("icon_public_url");
            if (iconURL!= null) {
                    try {
                    HttpURLConnection connection = getURLConnection(iconURL);
                    if (connection.getResponseCode() != 404 && connection.getResponseCode() != 1) {
                        File logoFile = Paths.get(piconsPath.toString(), channel.getId() + ".png").toFile();
                        channel.setLogoFile(logoFile);
                        FileOutputStream fos = new FileOutputStream(logoFile);
                        IOUtils.copy(connection.getInputStream(), fos);
                        fos.flush();
                        connection.getInputStream().close();
                        fos.close();
                        connection.disconnect();
                    }
                }catch(Exception ex){

                }
            }

            //Channel URL
            channel.setSources(new ArrayList());
            channel.getSources().add(new ChannelSource(1,config.getSource().getTvheadendURL()+"/stream/channel/"+channel.getId()));
        }
        if (tempLogoFilePath != null && tempLogoFilePath.exists()) {
            FileUtils.deleteDirectory(tempLogoFilePath);
        }
        tempLogoFilePath = piconsPath.toFile();
        logger.info("Updating Channel Info Completed");
        //ObjectMapper mapper = new ObjectMapper();
        //mapper.enable(SerializationFeature.INDENT_OUTPUT);
        //mapper.writeValue(new File("D:\\file.json"), channels);

        return channels;
    }

    @PreDestroy
    private void cleanup() throws IOException {
        logger.debug("cleaning picons directory");
        if (tempLogoFilePath != null && tempLogoFilePath.exists()) {
            FileUtils.deleteDirectory(tempLogoFilePath);
        }
    }

    private String getCategoryName(JSONArray tags, String category) {
        for (Object otag : tags) {
            JSONObject tag = (JSONObject) otag;
            if(tag.get("key").equals(category)){
                return (String)tag.get("val");
            }
        }
        return "channels";
    }

    private JSONObject getTvheadendResponse(String request) throws MalformedURLException, ProtocolException, IOException, ParseException {
        JSONParser jsonParser = new JSONParser();
        HttpURLConnection connection = getURLConnection(request);
        if (connection.getResponseCode() != 200) {
            throw new IOException("Error on open stream:" + request);
        }
        JSONObject returnObject = (JSONObject) jsonParser.parse(new InputStreamReader(connection.getInputStream(), "UTF-8"));
        connection.disconnect();
        return returnObject;

    }


    private HttpURLConnection getURLConnection(String request) throws MalformedURLException, IOException {
        URL tvheadendURL = new URL(config.getSource().getTvheadendURL() + "/" + request);
        HttpURLConnection connection = (HttpURLConnection) tvheadendURL.openConnection();
        connection.setReadTimeout(10000);
        if (tvheadendURL.getUserInfo() != null) {
            String basicAuth = "Basic " + new String(Base64.getEncoder().encode(tvheadendURL.getUserInfo().getBytes()));
            connection.setRequestProperty("Authorization", basicAuth);
        }
        connection.setRequestMethod("GET");
        connection.connect();
        return connection;
    }
    @Scheduled(fixedDelay = 60 * 1000) //Every Minute
    @PostConstruct
    public void getDataFromTvheadend() throws Exception {
        if(new Date().getTime()-lastUpdate>+(config.getSource().getEpg().getRefresh()*1000)) {
            //Get Channels, Tags
            getTvheadendData();
            channels = buildChannels();
            lastUpdate=new Date().getTime();
        }
    }
}
