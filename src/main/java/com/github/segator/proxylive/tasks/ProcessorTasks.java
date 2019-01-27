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

import com.github.segator.proxylive.entity.ClientInfo;
import com.github.segator.proxylive.processor.IStreamProcessor;

import java.util.*;
import java.util.Map.Entry;
import com.github.segator.proxylive.config.ProxyLiveConfiguration;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

/**
 *
 * @author Isaac Aymerich <isaac.aymerich@gmail.com>
 */
@Service
public class ProcessorTasks {
    

    private final Map<Thread, IStreamTask> httpSourceStreamTasks;
    private final Map<Thread, IStreamTask> transcodeTasks;
    private final Map<Thread, IStreamTask> directTranscodeTasks;
    private final Map<Thread, IStreamTask> HLSTasks;
    private final Map<Thread, IStreamTask> HLSDirectTasks;

    @Autowired
    private StreamProcessorsSession streamProcessorsSession;
    @Autowired
    private ProxyLiveConfiguration config;



    public ProcessorTasks() {
        httpSourceStreamTasks = new HashMap();
        transcodeTasks = new HashMap();
        directTranscodeTasks = new HashMap();
        HLSTasks = new HashMap();
        HLSDirectTasks = new HashMap();
    }

    public synchronized void runTask(IStreamTask task) throws Exception {
        task.initializate();
        Thread thread = new Thread(task, task.getIdentifier());
        getOperationMap(task).put(thread, task);
        thread.start();
    }

    public synchronized IStreamTask getTask(IStreamTask task) {
        for (IStreamTask stTask : getOperationMap(task).values()) {
            if (stTask.equals(task)) {
                return stTask;
            }
        }
        return null;
    }

    public synchronized void killTask(IStreamTask task) {
        Thread thread = getThread(task);
        if (thread != null) {
            task.terminate();
            try {
                thread.join();
            } catch (InterruptedException ex) {
            }
            getOperationMap(task).remove(thread);
        }
    }

    private synchronized Thread getThread(IStreamTask task) {
        for (Map.Entry<Thread, IStreamTask> entry : getOperationMap(task).entrySet()) {
            if (entry.getValue().equals(task)) {
                return entry.getKey();
            }
        }
        return null;
    }

    public synchronized Map<Thread, IStreamTask> getOperationMap(Class clazz) {
        if (clazz.equals(HttpDownloaderTask.class)) {
            return httpSourceStreamTasks;
        } else if (clazz.equals(TranscodeTask.class)) {
            return transcodeTasks;
        } else if (clazz.equals(DirectTranscodeTask.class)) {
            return directTranscodeTasks;
        } else if (clazz.equals(HLSTask.class)) {
            return HLSTasks;
        } else if (clazz.equals(HLSDirectTask.class)) {
            return HLSDirectTasks;
        }
        return null;
    }

    public synchronized Map<Thread, IStreamTask> getOperationMap() {
        Map<Thread, IStreamTask> allMaps = new HashMap();
        allMaps.putAll(httpSourceStreamTasks);
        allMaps.putAll(transcodeTasks);
        allMaps.putAll(directTranscodeTasks);
        allMaps.putAll(HLSTasks);
        allMaps.putAll(HLSDirectTasks);
        return allMaps;
    }

    private synchronized Map<Thread, IStreamTask> getOperationMap(IStreamTask task) {
        return getOperationMap(task.getClass());
    }

    /*@Scheduled(fixedDelay = 5*1000)//Every 30 seconds
    public void killNotLinkedTasks(){
        //Kill tasks that doens't have any consumer asigned
        Map nonHLSTasks = new HashMap(directTranscodeTasks);
        Collection onlyTasks = nonHLSTasks.values();
        nonHLSTasks.putAll(httpSourceStreamTasks);
        for (ClientInfo client : new ArrayList<>(streamProcessorsSession.getClientInfoList())) {
            for (IStreamProcessor streamProcessor : new ArrayList<>(client.getStreams())) {
                IStreamTask streamBindedTask = streamProcessor.getTask();
                if (!onlyTasks.contains(streamBindedTask)) {
                    try {
                        tasks.killTask(streamBindedTask);
                    } catch (Exception ex) {
                    }
                }
            }
        }
    }*/


    @Scheduled(fixedDelay = 5 * 1000)//Every 5 seconds
    public void killHLSStreams() {
        long now = new Date().getTime();
        Map<Thread, IStreamTask> HLSTasksFore = new HashMap(HLSDirectTasks);


        for (Entry<Thread, IStreamTask> entry : HLSTasksFore.entrySet()) {
            HLSDirectTask hlsTask = (HLSDirectTask) entry.getValue();
            if (hlsTask.getLastAccess() != null) {
                long difference = (now - hlsTask.getLastAccess()) / 1000;
                if (difference > config.getFfmpeg().getHls().getTimeout()) {
                    //Kill all stream processors using this task
                    for (ClientInfo client : new ArrayList<>(streamProcessorsSession.getClientInfoList())) {
                        for (IStreamProcessor streamProcessor : new ArrayList<>(client.getStreams())) {
                            IStreamTask streamBindedTask = streamProcessor.getTask();
                            if (streamBindedTask == hlsTask) {
                                try {
                                    streamProcessor.stop(false);
                                    streamProcessorsSession.removeClientInfo(client, streamProcessor);
                                } catch (Exception ex) {
                                }
                            }
                        }
                    }
                }
            }

        }
    }

    public void stopTask(IStreamTask iStreamTask) {
        try {
            if (iStreamTask instanceof IMultiplexerStreamer) {
                ((IMultiplexerStreamer) iStreamTask).getMultiplexer().removeAllConsumers();
            }

            killTask(iStreamTask);
            if (iStreamTask.getSourceProcessor() != null) {
                iStreamTask.getSourceProcessor().stop(false);
            }
        } catch (Exception ex) {
            ex.printStackTrace();
        }
    }
}
