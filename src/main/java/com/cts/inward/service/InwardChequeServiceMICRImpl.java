package com.cts.inward.service;


import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;

import com.cts.inward.dao.InwardBatchDao;
import com.cts.inward.dao.InwardBatchDaoImpl;
import com.cts.inward.dao.InwardChequeDao;
import com.cts.inward.dao.InwardChequeDaoImpl;
import com.cts.inward.entity.InwardBatch;
import com.cts.inward.entity.InwardCheque;
import com.cts.inward.enums.BatchStatus;
import com.cts.inward.enums.CbsValidation;
import com.cts.inward.enums.ChequeStatus;
import com.cts.inward.enums.DecisionStatus;
import com.cts.inward.enums.SendTo;

public class InwardChequeServiceMICRImpl implements InwardChequeMICRService {

    private final InwardChequeDao inwardChequeDao = new InwardChequeDaoImpl();
    private final InwardBatchDao inwardBatchDao = new InwardBatchDaoImpl();

    // Added — needed by parseChequeDate (teammate's version, used by saveChequeEdit)
    private static final DateTimeFormatter CHEQUE_DATE_DISPLAY_FMT =
            DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm");
    private static final DateTimeFormatter CHEQUE_LIST_DATE_FMT =
            DateTimeFormatter.ofPattern("dd-MM-yyyy");

    // Added — needed by convertAmountToWords / amountInWords (teammate's
    // Indian-banking-format amount-to-words converter)
    private static final String[] ONES = {
        "", "One", "Two", "Three", "Four", "Five", "Six", "Seven",
        "Eight", "Nine", "Ten", "Eleven", "Twelve", "Thirteen",
        "Fourteen", "Fifteen", "Sixteen", "Seventeen", "Eighteen", "Nineteen"
    };
    private static final String[] TENS = {
        "", "", "Twenty", "Thirty", "Forty", "Fifty",
        "Sixty", "Seventy", "Eighty", "Ninety"
    };

    // ============================================================
    // CHEQUE OPERATIONS — yours, kept as-is
    // ============================================================

    @Override
    public List<InwardCheque> getChequesByBatchId(Long batchId) {
        if (batchId == null) {
            throw new IllegalArgumentException("Batch ID cannot be null");
        }
        return inwardChequeDao.findByBatchId(batchId);
    }

    @Override
    public InwardCheque getChequeById(Long id) {
        if (id == null) {
            throw new IllegalArgumentException("Cheque ID cannot be null");
        }
        return inwardChequeDao.findById(id);
    }

    @Override
    public void updateCheque(InwardCheque inwardCheque) {
        if (inwardCheque == null) {
            throw new IllegalArgumentException("Cheque cannot be null");
        }
        inwardChequeDao.update(inwardCheque);
    }

    
    
    @Override
    public long getMicrErrorCount(Long batchId) {
        return inwardChequeDao.countMicrErrorsByBatchId(batchId);
    }

    @Override
    public List<InwardCheque> getInvalidChequesByBatchId(Long batchId) {
        return inwardChequeDao.findInvalidByBatchId(batchId);
    }

    @Override
    public void updateCbsValidationResult(Long chequeId, CbsValidation cbsValidation, String errorReason) {
        inwardChequeDao.updateCbsValidationResult(chequeId, cbsValidation, errorReason);
    }
    @Override
    public long[] forwardToTvQueuesByThreshold(Long batchId, java.math.BigDecimal threshold) {
        inwardChequeDao.forwardToTvQueuesByThreshold(batchId, threshold);

        // Batch status is already PendingAtChecker by now (composer sets it
        // before calling this), so getChequesByBatchId() returns only the
        // Valid cheques — rejected ones are excluded automatically.
        List<InwardCheque> forwardedCheques = getChequesByBatchId(batchId);

        long tv1Count = forwardedCheques.stream()
                .filter(c -> SendTo.TV_1.equals(c.getSendTo()))
                .count();
        long tv2Count = forwardedCheques.stream()
                .filter(c -> SendTo.TV_2.equals(c.getSendTo()))
                .count();

        return new long[] { tv1Count, tv2Count };
    }

    @Override
    public void markInvalidChequesAsRejected(Long batchId) {
        inwardChequeDao.updateDecisionToRejectedForBatch(batchId);
    }

