package com.cts.inward.dto;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Date;

import com.cts.inward.entity.InwardBatch;
import com.cts.inward.enums.BatchStatus;

public class InwardBatchDTO {

	private Long id;
	private String batchId;
	private LocalDateTime uploadDate;
	private Integer totalCheques; // all cheques in the batch
	private Integer assignedCheques; // cheques assigned to checker queue (checkerQueue = 'TV1')
	private Integer clearedCheques; // cheques with decision = APPROVED
	private Integer pendingCheques; // cheques with decision = PENDING
	private Integer submittedBatches;
	private BatchStatus status;
	private BigDecimal totalAmount;

	// accepted/rejected counts from DecisionStatus
	private Integer acceptedCheques;
	private Integer rejectedCheques;

	public InwardBatchDTO() {
	}
	

		private Integer validCheques;
		private Integer invalidCheques;

		public Integer getValidCheques() {
			return validCheques;
		}

		public void setValidCheques(Integer validCheques) {
			this.validCheques = validCheques;
		}

		public Integer getInvalidCheques() {
			return invalidCheques;
		}

		public void setInvalidCheques(Integer invalidCheques) {
			this.invalidCheques = invalidCheques;
		}

	public InwardBatchDTO(Long id, String batchId, LocalDateTime uploadDate, Integer totalCheques, Integer clearedCheques,
			Integer pendingCheques, Integer submittedBatches, BatchStatus status) {
		this.id = id;
		this.batchId = batchId;
		this.uploadDate = uploadDate;
		this.totalCheques = totalCheques;
		this.clearedCheques = clearedCheques;
		this.pendingCheques = pendingCheques;
		this.submittedBatches = submittedBatches;
		this.status = status;
	}

	public InwardBatchDTO(Long id, String batchId, LocalDateTime uploadDate, Integer totalCheques, Integer clearedCheques,
			Integer pendingCheques, BatchStatus status) {
		this.id = id;
		this.batchId = batchId;
		this.uploadDate = uploadDate;
		this.totalCheques = totalCheques;
		this.clearedCheques = clearedCheques;
		this.pendingCheques = pendingCheques;
		this.status = status;
	}

// ── Factory – entity → DTO ────────────────────────────────────────────────



	/**
	 * Creates a DTO from a detached / managed {@link InwardBatch} entity. Safe to
	 * call outside a Hibernate session (no lazy collections accessed).
	 */
	public static InwardBatchDTO from(InwardBatch entity) {
		return new InwardBatchDTO(entity.getId(), entity.getBatchId(), entity.getCreatedAt(), entity.getTotalCheques(),
				entity.getSuccessCount(), entity.getPendingCheques(), entity.getBatchStatus());
	}

	public Long getId() {
		return id;
	}

	public void setId(Long id) {
		this.id = id;
	}

	public String getBatchId() {
		return batchId;
	}

	public void setBatchId(String batchId) {
		this.batchId = batchId;
	}

	public LocalDateTime getUploadDate() {
		return uploadDate;
	}

	public void setUploadDate(LocalDateTime uploadDate) {
		this.uploadDate = uploadDate;
	}

	public Integer getTotalCheques() {
		return totalCheques;
	}

	public void setTotalCheques(Integer totalCheques) {
		this.totalCheques = totalCheques;
	}

	public Integer getAssignedCheques() {
		return assignedCheques;
	}

	public void setAssignedCheques(Integer assignedCheques) {
		this.assignedCheques = assignedCheques;
	}

	public Integer getClearedCheques() {
		return clearedCheques;
	}

	public void setClearedCheques(Integer clearedCheques) {
		this.clearedCheques = clearedCheques;
	}

	public Integer getPendingCheques() {
		return pendingCheques;
	}

	public void setPendingCheques(Integer pendingCheques) {
		this.pendingCheques = pendingCheques;
	}

	public Integer getSubmittedBatches() {
		return submittedBatches;
	}

	public void setSubmittedBatches(Integer submittedBatches) {
		this.submittedBatches = submittedBatches;
	}

	public BatchStatus getStatus() {
		return status;
	}

	public void setStatus(BatchStatus status) {
		this.status = status;
	}

	public BigDecimal getTotalAmount() {
		return totalAmount;
	}

	public void setTotalAmount(BigDecimal totalAmount) {
		this.totalAmount = totalAmount;
	}

	public Integer getAcceptedCheques() {
		return acceptedCheques;
	}

	public void setAcceptedCheques(Integer acceptedCheques) {
		this.acceptedCheques = acceptedCheques;
	}

	public Integer getRejectedCheques() {
		return rejectedCheques;
	}

	public void setRejectedCheques(Integer rejectedCheques) {
		this.rejectedCheques = rejectedCheques;
	}

	
}
