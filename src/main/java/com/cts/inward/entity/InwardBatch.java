package com.cts.inward.entity;

import java.time.LocalDateTime;
import java.util.Date;
import java.util.List;

import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import com.cts.inward.enums.BatchStatus;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.OneToMany;
import jakarta.persistence.Table;
import jakarta.persistence.Temporal;
import jakarta.persistence.TemporalType;

@Entity
@Table(name = "inward_batch")
public class InwardBatch {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@Column(name = "batch_id", unique = true, nullable = false)
	private String batchId;

//    @Enumerated(EnumType.STRING)
//    @Column(name = "batch_status", nullable = false)
//    private BatchStatus batchStatus;

	@Enumerated(EnumType.STRING)
	@JdbcTypeCode(SqlTypes.NAMED_ENUM)
	@Column(name = "batch_status", columnDefinition = "batch_status")
	private BatchStatus batchStatus;

	@Column(name = "created_at", nullable = false)
	private LocalDateTime createdAt;

	@Column(name = "total_cheques")
	private Integer totalCheques;

	@Column(name = "success_count")
	private Integer successCount = 0 ;

	@Column(name = "micr_repair_count")
	private Integer micrRepairCount;

	@OneToMany(mappedBy = "batch", cascade = CascadeType.ALL, fetch = FetchType.LAZY)
	private List<InwardCheque> cheques;

	
	
	 
	 

//	    @Column(name = "success_count", nullable = false)
//	    private Integer clearedCheques = 0;

	    @Column(name = "pending_cheques", nullable = false)
	    private Integer pendingCheques = 0;

	
	public InwardBatch() {
		this.createdAt = LocalDateTime.now();
	}

	public Long getId() {
		return id;
	}
	
	

	public void setCreatedAt(LocalDateTime createdAt) {
		this.createdAt = createdAt;
	}

	public String getBatchId() {
		return batchId;
	}

	public void setBatchId(String batchId) {
		this.batchId = batchId;
	}

	public BatchStatus getBatchStatus() {
		return batchStatus;
	}

	public void setBatchStatus(BatchStatus batchStatus) {
		this.batchStatus = batchStatus;
	}

	/**
	 * Convenience setter — accepts a String so existing service code keeps working.
	 */
	public void setBatchStatus(String status) {
		if (status == null) {
			this.batchStatus = null;
			return;
		}
		// Normalise legacy string values (e.g. "RECEIVED" → Pending)
		switch (status.toUpperCase()) {
		case "PENDING":
		case "RECEIVED":
			this.batchStatus = BatchStatus.Pending;
			break;
		case "AT_MICR_SERVICE":
			this.batchStatus = BatchStatus.Pending;
			break;
		case "AT_CHECKER_QUEUE":
			this.batchStatus = BatchStatus.Pending;
			break;
		case "CLEARED":
			this.batchStatus = BatchStatus.Cleared;
			break;
		default:
			try {
				this.batchStatus = BatchStatus.valueOf(status);
			} catch (IllegalArgumentException e) {
				this.batchStatus = BatchStatus.Pending;
			}
		}
	}

	public LocalDateTime getCreatedAt() {
		return createdAt;
	}

	public Integer getTotalCheques() {
		return totalCheques;
	}

	public void setTotalCheques(Integer t) {
		this.totalCheques = t;
	}

	public Integer getSuccessCount() {
		return successCount;
	}

	public void setSuccessCount(Integer s) {
		this.successCount = s;
	}

	public Integer getMicrRepairCount() {
		return micrRepairCount;
	}

	public void setMicrRepairCount(Integer m) {
		this.micrRepairCount = m;
	}

	public List<InwardCheque> getCheques() {
		return cheques;
	}

	public void setCheques(List<InwardCheque> cheques) {
		this.cheques = cheques;
	}
	  

	

		public Integer getPendingCheques() {
			return pendingCheques;
		}

		public void setPendingCheques(Integer pendingCheques) {
			this.pendingCheques = pendingCheques;
		}
	    
	    

}