    @Override
    public void updateBatchStatus(Long id, BatchStatus batchStatus) {
        // TODO Auto-generated method stub
        inwardBatchDao.updateBatchStatus(id, batchStatus);
    }

    @Override
    public List<InwardCheque> TV1_ChequesList(Long batchId) {
        // TODO Auto-generated method stub
        return inwardChequeDao.TV1_ChequesList(batchId);
    }

    @Override
    public List<InwardCheque> TV2_ChequesList(Long batchId) {
        // TODO Auto-generated method stub
        return inwardChequeDao.TV2_ChequesList(batchId);
    }

    @Override
    public void update(InwardCheque selectedCheque) {
        // TODO Auto-generated method stub
        inwardChequeDao.update(selectedCheque);
    }

//	@Override
//	public long getTV1ChequeCount(Long currentBatchId) {
//		// TODO Auto-generated method stub
//		return inwardChequeDao.getTV1ChequeCount(currentBatchId);
//	}

    //Fetches TV1 or TV2 data according to the login
    @Override
    public long getChequeCountByRole(Long batchId, String role) {
        // TODO Auto-generated method stub
        return inwardChequeDao.getChequeCountByRole(batchId, role);
    }

    //cbs button enable and disable is dependent on this method in maker
    //if all cheques status is normal then it will enable the cbs validation button
    @Override
    public long getNonNormalChequeCount(Long batchId) {
        // TODO Auto-generated method stub
        return inwardChequeDao.getNonNormalChequeCount(batchId);
    }

    // ============================================================
    // BATCH OPERATIONS — getBatchStatus/getBatchById/getLatestBatch
    // added from teammate's file (you didn't have these)
    // ============================================================

    @Override
    public BatchStatus getBatchStatus(Long batchId) {
        if (batchId == null) {
            throw new IllegalArgumentException("Batch ID cannot be null");
        }
        InwardBatch batch = inwardBatchDao.findById(batchId);
        return batch != null ? batch.getBatchStatus() : null;
    }

    @Override
    public InwardBatch getBatchById(Long batchId) {
        if (batchId == null) {
            throw new IllegalArgumentException("Batch ID cannot be null");
        }
        return inwardBatchDao.findById(batchId);
    }

    @Override
    public InwardBatch getLatestBatch() {
        return inwardBatchDao.findLatest();
    }
    
    @Override
    public Long resolveLatestBatchId() {
        try {
            InwardBatch latest = getLatestBatch();
            if (latest != null) {
                System.out.println("InwardChequeServiceMICRImpl: no batchDbId passed, "
                        + "using latest batch id = " + latest.getId());
                return latest.getId();
            }
        } catch (Exception e) {
            System.err.println("InwardChequeServiceMICRImpl: could not load latest batch: "
                    + e.getMessage());
        }
        return null;
    }

    // ============================================================
    // MICR PARSING / BUILDING — common, kept as your version
    // ============================================================

    // MICR — skip the first 6 digits (cheque number prefix), then read the
    // next 9 digits as city(3) + bank(3) + branch(3): positions 7-9, 10-12,
    // 13-15. The trailing 6-digit base number (positions 16-21) is
    // intentionally ignored for now.
    @Override
    public String[] parseMicrParts(String rawMicrCode) {
        String rawMicr    = rawMicrCode != null ? rawMicrCode : "";
        String cityCode   = rawMicr.length() >= 3 ? rawMicr.substring(0, 3) : "";
        String bankCode   = rawMicr.length() >= 6 ? rawMicr.substring(3, 6) : "";
        String branchCode = rawMicr.length() >= 9 ? rawMicr.substring(6, 9) : "";
        return new String[] { cityCode, bankCode, branchCode };
    }

    @Override
    public String buildMicrCode(String cityCode, String bankCode, String branchCode) {
        return (cityCode != null ? cityCode.trim() : "")
                + (bankCode != null ? bankCode.trim() : "")
                + (branchCode != null ? branchCode.trim() : "");
    }

    @Override
    public boolean isMicrMatch(String cityCode, String bankCode, String branchCode, String displayedMicr) {
        String computed  = buildMicrCode(cityCode, bankCode, branchCode);
        String displayed = displayedMicr != null ? displayedMicr.trim() : "";
        return computed.equals(displayed);
    }

