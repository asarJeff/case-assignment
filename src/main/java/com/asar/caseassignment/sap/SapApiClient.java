package com.asar.caseassignment.sap;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;

@Component
public class SapApiClient {

    private final RestTemplate restTemplate;
    private final String baseUrl;
    private final String username;
    private final String password;

    public SapApiClient(
            RestTemplate sapRestTemplate,
            @Value("${sap.baseUrl}") String baseUrl,
            @Value("${sap.username}") String username,
            @Value("${sap.password}") String password
    ) {
        this.restTemplate = sapRestTemplate;
        this.baseUrl = stripTrailingSlash(baseUrl);
        this.username = stripQuotes(username);
        this.password = stripQuotes(password);
    }

    // ----------------------------
    // GET
    // ----------------------------
    @SuppressWarnings("unchecked")
    public Map<String, Object> get(String path, Map<String, String> queryParams) {
        String url = buildUrl(path, queryParams);

        // (optional) helpful debug:
        // System.out.println("Calling SAP: " + url);
        // System.out.println("SAP queryParams: " + queryParams);

        HttpEntity<Void> entity = new HttpEntity<>(defaultHeaders());
        ResponseEntity<Map> resp = restTemplate.exchange(url, HttpMethod.GET, entity, Map.class);
        return (Map<String, Object>) resp.getBody();
    }

    public Map<String, Object> get(String path) {
        return get(path, Map.of());
    }

    // ----------------------------
    // GET with ETag
    // ----------------------------
    @SuppressWarnings("unchecked")
    public EtagResponse getWithEtag(String path, Map<String, String> queryParams) {
        String url = buildUrl(path, queryParams);

        // (optional) helpful debug:
        // System.out.println("Calling SAP (ETag): " + url);
        // System.out.println("SAP queryParams: " + queryParams);

        HttpEntity<Void> entity = new HttpEntity<>(defaultHeaders());
        ResponseEntity<Map> resp = restTemplate.exchange(url, HttpMethod.GET, entity, Map.class);

        String etag = resp.getHeaders().getETag(); // may include quotes
        Map<String, Object> body = (Map<String, Object>) resp.getBody();
        return new EtagResponse(etag, body);
    }

    public EtagResponse getWithEtag(String path) {
        return getWithEtag(path, Map.of());
    }

    // ----------------------------
    // PATCH with If-Match
    // ----------------------------
    public void patchWithIfMatch(String path, String etag, Map<String, Object> payload) {
        String url = buildUrl(path, Map.of());

        HttpHeaders h = defaultHeaders();
        if (etag != null && !etag.isBlank()) {
            h.setIfMatch(etag);
        }

        HttpEntity<Map<String, Object>> entity = new HttpEntity<>(payload, h);
        restTemplate.exchange(url, HttpMethod.PATCH, entity, Map.class);
    }

    // ----------------------------
    // Headers
    // ----------------------------
    private HttpHeaders defaultHeaders() {
        HttpHeaders h = new HttpHeaders();
        h.setAccept(List.of(MediaType.APPLICATION_JSON));
        h.setContentType(MediaType.APPLICATION_JSON);
        h.setBasicAuth(username, password);
        return h;
    }

    // ----------------------------
    // URL building (CRITICAL FIX)
    // ----------------------------
    private String buildUrl(String path, Map<String, String> queryParams) {
        StringBuilder url = new StringBuilder();
        url.append(baseUrl).append(path);

        if (queryParams == null || queryParams.isEmpty()) {
            return url.toString();
        }

        boolean first = true;
        for (Map.Entry<String, String> e : queryParams.entrySet()) {
            String key = e.getKey();
            String val = e.getValue();
            if (key == null || val == null) continue;

            url.append(first ? "?" : "&");
            first = false;
            url.append(key).append("=").append(encodeValue(val));
        }

        return url.toString();
    }

    private static String encodeValue(String s) {
        StringBuilder result = new StringBuilder();
        for (char c : s.toCharArray()) {
            if (c == ' ') result.append("%20");
            else if (c == '+') result.append("%2B");
            else result.append(c);
        }
        return result.toString();
    }
   
    // ----------------------------
    // Helpers
    // ----------------------------
    private static String stripTrailingSlash(String s) {
        if (s == null) return null;
        return s.endsWith("/") ? s.substring(0, s.length() - 1) : s;
    }

    private static String stripQuotes(String s) {
        if (s == null) return null;
        String t = s.trim();
        if (t.startsWith("\"") && t.endsWith("\"") && t.length() >= 2) {
            return t.substring(1, t.length() - 1);
        }
        return t;
    }

    public record EtagResponse(String etag, Map<String, Object> body) {}
}