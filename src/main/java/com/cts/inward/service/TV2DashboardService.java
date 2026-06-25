package com.cts.inward.service;

import java.util.Date;
import java.util.List;

import com.cts.inward.dto.CheckerDashboardStatsDTO;
import com.cts.inward.dto.InwardBatchDTO;

public interface TV2DashboardService {

    /**
     * Returns all TV2 batches created on the given date,
     * with per-batch cheque counts populated in the DTO.
     */
    List<InwardBatchDTO> getTV2BatchesForDate(Date date);

    /**
     * Returns KPI stats (assigned / pending / cleared batches,
     * pending cheques) for the given date — scoped to TV2 queue.
     */
    CheckerDashboardStatsDTO getStatsForDate(Date date);
}
