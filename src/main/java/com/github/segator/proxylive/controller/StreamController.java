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
package com.github.segator.proxylive.controller;

import com.github.segator.proxylive.ProxyLiveConstants;
import com.github.segator.proxylive.ProxyLiveUtils;
import com.github.segator.proxylive.entity.ClientInfo;
import com.github.segator.proxylive.processor.HLSStreamProcessor;
import com.github.segator.proxylive.processor.IHLSStreamProcessor;
import com.github.segator.proxylive.processor.IStreamMultiplexerProcessor;
import com.github.segator.proxylive.tasks.StreamProcessorsSession;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.URISyntaxException;
import java.util.Date;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import com.github.segator.proxylive.config.ProxyLiveConfiguration;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationContext;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.ResponseBody;

/**
 *
 * @author Isaac Aymerich <isaac.aymerich@gmail.com>
 */
@Controller
public class StreamController {

    @Autowired
    private ApplicationContext context;
    @Autowired
    private ProxyLiveConfiguration config;

    @Autowired
    private StreamProcessorsSession streamProcessorsSession;



    @RequestMapping(value = "/view//{channel}")
    public void dispatchStream(@PathVariable("channel") String channel,
            HttpServletRequest request, HttpServletResponse response) throws  Exception {
        dispatchStream(null, channel, request, response);
    }

    @RequestMapping(value = "/view/{profile}/{channel}")
    public void dispatchStream(@PathVariable("profile") String profile, @PathVariable("channel") String channel,
            HttpServletRequest request, HttpServletResponse response) throws Exception {
        String clientIdentifier = ProxyLiveUtils.getRequestIP(request) + ProxyLiveUtils.getBrowserInfo(request);
        IStreamMultiplexerProcessor iStreamProcessor = (IStreamMultiplexerProcessor) context.getBean("StreamProcessor", ProxyLiveConstants.STREAM_MODE, clientIdentifier, channel, profile);
        ClientInfo client = streamProcessorsSession.manage(iStreamProcessor, request);

        System.out.println("require connection from : " + client);
        iStreamProcessor.start();
        if (iStreamProcessor.isConnected()) {
            response.setHeader("Connection", "close");
            response.setHeader("Content-Type", "video/mpeg");
            response.setStatus(HttpStatus.OK.value());
            OutputStream clientStream = response.getOutputStream();
            InputStream multiplexedInputStream = iStreamProcessor.getMultiplexedInputStream();
            byte[] buffer = new byte[config.getBuffers().getChunkSize()];
            int len;
            try {
                while (true) {
                    len = multiplexedInputStream.read(buffer);
                    if (len > 0) {
                        clientStream.write(buffer, 0, len);
                    } else {
                        Thread.sleep(1);
                    }
                }
            } catch (Exception ex) {
                try {
                    clientStream.close();
                } catch (Exception ex2) {
                }
            }

            System.out.println("stopping from controller");
            iStreamProcessor.stop(false);
            streamProcessorsSession.removeClientInfo(client, iStreamProcessor);

        } else {
            System.out.println("stopping from controller (not conected)");
            iStreamProcessor.stop(false);
            streamProcessorsSession.removeClientInfo(client, iStreamProcessor);
            response.setStatus(HttpStatus.NOT_FOUND.value());
        }
        System.out.println("User Close:" + client);

    }

    @RequestMapping(value = "/crossdomain.xml", method = RequestMethod.GET)
    public @ResponseBody
    String getCrossDomain() {
        return "<?xml version=\"1.0\" ?>\n"
                + "<cross-domain-policy>\n"
                + "<allow-access-from domain=\"*\" />\n"
                + "</cross-domain-policy>";
    }

    @RequestMapping(value = "/view/{profile}/{channel}/{file:^(?i)playlist.m3u8|playlist\\d*.ts$}", method = RequestMethod.GET)
    public void dispatchHLS(@PathVariable("profile") String profile, @PathVariable("channel") String channel,
            @PathVariable String file, HttpServletRequest request, HttpServletResponse response) throws Exception {
        String clientIdentifier = ProxyLiveUtils.getRequestIP(request) + ProxyLiveUtils.getBrowserInfo(request);
        HLSStreamProcessor hlsStreamProcessor = streamProcessorsSession.getHLSStream(ProxyLiveUtils.getRequestIP(request), channel, profile);
        if (hlsStreamProcessor == null) {
            hlsStreamProcessor = (HLSStreamProcessor) context.getBean("StreamProcessor", ProxyLiveConstants.HLS_MODE, clientIdentifier, channel, profile);
            hlsStreamProcessor.start();
        }
        ClientInfo client = streamProcessorsSession.manage(hlsStreamProcessor, request);
        if (hlsStreamProcessor.isConnected()) {
            file = file.toLowerCase();
            System.out.println("Client require:" + file);
            InputStream downloadFile = getFileToUpload(file, hlsStreamProcessor, response);
            if (downloadFile != null) {
                response.setStatus(200);
                //response.setHeader(file, file);
                byte[] buffer = new byte[config.getBuffers().getChunkSize()];
                OutputStream output = response.getOutputStream();
                int len = 0;
                try {
                    while ((len = downloadFile.read(buffer)) != -1) {
                        output.write(buffer, 0, len);
                    }
                } catch (Exception ex) {

                } finally {
                    try {
                        output.close();
                    } catch (Exception ex2) {
                    }
                    try {
                        downloadFile.close();
                    } catch (Exception ex2) {
                    }
                }
            } else {
                response.setStatus(404);
            }
        } else {
            response.setStatus(404);
        }
    }

    private MediaType getMediaType(String downloadFile) {
        String name = downloadFile;
        try {
            String extension = name.substring(name.lastIndexOf(".") + 1);
            switch (extension.toLowerCase()) {
                case "ts":
                    return new MediaType("application", "octet-stream");
                case "m3u8":
                    return new MediaType("application", "octet-stream");
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        return null;
    }

    private InputStream getFileToUpload(String file, IHLSStreamProcessor hlsStreamProcessor, HttpServletResponse respHeaders) throws IOException, URISyntaxException, InterruptedException {
        InputStream downloadFile = null;
        long now = new Date().getTime();
        Long fileSize = null;
        if (file.endsWith("m3u8")) {

            do {
                downloadFile = hlsStreamProcessor.getPlayList();
                if (downloadFile == null) {
                    Thread.sleep(100);

                }
                if ((new Date().getTime() - now) > 60000) {
                    return null;
                }
            } while (downloadFile == null);
            fileSize = Long.valueOf(downloadFile.available());
        } else {
            do {
                downloadFile = hlsStreamProcessor.getSegment(file);

                if (downloadFile == null) {
                    Thread.sleep(100);
                }
                if ((new Date().getTime() - now) > 60000) {
                    return null;
                }
            } while (downloadFile == null);
            fileSize = hlsStreamProcessor.getSegmentSize(file);
        }
        respHeaders.setHeader("Connection", "close");
        respHeaders.setHeader(HttpHeaders.CACHE_CONTROL, "no-cache");
        respHeaders.setHeader(HttpHeaders.CONTENT_LENGTH, fileSize.toString());
        respHeaders.setHeader(HttpHeaders.CONTENT_TYPE, getMediaType(file).toString());
        return downloadFile;
    }
}
