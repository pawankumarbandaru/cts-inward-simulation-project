package com.cts.inward.enums;

/**
 * Enum: ReturnedChequeStatus
 * Used for: InwardReturnedCheque.status column
 *
 * NEEDS_CORRECTION  — Checker returned. Maker must correct.
 * RF_GENERATED      — Return File generated for this cheque.
 * RRF_GENERATED     — Return Reason File generated.
 * RESUBMITTED       — Maker corrected and resubmitted to Checker.
 */
public enum ReturnedChequeStatus {
    NEEDS_CORRECTION,
    RF_GENERATED,
    RRF_GENERATED,
    RESUBMITTED
}
