package com.cts.inward.service;

/**
 * File    : InwardChequeServiceImpl.java
 * Package : com.cts.inward.service
 * Purpose : Implements InwardChequeService — handles business logic for the Maker
 *           repair flow including input validation, CBS result saving, resubmit routing,
 *           and RRF generation. Delegates all DB operations to the DAO layer.
 * Author  : Ramana
 * Date    : 24-06-2025
 */

import java.util.List;
import java.util.Optional;

import com.cts.exceptions.CbsValidationException;
import com.cts.exceptions.ChequeNotFoundException;
import com.cts.exceptions.InvalidChequeDataException;
import com.cts.inward.dao.InwardBatchDao;
import com.cts.inward.dao.InwardBatchDaoImpl;
import com.cts.inward.dao.InwardChequeDao;
import com.cts.inward.dao.InwardChequeDaoImpl;
import com.cts.inward.entity.InwardBatch;
import com.cts.inward.entity.InwardCheque;
import com.cts.inward.enums.CbsValidation;
import com.cts.inward.enums.ChequeStatus;
import com.cts.inward.enums.DecisionStatus;
import com.cts.inward.enums.SendTo;
import com.cts.util.ChequeRoutingUtil;

public class InwardChequeServiceImpl implements InwardChequeService {

    private final InwardBatchDao  batchDao;
    private final InwardChequeDao chequeDao;

    /** Default constructor — used by all composers and components in production. */
    public InwardChequeServiceImpl() {
        this.batchDao  = new InwardBatchDaoImpl();
        this.chequeDao = new InwardChequeDaoImpl();
    }

    /**
     * Constructor for unit tests — allows injecting mock DAOs without hitting the real DB.
     * Production code never calls this directly.
     *
     * @param batchDao  mock or real InwardBatchDao
     * @param chequeDao mock or real InwardChequeDao
     */
    public InwardChequeServiceImpl(InwardBatchDao batchDao, InwardChequeDao chequeDao) {
        this.batchDao  = batchDao;
        this.chequeDao = chequeDao;
    }

    // ── Basic load ─────────────────────────────────────────────────────────────

    @Override
    public List<InwardBatch> getAllBatches() {
        return batchDao.findAll();
    }

    @Override
    public List<InwardCheque> getChequesByBatchId(String batchId) {
        if (batchId == null || batchId.isBlank())
            throw new InvalidChequeDataException("Batch ID cannot be empty");
        return chequeDao.findByBatchId(batchId);
    }

    @Override
    public void updateCheque(InwardCheque cheque) {
        if (cheque == null) throw new InvalidChequeDataException("Cheque cannot be null");
        chequeDao.update(cheque);
    }

    // ── Maker queue ────────────────────────────────────────────────────────────

    @Override
    public List<InwardCheque> getChequesNeedingCorrection() {
        return chequeDao.findAllNeedingCorrection();
    }

    /**
     * Loads a cheque for the Repair Workspace only if it is in the actionable Maker state:
     * chequeStatus = Repair, decision = REFERRED, sendTo = MAKER.
     * Returns empty if the cheque exists but is no longer actionable.
     */
    @Override
    public Optional<InwardCheque> getChequeForRepair(String chequeNo) {
        if (chequeNo == null || chequeNo.isBlank()) return Optional.empty();

        Optional<InwardCheque> found = chequeDao.findByChequeNumber(chequeNo.trim());

        if (found.isPresent()) {
            InwardCheque c = found.get();
            boolean isRepairStatus = ChequeStatus.Repair    == c.getChequeStatus();
            boolean isReferred     = DecisionStatus.REFERRED == c.getDecision();
            boolean isSentToMaker  = SendTo.MAKER            == c.getSendTo();

            if (!isRepairStatus || !isReferred || !isSentToMaker) {
                return Optional.empty();
            }
        }
        return found;
    }

    // ── Maker save corrections ─────────────────────────────────────────────────

    @Override
    public void saveCorrections(InwardCheque cheque) {
        if (cheque == null)
            throw new InvalidChequeDataException("Cheque cannot be null.");
        if (cheque.getChequeNo() == null || cheque.getChequeNo().isBlank())
            throw new InvalidChequeDataException("Cheque number is required.");
        if (cheque.getAmount() == null || cheque.getAmount().doubleValue() <= 0)
            throw new InvalidChequeDataException("Valid amount is required.");

        chequeDao.saveCorrections(cheque);
    }

    // ── Maker resubmit to checker ──────────────────────────────────────────────

    /**
     * Resubmits the corrected cheque to the Checker queue.
     * Routes to TV_1 or TV_2 based on amount via ChequeRoutingUtil.
     *
     * @param chequeNo     cheque number to resubmit
     * @param makerUserId  logged-in maker's user ID
     * @throws ChequeNotFoundException if the cheque does not exist
     */
    @Override
    public void resubmitToChecker(String chequeNo, String makerUserId) {
        InwardCheque cheque = chequeDao.findByChequeNumber(chequeNo)
            .orElseThrow(() -> new ChequeNotFoundException(chequeNo));

        SendTo routeTo = determineCheckerRoute(cheque);

        cheque.setChequeStatus(ChequeStatus.Resubmitted);
        cheque.setDecision(DecisionStatus.REFERRED);
        cheque.setSendTo(routeTo);
        cheque.setCbsValidation(CbsValidation.Valid);
        cheque.setResubmittedBy(makerUserId);

        chequeDao.resubmitToChecker(cheque);
    }

    // ── Generate RRF ───────────────────────────────────────────────────────────

    /**
     * Marks the cheque as REJECTED and CBS Invalid for RRF generation.
     *
     * @param chequeNo     cheque number
     * @param makerUserId  logged-in maker's user ID
     * @throws ChequeNotFoundException if the cheque does not exist
     */
    @Override
    public void generateRRF(String chequeNo, String makerUserId) {
        InwardCheque cheque = chequeDao.findByChequeNumber(chequeNo)
            .orElseThrow(() -> new ChequeNotFoundException(chequeNo));

        cheque.setDecision(DecisionStatus.REJECTED);
        cheque.setCbsValidation(CbsValidation.Invalid);
        cheque.setResubmittedBy(makerUserId);

        chequeDao.generateRRF(cheque);
    }

    // ── Save CBS result ────────────────────────────────────────────────────────

    @Override
    public void saveCbsResult(Long chequeId, boolean isValid, String failureReason) {
        if (chequeId == null)
            throw new CbsValidationException("Cheque id is required to save CBS result.");
        chequeDao.saveCbsResult(chequeId, isValid, failureReason);
    }

    // ── Count ──────────────────────────────────────────────────────────────────

    @Override
    public long getPendingCorrectionCount() {
        return chequeDao.countNeedingCorrection();
    }

    // ── Private helpers ────────────────────────────────────────────────────────

    /**
     * Determines whether the cheque should be routed to TV_1 (Checker)
     * or TV_2 (Verifier 2) based on its amount.
     */
    private SendTo determineCheckerRoute(InwardCheque cheque) {
        double amount = (cheque.getAmount() != null) ? cheque.getAmount().doubleValue() : 0.0;
        return ChequeRoutingUtil.routeFor(amount);
    }
    
    @Override
    public List<InwardCheque> getReferredChequesForTV2() {
        return chequeDao.listOfReferredCheques();
    }
}