    // Single source of truth for the MICR-mismatch text shown to the user —
    // both the save-time check and the live-typing check use this.
    @Override
    public String buildMicrMismatchMessage(String cityCode, String bankCode, String branchCode, String displayedMicr) {
        String computed  = buildMicrCode(cityCode, bankCode, branchCode);
        String displayed = displayedMicr != null ? displayedMicr.trim() : "";
        return "MICR mismatch: City(" + cityCode + ") + Bank(" + bankCode
                + ") + Branch(" + branchCode + ") = " + computed
                + " but MICR shows " + displayed;
    }

    @Override
    public boolean isMicrSourceLengthValid(String rawMicrCode) {
        String rawMicr = rawMicrCode != null ? rawMicrCode : "";
        return rawMicr.length() == 9;
    }

    // ============================================================
    // FIELD VALIDATION RULES — common, kept as your version
    // ============================================================

    @Override
    public String validateChequeNumber(String value) {
        String v = value != null ? value.trim() : "";
        if (v.isEmpty())             return "Cheque Number is required.";
        if (!v.matches("\\d+"))      return "Cheque Number must contain digits only (no letters/symbols).";
        if (v.length() != 6)         return "Cheque Number must be exactly 6 digits.";
        return null;
    }

    @Override
    public String validateCodePart(String value, String label) {
        String v = value != null ? value.trim() : "";
        if (v.isEmpty())             return label + " is required (3 digits).";
        if (!v.matches("\\d+"))      return label + " must contain digits only (no letters/symbols).";
        if (v.length() != 3)         return label + " must be exactly 3 digits.";
        return null;
    }

    @Override
    public String validateTransactionCode(String value) {
        String v = value != null ? value.trim() : "";
        if (v.isEmpty())                          return "Transaction Code is required.";
        if (!v.matches("\\d+"))                   return "Transaction Code must contain digits only.";
        if (v.length() < 2 || v.length() > 3)     return "Transaction Code must be 2 to 3 digits.";
        return null;
    }

    @Override
    public String validateAccountNumber(String value) {
        String v = value != null ? value.trim() : "";
        if (v.isEmpty())             return "Account Number is required.";
        if (!v.matches("\\d+"))      return "Account Number must contain digits only (no letters/symbols).";
        return null;
    }

    @Override
    public String validateAmountValue(String value) {
        String v = value != null ? value.trim() : "";
        if (v.isEmpty()) {
            return "Amount is required.";
        }
        try {
            if (new BigDecimal(v).compareTo(BigDecimal.ZERO) < 0) {
                return "Amount cannot be negative.";
            }
        } catch (NumberFormatException e) {
            return "Amount must be a valid number. Example: 35000.00";
        }
        return null;
    }

    // Dispatches to the correct rule above based on the field id —
    // moved out of the composer's checkFieldFormat switch statement.
    @Override
    public String validateField(String fieldId, String value) {
        if (fieldId == null) return null;
        switch (fieldId) {
            case "tbChequeNo":        return validateChequeNumber(value);
            case "tbCityCode":        return validateCodePart(value, "City Code");
            case "tbBankCode":        return validateCodePart(value, "Bank Code");
            case "tbBranchCode":      return validateCodePart(value, "Branch Code");
            case "tbTransactionCode": return validateTransactionCode(value);
            case "tbAccountNo":       return validateAccountNumber(value);
            case "tbAmount":          return validateAmountValue(value);
            default:                  return null; // no format rule defined for this field
        }
    }

    // ============================================================
    // SAVE BLOCKING VALIDATIONS — common, kept as your version
    // ============================================================

    // Priority-ordered "which single message should block Save" rules —
    // moved out of the composer's isValid() if/else chain.
    @Override
    public String getChequeIdentityBlockingMessage(boolean chequeNoOk, boolean cityOk, boolean bankOk, boolean branchOk) {
        if (!chequeNoOk) return "Cheque Number is invalid. See highlighted field.";
        if (!cityOk || !bankOk || !branchOk) return "City / Bank / Branch Code is invalid. Each must be exactly 3 digits.";
        return null;
    }

