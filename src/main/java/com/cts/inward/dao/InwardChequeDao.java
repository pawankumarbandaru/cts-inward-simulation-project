package com.cts.inward.dao;

import java.util.List;
import java.util.Optional;

import com.cts.inward.dto.ChequeReportDTO;
import com.cts.inward.dto.ReportChequeDetailDTO;
import com.cts.inward.entity.InwardCheque;
import com.cts.inward.enums.CbsValidation;
import com.cts.inward.enums.ChequeStatus;
import com.cts.inward.enums.DecisionStatus;
import com.cts.inward.enums.SendTo;

public interface InwardChequeDao {

	void save(InwardCheque cheque);

	void update(InwardCheque cheque);

	boolean existsByChequeNumber(String chequeNumber);

	List<InwardCheque> findAll();

	List<InwardCheque> findByBatchId(String batchId);

	InwardCheque findById(Long id);

	/** DTO projection — only reads decision column, never touches cheque_status */
	List<ChequeReportDTO> findAcceptedCheques(String batchId);


	/**
	 * Saves maker's field corrections using native SQL. Updates all editable fields
	 * in inward_cheque where id = cheque.getId().
	 */
	void saveCorrections(InwardCheque cheque);

	/**
	 * Fetches all cheques where: cheque_status = Repair, decision = REFERRED,
	 * send_to = MAKER These are cheques returned by checker waiting for maker to
	 * fix.
	 */
	List<InwardCheque> findAllNeedingCorrection();

	/**
	 * Count of cheques waiting for maker correction. Used for sidebar badge count
	 * display.
	 */
	long countNeedingCorrection();

	Optional<InwardCheque> findByChequeNumber(String chequeNumber);
	
	
	
	List<InwardCheque> findByBatchId(Long batchId);

	List<InwardCheque> TV1_ChequesList(Long batchId);

	List<InwardCheque> TV2_ChequesList(Long batchId);


	void updateMICR(InwardCheque inwardCheque);

	long countByBatchId(Long batchId, String role);

	long countMicrErrorsByBatchId(Long batchId);

	List<InwardCheque> findInvalidByBatchId(Long batchId);

	void updateCbsValidationResult(Long chequeId, CbsValidation cbsValidation, String errorReason);

	void forwardToTvQueuesByThreshold(Long batchId, java.math.BigDecimal threshold);

	void updateDecisionToRejectedForBatch(Long batchId);

	// Getting the TV1 Cheque count
	// long getTV1ChequeCount(Long currentBatchId);

	// Fetches TV1 or TV2 data according to the login
	long getChequeCountByRole(Long batchId, String role);

	// cbs button enable and disable is dependent on this method in maker
	// if all cheques status is normal then it will enable the cbs validation button
	long getNonNormalChequeCount(Long batchId);
	
	
	
	

	
	/**
	 * Updates cheque status to Resubmitted and routes it to TV1 or TV2. Uses native
	 * SQL to avoid enum type casting issues with PostgreSQL.
	 */
	void resubmitToChecker(InwardCheque cheque);
	
	  /**
     * Returns all cheques with status=Resubmitted, sendTo=TV_1, decision=REFERRED,
     * ordered by resubmitted_at DESC.
     */
    List<InwardCheque> findResubmittedForChecker();

    /**
     * Returns the count of resubmitted cheques waiting for Checker.
     * Used for the sidebar pending badge.
     */
    long countResubmittedForChecker();

    /**
     * Returns a single cheque by cheque number only when it is in the
     * actionable state (status=Resubmitted, sendTo=TV_1, decision=REFERRED).
     * Returns empty if the cheque has already been processed.
     */
    Optional<InwardCheque> findResubmittedByChequeNoTV1(String chequeNo);

    /**
     * Returns a single cheque by cheque number with no status filter.
     * Used by getChequeForCheckerReview() to return a meaningful message
     * even when the cheque has already been processed.
     */
    Optional<InwardCheque> findByChequeNoTV1(String chequeNo);

    /**
     * Persists the checker's decision for a cheque.
     * Updates: chequeStatus, decision, sendTo, checkerRole, rejectedBy,
     * rejectionReason, rejectedAt, isEditedByMaker, editedFields.
     *
     * @param cheque entity with all decision fields already set by the service
     */
    void updateCheckerDecision(InwardCheque cheque);
    
    //TV2 Dashboard
    
    /**
     * METHOD : findResubmittedForVerifier2()
     *
     * WHAT IT DOES:
     *   Fetches all cheques that are waiting for Verifier 2 (Branch Manager) to review.
     *
     * FILTER CONDITIONS (all 3 must be true):
     *   cheque_status = 'Resubmitted'  → Maker has corrected and sent back
     *   send_to       = 'TV_2'         → Routed to Verifier 2 (Branch Manager)
     *   decision      = 'REFERRED'     → No final decision given yet
     *
     * ORDERED BY: created_at DESC   (newest first)
     *   Note: resubmitted_at column was deleted from DB, so we order by created_at
     *
     * CALLED FROM:
     *   RA_V2_Verifier2ChequeServiceImpl.getResubmittedChequesForVerifier2()
     *   → which is called from RA_V2_Verifier2ResubmittedQueueComposer.loadData()
     *
     * RETURNS: List of InwardCheque — may be empty, never null
     */
    List<InwardCheque> findResubmittedForVerifier2();

