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
package com.github.segator.proxylive.stream;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.List;

/**
 *
 * @author Isaac Aymerich <isaac.aymerich@gmail.com>
 */
public class BroadcastCircularBufferedOutputStream extends OutputStream {

    private final ByteBuffer byteBuffer;
    private boolean filled;
    private final List<ClientBroadcastedInputStream> clientsList;
    private final String identifier;

    public BroadcastCircularBufferedOutputStream(int size, String identifier) {
        filled = false;
        this.identifier = identifier;
        clientsList = new ArrayList();
        byteBuffer = ByteBuffer.allocateDirect(size);

    }

    public synchronized List<ClientBroadcastedInputStream> getClientsList() {
        return clientsList;
    }

    public synchronized boolean isFilled() {
        return filled;
    }

    public synchronized boolean removeClientConsumer(ClientBroadcastedInputStream is) {
        return clientsList.remove(is);
    }

    public synchronized void removeAllConsumers() {
        clientsList.clear();
    }

    @Override
    public synchronized void write(int b) throws IOException {
        byteBuffer.put((byte)(b & 0xFF));
        if (byteBuffer.position() == byteBuffer.limit()) {
            filled = true;
            byteBuffer.rewind();
        }
    }

    @Override
    public void write(byte b[], int off, int len) throws IOException {
        if (byteBuffer.position() + len > byteBuffer.limit()) {
            int writeLen = byteBuffer.limit() - byteBuffer.position();
            byteBuffer.put(b, off, writeLen);
            filled=true;
            byteBuffer.rewind();
            byteBuffer.put(b, writeLen, len - writeLen);
        } else {
            byteBuffer.put(b, off, len);
        }
    }

    public synchronized ClientBroadcastedInputStream getConsumer(String id) {
        ClientBroadcastedInputStream clientInputStream  = new ClientBroadcastedInputStream(byteBuffer,id);
        clientsList.add(clientInputStream);
        return clientInputStream;
    }
}
