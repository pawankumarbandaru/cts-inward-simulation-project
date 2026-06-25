package com.cts.inward.service;

import java.util.List;

import com.cts.inward.dto.V2LoadResult;
import com.cts.inward.entity.InwardCheque;

/**
 * File    : TV2_ResubmittedChequeService.java
 * Package : com.cts.inward.service
 * Purpose : Service interface for the Verifier 2 (Branch Manager) workflow —
 *           cheques with amount >= Rs.5,00,000. Sits between the composer and the DAO.
 *           Mirrors TV1_ResubmittedChequeService: same operations, but Verifier 2
 *           handles cheques routed with sendTo = TV_2.
 * Author  : Ramana
 * Date    : 24-06-2025
 */
public interface TV2_ResubmittedChequeService {

    /**
     * Returns all cheques waiting for Verifier 2 review (chequeStatus=Resubmitted,
     * sendTo=TV_2, decision=REFERRED). May be empty, never null.
     */
    List<InwardCheque> getResubmittedChequesForVerifier2();

    /**
     * Loads a single cheque for the Verifier 2 review workspace.
     *
     * Returns a V2LoadResult which carries the cheque (if actionable) and
     * a plain-text message. The composer checks isOk() to decide what to show.
     *
     * Possible statuses:
     *   OK               — cheque is Resubmitted and actionable, cheque field is set
     *   NOT_FOUND        — chequeNo does not exist in DB
     *   ALREADY_ACCEPTED — verifier already approved this cheque
     *   ALREADY_REJECTED — verifier already rejected this cheque
     *   RETURNED_TO_MAKER— cheque is back with maker for correction
     *   WRONG_ROUTE      — cheque is routed to Verifier 1 (TV_1), not Verifier 2
     *   WRONG_STATUS     — unexpected status
     *
     * @param chequeNo cheque number to load
     */
    V2LoadResult getChequeForV2Review(String chequeNo);

    /**
     * Approves the resubmitted cheque.
     * Sets chequeStatus=Ready, decision=ACCEPTED, and records verifier details.
     * Approval remarks are optional — the Approve button does not collect any.
     *
     * @param chequeNo  cheque number to approve
     * @param v2UserId  ID of the Branch Manager performing the action
     * @param v2Remarks optional approval note (may be null or blank)
     * @return the updated InwardCheque entity
     */
    InwardCheque approveCheque(String chequeNo, String v2UserId, String v2Remarks);

    /**
     * Permanently rejects the resubmitted cheque.
     * Sets chequeStatus=Reject, decision=REJECTED, and records verifier details.
     * Saved to checker_reject_reason — kept separate from the return-to-maker
     * reason column so both are visible in the audit trail.
     * Throws IllegalArgumentException if v2Remarks is blank.
     *
     * @param chequeNo  cheque number to reject
     * @param v2UserId  ID of the Branch Manager performing the action
     * @param v2Remarks mandatory rejection reason
     * @return the updated InwardCheque entity
     */
    InwardCheque rejectCheque(String chequeNo, String v2UserId, String v2Remarks);

    /**
     * Returns the cheque to the maker for further correction.
     * Sets chequeStatus=Repair, decision=REFERRED, sendTo=MAKER.
     * Resets isEditedByMaker=false and editedFields=null for the new correction round.
     * Throws IllegalArgumentException if v2Remarks is blank.
     *
     * @param chequeNo  cheque number to return
     * @param v2UserId  ID of the Branch Manager performing the action
     * @param v2Remarks reason for returning to maker (shown to maker)
     * @return the updated InwardCheque entity
     */
    InwardCheque returnToMaker(String chequeNo, String v2UserId, String v2Remarks);

    /**
     * Returns the count of resubmitted cheques waiting for Verifier 2.
     * Used for the TV2 sidebar pending badge.
     */
    long getPendingResubmittedCount();
}