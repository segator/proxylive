package com.github.segator.proxylive.service;

import com.github.segator.proxylive.config.ProxyLiveConfiguration;
import com.maxmind.db.CHMCache;
import com.maxmind.geoip2.DatabaseReader;
import org.apache.commons.compress.archivers.tar.TarArchiveEntry;
import org.apache.commons.compress.archivers.tar.TarArchiveInputStream;
import org.apache.commons.io.IOUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import javax.annotation.PostConstruct;
import java.io.*;
import java.net.HttpURLConnection;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.Base64;
import java.util.zip.GZIPInputStream;

@Service
public class GeoIPService {
    private File tmpGEOIPFile;
    private DatabaseReader geoIPDB;
    @Autowired
    private ProxyLiveConfiguration config;

    @Scheduled(fixedDelay = 86400 * 1000) //Every 24H
    @PostConstruct
    private void downloadIPLocationDatabase() throws Exception {
        if(config.getGeoIP().isEnabled()){
            System.out.println("Refreshing GEOIP Database");

            File tmpGEOIPFileRound = File.createTempFile("geoIP", "mmdb");
            FileOutputStream fos = new FileOutputStream(tmpGEOIPFileRound);
            HttpURLConnection connection = getURLConnection(config.getGeoIP().getUrl());
            if (connection.getResponseCode() != 200) {
                return;
            }
            TarArchiveInputStream tarGzGeoIPStream = new TarArchiveInputStream(new GZIPInputStream(connection.getInputStream()));
            TarArchiveEntry entry= null;
            int offset;
            long pointer=0;
            while ((entry = tarGzGeoIPStream.getNextTarEntry()) != null) {
                pointer+=entry.getSize();
                if(entry.getName().endsWith("GeoLite2-City.mmdb")){
                    byte[] content = new byte[(int) entry.getSize()];
                    offset=0;
                    //FileInputStream fis = new FileInputStream(entry.getFile());
                    //IOUtils.copy(fis,fos);
                    //tarGzGeoIPStream.skip(pointer);
                    //tarGzGeoIPStream.read(content,offset,content.length-offset);
                    //IOUtils.write(content,fos);
                    int r;
                    byte[] b = new byte[1024];
                    while ((r = tarGzGeoIPStream.read(b)) != -1) {
                        fos.write(b, 0, r);
                    }
                    //fis.close();
                    break;
                }
            }
            tarGzGeoIPStream.close();
            fos.flush();
            fos.close();
            connection.disconnect();
            geoIPDB = new DatabaseReader.Builder(tmpGEOIPFileRound).withCache(new CHMCache()).build();
            if (tmpGEOIPFile != null && tmpGEOIPFile.exists()) {
                tmpGEOIPFile.delete();
            }
            tmpGEOIPFile = tmpGEOIPFileRound;
        }
    }

    public DatabaseReader geoGEOInfoReader(){
         return geoIPDB;
    }

    private HttpURLConnection getURLConnection(String urlString) throws MalformedURLException, IOException {
        URL url = new URL(urlString);
        HttpURLConnection connection = (HttpURLConnection) url.openConnection();
        connection.setReadTimeout(10000);
        connection.setRequestMethod("GET");
        connection.connect();
        return connection;
    }
}
