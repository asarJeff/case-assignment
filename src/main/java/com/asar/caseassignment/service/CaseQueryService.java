package com.asar.caseassignment.service;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import com.asar.caseassignment.sap.SapApiClient;

@Service
public class CaseQueryService {

    private final SapApiClient sap;
    private final Set<String> openLikeStatuses;
    private final Set<String> agentAssignmentEventCodes;
    private final int pageSize;

    public CaseQueryService(
            SapApiClient sap,
            @Value("${sap.openLikeStatuses}") String openLikeStatusesCsv,
            @Value("${sap.agentAssignmentEventCodes:51005,51103,51104,51105,51107,51108}") String agentEventCodesCsv,
            @Value("${sap.pageSize:200}") int pageSize
    ) {
        this.sap = sap;

        this.openLikeStatuses = new HashSet<>();
        for (String s : openLikeStatusesCsv.split(",")) {
            String t = s.trim();
            if (!t.isBlank()) {
                this.openLikeStatuses.add(t);
            }
        }

        this.agentAssignmentEventCodes = new HashSet<>();
        for (String s : agentEventCodesCsv.split(",")) {
            String t = s.trim();
            if (!t.isBlank()) {
                this.agentAssignmentEventCodes.add(t);
            }
        }

        this.pageSize = pageSize;

        System.out.println("Loaded openLikeStatuses = " + this.openLikeStatuses);
        System.out.println("Loaded agentAssignmentEventCodes = " + this.agentAssignmentEventCodes);
        System.out.println("Loaded pageSize = " + this.pageSize);
    }

    public List<Map<String, Object>> getAllCases() {
        List<Map<String, Object>> allCases = new ArrayList<>();
        int skip = 0;
        int batchSize = pageSize;

        String statusFilter = buildStatusFilter();
        System.out.println("CaseQueryService: fetching cases with SAP filter: " + statusFilter);

        while (true) {
            Map<String, String> qp = new LinkedHashMap<>();
            qp.put("$top", String.valueOf(batchSize));
            qp.put("$skip", String.valueOf(skip));
            qp.put("$filter", statusFilter);

            Map<String, Object> body = sap.get("/sap/c4c/api/v1/case-service/cases", qp);
            Object value = body != null ? body.get("value") : null;

            if (!(value instanceof List<?> list) || list.isEmpty()) {
                break;
            }

            @SuppressWarnings("unchecked")
            List<Map<String, Object>> page = (List<Map<String, Object>>) (List<?>) list;
            allCases.addAll(page);

            System.out.println("CaseQueryService: fetched page skip=" + skip
                    + " size=" + page.size()
                    + " total=" + allCases.size());

            if (page.size() < batchSize) {
                break;
            }

            skip += batchSize;

            if (skip >= 10000) {
                System.out.println("CaseQueryService: WARNING - hit SAP 10k $skip limit. Total fetched so far: "
                        + allCases.size());
                break;
            }
        }

        return allCases;
    }

    private String buildStatusFilter() {
        return "status eq '01' or "
                + "status eq 'Z1' or "
                + "status eq 'Z2' or "
                + "status eq 'Z3' or "
                + "status eq 'Z6' or "
                + "status eq 'Z7' or "
                + "status eq 'Z8'";
    }

    public List<Map<String, Object>> getCandidateCases() {
        List<Map<String, Object>> all = getAllCases();
        List<Map<String, Object>> out = new ArrayList<>();

        for (Map<String, Object> c : all) {
            String caseType = str(c.get("caseType"));
            if ("ZCLM".equals(caseType)) {
                continue;
            }

            String status = str(c.get("status"));
            if (!openLikeStatuses.contains(status)) {
                continue;
            }

            String lcs = str(c.get("lifeCycleStatus"));
            if (!lcs.isBlank() && !(lcs.equals("OPEN") || lcs.equals("IN_PROCESS"))) {
                continue;
            }

            String lastEvent = getLastTrackingEvent(c);
            if (!lastEvent.isBlank() && !agentAssignmentEventCodes.contains(lastEvent)) {
                System.out.println("Skipping case " + str(c.get("displayId"))
                        + " — LastTrackingEvent=" + lastEvent + " does not require agent assignment.");
                continue;
            }

            out.add(c);
        }

        return out;
    }

    public List<Map<String, Object>> getUnassignedOpenOrInProcessCases() {
        List<Map<String, Object>> candidates = getCandidateCases();
        List<Map<String, Object>> out = new ArrayList<>();

        for (Map<String, Object> c : candidates) {
            Map<String, Object> proc = safeMap(c.get("processor"));
            String pid = proc != null ? str(proc.get("id")) : "";
            if (pid.isBlank()) {
                out.add(c);
            }
        }

        return out;
    }

    @SuppressWarnings("unchecked")
    private String getLastTrackingEvent(Map<String, Object> caseMap) {
        Object ext = caseMap.get("extensions");
        if (!(ext instanceof Map<?, ?> extMap)) {
            return "";
        }

        Object val = ((Map<String, Object>) extMap).get("LastTrackingEvent");
        return str(val).trim();
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> safeMap(Object o) {
        if (o instanceof Map<?, ?> m) {
            return (Map<String, Object>) m;
        }
        return null;
    }

    private String str(Object o) {
        return o == null ? "" : String.valueOf(o);
    }
}