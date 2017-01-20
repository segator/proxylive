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
import java.util.ArrayList;
import java.util.List;

/**
 *
 * @author Isaac Aymerich <isaac.aymerich@gmail.com>
 */
public class BroadcastCircularBufferedOutputStream extends OutputStream {

    private final byte[] buffer;

    private final int size;
    private int pos;
    private boolean filled;
    private List<ClientBroadcastedInputStream> clientsList;
    private String identifier;

    public BroadcastCircularBufferedOutputStream(int size,String identifier) {
        buffer = new byte[size];
        this.size = size;
        filled = false;
        this.identifier=identifier;
        clientsList = new ArrayList();

    }

    public synchronized List<ClientBroadcastedInputStream> getClientsList() {
        return clientsList;
    }

    public byte[] getBuffer() {
        return buffer;
    }

    public synchronized int getSize() {
        return size;
    }

    public synchronized int getPos() {
        return pos;
    }

    public synchronized boolean isFilled() {
        return filled;
    }

    public synchronized ClientBroadcastedInputStream getClientInputStream(final String cla) {
        ClientBroadcastedInputStream cli = new ClientBroadcastedInputStream() {

            //private int cliPos = pos;
            private int cliPos = 0;
            private int sleeps = 0;

            @Override
            public int available() throws IOException {
                int available=0;
                if (filled || cliPos > pos) {
                    available= size - cliPos + pos;
                } else {
                    available= pos - cliPos;
                }
                return available;
            }

            @Override
            public int read() throws IOException {
                int readed = -1;
                if (cliPos < getPos()) {
                    readed = buffer[cliPos++];
                    sleeps = 0;
                } else if (filled && cliPos > getPos()) {
                    readed = buffer[cliPos++];
                    sleeps = 0;
                } else {
                    if (sleeps == 300) {
                        throw new IOException("No data received");
                    }
                    try {
                        Thread.sleep(100);
                    } catch (InterruptedException ex) {
                        throw new IOException("Interrupted waiting");
                    }
                    sleeps++;
                    readed = read();
                }
                if (cliPos == getSize()) {
                    System.out.println("jump"+cla);
                    cliPos = 0;
                }
                return readed & 0xFF;
            }
        };
        clientsList.add(cli);
        return cli;
    }

    public synchronized boolean removeClientInputStream(ClientBroadcastedInputStream is) {
        return clientsList.remove(is);
    }
      public synchronized void removeAllInputStream() {
        clientsList.clear();
    }

    @Override
    public synchronized void write(int b) throws IOException {
        buffer[pos++] = (byte) (b & 0xFF);
        if (pos == size) {
            System.out.println("filled "+identifier);
            filled = true;
            pos = 0;
        }
    }

}
