package com.bbot.sandbox.utils;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.tomakehurst.wiremock.common.FileSource;
import com.github.tomakehurst.wiremock.extension.Parameters;
import com.github.tomakehurst.wiremock.extension.ResponseDefinitionTransformer;
import com.github.tomakehurst.wiremock.http.Request;
import com.github.tomakehurst.wiremock.http.ResponseDefinition;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

import static com.github.tomakehurst.wiremock.client.ResponseDefinitionBuilder.responseDefinition;

/**
 * WireMock {@link ResponseDefinitionTransformer} that gives the blotter API real
 * in-memory state: POST /api/inquiry stores a new inquiry; GET /api/inquiries
 * returns seed data plus any dynamically submitted inquiries.
 *
 * <p>This makes it possible to submit portfolios via REST in a {@code Given} step
 * and then observe the submitted rows in the blotter grid when the browser opens,
 * because the React app fetches {@code GET /api/inquiries} on load.
 *
 * <h2>Transformer actions</h2>
 * WireMock stubs opt in by declaring {@code withTransformers(NAME)} and a
 * {@code withTransformerParameter("action", "submit"|"list")} on the stub:
 * <ul>
 *   <li>{@code "submit"} — parses the POST body, generates an inquiry ID
 *       ({@code DYN-NNNN}), stores the inquiry, returns 201 PENDING.</li>
 *   <li>{@code "list"}   — returns all stored inquiries (seed + dynamic) as a
 *       JSON array.</li>
 * </ul>
 *
 * <h2>Lifecycle</h2>
 * Call {@link #resetAndSeed(List)} from {@link MockBlotterServer#start(int)} to
 * initialise the store with seed rows; subsequent POST calls append to it.
 * The store is static so it persists across the WireMock server's lifetime —
 * reset explicitly between test runs if needed.
 */
public final class InquiryStoreTransformer extends ResponseDefinitionTransformer {

    /** WireMock transformer name — must match {@code withTransformers(NAME)}. */
    public static final String NAME = "inquiry-store";

    private static final ObjectMapper MAPPER = new ObjectMapper();

    /**
     * In-memory inquiry store: inquiry_id → full inquiry map.
     * {@link LinkedHashMap} wrapped in {@link Collections#synchronizedMap} preserves
     * insertion order (seed rows first, then dynamic rows in submission order).
     */
    private static final Map<String, Map<String, Object>> STORE =
            Collections.synchronizedMap(new LinkedHashMap<>());

    private static final AtomicInteger SEQ = new AtomicInteger(0);

    // ── Lifecycle (static, called from MockBlotterServer) ─────────────────────

    /**
     * Clears the store and re-populates it with the given seed inquiries.
     * Call once from {@link MockBlotterServer#start(int)} before tests run.
     */
    public static void resetAndSeed(List<Map<String, Object>> seedInquiries) {
        STORE.clear();
        SEQ.set(0);
        seedInquiries.forEach(i -> STORE.put(i.get("inquiry_id").toString(), i));
    }

    // ── WireMock extension ────────────────────────────────────────────────────

    @Override
    public String getName() {
        return NAME;
    }

    @Override
    public boolean applyGlobally() {
        return false;   // only applied to stubs that explicitly declare this transformer
    }

    @Override
    public ResponseDefinition transform(Request request, ResponseDefinition responseDefinition,
                                        FileSource files, Parameters parameters) {
        String action = parameters != null ? parameters.getString("action", "") : "";

        return switch (action) {
            case "submit"           -> handleSubmit(request.getBodyAsString());
            case "list"             -> handleList();
            case "dealer-cancel"    -> handlePtCancel(request, "DEALER_REJECT");
            case "customer-cancel"  -> handlePtCancel(request, "CUSTOMER_REJECT");
            default                 -> responseDefinition;   // pass-through for unknown action
        };
    }

    // ── Internal ──────────────────────────────────────────────────────────────

