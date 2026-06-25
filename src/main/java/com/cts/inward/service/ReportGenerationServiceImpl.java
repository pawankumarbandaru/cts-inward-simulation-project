package com.cts.inward.service;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import com.cts.inward.dao.InwardChequeDao;
import com.cts.inward.dao.InwardChequeDaoImpl;
import com.cts.inward.dto.ChequeReportDTO;
import com.cts.inward.dto.ReportChequeDetailDTO;

import net.sf.jasperreports.engine.*;
import net.sf.jasperreports.engine.data.JRBeanCollectionDataSource;

public class ReportGenerationServiceImpl implements ReportGenerationService {

	private final InwardChequeDao chequeDao = new InwardChequeDaoImpl();

	private final SimpleDateFormat sdfReport = new SimpleDateFormat("dd MMM yyyy");

	/**
	 * Generates a consolidated RES report for multiple batches.
	 *
	 * Input: - List<String> batchIds
	 *
	 * Process: - Fetches accepted cheques for each batch. - Merges all results into
	 * a single list. - Sorts by batchId for Jasper grouping. - Generates PDF using
	 * res_report.jrxml.
	 *
	 * Returns: - PDF content as byte[].
	 */

	@Override
	public byte[] generateBulkResReport(List<String> batchIds) {
		try {
			List<ChequeReportDTO> allData = new ArrayList<>();
			for (String batchId : batchIds) {
				allData.addAll(chequeDao.findAcceptedCheques(batchId));
			}
			// Sort by batchId — required for JasperReports grouping
			allData.sort(
					Comparator.comparing(ChequeReportDTO::getBatchId, Comparator.nullsLast(Comparator.naturalOrder())));

			System.out.println("Bulk RES — total accepted cheques: " + allData.size());

			if (allData.isEmpty()) {
				return new byte[0];
			}

			return generatePdf(allData, "/reports/res_report.jrxml");
		} catch (Exception e) {
			throw new RuntimeException("Failed to generate bulk RES report", e);
		}
	}

	/**
	 * Generates a RRF (Returned / Rejected Cheques) PDF report for multiple
	 * batches.
	 *
	 * Input: ------ batchIds : List<String> List of batch IDs selected by the user.
	 *
	 * Processing: ----------- 1. Retrieves returned cheques for each batch using
	 * InwardChequeDao.findReturnedCheques(). 2. Merges all cheque records into a
	 * single collection. 3. Sorts the records by batchId for JasperReports
	 * grouping. 4. Generates a PDF using rrf_report.jrxml.
	 *
	 * Output: ------- Returns the generated PDF as a byte array.
	 * 
	 * @param batchIds List of selected batch IDs.
	 * @return PDF report as byte[].
	 */
	@Override
	public byte[] generateBulkRrfReport(List<String> batchIds) {
		try {
			List<ChequeReportDTO> allData = new ArrayList<>();
			for (String batchId : batchIds) {
				allData.addAll(chequeDao.findReturnedCheques(batchId));
			}
			// Sort by batchId — required for JasperReports grouping
			allData.sort(
					Comparator.comparing(ChequeReportDTO::getBatchId, Comparator.nullsLast(Comparator.naturalOrder())));

			System.out.println("Bulk RRF — total returned cheques: " + allData.size());

			if (allData.isEmpty()) {
				return new byte[0];
			}

			return generatePdf(allData, "/reports/rrf_report.jrxml");
		} catch (Exception e) {
			throw new RuntimeException("Failed to generate bulk RRF report", e);
		}
	}

	/**
     * Generates both RES and RRF reports for a single batch.
     *
     * Input:
     * ------
     * batchId : String
     *      Unique batch ID.
     *
     * Output:
     * -------
     * Returns a map where:
     *
     * Key   = Generated PDF file name
     * Value = PDF content as byte[]
     */
	@Override
	public Map<String, byte[]> generateReports(String batchId) {
		Map<String, byte[]> reports = new HashMap<>();
		try {
			List<ChequeReportDTO> accepted = chequeDao.findAcceptedCheques(batchId);
			if (!accepted.isEmpty()) {
				byte[] pdf = generatePdf(accepted, "/reports/res_report.jrxml");
				reports.put("RES_Report_" + batchId + ".pdf", pdf);
			}

			List<ChequeReportDTO> returned = chequeDao.findReturnedCheques(batchId);
			if (!returned.isEmpty()) {
				byte[] pdf = generatePdf(returned, "/reports/rrf_report.jrxml");
				reports.put("RRF_Report_" + batchId + ".pdf", pdf);
			}
		} catch (Exception e) {
			throw new RuntimeException(e);
		}
		return reports;
	}


	/**
	 * Generates a PDF report from an already prepared collection of
	 * ChequeReportDTO records.
	 *
	 * Input:
	 * ------
	 * data : List<ChequeReportDTO>
	 *      Collection of cheque records to be displayed in the report.
	 *
	 * reportPath : String
	 *      Classpath location of the JasperReports JRXML template.
	 * Output:
	 * -------
	 * Returns the generated PDF as a byte array.	
	 */
	@Override
	public byte[] generatePdfFromData(List<ChequeReportDTO> data, String reportPath) {
		try {
			if (data == null || data.isEmpty())
				return new byte[0];
			return generatePdf(data, reportPath);
		} catch (Exception e) {
			throw new RuntimeException("Failed to generate PDF from data", e);
		}
	}

	
	/**
	 * Core PDF generation method used by all report download operations.
	 * 
	 * Converts a collection of ChequeReportDTO records into a PDF document
	 * using the specified JasperReports JRXML template.
	 *
	 * This is the central report generation engine used by:
	 * - generateBulkResReport()
	 * - generateBulkRrfReport()
	 * - generateReports()
	 * - generatePdfFromData()
	 *
	 * Input:
	 * ------
	 * data : List<ChequeReportDTO>
	 *      Report data to be displayed in the generated PDF.
	 *
	 * reportPath : String
	 *      Classpath location of the JRXML template.
	 *      
	 * Output:
	 * -------
	 * Returns the generated PDF document as a byte array.
	 */
	private byte[] generatePdf(List<ChequeReportDTO> data, String reportPath) throws Exception {
		System.out.println("========== REPORT DEBUG ==========");
		System.out.println("Report Path = " + reportPath);
		System.out.println("Records     = " + data.size());

		InputStream inputStream = getClass().getResourceAsStream(reportPath);
		System.out.println("InputStream = " + inputStream);

		if (inputStream == null) {
			throw new RuntimeException("JRXML file not found: " + reportPath);
		}

		JasperReport jasperReport = JasperCompileManager.compileReport(inputStream);
		JRBeanCollectionDataSource dataSource = new JRBeanCollectionDataSource(data);

		// Pass today's date as a formatted string so the JRXML title can show it
		Map<String, Object> parameters = new HashMap<>();
		parameters.put("REPORT_DATE", sdfReport.format(new Date()).toUpperCase());

		JasperPrint jasperPrint = JasperFillManager.fillReport(jasperReport, parameters, dataSource);

		ByteArrayOutputStream baos = new ByteArrayOutputStream();
		JasperExportManager.exportReportToPdfStream(jasperPrint, baos);
		System.out.println("PDF GENERATED SUCCESSFULLY");
		return baos.toByteArray();
	}
	
	//Get all cheques for Report.
	
	@Override
	public List<ReportChequeDetailDTO> findAllChequesForReport(String batchId) {
	    return chequeDao.findAllChequesForReport(batchId);
	}
}
