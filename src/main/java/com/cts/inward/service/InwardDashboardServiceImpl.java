package com.cts.inward.service;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.stream.Collectors;

import com.cts.inward.dao.InwardBatchDao;
import com.cts.inward.dao.InwardBatchDaoImpl;
import com.cts.inward.dto.DashboardStatsDTO;
import com.cts.inward.dto.InwardBatchDTO;
import com.cts.inward.entity.InwardBatch;
import com.cts.inward.enums.BatchStatus;

public class InwardDashboardServiceImpl implements InwardDashboardService {

	private final InwardBatchDao batchDao = new InwardBatchDaoImpl();

	/**
	 * Returns dashboard statistics for the given date range.
	 * 
	 * @return DashboardStatsDTO containing batch counts
	 */
	@Override
	public DashboardStatsDTO getDashboardStats(Date fromDate, Date toDate) {
		return batchDao.getDashboardStats(fromDate, toDate);
	}

	/**
	 * Fetches all batches from database and converts them to DTOs.
	 *
	 * @return List of InwardBatchDTO objects
	 */
	@Override
	public List<InwardBatchDTO> getAllBatches() {
		System.out.println("SERVICE CALLED");
		List<InwardBatch> batches = batchDao.getAllBatches();
		System.out.println("DAO RETURNED = " + batches.size());
		return convertToDTOList(batches);
	}

	/**
	 * Searches batches using optional filters.
	 *
	 * @param batchId  Batch ID
	 * @param status   Batch status as String
	 * @param fromDate Start date filter
	 * @param toDate   End date filter
	 * @return Filtered batch list as DTOs
	 */
	@Override
	public List<InwardBatchDTO> searchBatches(String batchId, String status, Date fromDate, Date toDate) {

		BatchStatus batchStatus = null;

		// Convert status String to BatchStatus enum
		if (status != null && !status.isBlank() && !"ALL".equalsIgnoreCase(status)) {

			batchStatus = BatchStatus.valueOf(status);
		}

		// Fetch matching batches from database
		List<InwardBatch> batches = batchDao.searchBatches(batchId, batchStatus, fromDate, toDate);

		// Convert entities to DTOs
		return convertToDTOList(batches);
	}

	/**
	 * Converts entity list to DTO list.
	 *
	 * PERFORMANCE FIX: previously this looped through batches and called
	 * batchDao.getInvalidCountByBatchId() ONCE PER BATCH — that meant a
	 * dashboard showing 20 batches made 20 separate DB round trips just for
	 * this one number. Now we fetch ALL invalid counts in a single bulk query
	 * (getInvalidCountsForBatchIds) BEFORE the loop, then just look values up
	 * from the in-memory map inside the loop — zero extra DB calls per row.
	 *
	 * @param batches List of InwardBatch entities
	 * @return List of InwardBatchDTO
	 */
	private List<InwardBatchDTO> convertToDTOList(List<InwardBatch> batches) {
		List<InwardBatchDTO> dtoList = new ArrayList<>();
		if (batches == null || batches.isEmpty())
			return dtoList;

		// Collect every batch ID up front so we can fetch all invalid
		// counts in ONE query instead of one query per batch.
		List<Long> batchIds = batches.stream()
				.map(InwardBatch::getId)
				.collect(Collectors.toList());

		// Single bulk query — replaces what used to be N separate queries.
		// Map key = batch.id, value = invalid cheque count for that batch.
		java.util.Map<Long, Long> invalidCountsByBatchId =
				batchDao.getInvalidCountsForBatchIds(batchIds);

		for (InwardBatch batch : batches) {
			dtoList.add(convertToDTO(batch, invalidCountsByBatchId));
		}
		return dtoList;
	}
	
	
	 /**
     * Converts a single InwardBatch entity into a DTO.
     * Also calculates accepted, rejected, pending,
     * valid and invalid cheque counts.
     *
     * @param batch                   Batch entity from database
     * @param invalidCountsByBatchId  Pre-fetched map of batchId -> invalid
     *                                count (built once for the whole list by
     *                                convertToDTOList — avoids a DB call here)
     * @return Fully populated InwardBatchDTO
     */
	private InwardBatchDTO convertToDTO(InwardBatch batch, java.util.Map<Long, Long> invalidCountsByBatchId) {
		InwardBatchDTO dto = new InwardBatchDTO();
		// Basic batch information	
		dto.setId(batch.getId());
		dto.setBatchId(batch.getBatchId());

		 // Upload date
		if (batch.getCreatedAt() != null) {
			dto.setUploadDate(batch.getCreatedAt());
		}
		
		dto.setTotalCheques(batch.getTotalCheques());
		
		 // Successfully processed cheques
		Integer cleared = batch.getSuccessCount() == null ? 0 : batch.getSuccessCount();
		dto.setClearedCheques(cleared);

		dto.setStatus(batch.getBatchStatus() != null ? batch.getBatchStatus() : null);

				
		Integer total = batch.getTotalCheques() == null ? 0 : batch.getTotalCheques();
		

		// Look up invalid count from the pre-fetched map instead of a DB call.
		// A batch with 0 invalid cheques simply won't be in the map — default to 0.
		Long invalid = invalidCountsByBatchId.getOrDefault(batch.getId(), 0L);
		Long valid = total - invalid;
		dto.setValidCheques(valid != null ? valid.intValue() : 0);
		dto.setInvalidCheques(invalid != null ? invalid.intValue() : 0);
		dto.setValidCheques(valid != null ? valid.intValue() : 0);

		return dto;
	}
	
	
}