package com.trustlens.domain;

public final class Status {
    private Status() {}
    // Audit
    public static final String CREATED = "CREATED";
    public static final String GENERATING_PERSONAS = "GENERATING_PERSONAS";
    public static final String PERSONAS_READY = "PERSONAS_READY";
    public static final String TESTING = "TESTING";
    public static final String ANALYZING = "ANALYZING";
    public static final String REPORT_READY = "REPORT_READY";
    public static final String FAILED = "FAILED";
    // Journey
    public static final String PENDING = "PENDING";
    public static final String RUNNING = "RUNNING";
    public static final String SUCCESS = "SUCCESS";
    public static final String PARTIAL = "PARTIAL";
    public static final String JOURNEY_FAILED = "FAILED";
    public static final String ERROR = "ERROR";
    // Preview / change
    public static final String GENERATING = "GENERATING";
    public static final String DRAFT = "DRAFT";
    public static final String APPROVED = "APPROVED";
    public static final String REJECTED = "REJECTED";
    // Modes
    public static final String LIVE = "LIVE";
    public static final String DEMO = "DEMO";
}
