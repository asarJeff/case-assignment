package com.asar.caseassignment.web;

import java.util.List;
import java.util.Map;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import com.asar.caseassignment.service.CaseQueryService;

@RestController
public class CaseDebugController {

    private final CaseQueryService caseQueryService;

    public CaseDebugController(CaseQueryService caseQueryService) {
        this.caseQueryService = caseQueryService;
    }

    @GetMapping("/debug/unassigned")
    public List<Map<String, Object>> unassigned() {
        return caseQueryService.getUnassignedOpenOrInProcessCases();
    }
}