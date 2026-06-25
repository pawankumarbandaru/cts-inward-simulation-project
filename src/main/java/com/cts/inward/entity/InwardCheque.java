package com.cts.inward.entity;

import java.math.BigDecimal;
import java.time.LocalDateTime;

import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import com.cts.inward.enums.CbsValidation;
import com.cts.inward.enums.ChequeStatus;
import com.cts.inward.enums.DecisionStatus;
import com.cts.inward.enums.SendTo;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToOne;
import jakarta.persistence.Table;

@Entity
@Table(name = "inward_cheque")
public class InwardCheque {

	// ── 1. id ─────────────────────────────────────────────────────────────
	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	@Column(name = "id")
	private Long id;

	// ── 2. batch_id (FK → inward_batch) ──────────────────────────────────
	@ManyToOne(fetch = FetchType.EAGER)
	@JoinColumn(name = "batch_id", nullable = false)
	private InwardBatch batch;

	// ── 3. cheque_no ──────────────────────────────────────────────────────
	@Column(name = "cheque_no", unique = true, nullable = false)
	private String chequeNo;

	// ── 4. micr_code ──────────────────────────────────────────────────────
	@Column(name = "micr_code")
	private String micrCode;

	// ── 5. amount ─────────────────────────────────────────────────────────
	@Column(name = "amount", precision = 15, scale = 2)
	private BigDecimal amount;

	// ── 6. amount_in_words ────────────────────────────────────────────────
	@Column(name = "amount_in_words")
	private String amountInWords;

	// ── 7. account_no ─────────────────────────────────────────────────────
	@Column(name = "account_no")
	private String accountNo;

	// ── 8. ifsc_code ──────────────────────────────────────────────────────
	@Column(name = "ifsc_code")
	private String ifscCode;

	// ── 9. presenting_bank ────────────────────────────────────────────────
	@Column(name = "presenting_bank")
	private String presentingBank;

	// ── 10. branch_name ───────────────────────────────────────────────────
	@Column(name = "branch_name")
	private String branchName;

	// ── 11. payee_name ────────────────────────────────────────────────────
	@Column(name = "payee_name")
	private String payeeName;

	// ── 12. drawer_name ───────────────────────────────────────────────────
	@Column(name = "drawer_name")
	private String drawerName;

	// ── 13. transaction_code ──────────────────────────────────────────────
	@Column(name = "transaction_code")
	private String transactionCode;

	// ── 14. cheque_status (enum: Normal | Micr_error) ────────────────────
	@Enumerated(EnumType.STRING)
	@JdbcTypeCode(SqlTypes.NAMED_ENUM)
	@Column(name = "cheque_status", columnDefinition = "cheque_status", nullable = false)
	private ChequeStatus chequeStatus = ChequeStatus.Normal;

	// ── 15. error_reason ──────────────────────────────────────────────────
	@Column(name = "cbs_reject_reason")
	private String errorReason;

	// ── 16. front_image_path ──────────────────────────────────────────────
	@Column(name = "front_image_path")
	private String frontImagePath;

	// ── 17. rear_image_path ───────────────────────────────────────────────
	@Column(name = "rear_image_path")
	private String rearImagePath;

	// ── 18. validation_status (enum: Valid | Invalid) ─────────────────────
	@Enumerated(EnumType.STRING)
	@JdbcTypeCode(SqlTypes.NAMED_ENUM)
	@Column(name = "cbs_validation", columnDefinition = "cbs_validation")
	private CbsValidation cbsValidation;

	// ── 19. cheque_date ───────────────────────────────────────────────────
	@Column(name = "cheque_date")
	private LocalDateTime chequeDate;

	// ── 21. created_at ────────────────────────────────────────────────────
	@Column(name = "created_at")
	private LocalDateTime createdAt;

	@Column(name = "checker_reject_reason")
	private String rejectReason;

	@Column(name = "checker_refer_reason")
	private String referReason;

	@Enumerated(EnumType.STRING)
	@JdbcTypeCode(SqlTypes.NAMED_ENUM)
	@Column(name = "decision", columnDefinition = "decision_status")
	private DecisionStatus decision;

