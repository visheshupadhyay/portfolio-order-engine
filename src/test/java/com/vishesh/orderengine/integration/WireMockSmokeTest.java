package com.vishesh.orderengine.integration;

import org.junit.jupiter.api.Test;

import com.github.tomakehurst.wiremock.junit5.WireMockRuntimeInfo;
import com.github.tomakehurst.wiremock.junit5.WireMockTest;

import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.ok;
import static com.github.tomakehurst.wiremock.client.WireMock.stubFor;
import static org.junit.jupiter.api.Assertions.assertEquals;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;

@WireMockTest
public class WireMockSmokeTest {

    @Test
    public void test(WireMockRuntimeInfo wireMockRuntimeInfo) throws Exception {

        // Stub GET /provider/health
        stubFor(get("/provider/health")
                .willReturn(ok("provider is available")));

        String url = "http://localhost:" + wireMockRuntimeInfo.getHttpPort() + "/provider/health";

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .GET()
                .build();

        HttpResponse<String> response = HttpClient.newHttpClient()
                .send(request, HttpResponse.BodyHandlers.ofString());

        assertEquals(200, response.statusCode());
        assertEquals("provider is available", response.body());

    }
}
