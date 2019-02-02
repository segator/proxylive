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
package com.github.segator.proxylive.profiler;

import com.github.segator.proxylive.config.FFMpegProfile;
import com.github.segator.proxylive.config.ProxyLiveConfiguration;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.nio.file.Path;
import java.nio.file.Paths;

/**
 *
 * @author Isaac Aymerich <isaac.aymerich@gmail.com>
 */
@Service
public class FFmpegProfilerService {
    @Autowired
    private ProxyLiveConfiguration config;


    public FFMpegProfile getProfile(String profile) {
        for (FFMpegProfile ffmpegProfile : config.getFfmpeg().getProfiles()) {
            if (ffmpegProfile.getAlias().equals(profile)) {
                return ffmpegProfile;
            }
        }
        return null;
    }

    public String getTranscodeParameters(String profile) {       
        return getProfile(profile).getParameters();
    }

    public Path getHLSTemporalPath(String taskIdentifier) {
        return Paths.get(config.getFfmpeg().getHls().getTempPath(),taskIdentifier.replace(":", "_"));
    }

    public String getSegmentFormat() {
        return getSegmentName() + getSegmentDate("FFmpeg") + "." + getSegmentFileFormat();
    }

    public String getSegmentFileFormat() {
        return "ts";
    }

    public String getSegmentName() {
        return "segment_";
    }

    public String getSegmentDate(String type) {
        switch (type) {
            case "FFmpeg":
                return "%Y_%m_%d_%H_%M_%S";

            case "SimpleDateFormat":
                return "yyyy_MM_dd_HH_mm_ss";
        }
        return null;
    }

    public String getHLSParameters(String taskIdentifier) {
        Path tempFolder = getHLSTemporalPath(taskIdentifier);
        return config.getFfmpeg().getHls().getParameters() + " " + tempFolder.toString() + "/playlist.m3u8";
    }

    public String getFFMpegExecutable() {
        return config.getFfmpeg().getPath();        
    }
}
