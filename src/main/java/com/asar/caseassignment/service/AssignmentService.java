package com.asar.caseassignment.service;

import java.util.*;

import org.springframework.stereotype.Service;

import com.asar.caseassignment.sap.SapApiClient;
import com.asar.caseassignment.service.OrgUnitService;

@Service
public class AssignmentService {

    private final CaseQueryService caseQueryService;
    private final EmployeeQueryService employeeQueryService;
    private final WorkloadService workloadService;
    private final SapApiClient sap;
    private final OrgUnitService orgUnitService;

    public AssignmentService(
            CaseQueryService caseQueryService,
            EmployeeQueryService employeeQueryService,
            WorkloadService workloadService,
            SapApiClient sap,
            OrgUnitService orgUnitService
    ) {
        this.caseQueryService = caseQueryService;
        this.employeeQueryService = employeeQueryService;
        this.workloadService = workloadService;
        this.sap = sap;
        this.orgUnitService = orgUnitService;
    }

    public List<Map<String, Object>> assignUnassignedCasesFairly() {

        List<Map<String, Object>> candidateCases = caseQueryService.getCandidateCases();
        List<Map<String, Object>> unassigned = filterUnassigned(candidateCases);

        Map<String, Integer> workload = workloadService.buildWorkloadFromCases(candidateCases);

        Map<String, List<Map<String, Object>>> employeesByTeam =
                orgUnitService.getEmployeesByOrgUnitId();

        // Build a set of all manager IDs across all teams so we can exclude them
        Set<String> managerIds = buildManagerIdSet(employeesByTeam);

        List<Map<String, Object>> results = new ArrayList<>();

        for (Map<String, Object> c : unassigned) {

            String caseId = str(c.get("id"));
            String displayId = str(c.get("displayId"));

            try {
                SapApiClient.EtagResponse etagResponse =
                        sap.getWithEtag("/sap/c4c/api/v1/case-service/cases/" + caseId);

                String etag = etagResponse.etag();

                Map<String, Object> fullCase = etagResponse.body();
                Map<String, Object> caseBody = unwrapValue(fullCase);

                Map<String, Object> serviceTeam = safeMap(caseBody != null
                        ? caseBody.get("serviceTeam")
                        : null);

                String teamId = serviceTeam == null ? "" : str(serviceTeam.get("id"));
                String teamDisplayId = serviceTeam == null ? "" : str(serviceTeam.get("displayId"));

                if (teamId.isBlank()) {
                    results.add(result(displayId, caseId, "", "SKIP: Missing serviceTeam.id (individual GET)"));
                    continue;
                }

                List<Map<String, Object>> teamMembers = employeesByTeam.getOrDefault(teamId, List.of());

                if (teamMembers.isEmpty()) {
                    results.add(result(displayId, caseId, "", "SKIP: No employees for team " + teamDisplayId + " (id=" + teamId + ")"));
                    continue;
                }

                // Filter out managers — only assign to non-manager agents
                List<Map<String, Object>> assignableMembers = teamMembers.stream()
                        .filter(m -> !managerIds.contains(str(m.get("id"))))
                        .toList();

                if (assignableMembers.isEmpty()) {
                    results.add(result(displayId, caseId, "", "SKIP: No assignable agents (all members are managers) for team " + teamDisplayId));
                    continue;
                }

                Map<String, Object> chosen = pickLowestWorkload(assignableMembers, workload);
                String chosenEmpId = str(chosen.get("id"));
                String chosenName = str(chosen.get("formattedName"));

                Map<String, Object> payload = new HashMap<>();
                Map<String, Object> processor = new HashMap<>();
                processor.put("id", chosenEmpId);
                payload.put("processor", processor);
                payload.put("status", "01");

                sap.patchWithIfMatch("/sap/c4c/api/v1/case-service/cases/" + caseId, etag, payload);

                workload.put(chosenEmpId, workload.getOrDefault(chosenEmpId, 0) + 1);
                results.add(result(displayId, caseId, chosenName, "ASSIGNED"));

            } catch (Exception ex) {
                results.add(result(displayId, caseId, "", "ERROR: " + ex.getMessage()));
            }
        }

        return results;
    }

    /**
     * Builds a set of employee IDs who are managers.
     * An employee is a manager if their ID appears as another employee's managerId.
     */
    private Set<String> buildManagerIdSet(Map<String, List<Map<String, Object>>> employeesByTeam) {
        Set<String> managerIds = new HashSet<>();
        for (List<Map<String, Object>> members : employeesByTeam.values()) {
            for (Map<String, Object> emp : members) {
                String managerId = str(emp.get("managerId"));
                if (!managerId.isBlank()) {
                    managerIds.add(managerId);
                }
            }
        }
        return managerIds;
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> unwrapValue(Map<String, Object> body) {
        if (body == null) return null;
        Object val = body.get("value");
        if (val instanceof Map<?, ?> m) return (Map<String, Object>) m;
        return body;
    }

    private List<Map<String, Object>> filterUnassigned(List<Map<String, Object>> candidates) {
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

    private Map<String, Object> pickLowestWorkload(List<Map<String, Object>> members, Map<String, Integer> workload) {
        Map<String, Object> best = members.get(0);
        int bestCount = Integer.MAX_VALUE;
        for (Map<String, Object> m : members) {
            String empId = str(m.get("id"));
            int count = workload.getOrDefault(empId, 0);
            if (count < bestCount) {
                best = m;
                bestCount = count;
            }
        }
        return best;
    }

    private Map<String, Object> result(String displayId, String caseId, String chosenName, String status) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("displayId", displayId);
        m.put("caseId", caseId);
        m.put("assignedTo", chosenName);
        m.put("status", status);
        return m;
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> safeMap(Object o) {
        if (o instanceof Map<?, ?> m) return (Map<String, Object>) m;
        return null;
    }

    private String str(Object o) {
        return o == null ? "" : String.valueOf(o);
    }
}