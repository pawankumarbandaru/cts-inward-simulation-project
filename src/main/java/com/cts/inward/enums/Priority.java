package com.cts.inward.enums;

/**
 * Enum: Priority
 * Used for: InwardReturnedCheque.priority column (stored as String in DB)
 *
 * HIGH   — amount >= 5 lakhs (₹5,00,000) → routes to Branch Manager
 * MEDIUM — amount < 5 lakhs               → routes to Verifier 1
 * LOW    — minor MICR corrections, low risk
 */
public enum Priority {
    HIGH,
    MEDIUM,
    LOW
}
