/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package com.github.segator.proxylive.processor;

import com.github.segator.proxylive.stream.ClientBroadcastedInputStream;
import com.github.segator.proxylive.tasks.DirectTranscodeTask;
import com.github.segator.proxylive.tasks.IStreamTask;
import com.github.segator.proxylive.tasks.ProcessorTasks;
import java.io.IOException;
import java.util.Objects;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationContext;

/**
 *
 * @author isaac
 */
public class DirectTranscoderStreamProcessor implements IStreamMultiplexerProcessor, ISourceStream {

    @Autowired
    private ProcessorTasks tasks;
    @Autowired
    private ApplicationContext context;


    private final String channel;
    private final String profile;
    private final String identifier;
    private ClientBroadcastedInputStream pip;
    private DirectTranscodeTask streamingDownloaderRunningTask;

    public DirectTranscoderStreamProcessor(String channel, String profile,String identifier) {
        this.channel = channel;
        this.profile = profile;
        this.identifier = identifier;
    }

    @Override
    public void start() throws Exception {
        DirectTranscodeTask streamingDownloaderTask = (DirectTranscodeTask) context.getBean("DirectTranscodeTask", channel,profile);
        synchronized (tasks) {
            streamingDownloaderRunningTask = (DirectTranscodeTask) tasks.getTask(streamingDownloaderTask);
            if (streamingDownloaderRunningTask == null) {
                tasks.runTask(streamingDownloaderTask);
                streamingDownloaderRunningTask = streamingDownloaderTask;
            }
            //pip = streamingDownloaderRunningTask.getMultiplexer().getClientInputStream("transcoded direct cli");
            pip = streamingDownloaderRunningTask.getMultiplexer().getConsumer("transcoded direct cli");
        }
    }

    public String getChannel() {
        return channel;
    }

    @Override
    public void stop(boolean force) throws IOException {
        synchronized (tasks) {
            streamingDownloaderRunningTask.getMultiplexer().flush();

            streamingDownloaderRunningTask.getMultiplexer().removeClientConsumer(pip);

            if (force || streamingDownloaderRunningTask.getMultiplexer().getClientsList().isEmpty()) {
                tasks.killTask(streamingDownloaderRunningTask);
            }
            try {
                pip.close();
            } catch (Exception ex) {
            }
        }
    }

    @Override
    public ClientBroadcastedInputStream getMultiplexedInputStream() {
        synchronized (tasks) {
            return pip;
        }
    }

    @Override
    public boolean isConnected() {
        synchronized (tasks) {
            boolean connected = isTaskRunning();
            if (!connected) {
                try {
                    Thread.sleep(5000);
                } catch (InterruptedException ex) {
                    return false;
                }
                return isTaskRunning();
            }
            return true;
        }
    }

    private boolean isTaskRunning() {
        return streamingDownloaderRunningTask != null && !streamingDownloaderRunningTask.isTerminated() && !streamingDownloaderRunningTask.isCrashed();
    }

    @Override
    public IStreamTask getTask() {
        return streamingDownloaderRunningTask;
    }

    @Override
    public int hashCode() {
        int hash = 3;
        hash = 89 * hash + Objects.hashCode(this.streamingDownloaderRunningTask);
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
        final DirectTranscoderStreamProcessor other = (DirectTranscoderStreamProcessor) obj;
        if (!Objects.equals(this.streamingDownloaderRunningTask, other.streamingDownloaderRunningTask)) {
            return false;
        }
        return true;
    }

    @Override
    public String getIdentifier() {
        return identifier;
    }
    
}
