package com.github.segator.proxylive.service;

import com.github.segator.proxylive.config.ProxyLiveConfiguration;
import org.apache.commons.io.IOUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Controller;
import org.springframework.stereotype.Service;

import javax.annotation.PostConstruct;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.Base64;
import java.util.Date;

@Service
public class EPGService {

    private File tempEPGFile;

    @Autowired
    private ProxyLiveConfiguration config;
    private long lastUpdate=0;

    @Scheduled(fixedDelay = 600 * 1000) //Every 100 Minute
    @PostConstruct
    private void buildEPG() throws Exception {
        if(new Date().getTime()-lastUpdate>+(config.getSource().getEpg().getRefresh()*1000)) {
            System.out.println("Refreshing EPG");
            HttpURLConnection connection = getURLConnection(config.getSource().getEpg().getUrl());
            if (connection.getResponseCode() != 200) {
                return;
            }

            File tempEPGFileRound = File.createTempFile("epg", "xml");
            FileOutputStream fos = new FileOutputStream(tempEPGFileRound);
            IOUtils.copy(connection.getInputStream(), fos);
            fos.flush();
            connection.getInputStream().close();
            fos.close();
            connection.disconnect();

            if (tempEPGFile != null && tempEPGFile.exists()) {
                tempEPGFile.delete();
            }
            tempEPGFile = tempEPGFileRound;
            lastUpdate=new Date().getTime();
        }
    }


    public File getEPG() throws IOException {
        return tempEPGFile;
    }

    private HttpURLConnection getURLConnection(String url) throws MalformedURLException, IOException {
        URL tvheadendURL = new URL(url);
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
}
