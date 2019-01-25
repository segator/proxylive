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

    private BufferingConfiguration buffers;
    private FFMpegConfiguration ffmpeg;
    private HttpLiveSource source;
    private AuthenticationConfiguration authentication;
    private String endpoint;
    private int streamTimeout;
    private boolean internalConnection;
    private String internalToken;

    @PostConstruct
    public void initializeBean() {
        int count=40;
        final String VALIDCHARS = "ABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789";
        StringBuilder builder = new StringBuilder();
        while (count-- != 0) {
            int character = (int)(Math.random()*VALIDCHARS.length());
            builder.append(VALIDCHARS.charAt(character));
        }
        internalToken= builder.toString();
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

    public String getEndpoint() {
        return endpoint;
    }

    public void setEndpoint(String endpoint) {
        this.endpoint = endpoint;
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

    public boolean isInternalConnection() {
        return internalConnection;
    }

    public void setInternalConnection(boolean internalConnection) {
        this.internalConnection = internalConnection;
    }

    public String getInternalToken() {
        return internalToken;
    }
}
