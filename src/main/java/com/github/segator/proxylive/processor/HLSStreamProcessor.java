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

import com.github.segator.proxylive.tasks.HLSTask;
import com.github.segator.proxylive.tasks.IStreamTask;
import com.github.segator.proxylive.tasks.ProcessorTasks;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.net.URISyntaxException;
import java.util.Objects;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationContext;

/**
 *
 * @author Isaac Aymerich <isaac.aymerich@gmail.com>
 */
public class HLSStreamProcessor implements IStreamProcessor, IHLSStreamProcessor {

    @Autowired
    private ProcessorTasks tasks;
    @Autowired
    private ApplicationContext context;
    private final IStreamProcessor inputStreamProcessor;
    private HLSTask hlsTask;
    private final String identifier;

    public HLSStreamProcessor(IStreamProcessor inputStreamProcessor, String identifier) {
        this.inputStreamProcessor = inputStreamProcessor;
        this.identifier = identifier;
    }

    @Override
    public void start() throws Exception {
        synchronized (tasks) {
            inputStreamProcessor.start();
            HLSTask hlsTaskTmp = (HLSTask) context.getBean("HLSTask", inputStreamProcessor);
            hlsTask = (HLSTask) tasks.getTask(hlsTaskTmp);
            if (hlsTask == null || hlsTask.isCrashed()) {
                if (hlsTask != null && hlsTask.isCrashed()) {
                    System.out.println("[" + hlsTask.getIdentifier() + "] found crashed, kill it");
                    tasks.killTask(hlsTask);
                }
                tasks.runTask(hlsTaskTmp);
                hlsTask = hlsTaskTmp;
            }

        }
    }

    @Override
    public void stop(boolean force) throws IOException {
        tasks.killTask(hlsTask);
        inputStreamProcessor.stop(false);
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
        return inputStreamProcessor.isConnected() && !hlsTask.isCrashed();
    }

    @Override
    public IStreamTask getTask() {
        return hlsTask;
    }

    @Override
    public int hashCode() {
        int hash = 7;
        hash = 83 * hash + Objects.hashCode(this.hlsTask);
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
        final HLSStreamProcessor other = (HLSStreamProcessor) obj;
        return Objects.equals(this.hlsTask, other.hlsTask);
    }

    @Override
    public InputStream getPlayList() throws IOException, URISyntaxException {
        return hlsTask.getPlayList();
    }

    @Override
    public InputStream getSegment(String segment) throws FileNotFoundException {
        return hlsTask.getSegment(segment);
    }

    @Override
    public long getSegmentSize(String segment) throws FileNotFoundException {
        return hlsTask.getSegmentSize(segment);
    }

    @Override
    public String getIdentifier() {
        return identifier;
    }

    @Override
    public String toString() {
        return "HLSStreamProcessor{" + "identifier=" + identifier + '}';
    }

}
