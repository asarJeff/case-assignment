package com.asar.caseassignment.web;

import java.util.List;
import java.util.Map;

import org.springframework.web.bind.annotation.*;

import com.asar.caseassignment.service.AssignmentService;

@RestController
public class AssignmentController {

    private final AssignmentService assignmentService;

    public AssignmentController(AssignmentService assignmentService) {
        this.assignmentService = assignmentService;
    }

    @PostMapping("/assign/run")
    public List<Map<String, Object>> run() {
        return assignmentService.assignUnassignedCasesFairly();
    }
}