package com.cts.inward.service;

import java.util.ArrayList;
import java.util.Date;
import java.util.List;

import com.cts.inward.dao.InwardBatchDao;
import com.cts.inward.dao.InwardBatchDaoImpl;
import com.cts.inward.dto.CheckerDashboardStatsDTO;
import com.cts.inward.dto.InwardBatchDTO;
import com.cts.inward.entity.InwardBatch;
import com.cts.inward.enums.BatchStatus;

public class CheckerDashboardServiceImpl implements CheckerDashboardService {

    private final InwardBatchDao inwardBatchDao = new InwardBatchDaoImpl();

    // ── Batch list for a specific date, with all cheque breakdowns ─────────
    @Override
    public List<InwardBatchDTO> getTV1BatchesForDate(Date date) {

        List<InwardBatch> batches = inwardBatchDao.getBatchesByDate(date);
        List<InwardBatchDTO> dtoList = new ArrayList<>();

        if (batches == null) return dtoList;

        for (InwardBatch batch : batches) {

            // Show only Pending and Submitted batches
            if (batch.getBatchStatus() != BatchStatus.PendingAtChecker
                    && batch.getBatchStatus() != BatchStatus.ClearedAtChecker) {
                continue;
            }

            InwardBatchDTO dto = convertToDTO(batch);

            Long total     = inwardBatchDao.getTotalChequesForBatchTV1(batch.getId());
            Long assigned  = inwardBatchDao.getAssignedChequesForBatchTV1(batch.getId());
            Long pending   = inwardBatchDao.getPendingChequesForBatchTV1(batch.getId());
            Long cleared   = inwardBatchDao.getClearedChequesForBatchTV1(batch.getId());
            Long submitted = inwardBatchDao.getSubmittedBatchesTV1(batch.getId());

            dto.setTotalCheques(total != null ? total.intValue() : 0);
            dto.setAssignedCheques(assigned != null ? assigned.intValue() : 0);
            dto.setPendingCheques(pending != null ? pending.intValue() : 0);
            dto.setClearedCheques(cleared != null ? cleared.intValue() : 0);
            dto.setSubmittedBatches(submitted != null ? submitted.intValue() : 0);

            // Display status in table
            dto.setStatus(batch.getBatchStatus());

            dtoList.add(dto);
        }

        return dtoList;
    }

    // ── KPI stats for a specific date ─────────────────────────────────────
    @Override
    public CheckerDashboardStatsDTO getStatsForDate(Date date) {

        CheckerDashboardStatsDTO stats = new CheckerDashboardStatsDTO();

        Long assigned = inwardBatchDao.getAssignedBatchCountForTV1(date);
        Long pendingBatches  = inwardBatchDao.getPendingBatchCountTV1(date);
        Long cleared  = inwardBatchDao.getClearedBatchCountTV1(date);
        Long pendingCheques = inwardBatchDao.getPendingChequeCountTV1(date);

        stats.setAssignedBatches(assigned != null ? assigned : 0L);
        stats.setPendingBatches (pendingBatches  != null ? pendingBatches  : 0L);
        stats.setClearedBatches (cleared  != null ? cleared  : 0L);
        stats.setPendingCheques (pendingCheques != null ? pendingCheques : 0L);

        return stats;
    }

    // ── Entity → DTO ──────────────────────────────────────────────────────
    private InwardBatchDTO convertToDTO(InwardBatch batch) {

        InwardBatchDTO dto = new InwardBatchDTO();
        dto.setId(batch.getId());
        dto.setBatchId(batch.getBatchId());

        if (batch.getCreatedAt() != null) {
        	dto.setUploadDate(batch.getCreatedAt());
        }

        dto.setTotalCheques(
            batch.getTotalCheques() != null ? batch.getTotalCheques() : 0);

        dto.setStatus(
            batch.getBatchStatus() != null
                ? batch.getBatchStatus()
                :null);

        return dto;
    }
}
