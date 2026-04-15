package com.asar.caseassignment.service;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.springframework.stereotype.Service;

@Service
public class WorkloadService {

    private final CaseQueryService caseQueryService;

    public WorkloadService(CaseQueryService caseQueryService) {
        this.caseQueryService = caseQueryService;
    }

    /**
     * ORIGINAL METHOD — kept for backward compatibility with any other callers.
     * Fetches cases itself. If you know AssignmentService is the only caller,
     * you can delete this and use buildWorkloadFromCases() everywhere.
     */
    public Map<String, Integer> buildWorkloadByEmployeeId() {
        List<Map<String, Object>> candidates = caseQueryService.getCandidateCases();
        return buildWorkloadFromCases(candidates);
    }

    /**
     * NEW METHOD — accepts an already-fetched candidate list.
     * Use this when the caller has already fetched cases to avoid a redundant GET /cases call.
     * AssignmentService now calls this instead of buildWorkloadByEmployeeId().
     */
    public Map<String, Integer> buildWorkloadFromCases(List<Map<String, Object>> candidates) {
        Map<String, Integer> counts = new HashMap<>();

        for (Map<String, Object> c : candidates) {
            Object procObj = c.get("processor");
            if (!(procObj instanceof Map<?, ?> p)) continue;

            Object idObj = p.get("id");
            if (idObj == null) continue;

            String processorId = String.valueOf(idObj).trim();
            if (processorId.isBlank()) continue;

            counts.put(processorId, counts.getOrDefault(processorId, 0) + 1);
        }

        return counts;
    }
}