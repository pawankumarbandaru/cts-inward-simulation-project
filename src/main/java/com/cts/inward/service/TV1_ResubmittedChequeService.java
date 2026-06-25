package com.cts.inward.service;

import java.util.List;

import com.cts.inward.dto.CheckerLoadResult;
import com.cts.inward.entity.InwardCheque;

/**
 * File    : TV1_ResubmittedChequeService.java
 * Package : com.cts.inward.service
 * Purpose : Service interface for the Checker (Verifier 1) workflow.
 *           Sits between the composer and the DAO — handles approve/reject/return-to-maker
 *           decisions and the loading rules for the review workspace.
 *           No DB queries here — only validation, routing, and state transitions.
 * Author  : Ramana JS
 * Date    : 24-06-2025
 */
public interface TV1_ResubmittedChequeService {

    /**
     * Returns all resubmitted cheques waiting for Verifier 1,
     * ordered by resubmitted_at DESC.
     */
    List<InwardCheque> getResubmittedChequesForChecker();

    /**
     * Loads a single cheque for the checker review workspace.
     *
     * Returns a CheckerLoadResult which carries the cheque (if actionable) and
     * a plain-text message. The composer checks isOk() to decide what to show.
     *
     * Possible statuses:
     *   OK               — cheque is Resubmitted and actionable, cheque field is set
     *   NOT_FOUND        — chequeNo does not exist in DB
     *   ALREADY_ACCEPTED — checker already approved this cheque
     *   ALREADY_REJECTED — checker already rejected this cheque
     *   RETURNED_TO_MAKER— cheque is back with maker for correction
     *   WRONG_ROUTE      — cheque is routed to a different checker role
     *   WRONG_STATUS     — unexpected status
     *
     * @param chequeNo cheque number to load
     */
    CheckerLoadResult getChequeForCheckerReview(String chequeNo);

    /**
     * Approves the resubmitted cheque.
     * Sets chequeStatus=Ready, decision=ACCEPTED, and records checker details.
     * Throws IllegalArgumentException if checkerRemarks is blank.
     *
     * @param chequeNo       cheque number to approve
     * @param checkerUserId  ID of the checker performing the action
     * @param checkerRemarks mandatory approval remarks
     * @return the updated InwardCheque entity
     */
    InwardCheque approveCheque(String chequeNo, String checkerUserId, String checkerRemarks);

    /**
     * Permanently rejects the resubmitted cheque.
     * Sets chequeStatus=Reject, decision=REJECTED, and records checker details.
     * Throws IllegalArgumentException if checkerRemarks is blank.
     *
     * @param chequeNo       cheque number to reject
     * @param checkerUserId  ID of the checker performing the action
     * @param checkerRemarks mandatory rejection reason
     * @return the updated InwardCheque entity
     */
    InwardCheque rejectCheque(String chequeNo, String checkerUserId, String checkerRemarks);

    /**
     * Returns the cheque to the maker for further correction.
     * Sets chequeStatus=Repair, decision=REFERRED, sendTo=MAKER.
     * Resets isEditedByMaker=false and editedFields=null for the new correction round.
     * Throws IllegalArgumentException if checkerRemarks is blank.
     *
     * @param chequeNo       cheque number to return
     * @param checkerUserId  ID of the checker performing the action
     * @param checkerRemarks reason for returning to maker (shown to maker)
     * @return the updated InwardCheque entity
     */
    InwardCheque returnToMaker(String chequeNo, String checkerUserId, String checkerRemarks);

    /**
     * Returns the count of resubmitted cheques waiting for Verifier 1.
     * Used for the sidebar pending badge.
     */
    long getPendingResubmittedCount();
}