	@Enumerated(EnumType.STRING)
	@JdbcTypeCode(SqlTypes.NAMED_ENUM)
	@Column(name = "send_to", columnDefinition = "send_to")
	private SendTo sendTo;

	// Checker Action Columns

	// ── Constructor ───────────────────────────────────────────────────────
	public InwardCheque() {
		this.createdAt = LocalDateTime.now();
		this.chequeStatus = ChequeStatus.Normal;
		this.sendTo = SendTo.MAKER;
		this.decision = DecisionStatus.PENDING;

	}

	// Maker Resubmission Columns

	@Column(name = "resubmitted_by", length = 100)
	private String resubmittedBy;

	// Maker Edit Tracking

	@Column(name = "is_edited_by_maker")
	private boolean isEditedByMaker = false;

	@Column(name = "edited_fields", columnDefinition = "TEXT")
	private String editedFields;

	@Column(name = "checker_sendback_reason")
	private String sendbackReason;

	@Column(name = "maker_correction_remarks", columnDefinition = "TEXT")
	private String makerCorrectionRemark;

	public String getMicrBand() {
		if (micrCode == null || micrCode.length() < 9)
			return micrCode;
		return micrCode.substring(micrCode.length() - 9);
	}

	public String getMicrCityCode() {
		String band = getMicrBand();
		if (band == null || band.length() < 3)
			return "";
		return band.substring(0, 3);
	}

	public String getMicrBankCode() {
		String band = getMicrBand();
		if (band == null || band.length() < 6)
			return "";
		return band.substring(3, 6);
	}

	public String getMicrBranchCode() {
		String band = getMicrBand();
		if (band == null || band.length() < 9)
			return "";
		return band.substring(6, 9);
	}

	// ── Getters & Setters ─────────────────────────────────────────────────

	public Long getId() {
		return id;
	}

	public InwardBatch getBatch() {
		return batch;
	}

	public void setBatch(InwardBatch batch) {
		this.batch = batch;
	}

	public String getChequeNo() {
		return chequeNo;
	}

	public void setChequeNo(String chequeNo) {
		this.chequeNo = chequeNo;
	}

	/** Backward-compat alias used by BatchProcessingServiceImpl */
	public String getChequeNumber() {
		return chequeNo;
	}

	public void setChequeNumber(String chequeNumber) {
		this.chequeNo = chequeNumber;
	}

	public String getMicrCode() {
		return micrCode;
	}

	public void setMicrCode(String micrCode) {
		this.micrCode = micrCode;
	}

	public BigDecimal getAmount() {
		return amount;
	}

	public void setAmount(BigDecimal amount) {
		this.amount = amount;
	}

	/** Backward-compat setter — allows Double from BatchProcessingServiceImpl */
	public void setAmount(Double amount) {
		this.amount = amount != null ? BigDecimal.valueOf(amount) : null;
	}

	public String getAmountInWords() {
		return amountInWords;
	}

	public void setAmountInWords(String amountInWords) {
		this.amountInWords = amountInWords;
	}

	public String getAccountNo() {
		return accountNo;
	}

	public void setAccountNo(String accountNo) {
		this.accountNo = accountNo;
	}

	/** Backward-compat alias */
	public String getAccountNumber() {
		return accountNo;
	}

	public void setAccountNumber(String accountNumber) {
		this.accountNo = accountNumber;
	}

	public String getIfscCode() {
		return ifscCode;
	}

	public void setIfscCode(String ifscCode) {
		this.ifscCode = ifscCode;
	}

	public String getPresentingBank() {
		return presentingBank;
	}

	public void setPresentingBank(String presentingBank) {
		this.presentingBank = presentingBank;
	}

	public String getBranchName() {
		return branchName;
	}

	public void setBranchName(String branchName) {
		this.branchName = branchName;
	}

	public String getPayeeName() {
		return payeeName;
	}

	public void setPayeeName(String payeeName) {
		this.payeeName = payeeName;
	}

