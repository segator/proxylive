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

/**
 *
 * @author Isaac Aymerich <isaac.aymerich@gmail.com>
 */
public class HttpLiveSource {
    private String tvheadendURL;
    private String m3u8URL;
    private EPGConfiguration epg;
    private int reconnectTimeout;
    private int channelListCacheTime=600;
    private ChannelsConfiguration channels;

    public String getTvheadendURL() {
        return tvheadendURL;
    }

    public void setTvheadendURL(String tvheadendURL) {
        this.tvheadendURL = tvheadendURL;
    }

    public EPGConfiguration getEpg() {
        return epg;
    }

    public void setEpg(EPGConfiguration epg) {
        this.epg = epg;
    }

    public ChannelsConfiguration getChannels() {
        return channels;
    }

    public void setChannels(ChannelsConfiguration channels) {
        this.channels = channels;
    }

    public int getReconnectTimeout() {
        return reconnectTimeout;
    }

    public void setReconnectTimeout(int reconnectTimeout) {
        this.reconnectTimeout = reconnectTimeout;
    }

    public int getChannelListCacheTime() {
        return channelListCacheTime;
    }

    public void setChannelListCacheTime(int channelListCacheTime) {
        this.channelListCacheTime = channelListCacheTime;
    }

    public String getM3u8URL() {
        return m3u8URL;
    }

    public void setM3u8URL(String m3u8URL) {
        this.m3u8URL = m3u8URL;
    }
}
