package com.cts.inward.service;

import java.math.BigDecimal;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;

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
	 * @param batches List of InwardBatch entities
	 * @return List of InwardBatchDTO
	 */
	private List<InwardBatchDTO> convertToDTOList(List<InwardBatch> batches) {
		List<InwardBatchDTO> dtoList = new ArrayList<>();
		if (batches == null)
			return dtoList;
		for (InwardBatch batch : batches) {
			dtoList.add(convertToDTO(batch));
		}
		return dtoList;
	}
	
	
	 /**
     * Converts a single InwardBatch entity into a DTO.
     * Also calculates accepted, rejected, pending,
     * valid and invalid cheque counts.
     *
     * @param batch Batch entity from database
     * @return Fully populated InwardBatchDTO
     */
	private InwardBatchDTO convertToDTO(InwardBatch batch) {
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

		// Total amount
		BigDecimal totalAmount = batchDao.getTotalAmountByBatchId(batch.getId());
		dto.setTotalAmount(totalAmount != null ? totalAmount : BigDecimal.ZERO);

		//  accepted and rejected counts from DecisionStatus ──
		Long accepted = batchDao.getAcceptedCountByBatchId(batch.getId());
		Long rejected = batchDao.getRejectedCountByBatchId(batch.getId());
		dto.setAcceptedCheques(accepted != null ? accepted.intValue() : 0);
		dto.setRejectedCheques(rejected != null ? rejected.intValue() : 0);
		
		Integer total = batch.getTotalCheques() == null ? 0 : batch.getTotalCheques();
		int pending = (int) Math.max(total - accepted - rejected, 0);
		dto.setPendingCheques(pending);

		Long invalid = batchDao.getInvalidCountByBatchId(batch.getId());
		Long valid = total - invalid;
		dto.setValidCheques(valid != null ? valid.intValue() : 0);
		dto.setInvalidCheques(invalid != null ? invalid.intValue() : 0);
		dto.setValidCheques(valid != null ? valid.intValue() : 0);

		return dto;
	}
}
