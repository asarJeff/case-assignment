package com.asar.caseassignment.schedule;

import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.asar.caseassignment.service.AssignmentService;

@RestController
@RequestMapping("/admin")
@Component
public class AssignmentScheduler {

    private static final Logger log = LoggerFactory.getLogger(AssignmentScheduler.class);
    private final AssignmentService assignmentService;

    private final AtomicBoolean running = new AtomicBoolean(false);

    public AssignmentScheduler(AssignmentService assignmentService) {
        this.assignmentService = assignmentService;
    }

    @Scheduled(
        fixedDelayString = "${assignment.scheduler.delayMs:30000}",
        initialDelayString = "${assignment.scheduler.initialDelayMs:10000}"
    )
    public void runAssignmentJob() {
        if (!running.compareAndSet(false, true)) {
            log.warn("AssignmentScheduler: previous run still active. Skipping this cycle.");
            return;
        }

        long start = System.currentTimeMillis();
        try {
            log.info("AssignmentScheduler: starting assignment run...");

            List<Map<String, Object>> results = assignmentService.assignUnassignedCasesFairly();

            logResults(results, start, "AssignmentScheduler");

        } catch (Exception ex) {
            log.error("AssignmentScheduler: unexpected error in scheduled run", ex);
        } finally {
            running.set(false);
        }
    }

    @PostMapping("/backfill")
    public ResponseEntity<String> triggerBackfill() {
        if (!running.compareAndSet(false, true)) {
            log.warn("Backfill requested but a run is already active. Skipping.");
            return ResponseEntity.ok("Already running - backfill not started");
        }

        new Thread(() -> {
            long start = System.currentTimeMillis();
            try {
                log.info("Backfill: starting full pass of all unassigned cases...");

                List<Map<String, Object>> results = assignmentService.assignUnassignedCasesFairly();

                logResults(results, start, "Backfill");

            } catch (Exception ex) {
                log.error("Backfill: unexpected error", ex);
            } finally {
                running.set(false);
            }
        }, "backfill-thread").start();

        return ResponseEntity.ok("Backfill started - monitor logs for progress");
    }

    private void logResults(List<Map<String, Object>> results, long start, String prefix) {
        long assigned = results.stream()
                .filter(r -> "ASSIGNED".equals(String.valueOf(r.get("status"))))
                .count();
        long skipped = results.stream()
                .filter(r -> String.valueOf(r.get("status")).startsWith("SKIP"))
                .count();
        long errors = results.stream()
                .filter(r -> String.valueOf(r.get("status")).startsWith("ERROR"))
                .count();

        // Log each skipped/errored case so we can see WHY they're being skipped
        for (Map<String, Object> r : results) {
            String status = String.valueOf(r.get("status"));
            if (status.startsWith("SKIP") || status.startsWith("ERROR")) {
                log.info("  Case {} (id={}): {} -> {}",
                        r.get("displayId"), r.get("caseId"), r.get("assignedTo"), status);
            }
        }

        log.info("{}: finished. total={}, assigned={}, skipped={}, errors={}, ms={}",
                prefix, results.size(), assigned, skipped, errors,
                (System.currentTimeMillis() - start));
    }
}