	public String getDrawerName() {
		return drawerName;
	}

	public void setDrawerName(String drawerName) {
		this.drawerName = drawerName;
	}

	public String getTransactionCode() {
		return transactionCode;
	}

	public void setTransactionCode(String transactionCode) {
		this.transactionCode = transactionCode;
	}

	public ChequeStatus getChequeStatus() {
		return chequeStatus;
	}

	public void setChequeStatus(ChequeStatus chequeStatus) {
		this.chequeStatus = chequeStatus;
	}

	/** Backward-compat setter — accepts String "VALID"/"NORMAL" etc. */
	public void setChequeStatus(String status) {
		if (status == null) {
			this.chequeStatus = ChequeStatus.Normal;
			return;
		}
		switch (status.toUpperCase()) {
		case "MICR_ERROR":
		case "MICR_ERR":
			this.chequeStatus = ChequeStatus.Repair;
			break;
		default:
			this.chequeStatus = ChequeStatus.Normal;
			break;
		}
	}

	public String getErrorReason() {
		return errorReason;
	}

	public void setErrorReason(String errorReason) {
		this.errorReason = errorReason;
	}

	public String getFrontImagePath() {
		return frontImagePath;
	}

	public void setFrontImagePath(String frontImagePath) {
		this.frontImagePath = frontImagePath;
	}

	public String getRearImagePath() {
		return rearImagePath;
	}

	public void setRearImagePath(String rearImagePath) {
		this.rearImagePath = rearImagePath;
	}

	public CbsValidation getCbsValidation() {
		return cbsValidation;
	}

	public void setCbsValidation(CbsValidation cbsValidation) {
		this.cbsValidation = cbsValidation;
	}

	/**
	 * Backward-compat — maps old "validation_status" String to new CbsValidation
	 * enum
	 */
	public String getValidationStatus() {
		return cbsValidation != null ? cbsValidation.name() : null;
	}

	public void setValidationStatus(String status) {
		if (status == null) {
			this.cbsValidation = null;
			return;
		}
		switch (status.toUpperCase()) {
		case "INVALID":
			this.cbsValidation = CbsValidation.Invalid;
			break;
		default:
			this.cbsValidation = CbsValidation.Valid;
			break;
		}
	}

	public LocalDateTime getChequeDate() {
		return chequeDate;
	}

	public void setChequeDate(LocalDateTime chequeDate) {
		this.chequeDate = chequeDate;
	}

	public LocalDateTime getCreatedAt() {
		return createdAt;
	}

	public DecisionStatus getDecision() {
		return decision;
	}

	public void setDecision(DecisionStatus decision) {
		this.decision = decision;
	}

	public SendTo getSendTo() {
		return sendTo;
	}

	public void setSendTo(SendTo sendTo) {
		this.sendTo = sendTo;
	}

	public String getResubmittedBy() {
		return resubmittedBy;
	}

	public void setResubmittedBy(String resubmittedBy) {
		this.resubmittedBy = resubmittedBy;
	}

	public boolean isEditedByMaker() {
		return isEditedByMaker;
	}

	public void setIsEditedByMaker(boolean isEditedByMaker) {
	    this.isEditedByMaker = isEditedByMaker;
	}

	public String getEditedFields() {
		return editedFields;
	}

	public void setEditedFields(String editedFields) {
		this.editedFields = editedFields;
	}

	public String getRejectReason() {
		return rejectReason;
	}

	public void setRejectReason(String rejectReason) {
		this.rejectReason = rejectReason;
	}

	public String getReferReason() {
		return referReason;
	}

	public void setReferReason(String referReason) {
		this.referReason = referReason;
	}

	public String getSendbackReason() {
		return sendbackReason;
	}

	public void setSendbackReason(String sendbackReason) {
		this.sendbackReason = sendbackReason;
	}

	public String getMakerCorrectionRemark() {
		return makerCorrectionRemark;
	}

	public void setMakerCorrectionRemark(String makerCorrectionRemark) {
		this.makerCorrectionRemark = makerCorrectionRemark;
	}

}