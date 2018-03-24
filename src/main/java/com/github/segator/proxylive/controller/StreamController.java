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
import com.github.segator.proxylive.processor.IStreamMultiplexerProcessor;
import com.github.segator.proxylive.tasks.StreamProcessorsSession;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import com.github.segator.proxylive.config.ProxyLiveConfiguration;
import com.github.segator.proxylive.processor.HLSStreamProcessor;
import com.github.segator.proxylive.processor.IHLSStreamProcessor;
import com.github.segator.proxylive.service.AuthenticationService;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.MalformedURLException;
import java.net.ProtocolException;
import java.net.URISyntaxException;
import java.net.URL;
import java.util.Base64;
import java.util.Date;
import java.util.List;
import static org.hibernate.validator.internal.util.CollectionHelper.newArrayList;
import org.json.simple.JSONArray;
import org.json.simple.JSONObject;
import org.json.simple.parser.JSONParser;
import org.json.simple.parser.ParseException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationContext;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Controller;
import org.springframework.util.MultiValueMap;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.web.util.UriComponentsBuilder;

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
    private AuthenticationService authService;

    @Autowired
    private StreamProcessorsSession streamProcessorsSession;

    @RequestMapping(value = "/view/{profile}/{channel}")
    public void dispatchStream(@PathVariable("profile") String profile, @PathVariable("channel") String channel,
            HttpServletRequest request, HttpServletResponse response) throws Exception {
        MultiValueMap<String, String> parameters = UriComponentsBuilder.fromUriString(ProxyLiveUtils.getURL(request)).build().getQueryParams();
        if (!authService.loginUser(parameters.getFirst("user"), parameters.getFirst("pass"))) {
            response.setStatus(404);
            return;
        }

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

            iStreamProcessor.stop(false);
            streamProcessorsSession.removeClientInfo(client, iStreamProcessor);

        } else {
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

    @RequestMapping(value = "icon/{iconID}", method = RequestMethod.GET)
    public void getIcon(@PathVariable("iconID") String iconPath, HttpServletResponse response) throws IOException {
        HttpURLConnection connection = getURLConnection("imagecache/" + iconPath);
        if (connection.getResponseCode() != 200) {
            response.setStatus(connection.getResponseCode());
            return;
        }
        response.setStatus(200);
        InputStream iconStream = connection.getInputStream();
        //response.setHeader(file, file);
        byte[] buffer = new byte[1024];
        OutputStream output = response.getOutputStream();
        int len = 0;
        try {
            while ((len = iconStream.read(buffer)) != -1) {
                output.write(buffer, 0, len);
            }
        } catch (Exception ex) {

        } finally {
            try {
                output.close();
            } catch (Exception ex2) {
            }
            try {
                iconStream.close();
            } catch (Exception ex2) {
            }
        }
    }

    @RequestMapping(value = "channel/list/{format:^mpeg|hls$}/{profile}", method = RequestMethod.GET)
    public @ResponseBody
    String generatePlaylist(HttpServletRequest request, HttpServletResponse response, @PathVariable("profile") String profile, @PathVariable("format") String format) throws MalformedURLException, ProtocolException, IOException, ParseException, Exception {
        MultiValueMap<String, String> parameters = UriComponentsBuilder.fromUriString(ProxyLiveUtils.getURL(request)).build().getQueryParams();
        if (!authService.loginUser(parameters.getFirst("user"), parameters.getFirst("pass"))) {
            response.setStatus(404);
            return "Invalid login";
        }
        StringBuffer buffer = new StringBuffer();
        String requestBaseURL = String.format("%s://%s:%s", request.getScheme(), request.getServerName(), request.getServerPort());
        if (config.getEndpoint() != null) {
            requestBaseURL = config.getEndpoint();
        }
        JSONArray channels = (JSONArray) getTvheadendResponse("api/channel/grid?start=0&limit=5000").get("entries");
        List<String> userAllowedTags = getAllowedTags(null, null, null);

        buffer.append("#EXTM3U").append("\n");
        for (Object ochannel : channels) {
            JSONObject channel = (JSONObject) ochannel;
            if (isChannelAllowed(userAllowedTags, channel)) {
                buffer.append("#EXTINF:-1 tvg-logo=\"").append(String.format("%s/icon/%s", requestBaseURL, channel.get("icon_public_url").toString().split("/")[1])).
                        append("\" tvg-name=\"").append(channel.get("name")).append("\" type=").append(format).append(",").
                        append(String.format("%s", channel.get("name"))).append("\n");
                if (format.equals("mpeg")) {
                    buffer.append(String.format("%s/view/%s/%s", requestBaseURL, profile, channel.get("uuid"))).append("?user=").append(parameters.getFirst("user")).append("&pass=").append(parameters.getFirst("pass")).append("\n");
                } else if (format.equals("hls")) {
                    buffer.append(String.format("%s/view/%s/%s", requestBaseURL, profile, channel.get("uuid"))).append("playlist.m3u8").append("?user=").append(parameters.getFirst("user")).append("&pass=").append(parameters.getFirst("pass")).append("\n");
                }
            }else{
                System.out.println("not enabled:"+channel);
            }
        }
        response.setHeader("Content-Disposition", "attachment; filename=playlist.m3u8");
        return buffer.toString();
    }

    private boolean isChannelAllowed(List<String> userAllowedTags, JSONObject channel) {
        return (Boolean) channel.get("enabled") && ((JSONArray) channel.get("tags")).stream().anyMatch(tag -> userAllowedTags.stream().anyMatch(validTag -> validTag.equals(tag)));
    }

    private List<String> getAllowedTags(String user, String pass, List<String> groups) throws ProtocolException, IOException, MalformedURLException, ParseException {
        JSONArray tags = (JSONArray) getTvheadendResponse("api/channeltag/list").get("entries");
        List<String> validsTags = newArrayList();
        for (Object otag : tags) {
            JSONObject tag = (JSONObject) otag;
            tag.get("val");
            validsTags.add(tag.get("key").toString());
        }
        return validsTags;
    }

    private HttpURLConnection getURLConnection(String request) throws MalformedURLException, IOException {
        URL tvheadendURL = new URL(config.getSource().getTvheadendurl() + "/" + request);
        HttpURLConnection connection = (HttpURLConnection) tvheadendURL.openConnection();
        connection.setReadTimeout(10000);
        if (tvheadendURL.getUserInfo() != null) {
            String basicAuth = "Basic " + new String(Base64.getEncoder().encode(tvheadendURL.getUserInfo().getBytes()));
            connection.setRequestProperty("Authorization", basicAuth);
        }
        connection.setRequestMethod("GET");
        connection.connect();
        return connection;
    }

    private JSONObject getTvheadendResponse(String request) throws MalformedURLException, ProtocolException, IOException, ParseException {
        JSONParser jsonParser = new JSONParser();
        HttpURLConnection connection = getURLConnection(request);
        if (connection.getResponseCode() != 200) {
            throw new IOException("Error on open stream:" + request);
        }
        return (JSONObject) jsonParser.parse(new InputStreamReader(connection.getInputStream(), "UTF-8"));

    }

    @RequestMapping(value = "/view/{profile}/{channel}/{file:^(?i)playlist.m3u8|playlist\\d*.ts$}", method = RequestMethod.GET)
    public void dispatchHLS(@PathVariable("profile") String profile, @PathVariable("channel") String channel,
            @PathVariable String file, HttpServletRequest request, HttpServletResponse response) throws Exception {

        MultiValueMap<String, String> parameters = UriComponentsBuilder.fromUriString(ProxyLiveUtils.getURL(request)).build().getQueryParams();
        if (!authService.loginUser(parameters.getFirst("user"), parameters.getFirst("pass"))) {
            response.setStatus(404);
            return;
        }
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
