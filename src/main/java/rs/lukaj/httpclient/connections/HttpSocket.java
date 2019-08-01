package rs.lukaj.httpclient.connections;

import rs.lukaj.httpclient.Utils;

import javax.net.SocketFactory;
import javax.net.ssl.SSLSocket;
import javax.net.ssl.SSLSocketFactory;
import java.io.*;
import java.net.Socket;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.*;

import static java.nio.charset.StandardCharsets.UTF_8;

/**
 * Represents a socket used for communicating with the nework. Supports HTTP and HTTPS {@link Endpoint}s.
 * Socket and connection are used interchangeably.
 */
//todo possible improvement: make it compatible with URLConnection
public class HttpSocket implements Closeable {
    private static final boolean AUTOFLUSH = true;

    private volatile long openedAt;
    private volatile long lastUsedAt;
    private volatile boolean inUse;

    private volatile boolean readingChunks = false;
    private Socket socket;
    private InputStream input;
    private PrintWriter writer;
    private final Object acquireLock = new Object();

    /**
     * Create a new socket to a given endpoint.
     * @param endpoint endpoint for the socket
     * @throws IOException
     */
    public HttpSocket(Endpoint endpoint) throws IOException {
        if(!endpoint.isHttps())
            socket = SocketFactory.getDefault().createSocket(endpoint.getAddress(), endpoint.getPort());
        else {
            SSLSocket sslSocket = (SSLSocket) SSLSocketFactory.getDefault().createSocket(endpoint.getAddress(), endpoint.getPort());
            sslSocket.setEnabledProtocols(new String[] {"TLSv1.2"});
            sslSocket.startHandshake();
            socket = sslSocket;
            //this uses default certs for JVM and all default config; because this wasn't a requirement, I didn't bother
            //much with it (and it was a shame to leave it unimplemented, seeing it's basically 3 LoC; might fail)
        }
        this.openedAt = System.currentTimeMillis();
        this.lastUsedAt = System.currentTimeMillis();

        input = socket.getInputStream();
        //assuming everything is in UTF-8 (this assumption can be dropped if we re-wrap Socket I/O streams
        //on each encoding change)
        writer = new PrintWriter(socket.getOutputStream(), AUTOFLUSH, UTF_8);
    }

    /**
     * Get how long is this connection idling. Idle time is calculated as a duration between the time it was released
     * last time and this moment. If connection is in use, idling time is 0.
     * @return idling duration
     */
    public Duration getIdlingTime() {
        if(inUse) return Duration.ZERO;
        return Duration.ofMillis(System.currentTimeMillis() - lastUsedAt);
    }

    /**
     * Get how old is this socket. Age is calculated as duration between the time it was opened and this moment.
     * @return socket age
     */
    public Duration getAge() {
        return Duration.ofMillis(System.currentTimeMillis() - openedAt);
    }

    /**
     * Release the socket, allowing it to be used for other transactions. Releasing the socket does not close
     * the underlying connection with the server.
     */
    public void release() {
        synchronized (acquireLock) {
            try { //attempt to read whatever's left in the stream after it's released
                while(input.available() > 0) input.read();
            } catch (IOException ignored) {
            }
            inUse = false;
            lastUsedAt = System.currentTimeMillis();
        }
    }

    /**
     * Acquires this connection if idle and not closed and returns true. Otherwise, returns false.
     * Connection must be acquired before writing to or reading from it.
     * @return whether the connection is acquired
     */
    //similar to read-modify-write; methods like "isAcquired" are inherently unsafe
    public boolean acquireIfIdle() {
        synchronized (acquireLock) {
            if(inUse || isClosed()) return false;
            inUse = true;
            return true;
        }
    }

    private void ensureAcquired() {
        if(!inUse) throw new IllegalStateException("Cannot print to idling connection!");
    }

    /**
     * Print a string to the socket; this sends data to server. This call is buffered, so it's not guaranteed
     * that bytes will be sent the same moment.
     * @param s data to be sent
     */
    public void print(String s) throws IOException {
        ensureAcquired();
        writer.print(s);
        if(writer.checkError()) throw new IOException("Error while writing data to socket"); //these APIs are awful
        lastUsedAt = System.currentTimeMillis();
    }

    /**
     * Write raw bytes to the socket; this sends bytes to the server and flushes the connection.
     * @param bytes data to be sent
     * @throws IOException
     */
    public void write(byte[] bytes) throws IOException {
        ensureAcquired();
        writer.flush();
        socket.getOutputStream().write(bytes);
        socket.getOutputStream().flush();
    }

    /**
     * Flush the connection, sending the bytes to the server.
     */
    public void flush() throws IOException {
        ensureAcquired();
        writer.flush();
        if(writer.checkError()) throw new IOException("Error while flushing the connection");
        lastUsedAt = System.currentTimeMillis();
    }

