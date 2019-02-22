package com.github.segator.proxylive.stream;

import java.io.IOException;
import java.io.InputStream;

public abstract class VideoInputStream extends InputStream {
    public abstract boolean isConnected();
    public abstract boolean connect()  throws IOException;
}
