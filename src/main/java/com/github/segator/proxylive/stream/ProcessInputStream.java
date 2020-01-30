package com.github.segator.proxylive.stream;

import com.github.segator.proxylive.ProxyLiveUtils;
import com.github.segator.proxylive.config.ProxyLiveConfiguration;
import com.github.segator.proxylive.entity.Channel;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.Date;

public class ProcessInputStream extends VideoInputStream {
    Logger logger = LoggerFactory.getLogger(ProcessInputStream.class);
    private String url;
    private Process process;
    private Thread threadErrorStream;
    private InputStream ffmpegInputStream;
    private boolean terminate;

    public ProcessInputStream(String url){
        this.url= url;
        this.terminate=false;
    }


    @Override
    public boolean isConnected() {
        return process.isAlive();
    }

    @Override
    public boolean connect() throws IOException {
        process = new ProcessBuilder().command(ProxyLiveUtils.translateCommandline(url)).start();
        ffmpegInputStream = new WithoutBlockingInputStream(process.getInputStream());
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
            terminate = true;
            ffmpegInputStream.close();
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
                    while(proc.isAlive() && !terminate){
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
                    br.close();
                } catch (Exception e) {
                    e.printStackTrace();
                }
            }
        });
        t.start();
        return t;
    }
}
