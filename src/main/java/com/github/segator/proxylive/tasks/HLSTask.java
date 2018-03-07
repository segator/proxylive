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
import com.github.segator.proxylive.processor.IStreamMultiplexerProcessor;
import com.github.segator.proxylive.processor.IStreamProcessor;
import com.github.segator.proxylive.profiler.FFmpegProfilerService;
import com.github.segator.proxylive.stream.WithoutBlockingInputStream;
import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.URISyntaxException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.logging.Level;
import java.util.logging.Logger;
import org.apache.commons.io.FileUtils;
import org.springframework.beans.factory.annotation.Autowired;

/**
 *
 * @author Isaac Aymerich <isaac.aymerich@gmail.com>
 */
public class HLSTask implements IStreamTask {

    @Autowired
    private FFmpegProfilerService ffmpegProfilerService;
    private final IStreamMultiplexerProcessor inputStreamProcessor;
    private Thread inputThread;
    private Process process;
    private Date runDate;
    private String hlsParameters;
    private boolean terminated = false;
    private boolean crashed = false;
    private boolean internalCrash = false;
    private int crashedTimes = 0;
    private InputStream sourceProcessorInputStream;
    //private final List<String> readableSegments;
    private byte[] playlistBuffer;
    private final Map<String, List<FileInputStream>> segmentsInputs;
    private SimpleDateFormat dateFormatter;
    private long playlistCount;
    private Long lastAccess;

    public HLSTask(IStreamMultiplexerProcessor inputStreamProcessor) {
        this.inputStreamProcessor = inputStreamProcessor;
        this.segmentsInputs = new HashMap();
        //this.readableSegments = new ArrayList();
        this.playlistCount = 0;
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
        return inputStreamProcessor.getTask().getIdentifier() + "_HLS";
    }

    @Override
    public void initializate() {
        sourceProcessorInputStream = new WithoutBlockingInputStream(inputStreamProcessor.getMultiplexedInputStream());
    }

    @Override
    public void run() {
        try {
            runDate = new Date();
            dateFormatter = new SimpleDateFormat(ffmpegProfilerService.getSegmentDate("SimpleDateFormat"));
            System.out.println("[" + getIdentifier() + "] Start HLS");
            hlsParameters = ffmpegProfilerService.getHLSParameters(getIdentifier());
            String hlsTempPath = ffmpegProfilerService.getHLSTemporalPath(getIdentifier());
            File tmpOutputHLSPath = new File(hlsTempPath);
            try {
                FileUtils.deleteDirectory(tmpOutputHLSPath);
            } catch (Exception ex) {
            }
            if (!tmpOutputHLSPath.mkdirs()) {
                crashed = true;
                return;
            }
            String ffmpegExecutable = "ffmpeg";
            if (ProxyLiveUtils.getOS().equals("win")) {
                ffmpegExecutable =ffmpegProfilerService.getFFMpegExecutable();;
            }
            if (isTerminated()) {
                return;
            }
            process = Runtime.getRuntime().exec(ffmpegExecutable + " -i pipe:0 " + hlsParameters);

            inputThread = new Thread("HLS Source Input Thread:" + getIdentifier()) {
                public void run() {
                    byte[] buffer = new byte[32 * 1024];
                    OutputStream outputStreamSource = process.getOutputStream();
                    try {
                        int read = 0;
                        while (isRunning() && !isTerminated()) {
                            if (internalCrash && !isTerminated()) {
                                throw new IOException("Handled Crash event HLS");
                            }
                            read = sourceProcessorInputStream.read(buffer);
                            if (read > 0) {
                                outputStreamSource.write(buffer, 0, read);
                            } else {
                                Thread.sleep(1);
                            }
                        }
                        System.out.println("Saliendo Input Thread HLS");
                    } catch (Exception e) {
                        if (!isTerminated()) {
                            System.out.println("Error:" + e.getMessage());
                            Logger.getLogger(HLSTask.class.getName()).log(Level.SEVERE, null, e);
                            internalCrash = true;
                        }
                    }
                }
            };
            inputThread.start();
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
                    Thread.sleep(100);
                }
                Thread.sleep(1);

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
            System.out.println("[" + getIdentifier() + "] Terminated HLS");
        }
    }

    public synchronized InputStream getPlayList() throws IOException, URISyntaxException {
        byte[] buffer = readPlayListDirect();
        if (buffer != null) {
            lastAccess = new Date().getTime();
            return new ByteArrayInputStream(buffer);
        }

//        if (playlistBuffer != null) {
//            return new ByteArrayInputStream(playlistBuffer);
//        }
        return null;
    }

    public long getSegmentSize(String segment) {
//        if (readableSegments.contains(segment)) {
        File segmentFile = buildSegmentFile(segment);
        if (segmentFile.exists()) {
            return segmentFile.length();
        }
//        }
        return 0;
    }

    private File buildSegmentFile(String segment) {
        return new File(ffmpegProfilerService.getHLSTemporalPath(getIdentifier()) + segment);
    }

    public InputStream getSegment(String segment) throws FileNotFoundException {
//        if (readableSegments.contains(segment)) {
        File segmentFile = buildSegmentFile(segment);
        if (segmentFile.exists()) {
//                synchronized (readableSegments) {
            FileInputStream fis = new FileInputStream(segmentFile);

//                    List<FileInputStream> fisList = segmentsInputs.get(segment);
//                    if (fisList == null) {
//                        fisList = new ArrayList();
//                        fisList.add(fis);
//                        segmentsInputs.put(segment, fisList);
//                    }
            lastAccess = new Date().getTime();
            return fis;
//                    return new InputStream() {
//
//                        @Override
//                        public int read() throws IOException {
//                            return fis.read();
//                        }
//
//                        @Override
//                        public int read(byte b[]) throws IOException {
//                            return fis.read(b);
//                        }
//
//                        @Override
//                        public int read(byte b[], int off, int len) throws IOException {
//                            return fis.read(b, off, len);
//                        }
//
//                        @Override
//                        public void close() throws IOException {
//                            synchronized (readableSegments) {
//                                fis.close();
//                                List<FileInputStream> fisList = segmentsInputs.get(segment);
//                                if (fisList != null) {
//                                    fisList.remove(fis);
//                                    if (fisList.isEmpty()) {
//                                        segmentsInputs.remove(segment);
//                                    }
//                                }
//                            }
//                        }
//
//                    };
//                }
        }
//        }
        return null;
    }

    @Override
    public int hashCode() {
        int hash = 5;
        hash = 31 * hash + Objects.hashCode(this.inputStreamProcessor);
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
        final HLSTask other = (HLSTask) obj;
        if (!Objects.equals(this.inputStreamProcessor, other.inputStreamProcessor)) {
            return false;
        }
        return true;
    }

    public synchronized boolean isRunning() {
        return process.isAlive();
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
                String hlsTempPath = ffmpegProfilerService.getHLSTemporalPath(getIdentifier());
                File tmpOutputHLSPath = new File(hlsTempPath);
                FileUtils.deleteDirectory(tmpOutputHLSPath);
            } catch (Exception ex) {
            }
        } catch (Exception ex) {
        }
    }

    public Long getLastAccess() {
        return lastAccess;
    }

