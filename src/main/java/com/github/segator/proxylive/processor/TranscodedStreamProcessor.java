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

import com.github.segator.proxylive.stream.ClientBroadcastedInputStream;
import com.github.segator.proxylive.tasks.IStreamTask;
import com.github.segator.proxylive.tasks.ProcessorTasks;
import com.github.segator.proxylive.tasks.TranscodeTask;
import java.io.IOException;
import java.util.Objects;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationContext;

/**
 *
 * @author Isaac Aymerich <isaac.aymerich@gmail.com>
 */
public class TranscodedStreamProcessor implements IStreamMultiplexerProcessor {

    @Autowired
    private ProcessorTasks tasks;
    @Autowired
    private ApplicationContext context;

    private final IStreamProcessor inputStreamProcessor;
    private final String profile;
    private final String identifier;
    private ClientBroadcastedInputStream pip;
    private TranscodeTask transcodeTask;

    public TranscodedStreamProcessor(IStreamProcessor inputStreamProcessor, String profile, String identifier) {
        this.inputStreamProcessor = inputStreamProcessor;
        this.profile = profile;
        this.identifier = identifier;
    }

    @Override
    public void start() throws Exception {
        synchronized (tasks) {
            inputStreamProcessor.start();
            TranscodeTask transcodeTaskTmp = (TranscodeTask) context.getBean("TranscodeTask", inputStreamProcessor, profile);
            transcodeTask = (TranscodeTask) tasks.getTask(transcodeTaskTmp);
            if (transcodeTask == null || transcodeTask.isCrashed()) {
                if (transcodeTask != null && transcodeTask.isCrashed()) {
                    System.out.println("[" + transcodeTask.getIdentifier() + "] found crashed, kill it");
                    tasks.killTask(transcodeTask);
                }
                tasks.runTask(transcodeTaskTmp);
                transcodeTask = transcodeTaskTmp;

            }
            pip = transcodeTask.getMultiplexer().getConsumer("trans cli");

        }
    }

    @Override
    public void stop(boolean force) throws IOException {
        synchronized (tasks) {
            System.out.println("Killing Transcode Processor");
            System.out.println("Clients Running Transcode Task:"+transcodeTask.getMultiplexer().getClientsList().size());
            transcodeTask.getMultiplexer().removeClientConsumer(pip);
            System.out.println("Clients Running Transcode Task:"+transcodeTask.getMultiplexer().getClientsList().size());
            if (force || transcodeTask.getMultiplexer().getClientsList().isEmpty()) {
                System.out.println("require kill transcode:" + profile);
                tasks.killTask(transcodeTask);
                inputStreamProcessor.stop(false);
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
        return inputStreamProcessor.isConnected() && transcodeTask.isTerminated() && !transcodeTask.isCrashed();
    }

    @Override
    public IStreamTask getTask() {
        return transcodeTask;
    }

    @Override
    public int hashCode() {
        int hash = 7;
        hash = 83 * hash + Objects.hashCode(this.transcodeTask);
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
        final TranscodedStreamProcessor other = (TranscodedStreamProcessor) obj;
        if (!Objects.equals(this.transcodeTask, other.transcodeTask)) {
            return false;
        }
        return true;
    }

    @Override
    public String getIdentifier() {
       return identifier;
    }
}
