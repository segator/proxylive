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
package com.github.segator.proxylive.config;

import java.rmi.Remote;

/**
 *
 * @author Isaac Aymerich <isaac.aymerich@gmail.com>
 */
public class FFMpegProfile {
    private String alias;
    private RemoteTranscoder transcoder;
    private String parameters;
    private Integer adaptiveBandWith;
    private String adaptiveResolution;

    public Integer getAdaptiveBandWith() {
        return adaptiveBandWith;
    }

    public void setAdaptiveBandWith(Integer adaptiveBandWith) {
        this.adaptiveBandWith = adaptiveBandWith;
    }

    public String getAdaptiveResolution() {
        return adaptiveResolution;
    }

    public void setAdaptiveResolution(String adaptiveResolution) {
        this.adaptiveResolution = adaptiveResolution;
    }

    public String getAlias() {
        return alias;
    }

    public void setAlias(String alias) {
        this.alias = alias;
    }

    public String getParameters() {
        return parameters;
    }

    public void setParameters(String parameters) {
        this.parameters = parameters;
    }

    public boolean isLocalTranscoding(){
        return transcoder==null;
    }

    public RemoteTranscoder getTranscoder() {
        return transcoder;
    }

    public void setTranscoder(RemoteTranscoder transcoder) {
        this.transcoder = transcoder;
    }


}
