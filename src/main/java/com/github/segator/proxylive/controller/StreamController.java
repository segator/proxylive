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
import com.github.segator.proxylive.config.FFMpegProfile;
import com.github.segator.proxylive.config.ProxyLiveConfiguration;
import com.github.segator.proxylive.entity.Channel;
import com.github.segator.proxylive.entity.ClientInfo;
import com.github.segator.proxylive.processor.DirectHLSTranscoderStreamProcessor;
import com.github.segator.proxylive.processor.IHLSStreamProcessor;
import com.github.segator.proxylive.processor.IStreamMultiplexerProcessor;
import com.github.segator.proxylive.profiler.FFmpegProfilerService;
import com.github.segator.proxylive.service.ChannelService;
import com.github.segator.proxylive.service.EPGService;
import com.github.segator.proxylive.tasks.StreamProcessorsSession;
import org.apache.commons.io.IOUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationContext;
import org.springframework.core.io.Resource;
import org.springframework.core.io.UrlResource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.ResponseBody;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.*;
import java.net.URISyntaxException;
import java.util.*;
import java.util.regex.Pattern;

/**
 *
 * @author Isaac Aymerich <isaac.aymerich@gmail.com>
 */
@Controller
public class StreamController {

    private final Logger logger = LoggerFactory.getLogger(StreamController.class);

    private final ApplicationContext context;
    private final ProxyLiveConfiguration config;
    private final ChannelService channelService;
    private final EPGService epgService;
    private final FFmpegProfilerService ffmpegProfileService;
    private final StreamProcessorsSession streamProcessorsSession;

    public StreamController(ApplicationContext context, ProxyLiveConfiguration config, ChannelService channelService, EPGService epgService, FFmpegProfilerService ffmpegProfileService,  StreamProcessorsSession streamProcessorsSession) {
        this.context = context;
        this.config = config;
        this.channelService = channelService;
        this.epgService = epgService;
        this.ffmpegProfileService = ffmpegProfileService;
        this.streamProcessorsSession = streamProcessorsSession;
    }

    @RequestMapping(value = "/view/{profile}/{channelID}",method=RequestMethod.GET)
    public void dispatchStream(@PathVariable("profile") String profile, @PathVariable("channelID") String channelID,
            HttpServletRequest request, HttpServletResponse response,Authentication authentication) throws Exception {

        if(channelID.contains("?")){
            channelID=channelID.split(Pattern.quote("?"))[0];
        }
        Channel channel = channelService.getChannelByID(channelID);
        FFMpegProfile ffmpegProfile = ffmpegProfileService.getProfile(profile);
        if(channel==null || (ffmpegProfile==null   && !"raw".equals(profile))){
            response.setStatus(404);
            return;
        }
        IStreamMultiplexerProcessor iStreamProcessor = (IStreamMultiplexerProcessor) context.getBean("StreamProcessor", ProxyLiveConstants.STREAM_MODE, channel.getName(), channel, profile);
        ClientInfo client = streamProcessorsSession.manage(iStreamProcessor, request,authentication.getPrincipal().toString());

        logger.debug("Open Stream " + channelID + " by " + client.getClientUser());
        iStreamProcessor.start();
        if (iStreamProcessor.isConnected()) {
            response.setHeader("Connection", "close");
            response.setHeader("Content-Type", "video/mpeg");
            response.setStatus(HttpStatus.OK.value());
            OutputStream clientStream = response.getOutputStream();
            if(!clientStream.getClass().getName().equals("javax.servlet.http.NoBodyOutputStream")) {
                InputStream multiplexedInputStream = iStreamProcessor.getMultiplexedInputStream();
                byte[] buffer = new byte[config.getBuffers().getChunkSize()];
                int len;
                try {

                    //RandomAccessFile fis = new RandomAccessFile("C:\\lol\\video.ts","r");

                    long lastReaded  = new Date().getTime();
                    while (true) {
                        len = multiplexedInputStream.read(buffer);
                        if (len > 0) {
                            lastReaded = new Date().getTime();
                            clientStream.write(buffer, 0, len);
                        } else {
                            if (!iStreamProcessor.isConnected()) {
                                throw new IOException("Disconnected" + client + " because task crashed on " + iStreamProcessor);
                            } else if ((new Date().getTime() - lastReaded) > config.getStreamTimeoutMilis()) {
                                throw new IOException("Disconnected" + client + " because timeout on " + iStreamProcessor);
                            /*}else if((new Date().getTime() - lastReaded)  > 500 && profile.equals("raw")) {
                                if(fis.getFilePointer()==fis.length()){fis.seek(0);}
                                len = fis.read(buffer);
                                clientStream.write(buffer, 0, len);
                                Thread.sleep(100);*/
                            } else {
                                Thread.sleep(10);
                            }
                        }
                    }
                } catch (Exception ex) {
                    try {
                        clientStream.close();
                    } catch (Exception ex2) {
                    }
                }
            }
            iStreamProcessor.stop(false);
            streamProcessorsSession.removeClientInfo(client, iStreamProcessor);

        } else {
            iStreamProcessor.stop(false);
            streamProcessorsSession.removeClientInfo(client, iStreamProcessor);
            response.setStatus(HttpStatus.NOT_FOUND.value());
        }
        logger.debug("Close Stream " + channelID  + " by " + client.getClientUser());
    }

