package com.asar.caseassignment.service;

import java.util.*;
import org.springframework.stereotype.Service;
import com.asar.caseassignment.sap.SapApiClient;

@Service
public class OrgUnitService {

    private final SapApiClient sap;

    public OrgUnitService(SapApiClient sap) {
        this.sap = sap;
    }

    /**
     * Fetches all org units and builds a map of orgUnitId -> list of employees
     * using employeeAssignments (includes both primary and secondary assignments).
     */
    @SuppressWarnings("unchecked")
    public Map<String, List<Map<String, Object>>> getEmployeesByOrgUnitId() {
        Map<String, List<Map<String, Object>>> result = new LinkedHashMap<>();

        List<Map<String, Object>> orgUnits = fetchAllOrgUnits();
        System.out.println("OrgUnitService: loaded " + orgUnits.size() + " org units");

        for (Map<String, Object> org : orgUnits) {
            String orgId = str(org.get("id"));
            String orgDisplayId = str(org.get("displayId"));
            if (orgId.isBlank()) continue;

            Object assignmentsObj = org.get("employeeAssignments");
            if (!(assignmentsObj instanceof List<?> assignments)) continue;

            List<Map<String, Object>> members = new ArrayList<>();
            for (Object a : assignments) {
                if (!(a instanceof Map<?, ?> assignment)) continue;
                Map<String, Object> assign = (Map<String, Object>) assignment;

                // Filter to active assignments only
                String validTo = str(assign.get("validTo"));
                if (!validTo.isBlank() && validTo.compareTo("2026") < 0) continue;

                // Skip managers — only assign to employees (role 222 = Employee)
                String role = str(assign.get("role"));
                if (role.equals("218") || role.equals("210")) continue;

                String empId = str(assign.get("employeeId"));
                String empName = str(assign.get("employeeName"));
                String empDisplayId = str(assign.get("employeeDisplayId"));
                if (empId.isBlank()) continue;

                Map<String, Object> emp = new LinkedHashMap<>();
                emp.put("id", empId);
                emp.put("formattedName", empName);
                emp.put("displayId", empDisplayId);
                emp.put("organizationalUnitId", orgId);
                emp.put("organizationalUnitDisplayId", orgDisplayId);
                members.add(emp);
            }

            if (!members.isEmpty()) {
                result.put(orgId, members);
            }
        }

        System.out.println("OrgUnitService: built map for " + result.size() + " org units with employees");
        return result;
    }

    @SuppressWarnings("unchecked")
    private List<Map<String, Object>> fetchAllOrgUnits() {
        List<Map<String, Object>> all = new ArrayList<>();
        int skip = 0;
        int batchSize = 200;

        while (true) {
            Map<String, String> qp = new LinkedHashMap<>();
            qp.put("$top", String.valueOf(batchSize));
            qp.put("$skip", String.valueOf(skip));

            Map<String, Object> body = sap.get(
                "/sap/c4c/api/v1/organizational-unit-service/organizationalUnits", qp);
            Object value = body != null ? body.get("value") : null;
            if (!(value instanceof List<?> list) || list.isEmpty()) break;

            List<Map<String, Object>> page = (List<Map<String, Object>>) (List<?>) list;
            all.addAll(page);
            if (page.size() < batchSize) break;
            skip += batchSize;
        }

        return all;
    }

    private String str(Object o) {
        return o == null ? "" : String.valueOf(o).trim();
    }
}
