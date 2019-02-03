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

        import com.github.segator.proxylive.ProxyLiveUtils;
        import com.github.segator.proxylive.config.ProxyLiveConfiguration;
        import com.github.segator.proxylive.entity.Channel;
        import com.github.segator.proxylive.processor.IStreamProcessor;
        import com.github.segator.proxylive.profiler.FFmpegProfilerService;
        import com.github.segator.proxylive.stream.WithoutBlockingInputStream;
        import java.io.ByteArrayInputStream;
        import java.io.File;
        import java.io.FileInputStream;
        import java.io.FileNotFoundException;
        import java.io.IOException;
        import java.io.InputStream;
        import java.net.URISyntaxException;
        import java.nio.file.Files;
        import java.nio.file.Path;
        import java.nio.file.Paths;
        import java.text.SimpleDateFormat;
        import java.util.Date;
        import java.util.HashMap;
        import java.util.List;
        import java.util.Map;
        import java.util.Objects;
        import java.util.logging.Level;
        import java.util.logging.Logger;
        import java.util.regex.Pattern;

        import org.apache.commons.io.FileUtils;
        import org.springframework.beans.factory.annotation.Autowired;
        import org.springframework.beans.factory.annotation.Value;

        import javax.annotation.PostConstruct;

/**
 *
 * @author Isaac Aymerich <isaac.aymerich@gmail.com>
 */
public class HLSDirectTask implements IStreamTask {

    @Autowired
    private FFmpegProfilerService ffmpegProfilerService;
    @Autowired
    private ProxyLiveConfiguration config;
    @Value("${local.server.port}")
    int serverPort;

    private final String profile;
    private final Channel channel;
    private Thread inputThread;
    private String url;
    private Process process;
    private Date runDate;
    private String hlsParameters,transcodeParameters;
    private boolean terminated = false;
    private boolean crashed = false;
    private boolean internalCrash = false;
    private int crashedTimes = 0;
    private byte[] playlistBuffer;
    private final Map<String, List<FileInputStream>> segmentsInputs;
    private SimpleDateFormat dateFormatter;
    private long playlistCount;
    private Long lastAccess;


    public HLSDirectTask(Channel channel, String profile) {
        this.profile = profile;
        this.channel = channel;
        this.segmentsInputs = new HashMap();
        //this.readableSegments = new ArrayList();
        this.playlistCount = 0;
    }

    @PostConstruct
    public void initializeBean() {
        url = "http://localhost:"+serverPort+"/view/"+profile+"/" + channel.getId()+"?user=internal&token="+config.getInternalToken();
    }
    @Override
    public String toString() {
        return getIdentifier();
    }

    @Override
    public boolean isCrashed() {
        return crashed;
    }

    @Override
    public void terminate() {
        System.out.println("[" + getIdentifier() + "] Required Terminate HLS");
        terminated = true;
        try {
            if (inputThread != null) {
                inputThread.join();
            }
        } catch (InterruptedException ex) {
        }

    }

    @Override
    public String getIdentifier() {
        return channel.getId() + "_" + profile + "_HLS";
    }

    @Override
    public void initializate() {
        lastAccess = new Date().getTime();
    }

