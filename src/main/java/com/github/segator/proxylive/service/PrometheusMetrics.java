package com.github.segator.proxylive.service;


import com.github.segator.proxylive.ProxyLiveUtils;
import com.github.segator.proxylive.entity.ClientInfo;
import com.github.segator.proxylive.processor.IStreamProcessor;
import com.github.segator.proxylive.tasks.DirectTranscodeTask;
import com.github.segator.proxylive.tasks.IStreamTask;
import com.github.segator.proxylive.tasks.ProcessorTasks;
import com.github.segator.proxylive.tasks.StreamProcessorsSession;
import io.micrometer.core.instrument.*;
import io.micrometer.core.instrument.binder.MeterBinder;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Date;
import java.util.List;
import java.util.stream.Collectors;

@Component
public class PrometheusMetrics implements MeterBinder {

    private final StreamProcessorsSession streamProcessorsSession;
    private final ProcessorTasks tasksProcessor;
    private  MeterRegistry meterRegistry;
    private List<Meter> activeCustomMetrics;

    public PrometheusMetrics(StreamProcessorsSession streamProcessorsSession, ProcessorTasks tasksProcessor) {
        this.streamProcessorsSession = streamProcessorsSession;
        this.tasksProcessor = tasksProcessor;
    }

    @Override
    public void bindTo(MeterRegistry meterRegistry) {
        this.meterRegistry=meterRegistry;
        this.activeCustomMetrics=new ArrayList();
    }



    @Scheduled(fixedDelay = 5 * 1000) //Every 5s
    private void  refreshMetrics(){
        for (Meter meter: activeCustomMetrics) {
            meterRegistry.remove(meter);
        }
        List<Meter> listOfMetrics=new ArrayList();
        for (ClientInfo clientInfo: new ArrayList<>(streamProcessorsSession.getClientInfoList())) {
            List<Tag> tagsClientInfo = new ArrayList();
            tagsClientInfo.add(Tag.of("user",clientInfo.getClientUser()));
            tagsClientInfo.add(Tag.of("ip",clientInfo.getIp().getHostAddress()));
            tagsClientInfo.add(Tag.of("user-agent",clientInfo.getBrowserInfo()));

            if(clientInfo.getGeoInfo()!=null && clientInfo.getGeoInfo().getCity()!=null && clientInfo.getGeoInfo().getCity().getName()!=null) {
                tagsClientInfo.add(Tag.of("ip-city", clientInfo.getGeoInfo().getCity().getName()));
            }else{
                tagsClientInfo.add(Tag.of("ip-city", "Not found"));
            }
            if(clientInfo.getGeoInfo()!=null && clientInfo.getGeoInfo().getCountry()!=null && clientInfo.getGeoInfo().getCountry().getName()!=null) {
                tagsClientInfo.add(Tag.of("ip-country", clientInfo.getGeoInfo().getCountry().getName()));
            }else{
                tagsClientInfo.add(Tag.of("ip-country", "Not found"));
            }
            if(clientInfo.getGeoInfo()!=null && clientInfo.getGeoInfo().getLocation()!=null) {
                tagsClientInfo.add(Tag.of("ip-latitude", clientInfo.getGeoInfo().getLocation().getLatitude().toString()));
                tagsClientInfo.add(Tag.of("ip-longitude", clientInfo.getGeoInfo().getLocation().getLongitude().toString()));
            }else{
                tagsClientInfo.add(Tag.of("ip-latitude", "0"));
                tagsClientInfo.add(Tag.of("ip-longitude", "0"));
            }


            for (IStreamProcessor stream:   new ArrayList<>(clientInfo.getStreams())) {
                if(stream.getTask()!=null && stream.getTask().getIdentifier()!=null) {
                    List<Tag> tagsClientStream = new ArrayList(tagsClientInfo);
                    tagsClientStream.add(Tag.of("task_identifier", stream.getTask().getIdentifier()));
                    listOfMetrics.add(Gauge.builder("client_stream_info", this, value -> 0).description("task status").tags(tagsClientStream).baseUnit("unit").register(meterRegistry));
                }
            }
        }
        Collection<IStreamTask> tasks = tasksProcessor.getOperationMap().values();
        for (IStreamTask task: tasks) {
            //task_status
            String profile="raw";
            if(task instanceof DirectTranscodeTask){
                DirectTranscodeTask transcodeTask = (DirectTranscodeTask) task;
                profile = transcodeTask.getProfile();
            }
            List<Tag> tagsStatus = new ArrayList();
            tagsStatus.add(Tag.of("source",task.getSource()));
            tagsStatus.add(Tag.of("identifier",task.getIdentifier()));
            tagsStatus.add(Tag.of("profile",profile));
            listOfMetrics.add(Gauge.builder("task_status",this, value -> task.isCrashed()?1:0).description("task status").tags(tagsStatus).baseUnit("status").register(meterRegistry));

            //task running_time
            long now = new Date().getTime();
            Date runDate = task.startTaskDate();
            if (runDate != null) {
                List<Tag> tagsRunningTime = new ArrayList();
                tagsRunningTime.add(Tag.of("identifier",task.getIdentifier()));
                long runTime = now - task.startTaskDate().getTime();
                listOfMetrics.add(Gauge.builder("task_running_time",this, value -> runTime).description("task running time").tags(tagsRunningTime).baseUnit("seconds").register(meterRegistry));
            }
        }

        /*List<Meter> notMatch = activeCustomMetrics.stream()
                .filter(ch -> listOfMetrics.stream().noneMatch(other -> ch.getId().equals(other.getId())))
                .collect(Collectors.toList());
        for (Meter meter: notMatch) {
            meterRegistry.remove(meter);
        }*/
        activeCustomMetrics = listOfMetrics;
        //Tod@
        //treure la llista de IDS actuals i borra els IDS que no estan a la llista listOfMetricsID amb meterRegistry.remove()
        //meterRegistry.getMeters()

        //Gauge.builder("task_status",this,value -> 4).description("desc").tag("tag","asd").baseUnit("unit").register(meterRegistry);
        //Gauge.builder("task_running_time",this,value -> 4).description("desc").tag("tag","asd").baseUnit("unit").register(meterRegistry);
    }


}