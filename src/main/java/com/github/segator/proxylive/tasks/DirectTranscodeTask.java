/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package com.github.segator.proxylive.tasks;

import com.github.segator.proxylive.config.ProxyLiveConfiguration;
import com.github.segator.proxylive.entity.Channel;
import com.github.segator.proxylive.processor.IStreamMultiplexerProcessor;
import com.github.segator.proxylive.processor.IStreamProcessor;
import com.github.segator.proxylive.profiler.FFmpegProfilerService;
import com.github.segator.proxylive.service.TokenService;
import com.github.segator.proxylive.stream.BroadcastCircularBufferedOutputStream;
import com.github.segator.proxylive.stream.WebInputStream;
import com.github.segator.proxylive.stream.WithoutBlockingInputStream;

import java.io.*;
import java.net.URL;
import java.util.Date;
import java.util.Objects;

import java.util.regex.Pattern;
import javax.annotation.PostConstruct;


import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.env.Environment;

/**
 *
 * @author isaac
 */
public class DirectTranscodeTask implements IMultiplexerStreamer {
    Logger logger = LoggerFactory.getLogger(DirectTranscodeTask.class);

    @Autowired
    private FFmpegProfilerService ffmpegProfilerService;
    @Autowired
    private TokenService tokenService;
    @Autowired
    private ProxyLiveConfiguration config;

    @Value("${local.server.port}")
    int serverPort;

    private final String profile;
    private final Channel channel;
    private BroadcastCircularBufferedOutputStream multiplexerOutputStream;
    private String transcodeParameters;
    private Process process;
    private Date runDate;
    private boolean terminated = false;
    private boolean crashed = false;
    private boolean internalCrash = false;
    private int crashedTimes = 0;
    private Thread errorReaderThread;

    public DirectTranscodeTask(Channel channel, String profile) {
        this.profile = profile;
        this.channel = channel;
    }

    @PostConstruct
    public void initializeBean() {
        multiplexerOutputStream = new BroadcastCircularBufferedOutputStream(config.getBuffers().getBroadcastBufferSize());

    }

    public String getAuthenticatedURL(){
        String token = tokenService.createServiceAccountRequestToken(String.format("DirectTranscode-%s",channel.getId()));
        return String.format("%s?&token=%s",getSource(),token);
    }


    @Override
    public void terminate() {
        logger.debug("[" + getIdentifier() + "] Required Terminate Transcode");
        terminated = true;
        try {
            if (errorReaderThread != null) {
                errorReaderThread.join();
            }
        } catch (InterruptedException ex) {
        }
    }

    @Override
    public String getSource() {
        return String.format("http://localhost:%s/view/raw/%s",serverPort,channel.getId());
    }

    @Override
    public void run() {
        try {
            logger.debug("[" + getIdentifier() + "] Start Transcode");
            runDate = new Date();
            transcodeParameters = ffmpegProfilerService.getTranscodeParameters(profile);
            String ffmpegExecutable = ffmpegProfilerService.getFFMpegExecutable();
            String ffmpegMpegTSParameters = config.getFfmpeg().getMpegTS().getParameters();

            if (isTerminated()) {
                return;
            }
            transcodeParameters = transcodeParameters.replace("{input}",getAuthenticatedURL()).replace("{channelParameters}",channel.getFfmpegParameters()!=null?channel.getFfmpegParameters():"");
            String ffmpegCommand = ffmpegExecutable + " " + transcodeParameters + " " + ffmpegMpegTSParameters+" -";
            logger.debug("[" + getIdentifier() + "] Transcoding Command: " + ffmpegCommand);
            process = Runtime.getRuntime().exec(ffmpegCommand);

            errorReaderThread = new Thread("Transcoding Error Reader Thread:" + getIdentifier()) {
                public void run() {
                    byte[] bufferError = new byte[4096];
                    InputStream is = new WithoutBlockingInputStream(process.getErrorStream());
                    try {
                        BufferedReader br = new BufferedReader(new InputStreamReader(is));
                        while (isRunning() && !isTerminated()) {
                            if (internalCrash && !isTerminated()) {
                                throw new IOException("Handled Crash event");
                            }
                            if(br.ready()) {
                                try {
                                    logger.debug("[" + getIdentifier() + "] " + br.readLine());
                                }catch(Exception e){
                                    //if the buffer it's empty after readiness it crash with underlying input stream returned zero bytes
                                }
                            }else{
                                Thread.sleep(200);
                            }
                        }
                    } catch (Exception e) {
                        if (!isTerminated()) {
                            logger.error("[" + getIdentifier() + "] Error on transcoding", e);
                            internalCrash = true;
                        }
                    }
                }
            };

            errorReaderThread.start();
            byte[] buffer = new byte[32 * 1024];
            InputStream is = new WithoutBlockingInputStream(process.getInputStream());

            int read = 0;

            while (isRunning() && !isTerminated()) {
                if (internalCrash && !isTerminated()) {
                    throw new IOException("Handled Crash event");
                }
                read = is.read(buffer);
                if (read > 0) {
                    multiplexerOutputStream.write(buffer, 0, read);
                } else {
                    Thread.sleep(5);
                }
            }
        } catch (Exception ex) {
            if (!isTerminated()) {
                logger.error("Error on transcoding " + getIdentifier(),ex);

                internalCrash = true;
            }
        } finally {
            stopProcess();
            try {
                errorReaderThread.join();
            } catch (Exception ex) {
            }
        }
        if (!isTerminated() && internalCrash) {
            crashedTimes++;
            if (crashedTimes > 10) {
                crashed = true;
            } else {
                internalCrash = false;
                run();
            }
        } else {
            logger.debug("[" + getIdentifier() + "] Terminated Transcode");
        }
    }

    @Override
    public int hashCode() {
        int hash = 5;
        hash = 31 * hash + Objects.hashCode(this.channel.getId());
        hash = 31 * hash + Objects.hashCode(this.profile);
        return hash;
    }

    @Override
    public boolean equals(Object obj) {
        if (obj == null) {
            return false;
        }
        if (getClass() != obj.getClass()) {
            return false;
        }
        final DirectTranscodeTask other = (DirectTranscodeTask) obj;
        if (!Objects.equals(this.channel.getId(), other.channel.getId())) {
            return false;
        }
        if (!Objects.equals(this.profile, other.profile)) {
            return false;
        }
        return true;
    }

    @Override
    public String getIdentifier() {
        return channel.getId() + "_" + profile;
    }

    @Override
    public void initializate() throws Exception {
    }

    @Override
    public String toString() {
        return getIdentifier();
    }

    @Override
    public BroadcastCircularBufferedOutputStream getMultiplexer() {
        return multiplexerOutputStream;
    }

    public synchronized boolean isRunning() throws IOException {
        if(process.isAlive()){
            return true;
        }else{
            throw new IOException("Process is not running");
        }
    }

    public boolean isTerminated() {
        return terminated;
    }

    public String getTranscodeParameters() {
        return transcodeParameters;
    }

    @Override
    public boolean isCrashed() {
        return crashed;
    }

    private void stopProcess() {
        try {
            if (process.isAlive()) {
                try {
                    process.getInputStream().close();
                } catch (IOException ex) {
                }
                try {
                    process.getOutputStream().close();
                } catch (IOException ex) {
                }
                try {
                    process.getErrorStream().close();
                } catch (IOException ex) {
                }
                process.destroy();
            }
        } catch (Exception ex) {
        }
    }

    @Override
    public IStreamProcessor getSourceProcessor() {
        return null;
    }

    @Override
    public Date startTaskDate() {
        return runDate;
    }

    public String getProfile() {
        return profile;
    }
}
