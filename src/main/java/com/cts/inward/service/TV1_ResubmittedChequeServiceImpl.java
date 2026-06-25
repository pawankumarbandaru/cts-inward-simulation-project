package com.cts.inward.service;

import java.util.List;
import java.util.Optional;

import com.cts.inward.dao.InwardChequeDao;
import com.cts.inward.dao.InwardChequeDaoImpl;
import com.cts.inward.dto.CheckerLoadResult;
import com.cts.inward.entity.InwardCheque;
import com.cts.inward.enums.ChequeStatus;
import com.cts.inward.enums.DecisionStatus;
import com.cts.inward.enums.SendTo;

/**
 * File    : TV1_ResubmittedChequeServiceImpl.java
 * Package : com.cts.inward.service
 * Purpose : Implements TV1_ResubmittedChequeService — Checker (Verifier 1) approve,
 *           reject, and return-to-maker logic. Delegates all DB operations to InwardChequeDao.
 *           Approval remarks are optional; reject/return remarks are mandatory, since
 *           the maker and the audit trail need a reason for those decisions.
 *           checker_refer_reason (return-to-maker note) and checker_reject_reason
 *           (permanent rejection note) are kept in separate columns so both stay
 *           visible in the audit trail.
 * Author  : Ramana JS
 * Date    : 24-06-2025
 */
public class TV1_ResubmittedChequeServiceImpl implements TV1_ResubmittedChequeService {

    private final InwardChequeDao checkerDao = new InwardChequeDaoImpl();


    // ── Queue load ─────────────────────────────────────────────────────────

    @Override
    public List<InwardCheque> getResubmittedChequesForChecker() {
        return checkerDao.findResubmittedForChecker();
    }

    /**
     * Loads a cheque for the review workspace.
     * Returns a CheckerLoadResult describing whether the cheque is actionable.
     */
    @Override
    public CheckerLoadResult getChequeForCheckerReview(String chequeNo) {

        if (chequeNo == null || chequeNo.isBlank()) {
            return CheckerLoadResult.notFound("(empty)");
        }

        Optional<InwardCheque> found = checkerDao.findByChequeNoTV1(chequeNo.trim());

        if (found.isEmpty()) {
            return CheckerLoadResult.notFound(chequeNo);
        }

        InwardCheque cheque = found.get();

        // Verifier 1 (TV_1) should not process Branch Manager (TV_2) cheques
        if (SendTo.TV_1 != cheque.getSendTo()) {
            return CheckerLoadResult.wrongRoute(chequeNo);
        }

        DecisionStatus decision = cheque.getDecision();

        if (DecisionStatus.ACCEPTED == decision) {
            return CheckerLoadResult.alreadyAccepted(chequeNo);
        }
        if (DecisionStatus.REJECTED == decision) {
            return CheckerLoadResult.alreadyRejected(chequeNo);
        }

        ChequeStatus status = cheque.getChequeStatus();

        switch (status) {
            case Resubmitted:
                return CheckerLoadResult.ok(cheque);
            case Repair:
                return CheckerLoadResult.returnedToMaker(chequeNo);
            default:
                return CheckerLoadResult.wrongStatus(
                    chequeNo, status != null ? status.name() : "null");
        }
    }

    // ── Checker actions ────────────────────────────────────────────────────

    /**
     * Approves the cheque.
     * Sets chequeStatus = Ready and decision = ACCEPTED.
     *
     * REMARKS ARE OPTIONAL HERE — unlike rejectCheque()/returnToMaker().
     * Business rule: a checker approving a cheque has nothing to explain
     * to anyone, so we don't force a remark. If the checker did type one,
     * we still save it in checker_refer_reason for reference.
     */
    @Override
    public InwardCheque approveCheque(String chequeNo, String checkerUserId, String checkerRemarks) {
        InwardCheque cheque = loadChequeOrThrow(chequeNo);

        cheque.setChequeStatus(ChequeStatus.Ready);
        cheque.setDecision(DecisionStatus.ACCEPTED);
        // Approval note → stored in checker_refer_reason (optional, may be null/blank)
        cheque.setSendbackReason(
            checkerRemarks != null && !checkerRemarks.isBlank() ? checkerRemarks.trim() : null);
        // Clear rejection reason if any previous value exists
        cheque.setRejectReason(null);

        checkerDao.updateCheckerDecision(cheque);
        return cheque;
    }

    /**
     * Permanently rejects the cheque.
     * Sets chequeStatus = Reject and decision = REJECTED.
     * The rejection reason is stored in checker_reject_reason (NEW column).
     */
    @Override
    public InwardCheque rejectCheque(String chequeNo, String checkerUserId, String checkerRemarks) {
        validateRemarks(checkerRemarks, "Checker remarks are required before rejecting.");

        InwardCheque cheque = loadChequeOrThrow(chequeNo);

        cheque.setChequeStatus(ChequeStatus.Reject);
        cheque.setDecision(DecisionStatus.REJECTED);
        // Rejection reason → stored in checker_reject_reason (new column)
        cheque.setRejectReason(checkerRemarks.trim());
        // Clear refer reason if any previous value exists
        cheque.setSendbackReason(null);

        checkerDao.updateCheckerDecision(cheque);
        return cheque;
    }

    /**
     * Returns the cheque to maker for re-correction.
     * Sets chequeStatus = Repair, decision = REFERRED, sendTo = MAKER.
     * The refer-back reason is stored in checker_refer_reason.
     * Resets isEditedByMaker and editedFields for a fresh correction round.
     */
    @Override
    public InwardCheque returnToMaker(String chequeNo, String checkerUserId, String checkerRemarks) {
        validateRemarks(checkerRemarks, "Checker remarks are required before returning to maker.");

        InwardCheque cheque = loadChequeOrThrow(chequeNo);

        cheque.setChequeStatus(ChequeStatus.Repair);
        cheque.setDecision(DecisionStatus.REFERRED);
        cheque.setSendTo(SendTo.MAKER);
        // Refer-back reason → stored in checker_refer_reason
        cheque.setSendbackReason(checkerRemarks.trim());
        // Clear rejection reason
        cheque.setRejectReason(null);

        // Reset maker edit tracking for the new correction round
        cheque.setIsEditedByMaker(false);
        cheque.setEditedFields(null);

        checkerDao.updateCheckerDecision(cheque);
        return cheque;
    }

    @Override
    public long getPendingResubmittedCount() {
        return checkerDao.countResubmittedForChecker();
    }

    // ── Private helpers ────────────────────────────────────────────────────

    private void validateRemarks(String remarks, String errorMessage) {
        if (remarks == null || remarks.trim().isEmpty()) {
            throw new IllegalArgumentException(errorMessage);
        }
    }

    private InwardCheque loadChequeOrThrow(String chequeNo) {
        return checkerDao.findResubmittedByChequeNoTV1(chequeNo)
            .orElseThrow(() -> new RuntimeException(
                "Cheque not found or no longer in Resubmitted state: " + chequeNo));
    }
}