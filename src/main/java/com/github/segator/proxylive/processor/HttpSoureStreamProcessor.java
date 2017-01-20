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

import com.github.segator.proxylive.tasks.HttpDownloaderTask;
import com.github.segator.proxylive.stream.ClientBroadcastedInputStream;
import com.github.segator.proxylive.tasks.IStreamTask;
import com.github.segator.proxylive.tasks.ProcessorTasks;
import java.io.IOException;
import java.util.Objects;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationContext;

/**
 *
 * @author Isaac Aymerich <isaac.aymerich@gmail.com>
 */
public class HttpSoureStreamProcessor implements IStreamMultiplexerProcessor, ISourceStream {

    @Autowired
    private ProcessorTasks tasks;
    @Autowired
    private ApplicationContext context;


    private final String channel;
    private final String identifier;
    private ClientBroadcastedInputStream pip;
    private HttpDownloaderTask streamingDownloaderRunningTask;

    public HttpSoureStreamProcessor(String channel, String identifier) {
        this.channel = channel;
        this.identifier = identifier;
    }

    @Override
    public void start() throws Exception {
        HttpDownloaderTask streamingDownloaderTask = (HttpDownloaderTask) context.getBean("HttpDownloaderTask", channel);
        synchronized (tasks) {
            streamingDownloaderRunningTask = (HttpDownloaderTask) tasks.getTask(streamingDownloaderTask);
            if (streamingDownloaderRunningTask == null) {
                tasks.runTask(streamingDownloaderTask);
                streamingDownloaderRunningTask = streamingDownloaderTask;
            }
            pip = streamingDownloaderRunningTask.getMultiplexer().getClientInputStream("http cli");
        }
    }

    public String getChannel() {
        return channel;
    }

    @Override
    public void stop(boolean force) throws IOException {
        synchronized (tasks) {
            streamingDownloaderRunningTask.getMultiplexer().flush();

            streamingDownloaderRunningTask.getMultiplexer().removeClientInputStream(pip);

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
        final HttpSoureStreamProcessor other = (HttpSoureStreamProcessor) obj;
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
