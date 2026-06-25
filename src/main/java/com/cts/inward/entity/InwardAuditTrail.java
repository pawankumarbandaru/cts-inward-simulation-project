package com.cts.inward.entity;

import java.time.LocalDateTime;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;

/**
 * Entity: InwardAuditTrail
 *
 * Stores every audit step for a cheque in the inward workflow.
 * Each row = one event (submitted, returned, repaired, resubmitted, etc.)
 *
 * Table: inward_audit_trail
 *
 * Relationship: Many audit rows belong to ONE cheque (Many-to-One)
 *               InwardAuditTrail HAS-A InwardReturnedCheque (via chequeNo)
 */
@Entity
@Table(name = "inward_audit_trail")
public class InwardAuditTrail {

    // ── Primary Key ────────────────────────────────────────────
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id")
    private Long id;

    // ── Which cheque this audit step belongs to ─────────────────
    // We store chequeNo as a plain column (not a FK join)
    // because it keeps queries simple for a trainee project.
    @Column(name = "cheque_no", nullable = false, length = 20)
    private String chequeNo;

    // ── What step/action happened ───────────────────────────────
    /**
     * Possible step values:
     *   MAKER_SUBMITTED   — Maker submitted the cheque
     *   CHECKER_RETURNED  — Checker sent it back
     *   MAKER_REPAIRED    — Maker saved corrections
     *   CBS_PASS          — CBS validation passed
     *   CBS_FAIL          — CBS validation failed
     *   RESUBMITTED       — Maker resubmitted to checker
     *   RF_GENERATED      — Return File generated
     *   RRF_GENERATED     — RRF generated
     */
    @Column(name = "step_code", nullable = false, length = 30)
    private String stepCode;

    // ── Human-readable description to show in the UI ────────────
    @Column(name = "step_description", columnDefinition = "TEXT")
    private String stepDescription;

    // ── Who performed this action ────────────────────────────────
    @Column(name = "performed_by", length = 100)
    private String performedBy;

    // ── Role of the person who performed this action ─────────────
    @Column(name = "performed_by_role", length = 50)
    private String performedByRole;

    // ── When this step happened ──────────────────────────────────
    @Column(name = "event_time", nullable = false)
    private LocalDateTime eventTime;

    // ── Optional extra info (reason, remarks, refId, etc.) ───────
    @Column(name = "extra_info", columnDefinition = "TEXT")
    private String extraInfo;

    // ── Auto-set the event time when inserting ───────────────────
    @PrePersist
    public void onInsert() {
        if (this.eventTime == null) {
            this.eventTime = LocalDateTime.now();
        }
    }

    // ── Constructors ─────────────────────────────────────────────

    public InwardAuditTrail() {
        // default constructor needed by Hibernate
    }

    /**
     * Convenience constructor — use this when saving an audit step.
     */
    public InwardAuditTrail(String chequeNo, String stepCode,
                             String stepDescription, String performedBy,
                             String performedByRole) {
        this.chequeNo        = chequeNo;
        this.stepCode        = stepCode;
        this.stepDescription = stepDescription;
        this.performedBy     = performedBy;
        this.performedByRole = performedByRole;
        this.eventTime       = LocalDateTime.now();
    }

    // ── Getters and Setters ──────────────────────────────────────

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public String getChequeNo() { return chequeNo; }
    public void setChequeNo(String chequeNo) { this.chequeNo = chequeNo; }

    public String getStepCode() { return stepCode; }
    public void setStepCode(String stepCode) { this.stepCode = stepCode; }

    public String getStepDescription() { return stepDescription; }
    public void setStepDescription(String stepDescription) { this.stepDescription = stepDescription; }

    public String getPerformedBy() { return performedBy; }
    public void setPerformedBy(String performedBy) { this.performedBy = performedBy; }

    public String getPerformedByRole() { return performedByRole; }
    public void setPerformedByRole(String performedByRole) { this.performedByRole = performedByRole; }

    public LocalDateTime getEventTime() { return eventTime; }
    public void setEventTime(LocalDateTime eventTime) { this.eventTime = eventTime; }

    public String getExtraInfo() { return extraInfo; }
    public void setExtraInfo(String extraInfo) { this.extraInfo = extraInfo; }

    @Override
    public String toString() {
        return "InwardAuditTrail{chequeNo=" + chequeNo
             + ", stepCode=" + stepCode
             + ", performedBy=" + performedBy
             + ", eventTime=" + eventTime + "}";
    }
}