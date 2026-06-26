package com.cts.inward.service;


import java.util.Date;
import java.util.List;

import com.cts.inward.dto.DashboardStatsDTO;
import com.cts.inward.dto.InwardBatchDTO;

public interface InwardDashboardService {

	DashboardStatsDTO getDashboardStats(
	        Date fromDate,
	        Date toDate);

    List<InwardBatchDTO> getAllBatches();

    List<InwardBatchDTO> searchBatches(
            String batchId,
            String status,
            Date fromDate,
            Date toDate
    );
   
}
