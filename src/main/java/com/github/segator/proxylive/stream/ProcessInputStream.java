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
import java.util.ArrayList;
import java.util.StringTokenizer;

public class ProcessInputStream extends VideoInputStream {
    Logger logger = LoggerFactory.getLogger(ProcessInputStream.class);
    private String url;
    private Process process;
    private Thread threadErrorStream;
    private ProxyLiveConfiguration config;
    private InputStream ffmpegInputStream;
    private Channel channel;
    private boolean terminate;

    public ProcessInputStream(String url, Channel channel, ProxyLiveConfiguration config){
        this.url= url.replaceAll("\\{user-agent\\}",config.getUserAgent()).replaceAll("\\{timeout\\}",config.getSource().getReconnectTimeout()+"").replaceAll("\\{ffmpegParameters\\}",(channel.getFfmpegParameters()!=null?channel.getFfmpegParameters():""));
        this.channel= channel;
        this.config=config;
        this.terminate=false;
    }


    @Override
    public boolean isConnected() {
        return process.isAlive();
    }

    @Override
    public boolean connect() throws IOException {
        process = new ProcessBuilder().command(translateCommandline(url)).start();
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
            try {
                if(process.isAlive()) {
                    Runtime.getRuntime().exec(String.format("%s -9 %d", "kill", process.pid()));
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
                } catch (Exception e) {
                    e.printStackTrace();
                }
            }
        });
        t.start();
        return t;
    }
    public static String[] translateCommandline(String toProcess) {
        if (toProcess == null || toProcess.length() == 0) {
            //no command? no string
            return new String[0];
        }
        // parse with a simple finite state machine

        final int normal = 0;
        final int inQuote = 1;
        final int inDoubleQuote = 2;
        int state = normal;
        final StringTokenizer tok = new StringTokenizer(toProcess, "\"\' ", true);
        final ArrayList<String> result = new ArrayList<String>();
        final StringBuilder current = new StringBuilder();
        boolean lastTokenHasBeenQuoted = false;

        while (tok.hasMoreTokens()) {
            String nextTok = tok.nextToken();
            switch (state) {
                case inQuote:
                    if ("\'".equals(nextTok)) {
                        lastTokenHasBeenQuoted = true;
                        state = normal;
                    } else {
                        current.append(nextTok);
                    }
                    break;
                case inDoubleQuote:
                    if ("\"".equals(nextTok)) {
                        lastTokenHasBeenQuoted = true;
                        state = normal;
                    } else {
                        current.append(nextTok);
                    }
                    break;
                default:
                    if ("\'".equals(nextTok)) {
                        state = inQuote;
                    } else if ("\"".equals(nextTok)) {
                        state = inDoubleQuote;
                    } else if (" ".equals(nextTok)) {
                        if (lastTokenHasBeenQuoted || current.length() != 0) {
                            result.add(current.toString());
                            current.setLength(0);
                        }
                    } else {
                        current.append(nextTok);
                    }
                    lastTokenHasBeenQuoted = false;
                    break;
            }
        }
        if (lastTokenHasBeenQuoted || current.length() != 0) {
            result.add(current.toString());
        }
        if (state == inQuote || state == inDoubleQuote) {
            throw new RuntimeException("unbalanced quotes in " + toProcess);
        }
        return result.toArray(new String[result.size()]);
    }
}
