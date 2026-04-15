package com.asar.caseassignment.service;

import java.util.*;
import org.springframework.stereotype.Service;

import com.asar.caseassignment.sap.SapApiClient;

@Service
public class CaseAssignmentService {

    private final SapApiClient sap;
    private final CaseQueryService caseQueryService;
    private final EmployeeQueryService employeeQueryService;

    public CaseAssignmentService(
            SapApiClient sap,
            CaseQueryService caseQueryService,
            EmployeeQueryService employeeQueryService
    ) {
        this.sap = sap;
        this.caseQueryService = caseQueryService;
        this.employeeQueryService = employeeQueryService;
    }

    public Map<String, Object> assignUnassignedCases() {

        Map<String, List<Map<String, Object>>> employeesByTeam =
                employeeQueryService.getEmployeesGroupedByOrgUnitId();

        List<Map<String, Object>> unassigned =
                caseQueryService.getUnassignedOpenOrInProcessCases();

        List<Map<String, Object>> candidates =
                caseQueryService.getCandidateCases();

        Map<String, Integer> workload = buildWorkloadByProcessorId(candidates);

        int assignedCount = 0;
        List<Map<String, Object>> assignments = new ArrayList<>();

        for (Map<String, Object> c : unassigned) {
            String caseId = str(c.get("id"));
            String displayId = str(c.get("displayId"));

            Map<String, Object> st = map(c.get("serviceTeam"));
            String teamId = st != null ? str(st.get("id")) : null;

            if (teamId == null || teamId.isBlank()) {
                assignments.add(result(displayId, caseId, null, "SKIP: no serviceTeam.id"));
                continue;
            }

            List<Map<String, Object>> teamMembers = employeesByTeam.getOrDefault(teamId, List.of());
            if (teamMembers.isEmpty()) {
                assignments.add(result(displayId, caseId, null, "SKIP: no employees for team " + teamId));
                continue;
            }

            Map<String, Object> chosen = pickLowestWorkload(teamMembers, workload);
            String chosenEmpId = str(chosen.get("id"));
            String chosenName = str(chosen.get("formattedName"));

            try {
                String etag = sap.getWithEtag("/sap/c4c/api/v1/case-service/cases/" + caseId).etag();

                Map<String, Object> payload = new HashMap<>();
                Map<String, Object> processor = new HashMap<>();
                processor.put("id", chosenEmpId);
                payload.put("processor", processor);
                payload.put("status", "01");

                sap.patchWithIfMatch("/sap/c4c/api/v1/case-service/cases/" + caseId, etag, payload);

                workload.put(chosenEmpId, workload.getOrDefault(chosenEmpId, 0) + 1);

                assignedCount++;
                assignments.add(result(displayId, caseId, chosenName, "ASSIGNED"));
            } catch (Exception ex) {
                assignments.add(result(displayId, caseId, chosenName, "ERROR: " + ex.getMessage()));
            }
        }

        Map<String, Object> out = new LinkedHashMap<>();
        out.put("unassignedFound", unassigned.size());
        out.put("assigned", assignedCount);
        out.put("details", assignments);
        return out;
    }

    private Map<String, Integer> buildWorkloadByProcessorId(List<Map<String, Object>> cases) {
        Map<String, Integer> counts = new HashMap<>();
        for (Map<String, Object> c : cases) {
            Map<String, Object> p = map(c.get("processor"));
            if (p == null) continue;
            String pid = str(p.get("id"));
            if (pid == null || pid.isBlank()) continue;
            counts.put(pid, counts.getOrDefault(pid, 0) + 1);
        }
        return counts;
    }

    private Map<String, Object> pickLowestWorkload(List<Map<String, Object>> members, Map<String, Integer> workload) {
        Map<String, Object> best = members.get(0);
        int bestCount = workload.getOrDefault(str(best.get("id")), 0);
        for (Map<String, Object> m : members) {
            String id = str(m.get("id"));
            int c = workload.getOrDefault(id, 0);
            if (c < bestCount) {
                best = m;
                bestCount = c;
            }
        }
        return best;
    }

    private Map<String, Object> result(String displayId, String caseId, String assignee, String status) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("caseDisplayId", displayId);
        m.put("caseId", caseId);
        m.put("assignee", assignee);
        m.put("status", status);
        return m;
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> map(Object o) {
        if (o instanceof Map<?, ?> mm) return (Map<String, Object>) mm;
        return null;
    }

    private String str(Object o) { return o == null ? null : o.toString(); }
}