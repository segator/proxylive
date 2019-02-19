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
import com.github.segator.proxylive.stream.BroadcastCircularBufferedOutputStream;
import com.github.segator.proxylive.stream.WebInputStream;
import com.github.segator.proxylive.stream.WithoutBlockingInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.URL;
import java.util.Date;
import java.util.Objects;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.regex.Pattern;
import javax.annotation.PostConstruct;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.env.Environment;

/**
 *
 * @author isaac
 */
public class DirectTranscodeTask implements IMultiplexerStreamer {

    @Autowired
    private FFmpegProfilerService ffmpegProfilerService;
    @Autowired
    private ProxyLiveConfiguration config;

    @Value("${local.server.port}")
    int serverPort;

    private String url;
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
        url = "http://localhost:"+serverPort+"/view/raw/" + channel.getId()+"?user=internal&token="+config.getInternalToken();
        multiplexerOutputStream = new BroadcastCircularBufferedOutputStream(config.getBuffers().getBroadcastBufferSize());

    }

    @Override
    public void initializate() {
    }

    @Override
    public void terminate() {
        System.out.println("[" + getIdentifier() + "] Required Terminate Transcode");
        terminated = true;
        try {
            if (errorReaderThread != null) {
                errorReaderThread.join();
            }
        } catch (InterruptedException ex) {
        }
    }

    @Override
    public void run() {
        try {
            System.out.println("[" + getIdentifier() + "] Start Transcode");
            runDate = new Date();
            transcodeParameters = ffmpegProfilerService.getTranscodeParameters(profile);
            String ffmpegExecutable = ffmpegProfilerService.getFFMpegExecutable();
            String ffmpegMpegTSParameters = config.getFfmpeg().getMpegTS().getParameters();

            if (isTerminated()) {
                return;
            }
            String ffmpegCommand = ffmpegExecutable + " -i " + url + " " + transcodeParameters + " " + ffmpegMpegTSParameters+" -";
            System.out.println("Transcoding Command" + ffmpegCommand);
            process = Runtime.getRuntime().exec(ffmpegCommand);

            errorReaderThread = new Thread("Transcoding Error Reader Thread:" + getIdentifier()) {
                public void run() {
                    byte[] bufferError = new byte[4096];
                    InputStream is = new WithoutBlockingInputStream(process.getErrorStream());
                    try {
                        while (isRunning() && !isTerminated()) {
                            if (internalCrash && !isTerminated()) {
                                throw new IOException("Handled Crash event");
                            }
                            int readed = is.read(bufferError);
                            if (readed > 0) {
                                System.out.print(new String(bufferError, 0, readed));
                            } else {
                                Thread.sleep(5);
                            }
                        }
                    } catch (Exception e) {
                        if (!isTerminated()) {
                            System.out.println("Error:" + e.getMessage());
                            Logger.getLogger(TranscodeTask.class.getName()).log(Level.SEVERE, null, e);
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
                System.out.println("Error:" + ex.getMessage());
                Logger.getLogger(DirectTranscodeTask.class.getName()).log(Level.SEVERE, null, ex);
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
            System.out.println("[" + getIdentifier() + "] Terminated Transcode");
        }
    }

    @Override
    public int hashCode() {
        int hash = 5;
        hash = 31 * hash + Objects.hashCode(this.url);
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
        if (!Objects.equals(this.url, other.url)) {
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
}
