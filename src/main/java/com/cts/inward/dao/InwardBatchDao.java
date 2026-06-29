package com.cts.inward.dao;

import java.math.BigDecimal;
import java.util.Date;
import java.util.List;

import com.cts.inward.dto.DashboardStatsDTO;
import com.cts.inward.entity.InwardBatch;
import com.cts.inward.enums.BatchStatus;
import com.cts.inward.enums.SendTo;

public interface InwardBatchDao {

	DashboardStatsDTO getDashboardStats(Date fromDate, Date toDate);

	List<InwardBatch> getAllBatches();

	public List<InwardBatch> searchBatches(String batchId, BatchStatus status, Date fromDate, Date toDate);

	void save(InwardBatch batch);

	void update(InwardBatch batch);

	InwardBatch findByBatchId(String batchId);

	List<InwardBatch> findAll();

	BigDecimal getTotalAmountByBatchId(Long batchId);

	// ── NEW ──
	Long getAcceptedCountByBatchId(Long batchId);

	Long getRejectedCountByBatchId(Long batchId);

	InwardBatch findLatest();

//	Long getValidCountByBatchId(Long batchId);

	Long getInvalidCountByBatchId(Long batchId);

	/**
	 * Bulk version of getInvalidCountByBatchId() — returns invalid cheque
	 * counts for ALL given batch IDs in a single query, instead of one query
	 * per batch. Used by the dashboard list to avoid N+1 queries.
	 *
	 * @param batchIds list of inward_batch.id values to look up
	 * @return map of batchId -> invalid cheque count (batches with 0 invalid
	 *         cheques will simply be absent from the map — caller should
	 *         treat a missing key as 0)
	 */
	java.util.Map<Long, Long> getInvalidCountsForBatchIds(List<Long> batchIds);

	void updateBatchStatus(Long id, BatchStatus batchStatus);

	InwardBatch findById(Long id);

	// Checker dashboard get for date
	List<InwardBatch> getBatchesByDate(Date date);
	
	//KPIS of TV1 dashboard 
	Long getAssignedBatchCountForTV1(Date date);

	Long getPendingBatchCountTV1(Date date);

	Long getClearedBatchCountTV1(Date date);

	Long getPendingChequeCountTV1(Date date);
	

    // ── Per-batch cheque breakdown ─────────────────────────────────────────
    Long getTotalChequesForBatchTV1(Long batchId);

    /** Cheques in this batch where sendTo = queue */
    Long getAssignedChequesForBatchTV1(Long batchId);

    /** Cheques where sendTo = queue AND decision = PENDING */
    Long getPendingChequesForBatchTV1(Long batchId);

    /** Cheques where sendTo = queue AND decision = APPROVED */
    Long getClearedChequesForBatchTV1(Long batchId);

	Long getSubmittedBatchesTV1(Long id);
	
	
	//Methods for TV2 Dashboard 
	

    // ── Batch list scoped to a single day ─────────────────────────────────
    List<InwardBatch> getBatchesByQueueAndDate(SendTo queue, Date date);

    // ── KPI counts scoped to a single day ─────────────────────────────────
    Long getAssignedBatchCountTV2(SendTo queue, Date date);

    Long getPendingBatchCountTV2(SendTo queue, Date date);

    Long getClearedBatchCountTV2(SendTo queue, Date date);

    Long getPendingChequeCountTV2(SendTo queue, Date date);

    // ── Per-batch cheque breakdown ─────────────────────────────────────────
    Long getTotalChequesForBatchTV2(Long batchId);

    /** Cheques in this batch where sendTo = queue */
    Long getAssignedChequesForBatchTV2(Long batchId, SendTo queue);

    /** Cheques where sendTo = queue AND decision = PENDING */
    Long getPendingChequesForBatchTV2(Long batchId, SendTo queue);

    /** Cheques where sendTo = queue AND decision = APPROVED */
    Long getClearedChequesForBatchTV2(Long batchId, SendTo queue);

	void updateBatchStatusIfCompleted(Long batchId);
	
	
	

}