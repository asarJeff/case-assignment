package com.asar.caseassignment.service;

import java.util.*;
import org.springframework.stereotype.Service;

@Service
public class AgentDirectoryService {

    private final EmployeeQueryService employeeQueryService;

    public AgentDirectoryService(EmployeeQueryService employeeQueryService) {
        this.employeeQueryService = employeeQueryService;
    }

    /**
     * Returns all employees grouped by organizationalUnitId.
     */
    public Map<String, List<Map<String, Object>>> getEmployeesGroupedByOrgUnit(int top) {
        List<Map<String, Object>> all = employeeQueryService.getAllEmployees(top);

        Map<String, List<Map<String, Object>>> grouped = new HashMap<>();
        for (Map<String, Object> e : all) {
            String ou = str(e.get("organizationalUnitId"));
            if (ou == null || ou.isBlank()) continue;
            grouped.computeIfAbsent(ou, k -> new ArrayList<>()).add(e);
        }
        return grouped;
    }

    private String str(Object o) { return o == null ? null : o.toString(); }
}