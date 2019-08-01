package rs.lukaj.httpclient;

import java.io.IOException;
import java.util.concurrent.TimeoutException;

public class Main {

    public static void main(String[] args) throws IOException, TimeoutException {
        System.out.println("This is a library; main does nothing. To compile documentation, run ./gradlew javadoc");
    }


    /*
    //this is how chunked response works, but it's pretty fragile
    private static void testChunk() throws IOException, TimeoutException, ExecutionException {
        RequestHeaders headers = RequestHeaders.createDefault();
        headers.put("Accept-Encoding", "identity"); //gzip seems to be screwed up
        HttpTransaction transaction = new HttpTransaction(new ConfigurableConnectionPool());
        transaction.setHeaders(headers)
                .makeRequest(Http.Verb.GET, "http://anglesharp.azurewebsites.net/Chunked")
                .getChunks(new HttpSocket.ChunkCallbacks() {
                    @Override
                    public void onChunkReceived(byte[] chunk) {
                        System.out.println("Received chunk!");
                        System.out.println(new String(chunk, UTF_8));
                    }

                    @Override
                    public void onEndTransfer() {
                        System.out.println("End transfer");
                    }

                    @Override
                    public void onExceptionThrown(IOException ex) {
                        ex.printStackTrace();
                    }
                }, null);
        transaction.close();
    }
    */

    /*
    //works as expected, dunno how to write unit test for it
    private static void testFile() throws IOException, TimeoutException {
        HttpClient client = HttpClient.create();
        HttpTransaction transaction = client.newTransaction();
        RequestHeaders headers = RequestHeaders.createDefault();
        headers.setAccept("image/jpeg");
        headers.setAcceptEncoding("gzip,deflate,identity");
        HttpResponse response = transaction
                .setHeaders(headers)
                .makeRequest(Http.Verb.GET, "http://httpbin.org/image/jpeg");
        System.out.println(response.getStatus());
        System.out.println(response.getHeaders());
        File output = new File("/home/luka/Documents/test_client.jpg");
        output.createNewFile();
        response.writeBodyToFile(output);
        transaction.close();
    }
    */
}
