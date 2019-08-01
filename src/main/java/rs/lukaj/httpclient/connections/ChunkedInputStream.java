package rs.lukaj.httpclient.connections;

import java.io.IOException;
import java.io.InputStream;

/**
 * InputStream designed to read from HTTP chunked data (Transfer-Encoding: Chunked), according to the spec.
 * Requires CRLF on all the right places, otherwise throws IOException. Use {@link #hasMoreChunks()} to see
 * whether more chunks are remaining and {@link #readChunk()} to read the next chunk.
 */
public class ChunkedInputStream extends InputStream {

    private InputStream in;
    private long remaining = 0;
    private boolean closed = false;
    private boolean end = false;
    private boolean beginning = true;

    /**
     * @param socketStream input stream with data from server
     */
    public ChunkedInputStream(InputStream socketStream) {
        in = socketStream;
    }

    private void ensureOpen() throws IOException {
        if(closed) throw new IOException("Trying to read from closed stream!");
    }

    @Override
    public int read() throws IOException {
        ensureOpen();
        if(end) return -1;
        if(remaining != 0) {
            int next = in.read();
            remaining--;
            return next;
        } else {
            enterChunk();
            return read();
        }
    }

    private void enterChunk() throws IOException {
        if(!beginning) {
            int current = in.read(), next = in.read();
            if (!(current == '\r' && next == '\n')) throw new IOException("Ill-formed chunk: no CRLF at the end");
            beginning = false;
        }
        int current = in.read();
        StringBuilder lenSb = new StringBuilder(4);
        lenSb.append((char)current);
        while((current = in.read()) != '\r' || (current = in.read()) != '\n') { //short-circuiting magic
            lenSb.append((char)current);
        } //we're making some not-entirely-unwarranted assumptions, i.e. assuming server is nice
        //that is, we're assuming that size ends with CRLF. But we're fine with CR inside the number.
        String lenStr = lenSb.toString().trim();
        if(lenStr.length() == 0) {
            end = true;
        }
        long len = Long.parseLong(lenStr, 16); //long is probably an overkill
        if(len == 0) {
            end = true;
            in.read(); in.read(); //that's final CRLF
        }
        else remaining = len;
    }

    /**
     *
     * @return number of remaining bytes in current chunk
     */
    public long getRemaining() {
        return remaining;
    }

    /**
     * Reads bytes to the end of the chunk.
     * @return remaining bytes in current chunk
     * @throws IOException
     */
    public byte[] readChunk() throws IOException {
        return readNBytes((int) getRemaining());
    }

    /**
     * Checks whether there are more chunks, and enters the next one if needed.
     * @return true if there are more chunks, false otherwise
     * @throws IOException
     */
    public boolean hasMoreChunks() throws IOException {
        if(remaining != 0) return true;
        else enterChunk();
        return !end;
    }

    @Override
    public int available() throws IOException {
        return in.available();
    }

    @Override
    public void close() {
        closed = true;
    }
}
