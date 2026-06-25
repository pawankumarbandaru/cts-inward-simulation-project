package com.cts.inward.service;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

import com.cts.inward.entity.InwardBatch;
import com.cts.inward.entity.InwardCheque;
import com.cts.inward.enums.BatchStatus;
import com.cts.inward.enums.CbsValidation;
import com.cts.inward.enums.ChequeStatus;
import com.cts.inward.enums.DecisionStatus;
import com.cts.inward.enums.SendTo;


public interface InwardChequeMICRService {

    // ============================================================
    // CHEQUE OPERATIONS
    // ============================================================

    List<InwardCheque> getChequesByBatchId(Long batchId);

    // Role-based queue listings — yours only
    List<InwardCheque> TV1_ChequesList(Long batchId);
    List<InwardCheque> TV2_ChequesList(Long currentBatchId);

    InwardCheque getChequeById(Long id);

    void updateCheque(InwardCheque inwardCheque);

    // Updation of status after approve or reject or refer — yours only
    void update(InwardCheque selectedCheque);
    void updateChequeStatus(
            InwardCheque cheque,
            DecisionStatus decision,
            ChequeStatus chequeStatus,
            SendTo sendTo);
    String validateChequeForSave(
            String chequeNo,
            String cityCode,
            String bankCode,
            String branchCode,
            String displayedMicr,
            String transactionCode,
            String accountNo,
            String chequeDate,
            String amount,
            String amountInWords);
    
    String formatChequeDate(java.time.LocalDateTime chequeDate);

    boolean isMicrError(InwardCheque cheque);
    // ============================================================
    // BATCH OPERATIONS
    // ============================================================

    long getTotalChequeCount(Long batchId, String role);

    long getMicrErrorCount(Long batchId);

    void updateBatchStatus(Long id, BatchStatus pending);

    // Retrieves the current status of a batch — teammate only
    BatchStatus getBatchStatus(Long batchId);

    // Retrieves complete batch information — teammate only
    InwardBatch getBatchById(Long batchId);

    // Retrieves the latest uploaded batch — teammate only
    InwardBatch getLatestBatch();
    
    Long resolveLatestBatchId();

    // Role-aware cheque count — yours only
    long getChequeCountByRole(Long currentBatchId, String currentRole);

    // Drives CBS-validation button enable/disable in Maker — yours only
    long getNonNormalChequeCount(Long currentBatchId);


    // ============================================================
    // CBS VALIDATION OPERATIONS
    // ============================================================

    List<InwardCheque> getInvalidChequesByBatchId(Long batchId);

    void updateCbsValidationResult(Long chequeId, CbsValidation cbsValidation, String errorReason);

 // Returns {tv1Count, tv2Count} so the composer can show how many
 // cheques went to each queue.
 long[] forwardToTvQueuesByThreshold(Long batchId, BigDecimal threshold);

    void markInvalidChequesAsRejected(Long batchId);


    // ============================================================
    // MICR PARSING & BUILDING
    // ============================================================

    String[] parseMicrParts(String rawMicrCode);

    String buildMicrCode(String cityCode, String bankCode, String branchCode);

    boolean isMicrMatch(String cityCode, String bankCode, String branchCode, String displayedMicr);

    String buildMicrMismatchMessage(String cityCode, String bankCode, String branchCode, String displayedMicr);

    // Validates MICR code length — teammate only
    boolean isMicrSourceLengthValid(String rawMicrCode);


    // ============================================================
    // FIELD VALIDATION RULES
    // ============================================================

    // Individual rules — teammate only (validateField below dispatches to these)
    String validateChequeNumber(String value);
    String validateCodePart(String value, String label);
    String validateTransactionCode(String value);
    String validateAccountNumber(String value);
    String validateAmountValue(String value);

    String validateField(String fieldId, String value);


    // ============================================================
    // SAVE BLOCKING VALIDATIONS
    // ============================================================

    String getChequeIdentityBlockingMessage(boolean chequeNoOk, boolean cityOk, boolean bankOk, boolean branchOk);

    String getChequeDetailsBlockingMessage(boolean transactionCodeOk, boolean accountNoOk, boolean amountOk);


    // ============================================================
    // AMOUNT CONVERSION
    // ============================================================

    String convertAmountToWords(BigDecimal amount);

    String generateAmountInWordsDisplay(String amountStr);


    // ============================================================
    // DATE HANDLING
    // ============================================================

    // NOTE: kept as LocalDateTime (teammate's signature), not void.
    // saveChequeEdit() below calls cheque.setChequeDate(parseChequeDate(dateStr)),
    // which requires a LocalDateTime return — a void signature here would not
    // compile against the real InwardChequeServiceMICRImpl.saveChequeEdit().
    LocalDateTime parseChequeDate(String dateStr);


    // ============================================================
    // CHEQUE EDIT & MICR REPAIR
    // ============================================================

    void saveChequeEdit(
            InwardCheque cheque,
            String chequeNo,
            String cityCode,
            String bankCode,
            String branchCode,
            String transactionCode,
            String accountNo,
            String chequeDateStr,
            String amountStr,
            String amountInWords);

        public boolean isMakerEditedAndPendingReview(InwardCheque cheque);

        public void updateMICR(InwardCheque selectedCheque, DecisionStatus newDecision,
                ChequeStatus newChequeStatus, SendTo newSendTo
        );
        
}