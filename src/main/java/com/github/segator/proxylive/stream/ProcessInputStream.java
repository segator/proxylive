package com.github.segator.proxylive.stream;

import com.github.segator.proxylive.config.ProxyLiveConfiguration;
import com.github.segator.proxylive.entity.Channel;
import com.github.segator.proxylive.tasks.DirectTranscodeTask;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;

public class ProcessInputStream extends VideoInputStream {
    Logger logger = LoggerFactory.getLogger(ProcessInputStream.class);
    private String url;
    private Process process;
    private Thread threadErrorStream;
    private ProxyLiveConfiguration config;
    private InputStream ffmpegInputStream;
    private Channel channel;
    private boolean alive;
    public ProcessInputStream(String url, Channel channel, ProxyLiveConfiguration config){
        this.url= url.replaceAll("{user-agent}",config.getUserAgent()).replaceAll("{timeout}",config.getSource().getReconnectTimeout()+"").replaceAll("{ffmpegParameters}",(channel.getFfmpegParameters()!=null?channel.getFfmpegParameters():""));
        this.channel= channel;
        this.config=config;
    }


    @Override
    public boolean isConnected() {
        return process.isAlive();
    }

    @Override
    public boolean connect() throws IOException {

        alive=true;
        process = Runtime.getRuntime().exec(url);
        ffmpegInputStream = process.getInputStream();
        threadErrorStream = errorStreamThread(new WithoutBlockingInputStream(process.getErrorStream()),process);
        return true;
    }

    @Override
    public int read() throws IOException {
        return ffmpegInputStream.read();
    }

    @Override
    public int read(byte b[]) throws IOException {
        return ffmpegInputStream.read(b);
    }

    public void close() throws IOException {
        if (isConnected()) {
            try {
                if(process.isAlive()) {
                    process.destroy();
                }
            }catch(Exception ex){}
            try{
                if(threadErrorStream.isAlive()) {
                    threadErrorStream.join();
                }
            }catch(Exception ex){}
        } else {
            throw new IOException("The Stream of " + url + " is not connected");
        }
    }

    private Thread  errorStreamThread(InputStream is,Process proc) {
        Thread t =  new Thread(new Runnable() {
            @Override
            public void run() {
                BufferedReader br = new BufferedReader(new InputStreamReader(is));
                try {
                    while(proc.isAlive()){
                        if(br.ready()) {
                            try {
                                logger.debug("[" + url + "] " + br.readLine());
                            }catch(Exception e){
                                //if the buffer it's empty after readiness it crash with underlying input stream returned zero bytes
                            }
                        }else{
                            Thread.sleep(200);
                        }
                    }
                } catch (Exception e) {
                    e.printStackTrace();
                }
            }
        });
        t.start();
        return t;
    }
}
