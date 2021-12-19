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
package com.github.segator.proxylive.config;

import com.github.segator.proxylive.helper.JwtHelper;
import com.github.segator.proxylive.tasks.DirectTranscodeTask;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.properties.ConfigurationProperties;


import org.springframework.context.annotation.Configuration;

import javax.annotation.PostConstruct;

/**
 *
 * @author Isaac Aymerich <isaac.aymerich@gmail.com>
 */
@Configuration
@ConfigurationProperties
public class ProxyLiveConfiguration {
    private final Logger logger = LoggerFactory.getLogger(DirectTranscodeTask.class);
    private final JwtHelper jwtHelper;
    private BufferingConfiguration buffers;
    private FFMpegConfiguration ffmpeg;
    private HttpLiveSource source;
    private GEOIPDatasource geoIP;
    private AuthenticationConfiguration authentication;
    private String userAgent;
    private int streamTimeout;

    public ProxyLiveConfiguration(JwtHelper jwtHelper) {
        this.jwtHelper = jwtHelper;
    }

    @PostConstruct
    public void initializeBean() {

        //If tvheadend input is set complete configuration
        if(source.getTvheadendURL()!=null){
            if( source.getEpg().getUrl()==null) {
                source.getEpg().setUrl(source.getTvheadendURL() + "/xmltv/channels");
            }
            if(source.getChannels().getUrl()==null) {
                source.getChannels().setUrl(source.getTvheadendURL());
            }
        }


    }

    public FFMpegConfiguration getFfmpeg() {
        return ffmpeg;
    }

    public void setFfmpeg(FFMpegConfiguration ffmpeg) {
        this.ffmpeg = ffmpeg;
    }

    public HttpLiveSource getSource() {
        return source;
    }

    public GEOIPDatasource getGeoIP() {
        return geoIP;
    }

    public void setGeoIP(GEOIPDatasource geoIP) {
        this.geoIP = geoIP;
    }

    public void setSource(HttpLiveSource source) {
        this.source = source;
    }

    public BufferingConfiguration getBuffers() {
        return buffers;
    }

    public void setBuffers(BufferingConfiguration buffers) {
        this.buffers = buffers;
    }

    public AuthenticationConfiguration getAuthentication() {
        return authentication;
    }

    public void setAuthentication(AuthenticationConfiguration authentication) {
        this.authentication = authentication;
    }

    public int getStreamTimeout() {
        return streamTimeout;
    }
    public int getStreamTimeoutMilis() {
        return streamTimeout*1000;
    }

    public void setStreamTimeout(int streamTimeout) {
        this.streamTimeout = streamTimeout;
    }


    public String getUserAgent() {
        return userAgent;
    }

    public void setUserAgent(String userAgent) {
        this.userAgent = userAgent;
    }
}
