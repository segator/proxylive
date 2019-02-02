package com.github.segator.proxylive.processor;

import com.github.segator.proxylive.entity.Channel;
import com.github.segator.proxylive.stream.ClientBroadcastedInputStream;
import com.github.segator.proxylive.tasks.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationContext;

import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.net.URISyntaxException;
import java.util.Objects;

public class DirectHLSTranscoderStreamProcessor implements IStreamProcessor, IHLSStreamProcessor {

    @Autowired
    private ProcessorTasks tasks;
    @Autowired
    private ApplicationContext context;


    private final Channel channel;
    private final String profile;
    private ClientBroadcastedInputStream pip;
    private HLSDirectTask hlsDirectTask;

    public DirectHLSTranscoderStreamProcessor(Channel channel, String profile) {
        this.channel = channel;
        this.profile = profile;
    }

    @Override
    public void start() throws Exception {
        HLSDirectTask streamingDownloaderTask = (HLSDirectTask) context.getBean("HLSDirectTask", channel,profile);
        synchronized (tasks) {
            hlsDirectTask = (HLSDirectTask) tasks.getTask(streamingDownloaderTask);
            if (hlsDirectTask == null) {
                tasks.runTask(streamingDownloaderTask);
                hlsDirectTask = streamingDownloaderTask;
            }           
        }
    }

    public String getChannel() {
        return channel.getId();
    }

    @Override
    public void stop(boolean force) throws IOException {
        tasks.killTask(hlsDirectTask);
    }



    @Override
    public boolean isConnected() {
        synchronized (tasks) {
            return isTaskRunning();
        }
    }

    private boolean isTaskRunning() {
        return hlsDirectTask != null  && !hlsDirectTask.isCrashed();
    }

    @Override
    public IStreamTask getTask() {
        return hlsDirectTask;
    }

    @Override
    public int hashCode() {
        int hash = 3;
        hash = 89 * hash + Objects.hashCode(this.hlsDirectTask);
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
        final DirectHLSTranscoderStreamProcessor other = (DirectHLSTranscoderStreamProcessor) obj;
        if (!Objects.equals(this.hlsDirectTask, other.hlsDirectTask)) {
            return false;
        }
        return true;
    }

    @Override
    public String getIdentifier() {
        return channel.getName();
    }

    @Override
    public InputStream getPlayList() throws IOException, URISyntaxException {
        return hlsDirectTask.getPlayList();
    }

    @Override
    public InputStream getSegment(String segment) throws FileNotFoundException {
        return hlsDirectTask.getSegment(segment);
    }

    @Override
    public long getSegmentSize(String segment) throws FileNotFoundException {
        return hlsDirectTask.getSegmentSize(segment);
    }

    @Override
    public String toString() {
        return "DirectHLSTranscoderStreamProcessor{" + "channel=" + channel + ", profile=" + profile + '}';
    }
}