    @Override
    public String getChequeDetailsBlockingMessage(boolean transactionCodeOk, boolean accountNoOk, boolean amountOk) {
        if (!transactionCodeOk) return "Transaction Code is invalid. See highlighted field.";
        if (!accountNoOk)       return "Account Number is invalid. See highlighted field.";
        if (!amountOk)          return "Amount is invalid. See highlighted field.";
        return null;
    }
    
    @Override
    public void updateChequeStatus(
            InwardCheque cheque,
            DecisionStatus decision,
            ChequeStatus chequeStatus,
            SendTo sendTo) {

        if (cheque == null || cheque.getId() == null) {
            throw new IllegalArgumentException("Cheque cannot be null");
        }

        cheque.setDecision(decision);
        cheque.setChequeStatus(chequeStatus);
        cheque.setSendTo(sendTo);

        inwardChequeDao.update(cheque);
    }
    @Override
    public String validateChequeForSave(
            String chequeNo,
            String cityCode,
            String bankCode,
            String branchCode,
            String displayedMicr,
            String transactionCode,
            String accountNo,
            String chequeDate,
            String amount,
            String amountInWords) {

        boolean chequeNoOk =
                validateField("tbChequeNo", chequeNo) == null;

        boolean cityOk =
                validateField("tbCityCode", cityCode) == null;

        boolean bankOk =
                validateField("tbBankCode", bankCode) == null;

        boolean branchOk =
                validateField("tbBranchCode", branchCode) == null;

        boolean tcOk =
                validateField("tbTransactionCode", transactionCode) == null;

        boolean accNoOk =
                validateField("tbAccountNo", accountNo) == null;

        boolean amountOk =
                validateField("tbAmount", amount) == null;

        String identityBlockMsg =
                getChequeIdentityBlockingMessage(
                        chequeNoOk,
                        cityOk,
                        bankOk,
                        branchOk);

        if (identityBlockMsg != null) {
            return identityBlockMsg;
        }

        if (!isMicrMatch(
                cityCode,
                bankCode,
                branchCode,
                displayedMicr)) {

            return buildMicrMismatchMessage(
                    cityCode,
                    bankCode,
                    branchCode,
                    displayedMicr);
        }

        String detailsBlockMsg =
                getChequeDetailsBlockingMessage(
                        tcOk,
                        accNoOk,
                        amountOk);

        if (detailsBlockMsg != null) {
            return detailsBlockMsg;
        }

        if (chequeDate != null
                && !chequeDate.trim().isEmpty()) {

            parseChequeDate(chequeDate);
        }

        if (amountInWords == null
                || amountInWords.trim().isEmpty()) {

            return "Amount in Words could not be generated.";
        }

        return null;
    }
    public boolean isMakerEditedAndPendingReview(InwardCheque cheque) {
        return cheque.isEditedByMaker()
            && cheque.getChequeStatus()   == ChequeStatus.Processed
            && cheque.getDecision() == DecisionStatus.PENDING
            && cheque.getSendTo()         == SendTo.TV_1
            || cheque.getSendTo()         == SendTo.TV_2
            && cheque.getCbsValidation()  == CbsValidation.Valid;
    }

    @Override
    public String formatChequeDate(LocalDateTime chequeDate) {
        return chequeDate != null
                ? chequeDate.format(CHEQUE_LIST_DATE_FMT)
                : "-";
    }

    @Override
    public boolean isMicrError(InwardCheque cheque) {
        return cheque != null
                && ChequeStatus.Repair.equals(cheque.getChequeStatus());
    }
    // ============================================================
    // AMOUNT CONVERSION — common, kept as your version
    // ============================================================

    @Override
    public String convertAmountToWords(BigDecimal amount) {
        if (amount == null || amount.compareTo(BigDecimal.ZERO) < 0) return "";
        long rupees = amount.longValue();
        int  paise  = amount.remainder(BigDecimal.ONE)
                            .multiply(new BigDecimal("100")).intValue();
        if (rupees == 0 && paise == 0) return "Zero Only";
        StringBuilder sb = new StringBuilder();
        if (rupees > 0) sb.append(amountInWords(rupees));
        if (paise  > 0) { if (sb.length() > 0) sb.append(" and "); sb.append("Paise ").append(amountInWords(paise)); }
        sb.append(" Only");
        return sb.toString().trim();
    }