    /**
     * METHOD : countResubmittedForVerifier2()
     *
     * WHAT IT DOES:
     *   Returns the total count of cheques waiting for Verifier 2.
     *   Same filter as findResubmittedForVerifier2() — just COUNT(*) instead of SELECT.
     *
     * CALLED FROM:
     *   RA_V2_Verifier2ChequeServiceImpl.getPendingResubmittedCount()
     *   → which is called from TV2SidebarComposer to show the pending badge number
     *
     * RETURNS: long — number of pending cheques (0 if none)
     */
    long countResubmittedForVerifier2();

    /**
     * METHOD : findResubmittedByChequeNo(String chequeNo)
     *
     * WHAT IT DOES:
     *   Finds a specific cheque by cheque number, but ONLY if it is currently
     *   in the actionable state (Resubmitted + TV_2 + REFERRED).
     *   If the cheque was already approved/rejected, this returns empty.
     *
     * CALLED FROM:
     *   RA_V2_Verifier2ChequeServiceImpl.loadChequeOrThrow()
     *   → which is called before any approve/reject/returnToMaker action
     *   → ensures we don't process a cheque that is no longer in the right state
     *
     * INPUT:
     *   chequeNo — the cheque number (e.g., "CHQ001234")
     *
     * RETURNS:
     *   Optional<InwardCheque> — present if cheque is actionable, empty if not
     */
    Optional<InwardCheque> findResubmittedByChequeNoTV2(String chequeNo);

    /**
     * METHOD : findByChequeNo(String chequeNo)
     *
     * WHAT IT DOES:
     *   Finds a cheque by cheque number with NO status filter.
     *   This is used when we want to give the user a meaningful error message
     *   even if the cheque is already approved, rejected, or in some other state.
     *
     * DIFFERENCE from findResubmittedByChequeNo():
     *   findResubmittedByChequeNo() → only returns if currently actionable
     *   findByChequeNo()            → returns regardless of status (for status-check messages)
     *
     * CALLED FROM:
     *   RA_V2_Verifier2ChequeServiceImpl.getChequeForV2Review()
     *   → used to check what status the cheque is in and show the right message
     *
     * INPUT:
     *   chequeNo — the cheque number to search
     *
     * RETURNS:
     *   Optional<InwardCheque> — present if cheque exists at all, empty if not found
     */
    Optional<InwardCheque> findByChequeNoTV2(String chequeNo);

    /**
     * METHOD : updateVerifier2Decision(InwardCheque cheque)
     *
     * WHAT IT DOES:
     *   Saves the Verifier 2 decision to the database.
     *   This is called after the service has already set all the decision fields
     *   on the cheque entity. This method just writes those values to the DB.
     *
     * FIELDS UPDATED in DB:
     *   cheque_status         → Ready / Reject / Repair (depends on action)
     *   decision              → ACCEPTED / REJECTED / REFERRED
     *   send_to               → VERIFIER_2 / MAKER (depends on action)
     *   checker_role          → "VERIFIER_2" (identifies who acted)
     *   checker_refer_reason  → reason given when approving or returning to maker
     *   checker_reject_reason → reason given when permanently rejecting
     *   is_edited_by_maker    → reset to false when returning to maker
     *   edited_fields         → reset to null when returning to maker
     *
     * FIELDS REMOVED (were in old version, now deleted from DB):
     *   rejected_by → removed
     *   rejected_at → removed
     *
     * CALLED FROM:
     *   RA_V2_Verifier2ChequeServiceImpl.approveCheque()
     *   RA_V2_Verifier2ChequeServiceImpl.rejectCheque()
     *   RA_V2_Verifier2ChequeServiceImpl.returnToMaker()
     *
     * INPUT:
     *   cheque — the InwardCheque entity with all decision fields already set by service
     *
     * THROWS: RuntimeException if the DB update fails (with rollback)
     */
    void updateVerifier2Decision(InwardCheque cheque);
    
    void updateTv2Decision(Long chequeId, DecisionStatus decision, ChequeStatus chequeStatus, SendTo sendTo);
    

	/**
	 * Sets decision = REJECTED on the cheque for RRF generation. Uses native SQL to
	 * avoid enum type casting issues with PostgreSQL.
	 */
	void generateRRF(InwardCheque cheque);
	
	public List<ReportChequeDetailDTO> findAllChequesForReport(String batchId);
	
	/** DTO projection — only reads decision column, never touches cheque_status */
	List<ChequeReportDTO> findReturnedCheques(String batchId);
	
	
	 /**
     * @param chequeId       the ID of the InwardCheque record
     * @param isValid        true if CBS validation passed, false if it failed
     * @param failureReason  the failure message (pass null if isValid is true)
     */
    void saveCbsResult(Long chequeId, boolean isValid, String failureReason);

	 List<InwardCheque> listOfReferredCheques();

}
