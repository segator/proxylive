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

import com.github.segator.proxylive.processor.IStreamProcessor;
import com.github.segator.proxylive.stream.BroadcastCircularBufferedOutputStream;
import com.github.segator.proxylive.stream.WebInputStream;
import java.io.IOException;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.Date;
import java.util.Objects;
import javax.annotation.PostConstruct;
import com.github.segator.proxylive.config.ProxyLiveConfiguration;
import org.springframework.beans.factory.annotation.Autowired;

/**
 *
 * @author Isaac Aymerich <isaac.aymerich@gmail.com>
 */
public class HttpDownloaderTask implements IMultiplexerStreamer {

    private String url;
    private final String channelName;
    private WebInputStream webInputStream;
    private BroadcastCircularBufferedOutputStream multiplexerOutputStream;
    private boolean terminate = false;
    private boolean crashed = false;
    private int crashTimes = 0;
    private byte[] buffer;
    @Autowired
    private ProxyLiveConfiguration config;

    public HttpDownloaderTask(String channelName) throws MalformedURLException, IOException {
        this.channelName = channelName;

    }

    @PostConstruct
    public void initializeBean() throws Exception {
        url = config.getSource().getTvheadendurl() + "/stream/channel/" + channelName;
        buffer = new byte[config.getBuffers().getChunkSize()];
        webInputStream = new WebInputStream(new URL(url), config.getSource().getReconnectTimeout());
        multiplexerOutputStream = new BroadcastCircularBufferedOutputStream(config.getBuffers().getBroadcastBufferSize());
    }

    @Override
    public void initializate() throws MalformedURLException, IOException {

    }

    public synchronized String getUrl() {
        return url;
    }

    public synchronized String getChannelName() {
        return channelName;
    }

    @Override
    public void terminate() {
        System.out.println("[" + getIdentifier() + "]Required Http stream terminate");
        terminate = true;
    }

    @Override
    public BroadcastCircularBufferedOutputStream getMultiplexer() {
        return multiplexerOutputStream;
    }

    @Override
    public void run() {
        int len;
        try {
            System.out.println("[" + getIdentifier() + "] Start Http stream");
            if (webInputStream.connect()) {
                //videoTranscodedStream.start();
                while (!terminate) {
                    len = webInputStream.read(buffer);
                    if (len > 0) {
                        multiplexerOutputStream.write(buffer, 0, len);
                    }
                }
            }
            System.out.println("[" + getIdentifier() + "]Required Http stream terminated");
        } catch (Exception ex) {
            crashTimes++;
            if (crashTimes > 10) {
                crashed = true;
            } else {
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
            webInputStream.close();
        } catch (Exception ex) {
        }
    }

    @Override
    public int hashCode() {
        int hash = 7;
        hash = 89 * hash + Objects.hashCode(this.url);
        return hash;
    }

    public boolean isTerminated() {
        return terminate;
    }

    @Override
    public boolean equals(Object obj) {
        if (obj == null) {
            return false;
        }
        if (getClass() != obj.getClass()) {
            return false;
        }
        final HttpDownloaderTask other = (HttpDownloaderTask) obj;
        if (!Objects.equals(this.url, other.url)) {
            return false;
        }
        return true;
    }

    @Override
    public String getIdentifier() {
        return channelName;
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
        if (webInputStream != null) {
            return new Date(webInputStream.getLastConnection());
        }
        return null;
    }

}
