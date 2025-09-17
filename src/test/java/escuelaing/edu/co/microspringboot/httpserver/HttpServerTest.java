package escuelaing.edu.co.microspringboot.httpserver;

import static org.junit.jupiter.api.Assertions.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.AfterEach;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import co.escuelaing.arep.microspringboot.httpserver.HttpServer;

class HttpServerTest {
    private static final int TEST_PORT = 35001;
    private ExecutorService executorService;
    private Thread serverThread;

    @BeforeEach
    void setUp() {
        executorService = Executors.newSingleThreadExecutor();
        HttpServer.staticfiles("src/main/resources/public");
        startServer();
    }

    @AfterEach
    void tearDown() throws Exception {
        HttpServer.stopServer();
        if (serverThread != null) {
            serverThread.interrupt();
        }
        executorService.shutdownNow();
        if (!executorService.awaitTermination(5, TimeUnit.SECONDS)) {
            System.err.println("ExecutorService did not terminate in time");
        }
    }

    private void startServer() {
        serverThread = new Thread(() -> {
            try {
                HttpServer.runServer(TEST_PORT);
            } catch (Exception e) {
                if (!e.getMessage().contains("socket closed")) {
                    e.printStackTrace();
                }
            }
        });
        serverThread.setDaemon(true);
        serverThread.start();

        // Wait for server to start
        waitForServerToStart();
    }

    private void waitForServerToStart() {
        int maxAttempts = 10;
        int attempt = 0;
        HttpClient testClient = HttpClient.newHttpClient();
        
        while (attempt < maxAttempts) {
            try {
                HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create("http://localhost:" + TEST_PORT + "/"))
                    .GET()
                    .build();
                HttpResponse<String> response = testClient.send(request, HttpResponse.BodyHandlers.ofString());
                if (response.statusCode() == 200 || response.statusCode() == 404) {
                    System.out.println("Server started successfully after " + attempt + " attempts.");
                    return;
                } else {
                    System.out.println("Unexpected status code: " + response.statusCode() + ", retrying...");
                }
            } catch (Exception e) {
                attempt++;
                System.out.println("Attempt " + attempt + " failed: " + e.getMessage());
                try {
                    Thread.sleep(500); // Mantener 500 ms de pausa
                } catch (InterruptedException ie) {
                    Thread.currentThread().interrupt();
                    break;
                }
            }
        }
        throw new RuntimeException("Server failed to start after " + maxAttempts + " attempts");
    }

    @Test
    void testStaticFileServing() throws Exception {
        HttpClient client = HttpClient.newHttpClient();

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create("http://localhost:" + TEST_PORT + "/index.html"))
                .GET()
                .build();

        HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
        assertEquals(200, response.statusCode());
        assertTrue(response.body().contains("Bienvenido"));
    }

    @Test
    void testRestEndpoint() throws Exception {
        HttpClient client = HttpClient.newHttpClient();
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create("http://localhost:" + TEST_PORT + "/api/greeting?name=TestUser"))
                .GET()
                .build();

        HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
        assertEquals(200, response.statusCode());
        assertTrue(response.body().contains("Hello, TestUser!"));
    }

    @Test
    void test404NotFound() throws Exception {
        HttpClient client = HttpClient.newHttpClient();
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create("http://localhost:" + TEST_PORT + "/nonexistent.html"))
                .GET()
                .build();

        HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
        assertEquals(404, response.statusCode());
        assertTrue(response.body().contains("<html><body><h1>404 - File Not Found</h1></body></html>"));
    }
}