    @Override
    public void run() {
        try {
            runDate = new Date();
            dateFormatter = new SimpleDateFormat(ffmpegProfilerService.getSegmentDate("SimpleDateFormat"));
            System.out.println("[" + getIdentifier() + "] Start HLS");
            hlsParameters = ffmpegProfilerService.getHLSParameters(getIdentifier());
            transcodeParameters = " -c:a copy -c:v copy ";

            Path hlsTempPath = ffmpegProfilerService.getHLSTemporalPath(getIdentifier());
            File tmpOutputHLSPath = hlsTempPath.toFile();
            try {
                FileUtils.deleteDirectory(tmpOutputHLSPath);
            } catch (Exception ex) {
            }
            if (!tmpOutputHLSPath.mkdirs()) {
                crashed = true;
                return;
            }
            //FileUtils.copyDirectory(new File("C:\\lol"), tmpOutputHLSPath);
            String ffmpegExecutable = "ffmpeg";
            if (ProxyLiveUtils.getOS().equals("win")) {
                ffmpegExecutable =ffmpegProfilerService.getFFMpegExecutable();;
            }
            if (isTerminated()) {
                return;
            }
            process = Runtime.getRuntime().exec(ffmpegExecutable +  " -i " + url + " " +  transcodeParameters +" "+ hlsParameters);

            InputStream is = new WithoutBlockingInputStream(process.getErrorStream());
            byte[] bufferError = new byte[1024];
            while (isRunning() && !isTerminated()) {
                if (internalCrash && !isTerminated()) {
                    throw new IOException("Handled Crash event");
                }
                int readed = is.read(bufferError);
                if (readed > 0) {
                    System.out.print(new String(bufferError, 0, readed));
                } else {
                    Thread.sleep(5);
                }
            }

        } catch (Exception ex) {
            if (!isTerminated()) {
                System.out.println("Error:" + ex.getMessage());
                Logger.getLogger(HLSTask.class.getName()).log(Level.SEVERE, null, ex);
                internalCrash = true;
            }
        } finally {
            System.out.println("HLS Finally");
            stopProcess();
            try {
                inputThread.join();
            } catch (Exception ex) {
            }
        }
        if (!isTerminated() && internalCrash) {
            crashedTimes++;
            if (crashedTimes > 10) {
                crashed = true;
            } else {
                internalCrash = false;
                run();
            }
        } else {
            try {
                FileUtils.deleteDirectory(ffmpegProfilerService.getHLSTemporalPath(getIdentifier()).toFile());
            } catch (Exception ex) {
            }
            System.out.println("[" + getIdentifier() + "] Terminated HLS");
        }
    }

    public synchronized InputStream getPlayList() throws IOException, URISyntaxException {
        byte[] buffer = readPlayListDirect();
        if (buffer != null) {
            lastAccess = new Date().getTime();
            return new ByteArrayInputStream(buffer);
        }
        return null;
    }

    public long getSegmentSize(String segment) {
        File segmentFile = buildSegmentFile(segment);
        if (segmentFile.exists()) {
            return segmentFile.length();
        }
        return 0;
    }

    private File buildSegmentFile(String segment) {
        return new File(ffmpegProfilerService.getHLSTemporalPath(getIdentifier()) + File.separator + segment);
    }

    public InputStream getSegment(String segment) throws FileNotFoundException {
        File segmentFile = buildSegmentFile(segment);
        if (segmentFile.exists()) {
            FileInputStream fis = new FileInputStream(segmentFile);
            lastAccess = new Date().getTime();
            return fis;

        }

        return null;
    }

    @Override
    public int hashCode() {
        int hash = 5;
        hash = 31 * hash + Objects.hashCode(this.url);
        hash = 31 * hash + Objects.hashCode(this.profile);
        hash = 31 * hash + "hls".hashCode();
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
        final HLSDirectTask other = (HLSDirectTask) obj;
        if (!Objects.equals(this.url, other.url)) {
            return false;
        }
        if (!Objects.equals(this.profile, other.profile)) {
            return false;
        }
        return true;
    }


    public synchronized boolean isRunning() throws IOException {
        if(process.isAlive()){
            return true;
        }else{
            throw new IOException("Process is not running");
        }
    }

    private boolean isTerminated() {
        return terminated;
    }

    private void stopProcess() {
        try {
            if (process.isAlive()) {
                try {
                    process.getInputStream().close();
                } catch (IOException ex) {
                }
                try {
                    process.getOutputStream().close();
                } catch (IOException ex) {
                }
                try {
                    process.getErrorStream().close();
                } catch (IOException ex) {
                }
                process.destroy();
            }
            try {
                Path hlsTempPath = ffmpegProfilerService.getHLSTemporalPath(getIdentifier());
                File tmpOutputHLSPath = ((Path) hlsTempPath).toFile();
                FileUtils.deleteDirectory(tmpOutputHLSPath);
            } catch (Exception ex) {
            }
        } catch (Exception ex) {
        }
    }

    public Long getLastAccess() {
        return lastAccess;
    }

    private byte[] readPlayListDirect() throws IOException, URISyntaxException {
        String playlistSt = ffmpegProfilerService.getHLSTemporalPath(getIdentifier()) + File.separator +"playlist.m3u8";
        File playList = new File(playlistSt);
        if (playList.exists() && playList.length() > 0) {
            return Files.readAllBytes(Paths.get(playlistSt));
        }
        return null;
    }

    @Override
    public IStreamProcessor getSourceProcessor() {
        return null;
    }

    @Override
    public Date startTaskDate() {
        return runDate;
    }
}
