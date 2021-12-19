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
package com.github.segator.proxylive.processor;

import com.github.segator.proxylive.ProxyLiveConstants;

import java.rmi.Remote;
import java.util.Base64;
import java.util.Date;
import java.util.List;

import com.github.segator.proxylive.config.FFMpegProfile;
import com.github.segator.proxylive.config.RemoteTranscoder;
import com.github.segator.proxylive.entity.Channel;
import com.github.segator.proxylive.entity.ChannelSource;
import com.github.segator.proxylive.profiler.FFmpegProfilerService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Scope;

/**
 *
 * @author Isaac Aymerich <isaac.aymerich@gmail.com>
 */
@Configuration
public class StreamProcessorFactory {

    private final ApplicationContext context;
    private final FFmpegProfilerService ffmpegProfileService;

    public StreamProcessorFactory(ApplicationContext context, FFmpegProfilerService ffmpegProfileService) {
        this.context = context;
        this.ffmpegProfileService = ffmpegProfileService;
    }

    @Bean
    @Scope(value = "prototype")
    public IStreamProcessor StreamProcessor(int mode, String clientIdentifier, Channel channel, String profile) {
        IStreamProcessor streamProcessor = null;
        switch (mode) {
            case ProxyLiveConstants.HLS_MODE:
                streamProcessor = (IStreamProcessor) context.getBean("DirectHLSTranscoderStreamProcessor",  channel, profile);
                break;
            case ProxyLiveConstants.STREAM_MODE:
                if(profile!=null && !profile.equals("raw")){
                    FFMpegProfile ffmpegProfile = ffmpegProfileService.getProfile(profile);
                    if(ffmpegProfile.isLocalTranscoding()) {
                        streamProcessor = (IStreamProcessor) context.getBean("DirectTranscodedStreamProcessor", channel, profile);
                        break;
                    }else{
                        RemoteTranscoder remoteTranscoder = RemoteTranscoder.CreateFrom(ffmpegProfile.getTranscoder());
                        if(remoteTranscoder.getProfile()==null){
                            remoteTranscoder.setProfile(profile);
                        }
                        streamProcessor = (IStreamProcessor) context.getBean("RemoteTranscodeStreamProcessor", channel.getId(), remoteTranscoder);
                    }
                }else {
                    streamProcessor = (HttpSoureStreamProcessor) context.getBean("HttpSoureStreamProcessor", channel);
                }
                break;
        }
        return streamProcessor;

    }

    @Bean
    @Scope(value = "prototype")
    public IStreamProcessor HttpSoureStreamProcessor(Channel channel) {
        return new HttpSoureStreamProcessor(channel);
    }

    @Bean
    @Scope(value = "prototype")
    public IStreamProcessor DirectTranscodedStreamProcessor(Channel channel, String profile) {
        return new DirectTranscoderStreamProcessor(channel, profile);
    }
    @Bean
    @Scope(value = "prototype")
    public IStreamProcessor RemoteTranscodeStreamProcessor(String channelID, RemoteTranscoder transcoder) {
        return new RemoteTranscodeStreamProcessor(channelID, transcoder);
    }

    @Bean
    @Scope(value = "prototype")
    public IStreamProcessor DirectHLSTranscoderStreamProcessor(Channel channel, String profile) {
        return new DirectHLSTranscoderStreamProcessor(channel, profile);
    }
}
