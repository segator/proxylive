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
package com.github.segator.proxylive.entity;

import com.github.segator.proxylive.processor.IStreamProcessor;

import java.net.InetAddress;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 *
 * @author Isaac Aymerich <isaac.aymerich@gmail.com>
 */
public class ClientInfo {
    private String clientUser;
    private InetAddress ip;
    private GEOInfo geoInfo;
    private String browserInfo;
    private List<IStreamProcessor> streams;
    
    public ClientInfo(){
        streams=new ArrayList();
    }

    public InetAddress getIp() {
        return ip;
    }

    public void setIp(InetAddress ip) {
        this.ip = ip;
    }

    public String getBrowserInfo() {
        return browserInfo;
    }

    public String getClientUser() {
        return clientUser;
    }

    public void setClientUser(String clientUser) {
        this.clientUser = clientUser;
    }

    public GEOInfo getGeoInfo() {
        return geoInfo;
    }

    public void setGeoInfo(GEOInfo geoInfo) {
        this.geoInfo = geoInfo;
    }

    public void setBrowserInfo(String browserInfo) {
        this.browserInfo = browserInfo;
    }

    public List<IStreamProcessor> getStreams() {
        return streams;
    }

    public void setStreams(List<IStreamProcessor> streams) {
        this.streams = streams;
    }

    @Override
    public int hashCode() {
        int hash = 5;
        hash = 53 * hash + Objects.hashCode(this.clientUser);
        hash = 53 * hash + Objects.hashCode(this.ip);
        hash = 53 * hash + Objects.hashCode(this.browserInfo);
        return hash;
    }

    @Override
    public boolean equals(Object obj) {
        if (obj == null) {
            return false;
        }
        if (getClass() != obj.getClass()) {
            return false;
        }
        final ClientInfo other = (ClientInfo) obj;
        if (!Objects.equals(this.ip, other.ip)) {
            return false;
        }
        if (!Objects.equals(this.browserInfo, other.browserInfo)) {
            return false;
        }
        if (!Objects.equals(this.clientUser, other.clientUser)) {
            return false;
        }
        return true;
    }

    @Override
    public String toString() {
        String streamToStr="[";
        for (IStreamProcessor stream : streams) {
            streamToStr+=stream.toString()+",";
        }
        streamToStr = streamToStr.substring(0,streamToStr.length()-1);
        streamToStr+="]";
        return "{user="+clientUser+", ip=" + ip + ", browserInfo=" + browserInfo + ", streams=" + streamToStr + '}';
    }
    
    
}
