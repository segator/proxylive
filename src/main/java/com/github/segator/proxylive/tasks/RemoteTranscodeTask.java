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
import com.github.segator.proxylive.config.FFMpegProfile;
import com.github.segator.proxylive.config.ProxyLiveConfiguration;
import com.github.segator.proxylive.config.RemoteTranscoder;
import com.github.segator.proxylive.entity.ChannelSource;
import com.github.segator.proxylive.processor.IStreamProcessor;
import com.github.segator.proxylive.service.TokenService;
import com.github.segator.proxylive.stream.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationContext;

import javax.annotation.PostConstruct;
import java.io.IOException;
import java.net.HttpURLConnection;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.Date;
import java.util.Objects;

/**
 *
 * @author Isaac Aymerich <isaac.aymerich@gmail.com>
 */
public class RemoteTranscodeTask implements IMultiplexerStreamer {
    private final Logger logger = LoggerFactory.getLogger(RemoteTranscodeTask.class);
    private final RemoteTranscoder transcoder;

    private final String channelID;
    private Date runDate;
    private VideoInputStream videoInputStream;
    private BroadcastCircularBufferedOutputStream multiplexerOutputStream;
    private boolean terminate = false;
    private boolean crashed = false;
    private int crashTimes = 0;
    private Long now;
    private byte[] buffer;

    @Autowired
    private TokenService tokenService;
    @Autowired
    private ProxyLiveConfiguration config;

    public RemoteTranscodeTask(String channelID,RemoteTranscoder transcoder) {
        this.transcoder = transcoder;
        this.channelID = channelID;
    }

    @PostConstruct
    public void initializeBean() throws Exception {
        buffer = new byte[config.getBuffers().getChunkSize()];
        multiplexerOutputStream = new BroadcastCircularBufferedOutputStream(config.getBuffers().getBroadcastBufferSize());
    }

    @Override
    public void initializate() throws MalformedURLException, IOException {

    }

    @Override
    public void terminate() {
        terminate = true;
    }

    public boolean isTerminated() {
        return terminate;
    }

    @Override
    public BroadcastCircularBufferedOutputStream getMultiplexer() {
        return multiplexerOutputStream;
    }

    @Override
    public String getSource() {
        return String.format("%s/view/%s/%s",transcoder.getEndpoint(), transcoder.getProfile(),channelID);
    }
    public String getAuthenticatedURL(){
        String token = tokenService.createServiceAccountRequestToken(String.format("RemoteTranscode-%s",getIdentifier()));
        return String.format("%s?token=%s",getSource(),token);
    }


    private String getStringIdentifier(String message){
        return "[id:" + getIdentifier() +"] " + message;
    }
    @Override
    public void run() {
        runDate = new Date();
        int len;
        try {
            now = new Date().getTime();
            videoInputStream = new WebInputStream(new URL(getAuthenticatedURL()),config);

            logger.debug(getStringIdentifier("Get Stream"));
            if (videoInputStream.connect()) {
                long lastReaded  = new Date().getTime();
                while (!terminate) {
                    len = videoInputStream.read(buffer);
                    if (len > 0) {
                        multiplexerOutputStream.write(buffer, 0, len);
                        lastReaded  = new Date().getTime();
                    }else if((new Date().getTime() - lastReaded)  > config.getSource().getReconnectTimeout()*1000) {
                        throw new Exception(String.format("no data received on %d seconds",config.getSource().getReconnectTimeout()));
                    }else{
                        Thread.sleep(10);
                    }
                }
            }else{
                throw new Exception (getStringIdentifier("Impossible to connect"));
            }
        } catch (Exception ex) {
            logger.error(getStringIdentifier(ex.getMessage()));

            if (crashTimes > 2 || terminate) {
                crashed = true;
                terminate = true;
            } else {
                crashTimes++;
                closeWebStream();
                run();
            }
        } finally {
            closeWebStream();
            try {
                multiplexerOutputStream.close();
            } catch (Exception ex) {
            }

        }
    }


    private void closeWebStream() {
        try {
            videoInputStream.close();
        }catch(Exception ex) {
        }
    }

    @Override
    public int hashCode() {
        int hash = 7;
        hash = 89 * hash + Objects.hashCode(getIdentifier());
        return hash;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        RemoteTranscodeTask that = (RemoteTranscodeTask) o;
        return transcoder.equals(that.transcoder) && channelID.equals(that.channelID);
    }

    @Override
    public String getIdentifier() {
        return channelID+"_"+transcoder.getEndpoint()+"_"+transcoder.getProfile();
    }

    @Override
    public String toString() {
        return getIdentifier();
    }

    @Override
    public boolean isCrashed() {
        return crashed;
    }

    @Override
    public IStreamProcessor getSourceProcessor() {
        return null;
    }

    @Override
    public Date startTaskDate() {
        return runDate;
    }

}
