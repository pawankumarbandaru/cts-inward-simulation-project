package com.cts.inward.service;

import java.util.Date;
import java.util.List;

import com.cts.inward.dto.CheckerDashboardStatsDTO;
import com.cts.inward.dto.InwardBatchDTO;

public interface CheckerDashboardService {

    /**
     * Returns all TV1 batches created on the given date,
     * with per-batch cheque counts populated in the DTO.
     */
    List<InwardBatchDTO> getTV1BatchesForDate(Date date);

    /**
     * Returns KPI stats (assigned / pending / cleared batches,
     * pending cheques) for the given date.
     */
    CheckerDashboardStatsDTO getStatsForDate(Date date);
}
