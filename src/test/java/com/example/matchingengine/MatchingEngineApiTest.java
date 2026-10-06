package com.example.matchingengine;

import com.example.matchingengine.dto.CancelRequest;
import com.example.matchingengine.dto.OrderBookSnapshot;
import com.example.matchingengine.dto.OrderRequest;
import com.example.matchingengine.dto.OrderResponse;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.reactive.AutoConfigureWebTestClient;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.reactive.server.WebTestClient;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Integration tests for the REST API layer.
 *
 * <p>Uses the {@code test} profile which:
 * <ul>
 *   <li>Uses H2 in-memory R2DBC</li>
 *   <li>Disables Kafka and Redis auto-configuration</li>
 * </ul>
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureWebTestClient
@ActiveProfiles("test")
class MatchingEngineApiTest {

    @Autowired
    WebTestClient webTestClient;

    // ────────────────────── Submit ──────────────────────

    @Test
    @DisplayName("POST /api/orders → 200 + order response")
    void submitOrder() {
        var request = new OrderRequest("AAPL", "BUY", "LIMIT", 15000, 100);

        webTestClient.post().uri("/api/orders")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(request)
                .exchange()
                .expectStatus().isOk()
                .expectBody(OrderResponse.class)
                .value(resp -> {
                    assertEquals("AAPL", resp.symbol());
                    assertEquals("BUY", resp.side());
                    assertEquals(15000, resp.price());
                    assertEquals(100, resp.quantity());
                    assertTrue(resp.trades().isEmpty());
                });
    }

    @Test
    @DisplayName("Submit BUY + SELL at same price → trade in response")
    void submitMatchingOrders() {
        // First order: rests in book
        webTestClient.post().uri("/api/orders")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(new OrderRequest("MSFT", "BUY", "LIMIT", 20000, 50))
                .exchange()
                .expectStatus().isOk();

        // Second order: should match
        webTestClient.post().uri("/api/orders")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(new OrderRequest("MSFT", "SELL", "LIMIT", 20000, 30))
                .exchange()
                .expectStatus().isOk()
                .expectBody(OrderResponse.class)
                .value(resp -> {
                    assertFalse(resp.trades().isEmpty());
                    assertEquals(1, resp.trades().size());
                    assertEquals(30, resp.trades().get(0).quantity());
                    assertEquals(20000, resp.trades().get(0).price());
                });
    }

    // ────────────────────── Order Book ──────────────────────

    @Test
    @DisplayName("GET /api/orderbook/{symbol} → snapshot")
    void getOrderBook() {
        // Seed some orders
        webTestClient.post().uri("/api/orders")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(new OrderRequest("TSLA", "BUY", "LIMIT", 25000, 100))
                .exchange().expectStatus().isOk();

        webTestClient.post().uri("/api/orders")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(new OrderRequest("TSLA", "SELL", "LIMIT", 26000, 80))
                .exchange().expectStatus().isOk();

        // Get snapshot
        webTestClient.get().uri("/api/orderbook/TSLA")
                .exchange()
                .expectStatus().isOk()
                .expectBody(OrderBookSnapshot.class)
                .value(snap -> {
                    assertEquals("TSLA", snap.symbol());
                    assertFalse(snap.bids().isEmpty());
                    assertFalse(snap.asks().isEmpty());
                    assertEquals(25000, snap.bids().get(0).price());
                    assertEquals(26000, snap.asks().get(0).price());
                });
    }

    @Test
    @DisplayName("GET /api/orderbook/{symbol} — empty book returns empty lists")
    void getEmptyOrderBook() {
        webTestClient.get().uri("/api/orderbook/UNKNOWN")
                .exchange()
                .expectStatus().isOk()
                .expectBody(OrderBookSnapshot.class)
                .value(snap -> {
                    assertEquals("UNKNOWN", snap.symbol());
                    assertTrue(snap.bids().isEmpty());
                    assertTrue(snap.asks().isEmpty());
                });
    }

    // ────────────────────── Cancel ──────────────────────

    @Test
    @DisplayName("DELETE /api/orders — cancel non-existent returns 404")
    void cancelNonExistent() {
        webTestClient.method(org.springframework.http.HttpMethod.DELETE)
                .uri("/api/orders")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(new CancelRequest("AAPL", 99999, "BUY"))
                .exchange()
                .expectStatus().isNotFound();
    }

    // ────────────────────── Validation ──────────────────────

    @Test
    @DisplayName("POST /api/orders — invalid price returns 400")
    void invalidPriceReturns400() {
        webTestClient.post().uri("/api/orders")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(new OrderRequest("AAPL", "BUY", "LIMIT", -100, 100))
                .exchange()
                .expectStatus().isBadRequest();
    }
}
