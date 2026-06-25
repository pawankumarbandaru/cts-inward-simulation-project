package com.cts.inward.service;

import java.util.List;
import java.util.Optional;

import com.cts.inward.dao.InwardChequeDao;
import com.cts.inward.dao.InwardChequeDaoImpl;
import com.cts.inward.dto.V2LoadResult;
import com.cts.inward.entity.InwardCheque;
import com.cts.inward.enums.ChequeStatus;
import com.cts.inward.enums.DecisionStatus;
import com.cts.inward.enums.SendTo;

/**
 * File    : TV2_ResubmittedChequeServiceImpl.java
 * Package : com.cts.inward.service
 * Purpose : Implements TV2_ResubmittedChequeService — Verifier 2 (Branch Manager) approve,
 *           reject, and return-to-maker logic for high-value cheques. Delegates all DB
 *           operations to InwardChequeDao.
 *           Approval remarks are optional; reject/return remarks are mandatory.
 * Author  : Ramana
 * Date    : 24-06-2025
 */
public class TV2_ResubmittedChequeServiceImpl implements TV2_ResubmittedChequeService {

    private final InwardChequeDao v2Dao = new InwardChequeDaoImpl();

    // ── Queue load ─────────────────────────────────────────────────────────

    @Override
    public List<InwardCheque> getResubmittedChequesForVerifier2() {
        return v2Dao.findResubmittedForVerifier2();
    }

    /**
     * Loads a cheque for the V2 review workspace.
     * Returns a V2LoadResult describing whether the cheque is actionable.
     */
    @Override
    public V2LoadResult getChequeForV2Review(String chequeNo) {

        if (chequeNo == null || chequeNo.isBlank()) {
            return V2LoadResult.notFound("(empty)");
        }

        Optional<InwardCheque> found = v2Dao.findByChequeNoTV2(chequeNo.trim());

        if (found.isEmpty()) {
            return V2LoadResult.notFound(chequeNo);
        }

        InwardCheque cheque = found.get();

        // Verifier 2 (TV_2) should not process Checker (TV_1) cheques
        if (SendTo.TV_2 != cheque.getSendTo()) {
            return V2LoadResult.wrongRoute(chequeNo);
        }

        DecisionStatus decision = cheque.getDecision();

        if (DecisionStatus.ACCEPTED == decision) {
            return V2LoadResult.alreadyAccepted(chequeNo);
        }
        if (DecisionStatus.REJECTED == decision) {
            return V2LoadResult.alreadyRejected(chequeNo);
        }

        ChequeStatus status = cheque.getChequeStatus();

        switch (status) {
            case Resubmitted:
                return V2LoadResult.ok(cheque);
            case Repair:
                return V2LoadResult.returnedToMaker(chequeNo);
            default:
                return V2LoadResult.wrongStatus(
                    chequeNo, status != null ? status.name() : "null");
        }
    }

    // ── Verifier 2 actions ─────────────────────────────────────────────────

    /**
     * Approves the cheque.
     * Sets chequeStatus = Ready and decision = ACCEPTED.
     *
     * REMARKS ARE OPTIONAL HERE — unlike rejectCheque()/returnToMaker().
     * A Branch Manager approving a cheque has nothing to explain, so we
     * don't force a remark. If one was typed, we still save it for reference.
     */
    @Override
    public InwardCheque approveCheque(String chequeNo, String v2UserId, String v2Remarks) {
        InwardCheque cheque = loadChequeOrThrow(chequeNo);

        cheque.setChequeStatus(ChequeStatus.Ready);
        cheque.setDecision(DecisionStatus.ACCEPTED);
        cheque.setSendbackReason(
            v2Remarks != null && !v2Remarks.isBlank() ? v2Remarks.trim() : null);
        cheque.setRejectReason(null);

        v2Dao.updateVerifier2Decision(cheque);
        return cheque;
    }

    /**
     * Permanently rejects the cheque.
     * Sets chequeStatus = Reject and decision = REJECTED.
     * The rejection reason is stored in checker_reject_reason.
     */
    @Override
    public InwardCheque rejectCheque(String chequeNo, String v2UserId, String v2Remarks) {
        validateRemarks(v2Remarks, "Verifier 2 remarks are required before rejecting.");

        InwardCheque cheque = loadChequeOrThrow(chequeNo);

        cheque.setChequeStatus(ChequeStatus.Reject);
        cheque.setDecision(DecisionStatus.REJECTED);
        cheque.setRejectReason(v2Remarks.trim());
        // FIX: was clearing referReason (wrong field) instead of sendbackReason.
        // sendbackReason is the field actually set by approveCheque()/returnToMaker(),
        // so it must be cleared here too — otherwise a stale value from an earlier
        // return-to-maker round would still show up after a permanent rejection.
        cheque.setSendbackReason(null);

        v2Dao.updateVerifier2Decision(cheque);
        return cheque;
    }

    /**
     * Returns the cheque to maker for re-correction.
     * Sets chequeStatus = Repair, decision = REFERRED, sendTo = MAKER.
     * The refer-back reason is stored in checker_refer_reason.
     * Resets isEditedByMaker and editedFields for a fresh correction round.
     */
    @Override
    public InwardCheque returnToMaker(String chequeNo, String v2UserId, String v2Remarks) {
        validateRemarks(v2Remarks, "Verifier 2 remarks are required before returning to maker.");

        InwardCheque cheque = loadChequeOrThrow(chequeNo);

        cheque.setChequeStatus(ChequeStatus.Repair);
        cheque.setDecision(DecisionStatus.REFERRED);
        cheque.setSendTo(SendTo.MAKER);
        cheque.setSendbackReason(v2Remarks.trim());
        cheque.setRejectReason(null);

        cheque.setIsEditedByMaker(false);
        cheque.setEditedFields(null);

        v2Dao.updateVerifier2Decision(cheque);
        return cheque;
    }

    @Override
    public long getPendingResubmittedCount() {
        return v2Dao.countResubmittedForVerifier2();
    }

    // ── Private helpers ────────────────────────────────────────────────────

    private void validateRemarks(String remarks, String errorMessage) {
        if (remarks == null || remarks.trim().isEmpty()) {
            throw new IllegalArgumentException(errorMessage);
        }
    }

    private InwardCheque loadChequeOrThrow(String chequeNo) {
        return v2Dao.findResubmittedByChequeNoTV2(chequeNo)
            .orElseThrow(() -> new RuntimeException(
                "Cheque not found or no longer in Resubmitted state (V2): " + chequeNo));
    }
}