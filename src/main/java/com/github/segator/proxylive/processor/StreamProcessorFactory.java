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
    public IStreamProcessor StreamProcessor(int mode, String clientIdentifier, String channel, String profile) {
        String identifier = new Date().getTime() + clientIdentifier;
        String identifier64 = new String(Base64.getEncoder().encode(identifier.getBytes()));

        HttpSoureStreamProcessor sourceStreamProcessor = (HttpSoureStreamProcessor) context.getBean("HttpSoureStreamProcessor", identifier64, channel);

        IStreamProcessor streamProcessor = null;
        if (profile==null) {
            streamProcessor = sourceStreamProcessor;
        } else {
            //streamProcessor = (IStreamProcessor) context.getBean("TranscodedStreamProcessor", identifier64, sourceStreamProcessor, profile);
            streamProcessor = (IStreamProcessor) context.getBean("DirectTranscodedStreamProcessor", identifier64, channel, profile);
        }
        IStreamProcessor postStreamProcessor = null;
        switch (mode) {
            case ProxyLiveConstants.HLS_MODE:
                postStreamProcessor = (IStreamProcessor) context.getBean("HLSStreamProcessor", identifier64, streamProcessor);
                break;
            case ProxyLiveConstants.STREAM_MODE:
                postStreamProcessor = streamProcessor;
                break;
        }
        return postStreamProcessor;

    }

    @Bean
    @Scope(value = "prototype")
    public IStreamProcessor HttpSoureStreamProcessor(String identifier, String channel) {
        return new HttpSoureStreamProcessor(channel, identifier);
    }

    @Bean
    @Scope(value = "prototype")
    public IStreamProcessor TranscodedStreamProcessor(String identifier,IStreamProcessor iStreamProcessor, String profile) {
        return new TranscodedStreamProcessor(iStreamProcessor, profile,identifier);
    }
    @Bean
    @Scope(value = "prototype")
    public IStreamProcessor DirectTranscodedStreamProcessor(String identifier,String channel, String profile) {
        return new DirectTranscoderStreamProcessor(channel, profile,identifier);
    }

    @Bean
    @Scope(value = "prototype")
    public IStreamProcessor HLSStreamProcessor(String identifier,IStreamProcessor iStreamProcessor) {
        return new HLSStreamProcessor(iStreamProcessor,identifier);
    }
}
