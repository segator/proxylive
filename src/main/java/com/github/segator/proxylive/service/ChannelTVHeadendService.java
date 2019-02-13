package com.github.segator.proxylive.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.github.segator.proxylive.config.ProxyLiveConfiguration;
import com.github.segator.proxylive.entity.Channel;
import com.github.segator.proxylive.entity.ChannelCategory;
import com.github.segator.proxylive.entity.ChannelSource;
import org.apache.commons.io.FileUtils;
import org.apache.commons.io.IOUtils;
import org.json.simple.JSONArray;
import org.json.simple.JSONObject;
import org.json.simple.parser.JSONParser;
import org.json.simple.parser.ParseException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;

import javax.annotation.PostConstruct;
import javax.annotation.PreDestroy;
import javax.servlet.http.HttpServletResponse;
import java.io.*;
import java.net.HttpURLConnection;
import java.net.MalformedURLException;
import java.net.ProtocolException;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.rmi.RemoteException;
import java.util.*;

public class ChannelTVHeadendService implements ChannelService {

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
        System.out.println("Updating Channel Info");
        List<Channel> channels=new ArrayList();
        Path piconsPath = Files.createTempDirectory("proxylivePicons");
        for (Object ochannel : cachedChannelList) {
            Channel channel = new Channel();
            channels.add(channel);
            JSONObject channelObject = (JSONObject) ochannel;
            //channel ID
            channel.setId((String)channelObject.get("uuid"));
            System.out.println("Updating Channel:"+channel.getId());

            //Channel number
            String channelNumber="";
            if(channelObject.get("number")!=null){
                channel.setNumber(new Integer(channelObject.get("number").toString()));
            }


            //Channel Name
            channel.setName((String)channelObject.get("name"));

            //Channel categories
            JSONArray categories = (JSONArray)channelObject.get("tags");
            List<ChannelCategory> categoriesNames=new ArrayList();
            for (Object oCategory:categories) {
                String category =(String)oCategory;
                ChannelCategory categoryObject = new ChannelCategory();
                categoryObject.setName(getCategoryName(cachedChannelTags,category));
                categoriesNames.add(categoryObject);
            }
            channel.setCategories(categoriesNames);

            HttpURLConnection connection = getURLConnection((String)channelObject.get("icon_public_url"));
            if (connection.getResponseCode() != 200 && connection.getResponseCode()!=404) {
                throw new Exception("Error loading Picons error:"+connection.getResponseCode() + " "+ connection.getResponseMessage());
            }

            if(connection.getResponseCode()!=404) {
                File logoFile = Paths.get(piconsPath.toString(),channel.getId()+".png").toFile();
                channel.setLogoFile(logoFile);
                FileOutputStream fos = new FileOutputStream(logoFile);
                IOUtils.copy(connection.getInputStream(), fos);
                fos.flush();
                connection.getInputStream().close();
                fos.close();
                connection.disconnect();
            }

            //Channel URL
            channel.setSources(new ArrayList());
            channel.getSources().add(new ChannelSource(1,config.getSource().getTvheadendURL()+"/stream/channel/"+channel.getId()));
        }
        if (tempLogoFilePath != null && tempLogoFilePath.exists()) {
            FileUtils.deleteDirectory(tempLogoFilePath);
        }
        tempLogoFilePath = piconsPath.toFile();
        System.out.println("Updating Channel Info Completed");
        /*ObjectMapper mapper = new ObjectMapper();
        mapper.enable(SerializationFeature.INDENT_OUTPUT);
        mapper.writeValue(new File("c:\\file.json"), channels);*/
        return channels;
    }

    @PreDestroy
    private void cleanup() throws IOException {
        System.out.println("cleaning picons directory");
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
        if(new Date().getTime()-lastUpdate>+(config.getSource().getChannels().getRefresh()*1000)) {
            //Get Channels, Tags
            getTvheadendData();
            channels = buildChannels();
            lastUpdate=new Date().getTime();
        }
    }
}
