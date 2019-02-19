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

import com.github.segator.proxylive.processor.IStreamMultiplexerProcessor;
import com.github.segator.proxylive.processor.IStreamProcessor;
import com.github.segator.proxylive.profiler.FFmpegProfilerService;
import com.github.segator.proxylive.stream.BroadcastCircularBufferedOutputStream;
import com.github.segator.proxylive.stream.WithoutBlockingInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.Date;
import java.util.Objects;
import java.util.logging.Level;
import java.util.logging.Logger;
import javax.annotation.PostConstruct;
import com.github.segator.proxylive.config.ProxyLiveConfiguration;
import org.springframework.beans.factory.annotation.Autowired;

/**
 *
 * @author Isaac Aymerich <isaac.aymerich@gmail.com>
 */
public class TranscodeTask implements IMultiplexerStreamer {

    @Autowired
    private FFmpegProfilerService ffmpegProfilerService;
    @Autowired
    private ProxyLiveConfiguration config;
    private final IStreamMultiplexerProcessor inputStreamProcessor;
    private final String profile;
    private BroadcastCircularBufferedOutputStream multiplexerOutputStream;
    private String transcodeParameters;
    private Process process;
    private Date runDate;
    private boolean terminated = false;
    private boolean crashed = false;
    private boolean internalCrash = false;
    private int crashedTimes = 0;
    private Thread errorReaderThread;
    private Thread inputThread;
    private InputStream sourceProcessorInputStream;

    public TranscodeTask(IStreamMultiplexerProcessor inputStreamProcessor, String profile) {
        this.inputStreamProcessor = inputStreamProcessor;
        this.profile = profile;
    }

    @PostConstruct
    public void initializeBean() {
        multiplexerOutputStream = new BroadcastCircularBufferedOutputStream(config.getBuffers().getBroadcastBufferSize());
        sourceProcessorInputStream = new WithoutBlockingInputStream(inputStreamProcessor.getMultiplexedInputStream());

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
        try {
            if (inputThread != null) {
                inputThread.join();
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
            if (isTerminated()) {
                return;
            }
            String ffmpegCommand = ffmpegExecutable + " -i pipe:0 " + transcodeParameters + " -f mpegts -";
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
            inputThread = new Thread("Transcoding Source Input Thread:" + getIdentifier()) {
                public void run() {
                    byte[] buffer = new byte[32 * 1024];
                    OutputStream outputStreamSource = process.getOutputStream();
                    try {
                        int read = 0;
                        while (isRunning() && !isTerminated()) {
                            if (internalCrash && !isTerminated()) {
                                throw new IOException("Handled Crash event");
                            }
                            read = sourceProcessorInputStream.read(buffer);
                            if (read > 0) {
                                outputStreamSource.write(buffer, 0, read);
                            } else {
                                Thread.sleep(1);
                            }
                        }
                        System.out.println("Saliendo Input Thread");
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
            inputThread.start();
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
                Logger.getLogger(TranscodeTask.class.getName()).log(Level.SEVERE, null, ex);
                internalCrash = true;
            }
        } finally {
            stopProcess();
            try {
                errorReaderThread.join();
            } catch (Exception ex) {
            }
            try {
                inputThread.join();
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
        hash = 31 * hash + Objects.hashCode(this.inputStreamProcessor);
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
        final TranscodeTask other = (TranscodeTask) obj;
        if (!Objects.equals(this.inputStreamProcessor, other.inputStreamProcessor)) {
            return false;
        }
        if (!Objects.equals(this.profile, other.profile)) {
            return false;
        }
        return true;
    }

    @Override
    public String getIdentifier() {
        return inputStreamProcessor.getTask().getIdentifier() + "_" + profile;
    }

    @Override
    public String toString() {
        return getIdentifier();
    }

    @Override
    public BroadcastCircularBufferedOutputStream getMultiplexer() {
        return multiplexerOutputStream;
    }

    public synchronized boolean isRunning() {
        return process.isAlive();
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
        return inputStreamProcessor;
    }

    @Override
    public Date startTaskDate() {
        return runDate;
    }
}
