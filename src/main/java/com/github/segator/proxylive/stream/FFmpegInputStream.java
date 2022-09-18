package com.github.segator.proxylive.stream;

import com.github.segator.proxylive.config.ProxyLiveConfiguration;
import com.github.segator.proxylive.entity.Channel;

import java.io.IOException;
import java.io.InputStream;


public class FFmpegInputStream extends VideoInputStream {

    private String url;
    private Process process;
    private Thread threadErrorStream;
    private ProxyLiveConfiguration config;
    private InputStream ffmpegInputStream;
    private Channel channel;
    private boolean alive;
    public FFmpegInputStream(String url, Channel channel, ProxyLiveConfiguration config){
        this.url= url;
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
        String ffmpegCommand =  config.getFfmpeg().getPath() + " -i \"" +url + "\" " + (channel.getFfmpegParameters()!=null?channel.getFfmpegParameters():"") + " -codec copy " + config.getFfmpeg().getMpegTS().getParameters() + " -";
        process = Runtime.getRuntime().exec(ffmpegCommand);
        ffmpegInputStream = process.getInputStream();
        threadErrorStream = printErrStream(process.getErrorStream(),process);
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

    private Thread printErrStream(InputStream is, Process proc) {
        Thread t =  new Thread(new Runnable() {
            @Override
            public void run() {
                byte[] buffer = new byte[1024];
                try {
                    while(proc.isAlive()){
                        int r = is.read(buffer);
                        if(r > 0 ) {
                            System.out.print(new String(buffer, 0, r));
                        }
                        Thread.sleep(10);
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
