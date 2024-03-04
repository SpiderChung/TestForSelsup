package ru.schung;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import lombok.Data;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.time.LocalDate;
import java.util.List;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;

/**
 * Класс для работы с API Честного знака
 * @author spiderchung
 */
@Data
public class CrptApi {
    private final HttpClient httpClient;
    private final Semaphore semaphore;
    private final int requestLimit;
    private final Duration timeUnit;
    private long intervalStart;
    private ObjectMapper objectMapper;

    public CrptApi(Duration timeUnit, int requestLimit) {
        this.httpClient = HttpClient.newHttpClient();
        this.timeUnit = timeUnit;
        this.requestLimit = requestLimit;
        this.semaphore = new Semaphore(requestLimit);
        this.intervalStart = System.currentTimeMillis();
        this.objectMapper = new ObjectMapper();

        this.objectMapper.registerModule(new JavaTimeModule());

        //Старт монитора проверки интервала
        Thread intervalMonitor = new Thread(this::monitorInterval);
        intervalMonitor.setDaemon(true);
        intervalMonitor.start();
    }

    public void createDocument(String accessToken, Document document) throws IOException, InterruptedException {
        System.out.println("time " + Thread.currentThread().getName());
        semaphore.acquire();
        try {
            String requestBody = objectMapper.writeValueAsString(document);

            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create("https://ismp.crpt.ru/api/v3/lk/documents/create"))
                    .header("Content-Type", "application/json")
                    .header("Authorization", "Bearer " + accessToken)
                    .POST(HttpRequest.BodyPublishers.ofString(requestBody))
                    .build();

            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());

            if (response.statusCode() != 200) {
                System.err.println("Failed to create document: " + response.statusCode() + " - " + response.body());
            } else {
                System.out.println("Document created successfully");
            }
        } finally {
            semaphore.release();

        }
    }

    /**
     * Метод для проверки интервала, в течение которого
     * действует ограничение @timeUnit
     */
    private void monitorInterval() {
        while (true) {
            long currentTime = System.currentTimeMillis();
            long elapsedTime = currentTime - intervalStart;
            if (elapsedTime >= timeUnit.toMillis()) {
                semaphore.release(requestLimit);
                intervalStart = currentTime;
            }
            try {
                TimeUnit.SECONDS.sleep(1);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }
    }

    @Data
    static class Document {
        private String participantInn;
        private String docId;
        private String docStatus;
        private String docType;
        private boolean importRequest;
        private String ownerInn;
        private String producerInn;
        private LocalDate productionDate;
        private String productionType;
        private List<Product> products;
        private LocalDate regDate;
        private String regNumber;

    }

    @Data
    static class Product {
        private String certificateDocument;
        private LocalDate certificateDocumentDate;
        private String certificateDocumentNumber;
        private String ownerInn;
        private String producerInn;
        private LocalDate productionDate;
        private String tnvedCode;
        private String uitCode;
        private String uituCode;

    }

    public static void main(String[] args) {
        CrptApi crptApi = new CrptApi(Duration.ofSeconds(2), 3);

        for (int i = 0; i < 50; i++) {
            Thread thread = new Thread(() -> {
                try {
                    crptApi.createDocument("testAccessToken", new CrptApi.Document());
                    System.out.println("Document created by thread: " + Thread.currentThread().getName());
                } catch (IOException | InterruptedException e) {
                    System.err.println("Exception occurred: " + e.getMessage());
                }
            });
            thread.start();
        }
    }
}

