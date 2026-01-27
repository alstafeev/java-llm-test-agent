package agent.tms;

import agent.model.TestCase;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Executors;

import static org.junit.jupiter.api.Assertions.assertEquals;

public class AllurePerformanceTest {

    private HttpServer mockServer;
    private String baseUrl;
    private AllureTMSClient client;
    private static final int TEST_CASE_COUNT = 50;
    private static final int LATENCY_MS = 50;

    @BeforeEach
    void setUp() throws IOException {
        mockServer = HttpServer.create(new InetSocketAddress(0), 0);

        // 1. Mock Search Endpoint - Returns TEST_CASE_COUNT items
        mockServer.createContext("/api/testcase/__search", new HttpHandler() {
            @Override
            public void handle(HttpExchange exchange) throws IOException {
                StringBuilder jsonBuilder = new StringBuilder();
                jsonBuilder.append("{ \"content\": [");
                for (int i = 0; i < TEST_CASE_COUNT; i++) {
                    if (i > 0) jsonBuilder.append(",");
                    jsonBuilder.append(String.format("{\"id\": %d, \"name\": \"Test Case %d\"}", 1000 + i, i));
                }
                jsonBuilder.append("], \"totalElements\": ").append(TEST_CASE_COUNT).append(" }");

                sendResponse(exchange, 200, jsonBuilder.toString());
            }
        });

        // 2. Mock Step Endpoint - Adds artificial latency
        mockServer.createContext("/api/testcase", new HttpHandler() {
            @Override
            public void handle(HttpExchange exchange) throws IOException {
                if (exchange.getRequestURI().getPath().endsWith("/step")) {
                    try {
                        Thread.sleep(LATENCY_MS);
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                    }
                    String response = "{\"steps\": [{\"name\": \"Step 1\"}, {\"name\": \"Step 2\"}]}";
                    sendResponse(exchange, 200, response);
                } else {
                    sendResponse(exchange, 404, "Not Found");
                }
            }
        });

        mockServer.setExecutor(Executors.newCachedThreadPool());
        mockServer.start();

        baseUrl = "http://localhost:" + mockServer.getAddress().getPort();
        client = new AllureTMSClient(baseUrl, "dummy_token");
    }

    @AfterEach
    void tearDown() {
        mockServer.stop(0);
    }

    @Test
    void benchmarkFetchTestCases() throws Exception {
        Map<String, String> filters = new HashMap<>();
        filters.put("projectId", "1");

        long startTime = System.currentTimeMillis();
        List<TestCase> testCases = client.fetchTestCases(filters);
        long endTime = System.currentTimeMillis();

        long duration = endTime - startTime;
        System.out.println("--------------------------------------------------");
        System.out.println("Performance Test Result:");
        System.out.println("Fetched " + testCases.size() + " test cases.");
        System.out.println("Total Time: " + duration + " ms");
        System.out.println("Average Time per Item: " + (testCases.isEmpty() ? 0 : duration / testCases.size()) + " ms");
        System.out.println("--------------------------------------------------");

        assertEquals(TEST_CASE_COUNT, testCases.size());
    }

    private void sendResponse(HttpExchange exchange, int statusCode, String response) throws IOException {
        byte[] bytes = response.getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().set("Content-Type", "application/json");
        exchange.sendResponseHeaders(statusCode, bytes.length);
        try (OutputStream os = exchange.getResponseBody()) {
            os.write(bytes);
        }
    }
}
