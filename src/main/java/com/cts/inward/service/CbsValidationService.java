package com.cts.inward.service;

import java.util.List;

import com.cts.inward.dto.CbsAccountData;
import com.cts.inward.dto.CbsValidationResult;
import com.cts.inward.entity.InwardCheque;

/**
 * Contract for CBS (Core Banking System) validation via Firebase.
 *
 * Rule: existing methods must never be removed or modified.
 * New methods may be added below the original set.
 *
 * Existing methods
 * ─────────────────
 *   fetchAccountData(String accountNo)      → fetch account info from CBS Firebase
 *   validateCheque(InwardCheque, CbsAccountData) → run all validation rules
 *
 * Added methods (new — do not remove or modify the above)
 * ────────────────────────────────────────────────────────
 *   (none added yet; extension point reserved)
 */
public interface CbsValidationService {

    /**
     * Fetches CBS account data from Firebase for the given account number.
     *
     * @param accountNo the account number to look up
     * @return {@link CbsAccountData} populated from Firebase,
     *         or {@code null} if the account is not found
     */
    CbsAccountData fetchAccountData(String accountNo);

    /**
     * Validates a single cheque against the CBS data returned by
     * {@link #fetchAccountData(String)}.
     *
     * Validation rules (applied in order — first failure wins):
     * <ol>
     *   <li>Missing account number         → INVALID: "Missing Account Number"</li>
     *   <li>CBS data null (not found)      → INVALID: "Account Not Found"</li>
     *   <li>Missing CBS data               → INVALID: "Missing CBS Data"</li>
     *   <li>Account inactive               → INVALID: "Account Inactive"</li>
     *   <li>Cheque not found in CBS        → INVALID: "Cheque Not Found"</li>
     *   <li>Stop payment marked            → INVALID: "Stop Payment Marked"</li>
     *   <li>Insufficient funds             → INVALID: "Insufficient Funds"</li>
     *   <li>Account holder name missing    → INVALID: "Missing Account Holder Information"</li>
     *   <li>Account holder name mismatch   → INVALID: "Account Holder Name Mismatch"</li>
     *   <li>All checks pass                → VALID:   "Validation Successful"</li>
     * </ol>
     *
     * @param cheque     the cheque to validate (must not be null)
     * @param cbsData    CBS account data (may be null — treated as "not found")
     * @return a {@link CbsValidationResult} — never null
     */
    CbsValidationResult validateCheque(InwardCheque cheque, CbsAccountData cbsData);

	long[] countValidAndInvalid(List<CbsValidationResult> results);
}
