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
import java.util.Base64;
import java.util.Date;

import com.github.segator.proxylive.entity.Channel;
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

    @Autowired
    private ApplicationContext context;

    @Bean
    @Scope(value = "prototype")
    public IStreamProcessor StreamProcessor(int mode, String clientIdentifier, Channel channel, String profile) {
        IStreamProcessor streamProcessor = null;
        switch (mode) {
            case ProxyLiveConstants.HLS_MODE:
                streamProcessor = (IStreamProcessor) context.getBean("DirectHLSTranscoderStreamProcessor",  channel, profile);
                break;
            case ProxyLiveConstants.STREAM_MODE:
                if (profile==null || profile.equals("raw")) {
                    streamProcessor = (HttpSoureStreamProcessor) context.getBean("HttpSoureStreamProcessor",  channel);
                }else{
                    streamProcessor = (IStreamProcessor) context.getBean("DirectTranscodedStreamProcessor", channel, profile);
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
    public IStreamProcessor DirectHLSTranscoderStreamProcessor(Channel channel, String profile) {
        return new DirectHLSTranscoderStreamProcessor(channel, profile);
    }
}
