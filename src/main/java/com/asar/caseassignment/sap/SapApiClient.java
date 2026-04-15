package com.asar.caseassignment.sap;

import java.util.List;
import java.util.Map;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.util.UriComponentsBuilder;

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

    @SuppressWarnings("unchecked")
    public Map<String, Object> get(String path, Map<String, String> queryParams) {
        String url = buildUrl(path, queryParams);

        System.out.println("SAP GET URL: " + url);
        System.out.println("SAP GET PARAMS: " + queryParams);

        HttpEntity<Void> entity = new HttpEntity<>(defaultHeaders());
        ResponseEntity<Map> resp = restTemplate.exchange(url, HttpMethod.GET, entity, Map.class);
        return (Map<String, Object>) resp.getBody();
    }

    public Map<String, Object> get(String path) {
        return get(path, Map.of());
    }

    @SuppressWarnings("unchecked")
    public EtagResponse getWithEtag(String path, Map<String, String> queryParams) {
        String url = buildUrl(path, queryParams);

        System.out.println("SAP GET (ETAG) URL: " + url);
        System.out.println("SAP GET (ETAG) PARAMS: " + queryParams);

        HttpEntity<Void> entity = new HttpEntity<>(defaultHeaders());
        ResponseEntity<Map> resp = restTemplate.exchange(url, HttpMethod.GET, entity, Map.class);

        String etag = resp.getHeaders().getETag();
        Map<String, Object> body = (Map<String, Object>) resp.getBody();
        return new EtagResponse(etag, body);
    }

    public EtagResponse getWithEtag(String path) {
        return getWithEtag(path, Map.of());
    }

    public void patchWithIfMatch(String path, String etag, Map<String, Object> payload) {
        String url = buildUrl(path, Map.of());

        System.out.println("SAP PATCH URL: " + url);
        System.out.println("SAP PATCH ETAG: " + etag);
        System.out.println("SAP PATCH PAYLOAD: " + payload);

        HttpHeaders h = defaultHeaders();
        if (etag != null && !etag.isBlank()) {
            h.setIfMatch(etag);
        }

        HttpEntity<Map<String, Object>> entity = new HttpEntity<>(payload, h);
        restTemplate.exchange(url, HttpMethod.PATCH, entity, Map.class);
    }

    private HttpHeaders defaultHeaders() {
        HttpHeaders h = new HttpHeaders();
        h.setAccept(List.of(MediaType.APPLICATION_JSON));
        h.setContentType(MediaType.APPLICATION_JSON);
        h.setBasicAuth(username, password);
        return h;
    }

    private String buildUrl(String path, Map<String, String> queryParams) {
        UriComponentsBuilder builder = UriComponentsBuilder
                .fromHttpUrl(baseUrl + path);

        if (queryParams != null) {
            for (Map.Entry<String, String> e : queryParams.entrySet()) {
                String key = e.getKey();
                String val = e.getValue();
                if (key != null && val != null) {
                    builder.queryParam(key, val);
                }
            }
        }

        return builder.build(false).toUriString();
    }

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