//    private synchronized void generatePlayList() throws ParseException, CharacterCodingException {
//
//        StringBuilder string = new StringBuilder();
//        string.append("#EXTM3U\n");
//        string.append("#EXT-X-VERSION:3\n");
//        string.append("#EXT-X-ALLOW-CACHE:YES\n");
//        string.append("#EXT-X-TARGETDURATION:10\n");
//        string.append("#EXT-X-MEDIA-SEQUENCE:").append(playlistCount).append("\n");
//        int i = readableSegments.size() - 5;
//        if (i < 0) {
//            i = 0;
//        }
//        for (; i < readableSegments.size(); i++) {
//            String readableSegment = readableSegments.get(i);
//
//            string.append("#EXTINF:10,\n");
//            string.append(readableSegment);
//            string.append("\n");
//        }
//        //string.append("#EXT-X-ENDLIST\n");
//        playlistBuffer = string.toString().getBytes();
//        playlistCount++;
//    }
//    private Date getSegmentDate(String segment) throws ParseException {
//        String dateFile = segment.replace(ffmpegProfilerService.getSegmentName(), "").replace("." + ffmpegProfilerService.getSegmentFileFormat(), "");
//        return dateFormatter.parse(dateFile);
//    }
//    private synchronized void readPlayList() throws IOException, URISyntaxException {
//        String playlistSt = ffmpegProfilerService.getHLSTemporalPath(getIdentifier()) + "playlist.m3u8";
//        File playList = new File(playlistSt);
//        if (playList.exists() && playList.length() > 0) {
//            playlistBuffer = Files.readAllBytes(Paths.get(playlistSt));
//        }
//    }
    private byte[] readPlayListDirect() throws IOException, URISyntaxException {
        String playlistSt = ffmpegProfilerService.getHLSTemporalPath(getIdentifier()) + "playlist.m3u8";
        File playList = new File(playlistSt);
        if (playList.exists() && playList.length() > 0) {
            return Files.readAllBytes(Paths.get(playlistSt));
        }
        return null;
    }

    @Override
    public IStreamProcessor getSourceProcessor() {
        return inputStreamProcessor;
    }

    @Override
    public Date startTaskDate() {
        return runDate;
    }
}
