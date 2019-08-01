package rs.lukaj.httpclient;


import java.io.*;
import java.util.List;
import java.util.zip.*;

//you know, other stuff
public class Utils {
    /**
     * Decompresses gzip- or deflate-encoded byte array. Encoding should be one of "gzip" or "deflate". If encoding is
     * "identity" or null, the data array is returned. In all other cases, the array is returned and warning is
     * printed on error stream
     * @param data data to decompress
     * @param encoding encoding
     * @return decompressed bytes
     * @throws IOException if {@link GZIPInputStream} throws IOException
     */
    //we're only supporting gzip and deflate because I'm not feeling like writing decompression algorithm for lzma or
    //brotli (or bzip or bzip2, even though those two are not officially supported), and Java library doesn't have
    //out of the box solution for the two afaik
    public static byte[] decompress(byte[] data, String encoding) throws IOException {
        if(encoding == null || encoding.equals("identity")) {
            return data;
        } else if(encoding.equals("gzip") || encoding.equals("deflate")) {
            ByteArrayInputStream bytein = new ByteArrayInputStream(data);
            InputStream compressed;
            if(encoding.equals("gzip")) compressed = new GZIPInputStream(bytein);
            else compressed = new InflaterInputStream(bytein, new Inflater(false), 512);
            ByteArrayOutputStream byteout = new ByteArrayOutputStream();

            try(bytein; compressed) {
                byte[] comp = compressed.readAllBytes();
                byteout.write(comp);
            }
            return byteout.toByteArray();
        } else {
            System.err.println("Waning: ignoring unknown encoding " + encoding);
            return data;
        }
    }

    public static byte[] compress(byte[] data, String encoding) throws IOException {
        if(encoding == null || encoding.equals("identity")) {
            return data;
        } else if(encoding.equals("gzip") || encoding.equals("deflate")) {
            byte[] result;
            OutputStream compressed;
            ByteArrayOutputStream byteout = new ByteArrayOutputStream(data.length);
            if(encoding.equals("gzip")) compressed = new GZIPOutputStream(byteout);
            else compressed = new DeflaterOutputStream(byteout, new Deflater(8, false));
            try (byteout; compressed) {
                compressed.write(data);
                compressed.close();
                result = byteout.toByteArray();
            }
            return result;
        } else {
            System.err.println("Waning: ignoring unknown encoding " + encoding);
            return data;
        }
    }

    public static void append(List<Byte> to, byte[] from) {
        for(byte b : from) to.add(b);
    }

    public static byte[] unbox(Byte[] boxed) {
        byte[] bytes = new byte[boxed.length];
        for(int i=0; i<boxed.length; i++)
            bytes[i] = boxed[i];
        return bytes;
    }
}
