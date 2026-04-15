package com.asar.caseassignment.web;

import java.util.LinkedHashMap;
import java.util.Map;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

@RestControllerAdvice
public class ApiExceptionHandler {

    @ExceptionHandler(Exception.class)
    @ResponseStatus(HttpStatus.INTERNAL_SERVER_ERROR)
    public Map<String, Object> handle(Exception ex) {
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("error", ex.getClass().getName());
        out.put("message", ex.getMessage());
        out.put("status", 500);
        return out;
    }
}