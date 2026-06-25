package com.cts.inward.service;

/**
 * File    : InwardChequeService.java
 * Package : com.cts.inward.service
 * Purpose : Service interface for all Inward Cheque operations.
 *           Covers the Maker queue (returned-by-checker), repair workspace,
 *           CBS validation result saving, and resubmit/RRF flows.
 * Author  : Ramana
 * Date    : 24-06-2025
 */

import java.util.List;
import java.util.Optional;

import com.cts.inward.entity.InwardBatch;
import com.cts.inward.entity.InwardCheque;

public interface InwardChequeService {

    List<InwardBatch> getAllBatches();

    List<InwardCheque> getChequesByBatchId(String batchId);

    void updateCheque(InwardCheque cheque);

    /**
     * Returns all cheques waiting for Maker correction.
     * Filter: chequeStatus = Repair, decision = REFERRED, sendTo = MAKER
     */
    List<InwardCheque> getChequesNeedingCorrection();

    /**
     * Returns a single cheque by cheque number for the Repair Workspace.
     * Returns empty if the cheque is not in an actionable Maker state.
     */
    Optional<InwardCheque> getChequeForRepair(String chequeNo);

    /**
     * Saves the Maker's field corrections to the DB.
     * Does NOT change chequeStatus — only updates the edited field values.
     */
    void saveCorrections(InwardCheque cheque);

    /**
     * Marks cheque as Resubmitted after CBS validation passes.
     * Routes to TV_1 (amount <= ₹1L) or TV_2 (amount > ₹1L).
     * Sets: chequeStatus, decision, sendTo, cbsValidation, resubmittedBy.
     */
    void resubmitToChecker(String chequeNo, String makerUserId);

    /**
     * Marks cheque decision as REJECTED when CBS fails and Maker generates RRF.
     */
    void generateRRF(String chequeNo, String makerUserId);

    /**
     * Saves the result of a CBS validation check (pass or fail) for a cheque.
     * Routes through the service layer — components must not call the DAO directly.
     *
     * @param chequeId      the cheque's database id
     * @param isValid       true if all 5 CBS checks passed
     * @param failureReason the failure message to store; null if isValid is true
     */
    void saveCbsResult(Long chequeId, boolean isValid, String failureReason);

    /**
     * Returns the count of cheques waiting for Maker correction.
     * Used for the sidebar pending badge.
     */
    long getPendingCorrectionCount();

	List<InwardCheque> getReferredChequesForTV2();
}