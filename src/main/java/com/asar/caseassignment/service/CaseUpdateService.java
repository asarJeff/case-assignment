package com.asar.caseassignment.service;

import java.util.HashMap;
import java.util.Map;

import org.springframework.stereotype.Service;

import com.asar.caseassignment.sap.SapApiClient;

@Service
public class CaseUpdateService {

    private final SapApiClient sap;

    public CaseUpdateService(SapApiClient sap) {
        this.sap = sap;
    }

    public Map<String, Object> assignProcessor(String caseId, String employeeId) {

        var etagResp = sap.getWithEtag("/sap/c4c/api/v1/case-service/cases/" + caseId);
        String etag = etagResp.etag();

        Map<String, Object> payload = new HashMap<>();
        Map<String, Object> processor = new HashMap<>();
        processor.put("id", employeeId);
        payload.put("processor", processor);
        payload.put("status", "01");

        sap.patchWithIfMatch("/sap/c4c/api/v1/case-service/cases/" + caseId, etag, payload);

        Map<String, Object> out = new HashMap<>();
        out.put("caseId", caseId);
        out.put("processorId", employeeId);
        out.put("status", "ASSIGNED");
        out.put("etagUsed", etag);
        return out;
    }
}