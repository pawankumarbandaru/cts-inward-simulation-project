package com.cts.inward.enums;

/**
 * Enum: AuditStepCode
 * Used for: InwardAuditTrail.stepCode column (stored as String in DB)
 *
 * Each value represents one step in the inward cheque workflow.
 * InwardAuditTrailServiceImpl uses these to build audit rows.
 *
 * RULE: Always save as String: auditTrail.setStepCode(AuditStepCode.CBS_PASS.name())
 */
public enum AuditStepCode {
    MAKER_SUBMITTED,
    CHECKER_RETURNED,
    MAKER_REPAIRED,
    CBS_PASS,
    CBS_FAIL,
    RESUBMITTED,
    RF_GENERATED,
    RRF_GENERATED
}
