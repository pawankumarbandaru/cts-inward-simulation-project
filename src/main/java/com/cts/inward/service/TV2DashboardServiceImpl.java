package com.cts.inward.service;

import java.util.ArrayList;
import java.util.Date;
import java.util.List;

import com.cts.inward.dao.InwardBatchDao;
import com.cts.inward.dao.InwardBatchDaoImpl;
import com.cts.inward.dto.CheckerDashboardStatsDTO;
import com.cts.inward.dto.InwardBatchDTO;
import com.cts.inward.entity.InwardBatch;
import com.cts.inward.enums.SendTo;


public class TV2DashboardServiceImpl implements TV2DashboardService {

    private final InwardBatchDao dao = new InwardBatchDaoImpl();

    // ── Batch list for a specific date, with all cheque breakdowns ─────────
    @Override
    public List<InwardBatchDTO> getTV2BatchesForDate(Date date) {

        List<InwardBatch> batches = dao.getBatchesByQueueAndDate(SendTo.TV_2, date);
        List<InwardBatchDTO> dtoList = new ArrayList<>();

        if (batches == null) return dtoList;

        for (InwardBatch batch : batches) {

            InwardBatchDTO dto = convertToDTO(batch);

            Long total    = dao.getTotalChequesForBatchTV2(batch.getId());
            Long assigned = dao.getAssignedChequesForBatchTV2(batch.getId(), SendTo.TV_2);
            Long pending  = dao.getPendingChequesForBatchTV2(batch.getId(), SendTo.TV_2);   // ← queue-scoped
            Long cleared  = dao.getClearedChequesForBatchTV2(batch.getId(), SendTo.TV_2);   // ← queue-scoped

            dto.setTotalCheques   (total    != null ? total.intValue()    : 0);
            dto.setAssignedCheques(assigned != null ? assigned.intValue() : 0);
            dto.setPendingCheques (pending  != null ? pending.intValue()  : 0);
            dto.setClearedCheques (cleared  != null ? cleared.intValue()  : 0);

            dtoList.add(dto);
        }

        return dtoList;
    }

    // ── KPI stats for a specific date ─────────────────────────────────────
    @Override
    public CheckerDashboardStatsDTO getStatsForDate(Date date) {

        CheckerDashboardStatsDTO stats = new CheckerDashboardStatsDTO();

        Long assigned = dao.getAssignedBatchCountTV2(SendTo.TV_2, date);
        Long pending  = dao.getPendingBatchCountTV2(SendTo.TV_2, date);
        Long cleared  = dao.getClearedBatchCountTV2(SendTo.TV_2, date);
        Long pendingQ = dao.getPendingChequeCountTV2(SendTo.TV_2, date);

        stats.setAssignedBatches(assigned != null ? assigned : 0L);
        stats.setPendingBatches (pending  != null ? pending  : 0L);
        stats.setClearedBatches (cleared  != null ? cleared  : 0L);
        stats.setPendingCheques (pendingQ != null ? pendingQ : 0L);

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
                : null);

        return dto;
    }
}