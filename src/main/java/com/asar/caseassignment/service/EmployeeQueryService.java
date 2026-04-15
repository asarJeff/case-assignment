package com.asar.caseassignment.service;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import com.asar.caseassignment.sap.SapApiClient;

@Service
public class EmployeeQueryService {

    private final SapApiClient sap;
    private final int defaultPageSize;

    public EmployeeQueryService(
            SapApiClient sap,
            @Value("${sap.pageSize:200}") int defaultPageSize
    ) {
        this.sap = sap;
        this.defaultPageSize = defaultPageSize;
    }

    /**
     * Used by AgentDirectoryService (keeps old signature alive).
     * NO $filter to avoid "Invalid operator" errors.
     */
    @SuppressWarnings("unchecked")
    public List<Map<String, Object>> getAllEmployees(int top) {
        int useTop = top > 0 ? top : defaultPageSize;

        Map<String, String> qp = new LinkedHashMap<>();
        qp.put("$top", String.valueOf(useTop));

        Map<String, Object> body = sap.get("/sap/c4c/api/v1/employee-service/employees", qp);

        Object value = body.get("value");
        if (value instanceof List<?> list) {
            return (List<Map<String, Object>>) (List<?>) list;
        }
        return List.of();
    }

    /**
     * Your main method used by AssignmentService.
     * Pull a page and filter locally (ACTIVE + org unit match).
     */
    public List<Map<String, Object>> getEmployeesByOrgUnitId(String orgUnitId) {
        List<Map<String, Object>> all = getAllEmployees(defaultPageSize);

        List<Map<String, Object>> out = new ArrayList<>();
        for (Map<String, Object> e : all) {

            String eOrgUnitId = str(e.get("organizationalUnitId"));
            if (!orgUnitId.equals(eOrgUnitId)) continue;

            String lcs = str(e.get("lifeCycleStatus"));
            if (!"ACTIVE".equalsIgnoreCase(lcs)) continue;

            out.add(e);
        }
        return out;
    }

    /**
     * Used by CaseAssignmentService (keeps old signature alive).
     * Groups ACTIVE employees by organizationalUnitId.
     */
    public Map<String, List<Map<String, Object>>> getEmployeesGroupedByOrgUnitId() {
        List<Map<String, Object>> all = getAllEmployees(defaultPageSize);

        Map<String, List<Map<String, Object>>> grouped = new LinkedHashMap<>();

        for (Map<String, Object> e : all) {
            String lcs = str(e.get("lifeCycleStatus"));
            if (!"ACTIVE".equalsIgnoreCase(lcs)) continue;

            String orgUnitId = str(e.get("organizationalUnitId"));
            if (orgUnitId.isBlank()) continue;

            grouped.computeIfAbsent(orgUnitId, k -> new ArrayList<>()).add(e);
        }

        return grouped;
    }

    private String str(Object o) {
        return o == null ? "" : String.valueOf(o).trim();
    }
}