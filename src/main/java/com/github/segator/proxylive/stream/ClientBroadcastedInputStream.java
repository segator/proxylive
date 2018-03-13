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
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.nio.channels.InterruptedByTimeoutException;

/**
 *
 * @author Isaac Aymerich <isaac.aymerich@gmail.com>
 */
public class ClientBroadcastedInputStream extends InputStream {

    private final ByteBuffer sourceByteBuffer, clientByteBuffer;
    private final String id;

    ClientBroadcastedInputStream(ByteBuffer byteBuffer, String id) {
        this.sourceByteBuffer = byteBuffer;
        this.clientByteBuffer = byteBuffer.asReadOnlyBuffer();
        this.id = id;

    }

    @Override
    public int available() throws IOException {
        int available = 0;
        if (clientByteBuffer.position() > sourceByteBuffer.position()) {
            available = sourceByteBuffer.limit() - clientByteBuffer.position() + sourceByteBuffer.position();
        } else {
            available = sourceByteBuffer.position() - clientByteBuffer.position();
        }
        return available;
    }

    @Override
    public int read() throws IOException {
        int readed = -1;
        if (clientByteBuffer.position() == sourceByteBuffer.position()) {
            try {
                while (available() == 0) {
                    Thread.sleep(10);
                }
            } catch (InterruptedException ex) {
                throw new IOException("Interrupted waiting");
            }
        }
        readed = clientByteBuffer.get();
        clientByteBuffer.position();
        if (clientByteBuffer.position() == sourceByteBuffer.limit()) {
            System.out.println("jump" + id);
            clientByteBuffer.rewind();
        }
        return readed & 0xFF;
    }

    @Override
    public int read(byte b[]) throws IOException {
        int readed = -1;
        int sourcePosition = sourceByteBuffer.position();
        if (clientByteBuffer.position() > sourcePosition) {
            readed = b.length;
            if (b.length > sourceByteBuffer.limit() - clientByteBuffer.position()) {
                readed = sourceByteBuffer.limit() - clientByteBuffer.position();
                clientByteBuffer.get(b, 0, readed);
                clientByteBuffer.rewind();
            } else {
                clientByteBuffer.get(b, 0, readed);
            }

        } else if (clientByteBuffer.position() <= sourcePosition) {
            try {
                long waitTime = 0;
                while (clientByteBuffer.position() == sourceByteBuffer.position()) {
                    Thread.sleep(10);
                    waitTime+=10;
                    if(waitTime>15000){
                        throw new InterruptedByTimeoutException();
                    }
                }
            } catch (InterruptedException ex) {
                throw new IOException("Interrupted waiting");
            }
            sourcePosition = sourceByteBuffer.position();
            //Write buffer rewinded
            if (sourcePosition < clientByteBuffer.position()) {
                readed = read(b);
            } else {
                readed = sourcePosition - clientByteBuffer.position();
                if (b.length < readed) {
                    readed = b.length;
                }
                clientByteBuffer.get(b, 0, readed);
            }
        }

        return readed;
    }
}