    private String amountInWords(long n) {
        if (n == 0)         return "";
        if (n < 20)         return ONES[(int) n];
        if (n < 100)        return TENS[(int)(n/10)] + (n%10>0 ? " "+ONES[(int)(n%10)] : "");
        if (n < 1000)       return ONES[(int)(n/100)] + " Hundred" + (n%100>0 ? " "+amountInWords(n%100) : "");
        if (n < 100000)     return amountInWords(n/1000)    + " Thousand" + (n%1000>0   ? " "+amountInWords(n%1000)   : "");
        if (n < 10000000)   return amountInWords(n/100000)  + " Lakh"     + (n%100000>0 ? " "+amountInWords(n%100000) : "");
        return                     amountInWords(n/10000000)+ " Crore"    + (n%10000000>0? " "+amountInWords(n%10000000):"");
    }

    // Full "what should the Amount-in-Words textbox show right now" rule —
    // moved out of the composer's autoGenerateAmountInWords() method.
    @Override
    public String generateAmountInWordsDisplay(String amountStr) {
        String raw = amountStr != null ? amountStr.trim() : "";
        if (raw.isEmpty()) return "";
        try {
            BigDecimal val = new BigDecimal(raw);
            if (val.compareTo(BigDecimal.ZERO) < 0) {
                return "";
            }
            return convertAmountToWords(val);
        } catch (NumberFormatException e) {
            return "";
        }
    }

    // ============================================================
    // DATE HANDLING — common, kept as your version
    // ============================================================

    @Override
    public LocalDateTime parseChequeDate(String dateStr) {
        // Throws DateTimeParseException on bad input — caller (composer)
        // catches it to mark the date field and show a UI message.
        return LocalDateTime.parse(dateStr + " 00:00", CHEQUE_DATE_DISPLAY_FMT);
    }

    // ============================================================
    // CHEQUE EDIT & MICR REPAIR — added from teammate's file
    // (you didn't have saveChequeEdit, getRepairByChequeId, saveRepair,
    // updateRepair, or the saveOrUpdateMicrRepair helper)
    // ============================================================

    @Override
    public void saveChequeEdit(InwardCheque cheque, String chequeNo, String cityCode, String bankCode,
            String branchCode, String transactionCode, String accountNo, String chequeDateStr,
            String amountStr, String amountInWords) {

        if (cheque == null) {
            throw new IllegalArgumentException("Cheque cannot be null");
        }

        String correctedMicr = buildMicrCode(cityCode, bankCode, branchCode);
        String correctedChequeNo = chequeNo != null ? chequeNo.trim() : "";
        String correctedBankCode = bankCode != null ? bankCode.trim() : "";
        String correctedBranchCode = branchCode != null ? branchCode.trim() : "";

//        if (ChequeStatus.Repair.equals(cheque.getChequeStatus())) {
//            saveOrUpdateMicrRepair(cheque, correctedMicr, correctedChequeNo, correctedBankCode, correctedBranchCode);
//        }

        cheque.setChequeNo(correctedChequeNo);
        cheque.setMicrCode(correctedMicr);
        cheque.setTransactionCode(transactionCode != null ? transactionCode.trim() : "");
        cheque.setAccountNo(accountNo != null ? accountNo.trim() : "");
        cheque.setAmount(new BigDecimal(amountStr.trim())); // may throw NumberFormatException
        cheque.setAmountInWords(amountInWords != null ? amountInWords.trim() : "");

        String dateStr = chequeDateStr != null ? chequeDateStr.trim() : "";
        if (!dateStr.isEmpty()) {
            cheque.setChequeDate(parseChequeDate(dateStr)); // may throw DateTimeParseException
        }

        cheque.setChequeStatus(ChequeStatus.Normal);
        cheque.setErrorReason(null);

        inwardChequeDao.update(cheque);
        if (cheque.getBatch() != null) {
            Long batchId = cheque.getBatch().getId();
            BatchStatus currentStatus = getBatchStatus(batchId);
            if (BatchStatus.Draft.equals(currentStatus)) {
                inwardBatchDao.updateBatchStatus(batchId, BatchStatus.Pending);
            }
        }
    }

	@Override
	public long getTotalChequeCount(Long batchId, String role) {
		// TODO Auto-generated method stub
		return inwardChequeDao.countByBatchId(batchId, role);
	}
}