    @SuppressWarnings("unchecked")
    private ResponseDefinition handleSubmit(String requestBody) {
        try {
            Map<String, Object> body = MAPPER.readValue(requestBody, Map.class);

            /* Generate a unique deterministic inquiry ID */
            String id = String.format("DYN-%04d", SEQ.incrementAndGet());

            /* Extract submitted fields; supply sensible defaults */
            String isin    = str(body, "isin",    "UNKNOWN");
            String ptId    = str(body, "pt_id",   str(body, "ptId", "PT_DYN"));
            String ptLineId = ptId + "_DYN_" + SEQ.get();

            /* Build the stored inquiry record */
            Map<String, Object> inquiry = new LinkedHashMap<>();
            inquiry.put("inquiry_id", id);
            inquiry.put("isin",       isin);
            inquiry.put("status",     "PENDING");
            inquiry.put("pt_id",      ptId);
            inquiry.put("pt_line_id", ptLineId);
            /* Carry through any extra fields from the request body */
            body.forEach(inquiry::putIfAbsent);

            STORE.put(id, inquiry);

            String response = MAPPER.writeValueAsString(
                    Map.of("inquiry_id", id, "status", "PENDING"));

            return responseDefinition()
                    .withStatus(201)
                    .withHeader("Content-Type", "application/json")
                    .withBody(response)
                    .build();

        } catch (Exception e) {
            return responseDefinition()
                    .withStatus(400)
                    .withBody("Bad inquiry request: " + e.getMessage())
                    .build();
        }
    }

    private ResponseDefinition handleList() {
        try {
            List<Map<String, Object>> all;
            synchronized (STORE) {
                all = new ArrayList<>(STORE.values());
            }
            return responseDefinition()
                    .withStatus(200)
                    .withHeader("Content-Type", "application/json")
                    .withBody(MAPPER.writeValueAsString(all))
                    .build();
        } catch (JsonProcessingException e) {
            return responseDefinition()
                    .withStatus(500)
                    .withBody("Error serialising inquiry list: " + e.getMessage())
                    .build();
        }
    }

    /**
     * Cancels all line items belonging to a PT, setting their status to {@code targetStatus}.
     *
     * <p>URL pattern: {@code /api/pt/{ptId}/dealer-cancel} or {@code /api/pt/{ptId}/customer-cancel}.
     * The {@code ptId} segment is extracted from the request URL.
     *
     * <p>Response: {@code {"pt_id":"…","status":"DEALER_REJECT","affected_count":N}}
     */
    private ResponseDefinition handlePtCancel(Request request, String targetStatus) {
        String url = request.getUrl();
        // URL: /api/pt/{ptId}/dealer-cancel  →  ["", "api", "pt", ptId, "dealer-cancel"]
        String[] parts = url.split("/");
        if (parts.length < 5) {
            return responseDefinition()
                    .withStatus(400)
                    .withBody("Invalid PT cancel URL: " + url)
                    .build();
        }
        String ptId = parts[3];

        int count = 0;
        synchronized (STORE) {
            for (Map<String, Object> inquiry : STORE.values()) {
                if (ptId.equals(inquiry.get("pt_id"))) {
                    inquiry.put("status", targetStatus);
                    count++;
                }
            }
        }

        try {
            /* Use a LinkedHashMap to preserve key order in the JSON response */
            Map<String, Object> resp = new LinkedHashMap<>();
            resp.put("pt_id",          ptId);
            resp.put("status",         targetStatus);
            resp.put("affected_count", count);
            return responseDefinition()
                    .withStatus(200)
                    .withHeader("Content-Type", "application/json")
                    .withBody(MAPPER.writeValueAsString(resp))
                    .build();
        } catch (JsonProcessingException e) {
            return responseDefinition()
                    .withStatus(500)
                    .withBody("Error serialising cancel response: " + e.getMessage())
                    .build();
        }
    }

    private static String str(Map<String, Object> map, String key, String defaultValue) {
        Object v = map.get(key);
        return v != null ? v.toString() : defaultValue;
    }
}
