package com.cts.inward.dto;

import com.cts.inward.service.CbsValidationService;

/**
 * Immutable result returned by {@link CbsValidationService} for a single cheque.
 *
 * valid  = true  → cbsValidation = VALID,  reason = "Validation Successful"
 * valid  = false → cbsValidation = INVALID, reason = specific failure reason
 */
public class CbsValidationResult {

    private final boolean valid;
    private final String  reason;

    // ── Factory helpers ──────────────────────────────────────────────────

    public static CbsValidationResult success() {
        return new CbsValidationResult(true, "Validation Successful");
    }

    public static CbsValidationResult failure(String reason) {
        return new CbsValidationResult(false,
                reason != null && !reason.isBlank() ? reason : "Validation Failed");
    }

    // ── Constructor ──────────────────────────────────────────────────────

    private CbsValidationResult(boolean valid, String reason) {
        this.valid  = valid;
        this.reason = reason;
    }

    // ── Accessors ────────────────────────────────────────────────────────

    public boolean isValid()    { return valid;  }
    public String  getReason()  { return reason; }

    @Override
    public String toString() {
        return "CbsValidationResult{valid=" + valid + ", reason='" + reason + "'}";
    }
}