    /**
     * Read a single byte from the server. This method is blocking.
     * @return next byte, or -1 if end of stream has been reached.
     * @throws IOException
     */
    public int read() throws IOException {
        ensureAcquired();
        if(readingChunks) return -1;
        lastUsedAt = System.currentTimeMillis();
        return input.read();
    }

    /**
     * Read a line from the server. Lines are terminated with <em>either</em> CRLF or just LF. This method should not
     * be used for reading body of the response.
     * @return next line, or empty string if there are none.
     * @throws IOException
     */
    //we're skirting the spec here, because it specifies only CRLF as newline
    public String readLine() throws IOException { //avoiding BufferedReader because I can't read bytes with it
        ensureAcquired();
        if(readingChunks || socket.isClosed()) return "";
        while (input.available() == 0);
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        char curr, next = (char)input.read();
        do {
            if(socket.isClosed()) break;
            while (input.available() == 0);
            curr = next;
            next = (char)input.read();
            if(((curr != '\r' || next != '\n') && curr != '\n')) out.write(curr);
        } while((curr != '\r' || next != '\n') && curr != '\n');
        String line = out.toString(UTF_8);
        lastUsedAt = System.currentTimeMillis();
        return line;
    }

    /**
     * If there's more input waiting to be read and chunk reading is not in progress. Input not being ready does
     * <em>not</em> imply there won't be more data in future on this same socket.
     * @return
     * @throws IOException
     */
    public boolean inputReady() throws IOException {
        ensureAcquired();
        return !socket.isClosed() && !readingChunks && input.available() != 0;
    }

    /**
     * Read at most len bytes into the buffer, starting at offset. Number of read bytes is returned.
     * @param buf buffer used for storing read data
     * @param offset data is stored starting on this index
     * @param len maximum number of bytes to read
     * @return number of bytes read
     * @throws IOException
     */
    public int read(byte[] buf, int offset, int len) throws IOException {
        ensureAcquired();
        if(readingChunks || socket.isClosed()) return 0;
        int ret = input.read(buf, offset, len);
        lastUsedAt = System.currentTimeMillis();
        return ret;
    }

    /**
     * Assume a chunked response and read all chunks at once. This method will stall until all chunks
     * are received.
     * @return
     * @throws IOException if I/O exception occurs on underlying socket or chunks are malformed
     */
    public byte[] readAllChunks() throws IOException {
        ensureAcquired();
        readingChunks = true;
        ChunkedInputStream in = new ChunkedInputStream(input);

        List<Byte> body = new ArrayList<>(1024);
        while(in.hasMoreChunks()) {
            byte[] chunk = in.readChunk();
            Utils.append(body, chunk);
        }
        in.close();
        readingChunks = false;
        return Utils.unbox(body.toArray(new Byte[]{}));
    }

    /**
     * Reads chunks in background, and informs the caller about the progress using callbacks executed
     * on the given executor.
     * @param callbacks callbacks used to report back about the progress
     * @param executor executor on which callbacks are executed. If null, callbacks are executed
     *                 on the background thread
     */
    public void readChunks(ChunkCallbacks callbacks, Executor executor) {
        readingChunks = true;
        ExecutorService background = Executors.newSingleThreadExecutor();
        Future<?> task = background.submit(() -> {
            ChunkedInputStream in = new ChunkedInputStream(input);
            try {
                while (in.hasMoreChunks()) {
                    byte[] chunk = in.readChunk();
                    Runnable received = () -> callbacks.onChunkReceived(chunk);
                    if (executor != null) executor.execute(received);
                    else received.run();
                }
                Runnable end = callbacks::onEndTransfer;
                if (executor != null) executor.execute(end);
                else end.run();
            } catch (IOException e) {
                Runnable exception = () -> callbacks.onExceptionThrown(e);
                if (executor != null) executor.execute(exception);
                else exception.run();
            }
        });
        try {
            task.get();
        } catch (InterruptedException | ExecutionException e) {
            e.printStackTrace();
        }
    }

    /**
     * Returns whether the underlying (and, by extension, this) socket is closed. You cannot write to nor read from
     * closed sockets.
     * @return true if socket is closed, false otherwise
     */
    public boolean isClosed() {
        return socket.isClosed();
    }

    /**
     * Close the connection to the server. After closing, socket cannot be re-acquired and no more data can be read
     * from or written to this socket.
     * @throws IOException
     */
    @Override
    public void close() throws IOException {
        release();
        socket.close();
    }

    /**
     * Callbacks used to inform caller about chunk reading progress.
     */
    public interface ChunkCallbacks {
        /**
         * Called every time whole chunk is received.
         * @param chunk data in the chunk
         */
        void onChunkReceived(byte[] chunk);

        /**
         * Called after last chunk is read.
         */
        void onEndTransfer();

        /**
         * Called if I/O exception occurred
         * @param ex thrown exception
         */
        void onExceptionThrown(IOException ex);
    }
}