    @RequestMapping(value = "/crossdomain.xml", method = RequestMethod.GET)
    public @ResponseBody
    String getCrossDomain() {
        return "<?xml version=\"1.0\" ?>\n"
                + "<cross-domain-policy>\n"
                + "<allow-access-from domain=\"*\" />\n"
                + "</cross-domain-policy>";
    }



    @RequestMapping(value = "epg", method = {RequestMethod.GET,RequestMethod.HEAD})
    public ResponseEntity<Resource> readEPG(HttpServletRequest request) throws IOException {
        String fileName="xmltv.xml";
        /*Enumeration headerNames = request.getHeaderNames();
        while(headerNames.hasMoreElements()) {
            String headerName = (String)headerNames.nextElement();
            System.out.println("" + headerName + ":" + request.getHeader(headerName));

        }*/

        File epgFile = epgService.getEPG();
        Resource resource = new UrlResource(epgFile.toPath().toUri());

        // Try to determine file's content type
        String contentType = null;
        contentType = request.getServletContext().getMimeType(fileName);

        // Fallback to the default content type if type could not be determined
        if(contentType == null) {
            contentType = "application/octet-stream";
        }

        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(contentType))
                .contentLength(epgFile.length())
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + fileName + "\"")
                .header("Cache-Control", "no-cache, no-store, must-revalidate")
                .header("Pragma", "no-cache")
                .header("Expires", "0")
                .lastModified(epgFile.lastModified())
                .body(resource);
    }

    @RequestMapping(value = "channel/list/{format:^mpeg|hls$}/{profile}", method = RequestMethod.GET)
    public @ResponseBody
    String generatePlaylist(HttpServletRequest request, HttpServletResponse response, @PathVariable("profile") String profile, @PathVariable("format") String format, Authentication authentication) throws Exception {
        FFMpegProfile ffmpegProfile = ffmpegProfileService.getProfile(profile);
        if(ffmpegProfile==null && !"raw".equals(profile)){
            response.setStatus(404);
            return "Profile not found";
        }

        response.setHeader("Content-Disposition", "attachment; filename=playlist.m3u");
        response.setHeader("Access-Control-Allow-Origin", "*");
        response.setStatus(HttpStatus.OK.value());
        StringBuffer buffer = new StringBuffer();
        String requestBaseURL = ProxyLiveUtils.getBaseURL(request);
        List<Channel> channelsOrdered = new ArrayList(channelService.getChannelList());
        channelsOrdered.sort(new Comparator<Channel>() {
            @Override
            public int compare(Channel o1, Channel o2) {
                return o1.getNumber().compareTo(o2.getNumber());
            }
        });
        String EPGURL = String.format("%s/epg", requestBaseURL);

        buffer.append(String.format("#EXTM3U cache=2000 url-tvg=\"%s\" x-tvg-url=\"%s\" tvg-shift=0\r\n\r\n",EPGURL,EPGURL));

        for (Channel channel : channelsOrdered) {
            Set<String> categories= new HashSet<>();
            for (String channelCategory: channel.getCategories()) {
                categories.add(String.format(" group-title=\"%s\" ",channelCategory));
            }
            String categoriesString = String.join(" ",categories);
            String epgIDString = "";
            if(channel.getEpgID()!=null){
                epgIDString = String.format("tvg-id=\"%s\"",channel.getEpgID());
            }
            String logoURL="";
            if(channel.getLogoURL()!=null || channel.getLogoFile()!=null){
                logoURL = String.format("tvg-logo=\"%s/channel/%s/icon\"",requestBaseURL,channel.getId());
            }

            String channelURL=String.format("%s/view/%s/%s", requestBaseURL, profile, channel.getId());
            if(format.equals("hls")){
                channelURL=channelURL+"/playlist.m3u8";
            }
            channelURL=String.format("%s?token=%s",channelURL,authentication.getCredentials());

            buffer.append(String.format("#EXTINF:-1 tvg-chno=\"%d\" %s %s %s tvg-name=\"%s\" type=\"%s\",%s\r\n%s\r\n",
                    channel.getNumber(),logoURL,categoriesString,epgIDString,channel.getName(),format,channel.getName(),channelURL));
        }
        return buffer.toString();
    }

    @RequestMapping(value = "channel/{channelID}/icon", method = {RequestMethod.GET,RequestMethod.HEAD})
    public ResponseEntity<Resource> downloadIcon(HttpServletRequest request,@PathVariable("channelID") String channelID) throws Exception {
        Channel channel = channelService.getChannelByID(channelID);
        if(channel!=null && channel.getLogoFile()!=null){
            Resource resource = new UrlResource(channel.getLogoFile().toPath().toUri());
            String contentType = null;
            contentType = request.getServletContext().getMimeType(channel.getLogoFile().getName());

            // Fallback to the default content type if type could not be determined
            if(contentType == null) {
                contentType = "application/octet-stream";
            }
            return ResponseEntity.ok()
                    .contentType(MediaType.parseMediaType(contentType))
                    .contentLength(channel.getLogoFile().length())
                    .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + channel.getLogoFile().getName() + "\"")
                    .lastModified(channel.getLogoFile().lastModified())
                    .body(resource);
        }else{
            return ResponseEntity.notFound().build();
        }
    }



    @RequestMapping(value = "/view/{profile}/{channelID}/{file:^(?i)playlist.m3u8|dummy\\d*.ts|playlist\\d*.ts$}", method = RequestMethod.GET)
    public void dispatchHLS(@PathVariable("profile") String profile, @PathVariable("channelID") String channelID,
            @PathVariable String file, HttpServletRequest request, HttpServletResponse response,Authentication authentication) throws Exception {
        long now = new Date().getTime();

        if(!config.getFfmpeg().getHls().getEnabled()){
            response.setStatus(404);
            return;
        }
        if(profile.equals("adaptive")){
            uploadFileStream(response, createAdaptivePlaylist(channelID));
            return;
        }

        String clientIdentifier = ProxyLiveUtils.getRequestIP(request) + ProxyLiveUtils.getBrowserInfo(request);
        Channel channel = channelService.getChannelByID(channelID);
        FFMpegProfile ffmpegProfile = ffmpegProfileService.getProfile(profile);
        if(channel==null || (ffmpegProfile==null && !"raw".equals(profile))){
            response.setStatus(404);
            return;
        }


        DirectHLSTranscoderStreamProcessor hlsStreamProcessor = streamProcessorsSession.getHLSStream(ProxyLiveUtils.getRequestIP(request), channel.getId(), profile);
        if (hlsStreamProcessor == null) {
            hlsStreamProcessor = (DirectHLSTranscoderStreamProcessor) context.getBean("StreamProcessor", ProxyLiveConstants.HLS_MODE, clientIdentifier, channel, profile);
            hlsStreamProcessor.start();
        }
        ClientInfo client = streamProcessorsSession.manage(hlsStreamProcessor, request,authentication.getPrincipal().toString());
        //if (hlsStreamProcessor.isConnected()) {
            file = file.toLowerCase();
            logger.debug("Client require:" + file + " after " + (new Date().getTime() - now)/1000);
            InputStream downloadFile = getFileToUpload(file, hlsStreamProcessor, request,response);
            logger.debug("Client get Stream:" + file + " after " + (new Date().getTime() - now)/1000);
            //probably the stream is not ready yet.
            //file.endsWith("m3u8") &&
            /*if(downloadFile==null){
                //send fake playlist to stream something meanwhile is waitting
                uploadFakeHLSFile(file,request,response);
                System.out.println("Client dowloaded:" + file + " after " + (new Date().getTime() - now)/1000);
                return;
            }*/
            if (downloadFile != null) {
                uploadFileStream(response, downloadFile);
            } else {
                response.setStatus(404);
            }
        logger.debug("Client exit("+response.getStatus()+") request:" + file + " after " + (new Date().getTime() - now)/1000);
        //} else {
            //    response.setStatus(404);
            //}
    }

    private InputStream createAdaptivePlaylist(String channelName) {
        StringBuilder sb= new StringBuilder("#EXTM3U\n" +
                "#EXT-X-VERSION:3\n");
        for (FFMpegProfile profile : config.getFfmpeg().getProfiles()) {
            if(profile.getAdaptiveBandWith()!=null && profile.getAdaptiveResolution()!=null){
                sb.append("#EXT-X-STREAM-INF:BANDWIDTH="+profile.getAdaptiveBandWith()+",RESOLUTION="+profile.getAdaptiveResolution()+"\n"+
                        "/view/"+profile.getAlias()+"/"+channelName+"/playlist.m3u8\n");
            }
        }
        return new ByteArrayInputStream(sb.toString().getBytes());
    }

    private void uploadFileStream(HttpServletResponse response, InputStream downloadFile) throws IOException {
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
    }

    @RequestMapping(value="/hls/dummy/{fileName:^(?i)playlist.m3u8|dummy\\d*.ts$}",method=RequestMethod.GET)
    private void uploadFakeHLSFile(@PathVariable String fileName, HttpServletRequest request, HttpServletResponse response) throws IOException, InterruptedException {
        File file = new File("C:\\lol\\"+fileName);
        long now = new Date().getTime();
        while(!file.exists() &&    (new Date().getTime() - now) < config.getFfmpeg().getHls().getTimeout()*1000) {
            Thread.sleep(100);
        }
        FileInputStream fis=new FileInputStream(file);

        response.setHeader("Connection", "close");//keep-alive
        response.setHeader("Access-Control-Allow-Origin","*");
        response.setHeader("Access-Control-Expose-Headers","Content-Length");
        response.setHeader(HttpHeaders.CACHE_CONTROL, "no-cache");
        response.setHeader(HttpHeaders.CONTENT_LENGTH, ""+file.length());
        response.setHeader(HttpHeaders.CONTENT_TYPE, getMediaType(fileName).toString());
        uploadFileStream(response, fis);

    }

    private MediaType getMediaType(String downloadFile) {
        String name = downloadFile;
        try {
            String extension = name.substring(name.lastIndexOf(".") + 1);
            switch (extension.toLowerCase()) {
                case "ts":
                    return new MediaType("video", "mp2t");
                case "m3u8":
                    return new MediaType("application", "vnd.apple.mpegurl");
                    //x-mpegURL:
                case "mpd":
                    return new MediaType("application","dash+xml");
            }
        } catch (Exception e) {
           logger.error("Error",e);
        }
        return null;
    }

    private InputStream getFileToUpload(String file, IHLSStreamProcessor hlsStreamProcessor, HttpServletRequest request,HttpServletResponse respHeaders) throws IOException, URISyntaxException, InterruptedException {
        InputStream downloadFile = null;
        long now = new Date().getTime();
        Long fileSize = null;
        if (file.endsWith("m3u8")) {

            do {
                downloadFile = hlsStreamProcessor.getPlayList();
                if (downloadFile == null) {
                    //return null;
                    Thread.sleep(100);

                }
                if ((new Date().getTime() - now) > config.getFfmpeg().getHls().getTimeout()*1000) {
                    return null;
                }
            } while (downloadFile == null);
            if(file.equals("playlist.m3u8")){
                StringBuilder playlistEdit = new StringBuilder();
                BufferedReader reader = new BufferedReader(new InputStreamReader(downloadFile));
                String currentURL =ProxyLiveUtils.getURL(request,false);
                while(reader.ready()) {
                    String line = reader.readLine();
                    if(line.endsWith(".ts")){
                        line = currentURL.replace("playlist.m3u8",line);
                    }
                    playlistEdit.append(line).append("\n");
                }
                downloadFile.close();
                downloadFile = IOUtils.toInputStream(playlistEdit.toString());
                respHeaders.setHeader(HttpHeaders.CACHE_CONTROL, "no-cache");
            }else{
                respHeaders.setHeader(HttpHeaders.CACHE_CONTROL, "public,max-age=360");
            }
            fileSize = Long.valueOf(downloadFile.available());
        } else {
            downloadFile = hlsStreamProcessor.getSegment(file);

            if (downloadFile == null) {
                return null;
            }
            fileSize = hlsStreamProcessor.getSegmentSize(file);
        }
        respHeaders.setHeader("Connection", "close");//keep-alive
        respHeaders.setHeader("Access-Control-Allow-Origin","*");
        respHeaders.setHeader("Access-Control-Expose-Headers","Content-Length");

        respHeaders.setHeader(HttpHeaders.CONTENT_LENGTH, fileSize.toString());
        respHeaders.setHeader(HttpHeaders.CONTENT_TYPE, getMediaType(file).toString());
        return downloadFile;
    }


}
