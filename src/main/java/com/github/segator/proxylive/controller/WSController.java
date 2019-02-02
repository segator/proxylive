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
package com.github.segator.proxylive.controller;

import com.github.segator.proxylive.ProxyLiveUtils;
import com.github.segator.proxylive.entity.*;
import com.github.segator.proxylive.processor.IStreamProcessor;
import com.github.segator.proxylive.service.TokensService;
import com.github.segator.proxylive.tasks.IStreamTask;
import com.github.segator.proxylive.tasks.ProcessorTasks;
import com.github.segator.proxylive.tasks.StreamProcessorsSession;
import java.io.IOException;
import java.util.*;
import java.util.Map.Entry;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.*;

/**
 *
 * @author Isaac Aymerich <isaac.aymerich@gmail.com>
 */
@Controller
public class WSController {

    @Autowired
    private ProcessorTasks tasksProcessor;
    @Autowired
    private StreamProcessorsSession streamProcessorsSession;
    @Autowired
    private TokensService tokenService;

    @RequestMapping(value = "/ws/getTasks", produces = "application/json")
    public @ResponseBody
    List<JSONTask> getTasks() throws IOException {
        List<JSONTask> retorn = new ArrayList();
        retorn.addAll(createJSONTask(tasksProcessor.getOperationMap()));
        return retorn;
    }

    @RequestMapping(value = "/ws/getClients", produces = "application/json")
    public @ResponseBody
    List<JSONClientInfo> getClients() throws IOException {
        return createJSONClients(streamProcessorsSession.getClientInfoList());
    }

    @RequestMapping(value = "/ws/task/{identifierID}", method = RequestMethod.DELETE, produces = "application/json")
    public @ResponseBody
    void killTask(@PathVariable("identifierID") String identifierID) throws IOException {
        JSONResponse response = new JSONResponse();
        response.setCode(404);
        response.setMessage("Identifier " + identifierID + " not found");
        response.setStatus("completed");

        Collection<IStreamTask> tasks = tasksProcessor.getOperationMap().values();
        for (IStreamTask task : tasks) {
            if (task.getIdentifier().equals(identifierID)) {
                tasksProcessor.killTask(task);
                if (task.getSourceProcessor() != null) {
                    task.getSourceProcessor().stop(true);
                }
                response.setCode(200);
                response.setMessage("Task Identifier " + identifierID + " killed");
                response.setStatus("completed");

            }
        }
    }

    @RequestMapping(value = "/ws/stream/{identifierID}", method = RequestMethod.DELETE, produces = "application/json")
    public @ResponseBody
    void killStreamProcessor(@PathVariable("identifierID") String identifierID) throws IOException {
        JSONResponse response = new JSONResponse();
        response.setCode(404);
        response.setMessage("Identifier " + identifierID + " not found");
        response.setStatus("completed");

        Collection<ClientInfo> clients = streamProcessorsSession.getClientInfoList();
        for (ClientInfo client : new ArrayList<>(clients)) {
            for (IStreamProcessor streamProcessor : new ArrayList<>(client.getStreams())) {
                if (streamProcessor.getIdentifier().equals(identifierID)) {
                    streamProcessor.stop(false);
                    streamProcessorsSession.removeClientInfo(client, streamProcessor);
                    response.setCode(200);
                    response.setMessage("StreamProcessor Identifier " + identifierID + " killed");
                    response.setStatus("completed");
                }
            }
        }
    }

    @RequestMapping(value = "/ws/token", produces = "application/json",method = RequestMethod.GET)
    public @ResponseBody
    List<AuthToken> getTokenList() throws IOException {
        return tokenService.getAllTokens();
    }

    @RequestMapping(value = "/ws/token/{tokenID}", produces = "application/json",method = RequestMethod.DELETE)
    public @ResponseBody void  deleteToken(@PathVariable("tokenID") String tokenID) throws IOException {
        tokenService.deleteTokenByID(tokenID);
    }

    @RequestMapping(value = "/ws/token/", produces = "application/json",method = RequestMethod.PUT)
    public @ResponseBody void  addToken(@RequestBody Map<String,Object> payload) throws Exception {
        String expiration = (String)payload.get("expiration");
        Calendar nowCalendar = Calendar.getInstance();
        int typeOfAdd=Calendar.DATE;
        String unitType = expiration.substring(expiration.length()-1,expiration.length()).toLowerCase();
        switch(unitType){
            case "d":
                typeOfAdd = Calendar.DATE;
                break;
            case "m":
                typeOfAdd = Calendar.MONTH;
                break;
            case "h":
                typeOfAdd = Calendar.HOUR_OF_DAY;
                break;
        }
        nowCalendar.add(typeOfAdd,new Integer(expiration.substring(0,expiration.length()-1)));

        tokenService.addTokenByID(new AuthToken((String)payload.get("user"),(String)payload.get("id"),nowCalendar.getTime()));

    }


    private Collection<? extends JSONTask> createJSONTask(Map<Thread, IStreamTask> operationMap) {
        List<JSONTask> jsonTasks = new ArrayList();
        long now = new Date().getTime();
        for (Entry<Thread, IStreamTask> entry : operationMap.entrySet()) {
            JSONTask jsTask = new JSONTask();
            Date runDate = entry.getValue().startTaskDate();
            if (runDate != null) {
                jsTask.setRunningTime(ProxyLiveUtils.convertMilisToTime(now - runDate.getTime()));
            } else {
                jsTask.setRunningTime("");
            }

            jsTask.setIdentifier(entry.getValue().getIdentifier());
            jsTask.setStatus(!entry.getValue().isCrashed() && entry.getKey().isAlive() ? "running" : "crashed");
            jsonTasks.add(jsTask);
        }
        return jsonTasks;

    }

    private List<JSONClientInfo> createJSONClients(List<ClientInfo> clientInfoList) {
        List<JSONClientInfo> jsonClients = new ArrayList();
        for (ClientInfo client : clientInfoList) {
            JSONClientInfo jsonClient = new JSONClientInfo();
            jsonClient.setUser(client.getClientUser());
            jsonClient.setIp(client.getIp());
            jsonClient.setBrowserInfo(client.getBrowserInfo());
            jsonClient.setStreams(new ArrayList());
            jsonClients.add(jsonClient);

            for (IStreamProcessor streamProcessor : client.getStreams()) {
                JSONClientInfo.StreamProcessor jsonStreamProcessor = jsonClient.new StreamProcessor();
                jsonStreamProcessor.setIdentifier(streamProcessor.getIdentifier());
                if (streamProcessor.getTask() != null) {
                    jsonStreamProcessor.setTaskIdentifier(streamProcessor.getTask().getIdentifier());
                }
                jsonClient.getStreams().add(jsonStreamProcessor);
            }
        }
        return jsonClients;
    }
}
