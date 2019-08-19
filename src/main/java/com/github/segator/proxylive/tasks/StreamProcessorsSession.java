/*
 * The MIT License
 *
 * Copyright 2017 Isaac Aymerich <isaac.aymerich@gmail.com>.
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in
 * all copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN
 * THE SOFTWARE.
 */
package com.github.segator.proxylive.tasks;

import com.github.segator.proxylive.ProxyLiveUtils;
import com.github.segator.proxylive.entity.ClientInfo;
import com.github.segator.proxylive.entity.GEOInfo;
import com.github.segator.proxylive.processor.DirectHLSTranscoderStreamProcessor;
import com.github.segator.proxylive.processor.IStreamProcessor;

import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.*;
import javax.servlet.http.HttpServletRequest;

import com.github.segator.proxylive.service.GeoIPService;
import com.maxmind.geoip2.DatabaseReader;
import com.maxmind.geoip2.model.AnonymousIpResponse;
import com.maxmind.geoip2.model.CityResponse;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.util.MultiValueMap;
import org.springframework.web.util.UriComponentsBuilder;

/**
 *
 * @author Isaac Aymerich <isaac.aymerich@gmail.com>
 */
@Service
public class StreamProcessorsSession {

    @Autowired
    private GeoIPService geoIPService;

    private final List<ClientInfo> clientInfoList;
    private final Map<String, Long> cacheClientsLogged;
    private final Map<String, Long> cacheClientsFailed;

    public StreamProcessorsSession() {
        cacheClientsLogged = new HashMap();
        cacheClientsFailed = new HashMap();
        clientInfoList = new ArrayList();
    }

    public  void addCacheClient(String user,boolean logged) {
        synchronized (this) {
            if (logged) {
                cacheClientsLogged.put(user, new Date().getTime() + 1800000); //30 min cache
            } else {
                cacheClientsFailed.put(user, new Date().getTime() + 5000); //5s cache
            }
        }
    }

    public Boolean isUserLogged(String user){
        boolean logged=false;
       Long expireTime = cacheClientsLogged.get(user);
       if(expireTime==null){
           expireTime = cacheClientsFailed.get(user);
           if(expireTime==null){
               return null;
           }else if(new Date().getTime() > expireTime){
               return null;
           }else{
               return false;
           }
       }else{
           if(new Date().getTime() > expireTime){
               return null;
           }else{
               return true;
           }
       }
    }

    public synchronized ClientInfo addClientInfo(ClientInfo client) {
        if (!clientInfoList.contains(client)) {
            clientInfoList.add(client);
        }
        return clientInfoList.get(clientInfoList.indexOf(client));
    }

    public synchronized void removeClientInfo(ClientInfo client) {
        if (clientInfoList.contains(client)) {
            clientInfoList.remove(client);
        }
    }

    public synchronized List<ClientInfo> getClientInfoList() {
        return clientInfoList;
    }

    public synchronized ClientInfo manage(IStreamProcessor iStreamProcessor, HttpServletRequest request) throws UnknownHostException {
        ClientInfo client = new ClientInfo();
        request.getQueryString();
        MultiValueMap<String, String> parameters = UriComponentsBuilder.fromUriString(ProxyLiveUtils.getURL(request)).build().getQueryParams();
        String clientUser = parameters.getFirst("user");
        if (clientUser == null || clientUser.trim().equals("null")) {
            clientUser = "guest";
        }
        client.setClientUser(clientUser);
        client.setIp(InetAddress.getByName(ProxyLiveUtils.getRequestIP(request)));
        client.setBrowserInfo(ProxyLiveUtils.getBrowserInfo(request));
        client = addClientInfo(client);
        try {
            DatabaseReader geoDBReader = geoIPService.geoGEOInfoReader();
            CityResponse cityResponse = geoDBReader.city(client.getIp());

            if(cityResponse.getLocation()!=null){
                GEOInfo geoInfo = new GEOInfo();
                geoInfo.setCity(cityResponse.getCity());
                geoInfo.setCountry(cityResponse.getCountry());
                geoInfo.setLocation(cityResponse.getLocation());
                client.setGeoInfo(geoInfo);
            }

        }catch(Exception ex ){
            System.err.println(ex);
        }
        if (!client.getStreams().contains(iStreamProcessor)) {
            client.getStreams().add(iStreamProcessor);
        }
        return client;
    }

    public synchronized void removeClientInfo(ClientInfo client, IStreamProcessor iStreamProcessor) {
        client.getStreams().remove(iStreamProcessor);
        if (client.getStreams().isEmpty()) {
            removeClientInfo(client);
        }
    }

    public synchronized DirectHLSTranscoderStreamProcessor getHLSStream(String clientIdentifier, String channel, String profile) {
        for (ClientInfo clientInfo : clientInfoList) {
            if (clientIdentifier.equals(clientInfo.getIp())) {
                for (IStreamProcessor streamProcessor : clientInfo.getStreams()) {
                    profile=profile.equals("raw")?"":"_" +profile;
                    if (streamProcessor.getTask().getIdentifier().equals(channel +  profile + "_HLS")) {
                        return (DirectHLSTranscoderStreamProcessor) streamProcessor;
                    }
                }
            }
        }
        return null;
    }
}
