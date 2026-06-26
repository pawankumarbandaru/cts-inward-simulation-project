package com.cts.inward.service;

import java.util.Date;
import java.util.List;
import java.util.Map;

import com.cts.inward.dto.ChequeReportDTO;
import com.cts.inward.dto.InwardBatchDTO;
import com.cts.inward.dto.ReportChequeDetailDTO;

public interface ReportGenerationService {

    /** Generate RES + RRF reports for a single batch */
    Map<String, byte[]> generateReports(String batchId);

    /** Generate a combined RES report for multiple batches */
    byte[] generateBulkResReport(List<String> batchIds);

    /** Generate a combined RRF report for multiple batches */
    byte[] generateBulkRrfReport(List<String> batchIds);

    /**
     * Generate a PDF from an already-built list of ChequeReportDTOs.
     * Used by ReportComposer to download only the filtered cheques
     * shown in the detail grid (accepted-only, rejected-only, or both).
     */
    byte[] generatePdfFromData(List<ChequeReportDTO> data, String reportPath);
    
    
    List<ReportChequeDetailDTO> findAllChequesForReport(String batchId);
    
    
     List<InwardBatchDTO> getProcessedBatches(Date fromDate, Date toDate);
    
     List<ReportChequeDetailDTO> filterCheques(
	        List<ReportChequeDetailDTO> cheques,
	        String searchText,
	        String status) ;
     
     List<InwardBatchDTO> filterBatches(
    	        List<InwardBatchDTO> batches,
    	        String batchNo);
     
     
}
