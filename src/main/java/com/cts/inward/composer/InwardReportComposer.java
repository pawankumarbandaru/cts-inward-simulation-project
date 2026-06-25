package com.cts.inward.composer;

import java.math.BigDecimal;

import java.text.SimpleDateFormat;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import org.zkoss.zk.ui.Component;
import org.zkoss.zk.ui.select.SelectorComposer;
import org.zkoss.zk.ui.select.annotation.Listen;
import org.zkoss.zk.ui.select.annotation.Wire;
import org.zkoss.zk.ui.util.Clients;
import org.zkoss.zul.Button;
import org.zkoss.zul.Checkbox;
import org.zkoss.zul.Combobox;
import org.zkoss.zul.Datebox;
import org.zkoss.zul.Div;
import org.zkoss.zul.Filedownload;
import org.zkoss.zul.Label;
import org.zkoss.zul.Listbox;
import org.zkoss.zul.Listcell;
import org.zkoss.zul.Listitem;
import org.zkoss.zul.Textbox;

import com.cts.inward.dto.ChequeReportDTO;
import com.cts.inward.dto.InwardBatchDTO;
import com.cts.inward.dto.ReportChequeDetailDTO;
import com.cts.inward.service.InwardDashboardService;
import com.cts.inward.service.InwardDashboardServiceImpl;
import com.cts.inward.service.ReportGenerationService;
import com.cts.inward.service.ReportGenerationServiceImpl;

/**
 * Handles UI logic for the Inward Reports screen — batch list, date-range
 * filtering, batch detail drill-down, and RES/RRF report downloads
 */
public class InwardReportComposer extends SelectorComposer<Component> {

	// ── VIEW 1: Batch list ────────────────────────────────────────
	@Wire
	private Div viewBatchList;
	@Wire
	private Datebox filterDate;
	@Wire
	private Datebox filterDateTo;
	@Wire
	private Textbox txtBatchNo;
	@Wire
	private Label lblBatchCount;
	@Wire
	private Listbox batchListbox;

	// ── VIEW 1: Multi-select controls ────────────────────────────
	@Wire
	private Checkbox chkSelectAll; // header select-all checkbox
	@Wire
	private Button btnSmartDownload; // single smart download button

	// ── VIEW 2: Batch detail ──────────────────────────────────────
	@Wire
	private Div viewBatchDetail;
	@Wire
	private Label lblDetailBreadcrumb;
	@Wire
	private Label lblDetailSubtitle;
	@Wire
	private Label lblDetailBatchId;
	@Wire
	private Label lblDetailUploadTime;
	@Wire
	private Label lblDetailTotal;
	@Wire
	private Label lblDetailAccepted;
	@Wire
	private Label lblDetailRejected;
	@Wire
	private Label lblDetailPending;
	@Wire
	private Textbox txtDetailSearch;
	@Wire
	private Combobox cmbStatusFilter; // NEW: status filter dropdown
	@Wire
	private Listbox chequeListbox;

	// ── Services / DAOs ───────────────────────────────────────────
	private final InwardDashboardService dashService = new InwardDashboardServiceImpl();
	private final ReportGenerationService reportService = new ReportGenerationServiceImpl();
//	private final ReportChequeDetailDao chequeDao = new ReportChequeDetailDaoImpl();

	// ── State ─────────────────────────────────────────────────────
	private List<InwardBatchDTO> allBatches = new ArrayList<>();
	private List<InwardBatchDTO> filteredBatches = new ArrayList<>();
	private List<ReportChequeDetailDTO> allCheques = new ArrayList<>();
	private List<ReportChequeDetailDTO> currentFilteredCheques = new ArrayList<>();
	private InwardBatchDTO currentBatch = null;

	/** Tracks which batch IDs are currently checked */
	private final Set<String> selectedBatchIds = new HashSet<>();

	private final SimpleDateFormat sdfDisplay = new SimpleDateFormat("dd MMM yyyy");
	private final SimpleDateFormat sdfDateTime = new SimpleDateFormat("dd MMM yyyy HH:mm");

	private final DateTimeFormatter dtfDisplay = DateTimeFormatter.ofPattern("dd MMM yyyy");
	private final DateTimeFormatter dtfDateTime = DateTimeFormatter.ofPattern("dd MMM yyyy HH:mm");

	@Override
	public void doAfterCompose(Component comp) throws Exception {
		super.doAfterCompose(comp);
		showBatchList();
		filterDate.setValue(today());
		filterDateTo.setValue(today());
		loadBatchesForRange(today(), today());
		updateSmartButton();

		// Wire paging — re-render the current page slice when the user clicks
		// a page number, since rows are built manually (no ListModel bound).
		org.zkoss.zul.Paging batchPaging = batchListbox.getPagingChild();
		if (batchPaging != null) {
			batchPaging.addEventListener("onPaging", e -> {
				int newPage = ((org.zkoss.zul.event.PagingEvent) e).getActivePage();
				batchPaging.setActivePage(newPage);
				renderBatchTable(filteredBatches);
			});
		}
	}

	// Filter functionality ON date change
	@Listen("onChange=#filterDate")
	public void onDateChange() {
		Date selected = filterDate.getValue();
		if (selected == null) {
			selected = today();
			filterDate.setValue(selected);
		}
		Date to = filterDateTo.getValue();
		if (to == null) {
			to = today();
			filterDateTo.setValue(to);
		}
		if (selected.after(to)) {
			// keep range valid: push "to" forward to match "from"
			to = selected;
			filterDateTo.setValue(to);
		}
		selectedBatchIds.clear();
		loadBatchesForRange(selected, to);
	}

	@Listen("onChange=#filterDateTo")
	public void onDateToChange() {
		Date from = filterDate.getValue();
		if (from == null) {
			from = today();
			filterDate.setValue(from);
		}
		Date selected = filterDateTo.getValue();
		if (selected == null) {
			selected = today();
			filterDateTo.setValue(selected);
		}
		if (selected.before(from)) {
			// keep range valid: pull "from" back to match "to"
			from = selected;
			filterDate.setValue(from);
		}
		selectedBatchIds.clear();
		loadBatchesForRange(from, selected);
	}

	@Listen("onClick=#btnSearch")
	public void onSearch() {
		Date from = filterDate.getValue();
		if (from == null) {
			from = today();
			filterDate.setValue(from);
		}
		Date to = filterDateTo.getValue();
		if (to == null) {
			to = today();
			filterDateTo.setValue(to);
		}
		if (from.after(to)) {
			Clients.showNotification("From date cannot be after To date.", "warning", null, "top_center", 3000);
			return;
		}
		loadBatchesForRange(from, to);
		applyBatchNoFilter();
	}

	@Listen("onClick=#btnClear")
	public void onClear() {
		txtBatchNo.setValue("");
		filterDate.setValue(today());
		filterDateTo.setValue(today());
		selectedBatchIds.clear();
		loadBatchesForRange(today(), today());
	}

	/** Select-all header checkbox */
	@Listen("onCheck=#chkSelectAll")
	public void onSelectAll() {
		boolean checked = chkSelectAll.isChecked();
		if (checked) {
			filteredBatches.forEach(b -> selectedBatchIds.add(b.getBatchId()));
		} else {
			selectedBatchIds.clear();
		}
		renderBatchTable(filteredBatches);
		updateSmartButton();
	}

	/** Single smart download button — downloads selected if any, else all */
	@Listen("onClick=#btnSmartDownload")
	public void onSmartDownload() {
		if (!selectedBatchIds.isEmpty()) {
			List<InwardBatchDTO> selected = filteredBatches.stream()
					.filter(b -> selectedBatchIds.contains(b.getBatchId())).collect(Collectors.toList());
			downloadReportForBatches(selected);
		} else {
			if (filteredBatches.isEmpty()) {
				Clients.showNotification("No batches to download.", "warning", null, "top_center", 3000);
				return;
			}
			downloadReportForBatches(filteredBatches);
		}
	}

	/**
	 * Loads batches for the given date range (inclusive). Passes start-of-day for
	 * the from-date and end-of-day for the to-date to the service. Returns a list
	 * of InwardBatchDTO objects and filters valid batches.
	 */
	private void loadBatchesForRange(Date from, Date to) {
		/**
		 * Calls the dashboard service to fetch inward batch data for the
		 * specified date range and converts the results into DTO objects.
		 */
		List<InwardBatchDTO> raw = dashService.searchBatches("", "", toStartOfDay(from), toEndOfDay(to));

		allBatches = raw.stream().filter(b -> {
			int acc = b.getAcceptedCheques() != null ? b.getAcceptedCheques() : 0;
			int rej = b.getRejectedCheques() != null ? b.getRejectedCheques() : 0;
			return (acc + rej) > 0;
		}).collect(Collectors.toList());

		filteredBatches = new ArrayList<>(allBatches);
		resetToFirstPage();
		renderBatchTable(filteredBatches);
		updateSmartButton();
	}

	/**
	 * Filters batches based on the entered batch number. Returns matching batches
	 * and updates the batch table.
	 */

	private void applyBatchNoFilter() {
		// Get the entered batch number and convert it to lowercase for search.
		final String f = txtBatchNo.getValue() == null ? "" : txtBatchNo.getValue().trim().toLowerCase();

		// Filter batches that match the entered batch number.
		filteredBatches = f.isEmpty() ? new ArrayList<>(allBatches)
				: allBatches.stream().filter(b -> b.getBatchId() != null && b.getBatchId().toLowerCase().contains(f))
						.collect(Collectors.toList());
		resetToFirstPage();
		renderBatchTable(filteredBatches);
		if (filteredBatches.isEmpty())
			Clients.showNotification("No batches found.", "warning", null, "top_center", 3000);
	}

	/**
	 * Jumps the batch listbox's pager back to page 1 (used whenever filters
	 * change).
	 */
	private void resetToFirstPage() {
		org.zkoss.zul.Paging paging = batchListbox.getPagingChild();
		if (paging != null) {
			paging.setActivePage(0);
		}
	}

	/**
	 * Renders the batch list table for the current page. Pagination is handled
	 * manually because rows are appended directly to the table.
	 */
	private void renderBatchTable(List<InwardBatchDTO> batches) {

		batchListbox.getItems().clear();
		lblBatchCount.setValue(batches.size() + " Batch" + (batches.size() == 1 ? "" : "es"));

		/**
		 * Pagination setup. Since rows are added manually using appendChild(),
		 * automatic pagination provided by mold="paging" is not available. Therefore,
		 * pagination is handled manually by setting the total record count in the
		 * Paging component and rendering only the records for the current page.
		 */
		final int pageSize = batchListbox.getPageSize() > 0 ? batchListbox.getPageSize() : 5;
		org.zkoss.zul.Paging paging = batchListbox.getPagingChild();
		if (paging != null) {
			paging.setPageSize(pageSize);
			paging.setTotalSize(batches.size());
			// Keep current page if still valid, else reset to first page
			int maxPage = Math.max(0, (batches.size() - 1) / pageSize);
			if (paging.getActivePage() > maxPage) {
				paging.setActivePage(0);
			}
		}
		int activePage = paging != null ? paging.getActivePage() : 0;
		int fromIndex = Math.min(activePage * pageSize, batches.size());
		int toIndex = Math.min(fromIndex + pageSize, batches.size());
		List<InwardBatchDTO> pageBatches = batches.subList(fromIndex, toIndex);

		// Sync select-all checkbox state (based on full filtered list, not just this
		// page)
		boolean allSelected = !batches.isEmpty()
				&& batches.stream().allMatch(b -> selectedBatchIds.contains(b.getBatchId()));
		chkSelectAll.setChecked(allSelected);

		int rowNum = fromIndex + 1;
		for (InwardBatchDTO batch : pageBatches) {

			Listitem item = new Listitem();
			item.setSclass("rpt-list-row");

			// ── Col 0: Checkbox ──
			Listcell chkCell = new Listcell();
			Checkbox chk = new Checkbox();
			chk.setChecked(selectedBatchIds.contains(batch.getBatchId()));
			chk.addEventListener("onCheck", e -> {
				if (chk.isChecked()) {
					selectedBatchIds.add(batch.getBatchId());
				} else {
					selectedBatchIds.remove(batch.getBatchId());
				}
				updateSmartButton();
				// update select-all checkbox
				boolean allNowSelected = !filteredBatches.isEmpty()
						&& filteredBatches.stream().allMatch(b -> selectedBatchIds.contains(b.getBatchId()));
				chkSelectAll.setChecked(allNowSelected);
			});
			chkCell.appendChild(chk);
			// Stop click propagation so checkbox click doesn't open batch detail
			chkCell.addEventListener("onClick", e -> e.stopPropagation());
			item.appendChild(chkCell);

			// ── Col 1: # ──
			item.appendChild(new Listcell(String.valueOf(rowNum++)));

			// ── Col 2: BATCH NO — blue clickable link ──
			Listcell batchCell = new Listcell();
			Label lnk = new Label(nullSafe(batch.getBatchId()));
			lnk.setSclass("rpt-batch-link");
			batchCell.appendChild(lnk);
			item.appendChild(batchCell);

			// ── Col 3: DATE ──
			item.appendChild(
					new Listcell(batch.getUploadDate() != null ? batch.getUploadDate().format(dtfDisplay) : "-"));

			// ── Col 4: TOTAL CHEQUES ──
			item.appendChild(
					new Listcell(batch.getTotalCheques() != null ? String.valueOf(batch.getTotalCheques()) : "0"));

			// ── Col 5: AMOUNT ──
			BigDecimal amt = batch.getTotalAmount();
			item.appendChild(
					new Listcell((amt != null && amt.compareTo(BigDecimal.ZERO) > 0) ? "₹" + formatIndian(amt) : "₹0"));

			// ── Col 6: ACCEPTED badge ──
			Listcell accCell = new Listcell();
			int accepted = batch.getAcceptedCheques() != null ? batch.getAcceptedCheques() : 0;
			Label lblAcc = new Label(String.valueOf(accepted));
			lblAcc.setSclass(accepted > 0 ? "rpt-badge-accepted" : "rpt-badge-zero");
			accCell.appendChild(lblAcc);
			item.appendChild(accCell);

			// ── Col 7: REJECTED badge ──
			Listcell rejCell = new Listcell();
			int rejected = batch.getRejectedCheques() != null ? batch.getRejectedCheques() : 0;
			Label lblRej = new Label(String.valueOf(rejected));
			lblRej.setSclass(rejected > 0 ? "rpt-badge-rejected" : "rpt-badge-zero");
			rejCell.appendChild(lblRej);
			item.appendChild(rejCell);

			// ── Col 8: PENDING badge ──
			Listcell penCell = new Listcell();
			int pending = batch.getPendingCheques() != null ? batch.getPendingCheques() : 0;
			Label lblPen = new Label(String.valueOf(pending));
			lblPen.setSclass(pending > 0 ? "rpt-badge-pending" : "rpt-badge-zero");
			penCell.appendChild(lblPen);
			item.appendChild(penCell);

			// ── Col 9: ACTION — Generate Report button ──
			Listcell actionCell = new Listcell();
			Button genBtn = new Button("Generate Report");
			genBtn.setSclass("rpt-gen-btn");
			final InwardBatchDTO batchRef = batch;
			genBtn.addEventListener("onClick", e -> {
				e.stopPropagation();
				downloadReportForBatches(java.util.Collections.singletonList(batchRef));
			});
			actionCell.appendChild(genBtn);
			item.appendChild(actionCell);

			// ── Row click → open detail view ──
			final InwardBatchDTO batchForDetail = batch;
			item.addEventListener("onClick", e -> openBatchDetail(batchForDetail));

			batchListbox.appendChild(item);
		}
	}

	/** Updates the smart button label based on selection state */
	private void updateSmartButton() {
		int count = selectedBatchIds.size();
		if (count > 0) {
			btnSmartDownload.setLabel("⬇ Download Selected (" + count + ")");
			btnSmartDownload.setSclass("rpt-btn-download-selected-active");
		} else {
			btnSmartDownload.setLabel("⬇ Download All Reports");
			btnSmartDownload.setSclass("rpt-btn-download-all");
		}
	}

	private void openBatchDetail(InwardBatchDTO batch) {
		currentBatch = batch;

		// breadcrumb + subtitle
		lblDetailBreadcrumb.setValue("Inward - Batch Detail");
		int total = batch.getTotalCheques() != null ? batch.getTotalCheques() : 0;
		lblDetailSubtitle.setValue("Batch " + batch.getBatchId() + " - " + total + " cheque" + (total == 1 ? "" : "s"));

		// left panel
		lblDetailBatchId.setValue(nullSafe(batch.getBatchId()));
		lblDetailUploadTime
				.setValue(batch.getUploadDate() != null ? batch.getUploadDate().format(dtfDateTime) : "--:-- --");
		lblDetailTotal.setValue(String.valueOf(total));
		lblDetailAccepted.setValue(String.valueOf(batch.getAcceptedCheques() != null ? batch.getAcceptedCheques() : 0));
		lblDetailRejected.setValue(String.valueOf(batch.getRejectedCheques() != null ? batch.getRejectedCheques() : 0));
		lblDetailPending.setValue(String.valueOf(batch.getPendingCheques() != null ? batch.getPendingCheques() : 0));

		// reset filters
		txtDetailSearch.setValue("");
		cmbStatusFilter.setValue("All");

		// load cheques
		allCheques = reportService.findAllChequesForReport(batch.getBatchId());
		currentFilteredCheques = new ArrayList<>(allCheques);
		renderChequeTable(currentFilteredCheques);

		showBatchDetail();
	}

	@Listen("onClick=#btnBackToList")
	public void onBackToList() {
		currentBatch = null;
		showBatchList();
	}

	@Listen("onClick=#btnDetailDownload")
	public void onDetailDownload() {
		if (currentBatch == null)
			return;
		downloadFilteredCheques(currentBatch.getBatchId(), currentFilteredCheques);
	}

	/**
	 * Live search — fires on every keystroke (onChanging gives the mid-type value
	 * via event)
	 */
	@Listen("onChanging=#txtDetailSearch")
	public void onDetailSearchChanging(org.zkoss.zk.ui.event.InputEvent e) {
		applyDetailFilters(e.getValue());
	}

	/** Also handle onChange in case user pastes or clears the field */
	@Listen("onChange=#txtDetailSearch")
	public void onDetailSearchChange() {
		applyDetailFilters(txtDetailSearch.getValue());
	}

	/** Status filter dropdown */
	@Listen("onChange=#cmbStatusFilter")
	public void onStatusFilter() {
		applyDetailFilters(txtDetailSearch.getValue());
	}

	private void applyDetailFilters(String searchText) {
		String q = searchText == null ? "" : searchText.trim().toLowerCase();
		String status = cmbStatusFilter.getValue() == null ? "All" : cmbStatusFilter.getValue().trim();

		List<ReportChequeDetailDTO> filtered = allCheques.stream().filter(c -> {
			// text search — matches chequeNo, accountNo, payeeName, or amount
			if (!q.isEmpty()) {
				boolean textMatch = (c.getChequeNo() != null && c.getChequeNo().toLowerCase().contains(q))
						|| (c.getAccountNo() != null && c.getAccountNo().toLowerCase().contains(q))
						|| (c.getPayeeName() != null && c.getPayeeName().toLowerCase().contains(q))
						|| (c.getAmount() != null && c.getAmount().toPlainString().contains(q));
				if (!textMatch)
					return false;
			}
			// status filter
			if (!status.isEmpty() && !"All".equalsIgnoreCase(status)) {
				String chequeStatus = c.getStatus() != null ? c.getStatus() : "PENDING";
				return chequeStatus.equalsIgnoreCase(status);
			}
			return true;
		}).collect(Collectors.toList());

		currentFilteredCheques = filtered;
		renderChequeTable(currentFilteredCheques);
	}

	private void renderChequeTable(List<ReportChequeDetailDTO> cheques) {
		chequeListbox.getItems().clear();

		int rowNum = 1;
		for (ReportChequeDetailDTO c : cheques) {
			Listitem item = new Listitem();
			item.setSclass("rpt-list-row");

			item.appendChild(new Listcell(String.valueOf(rowNum++)));

			Listcell chqCell = new Listcell();
			Label chqLbl = new Label(nullSafe(c.getChequeNo()));
			chqLbl.setSclass("rpt-cheque-no");
			chqCell.appendChild(chqLbl);
			item.appendChild(chqCell);

			item.appendChild(new Listcell(nullSafe(c.getAccountNo())));
			item.appendChild(new Listcell(nullSafe(c.getPayeeName())));

			BigDecimal amt = c.getAmount();
			item.appendChild(new Listcell(amt != null ? "Rs. " + amt.toPlainString() : "Rs. 0.00"));

			Listcell statusCell = new Listcell();
			String status = c.getStatus() != null ? c.getStatus() : "PENDING";
			Label statusLbl = new Label(status);
			switch (status) {
			case "ACCEPTED" -> statusLbl.setSclass("rpt-status-accepted");
			case "REJECTED" -> statusLbl.setSclass("rpt-status-rejected");
			default -> statusLbl.setSclass("rpt-status-pending");
			}
			statusCell.appendChild(statusLbl);
			item.appendChild(statusCell);

			String reason = c.getReason();
			item.appendChild(new Listcell(reason != null && !reason.isBlank() ? reason : "-"));

			chequeListbox.appendChild(item);
		}
	}

	// ══════════════════════════════════════════════════════════════
	// View switching
	// ══════════════════════════════════════════════════════════════

	private void showBatchList() {
		viewBatchList.setVisible(true);
		viewBatchDetail.setVisible(false);
	}

	private void showBatchDetail() {
		viewBatchList.setVisible(false);
		viewBatchDetail.setVisible(true);
	}

	// ══════════════════════════════════════════════════════════════
	// Download logic
	// ══════════════════════════════════════════════════════════════

	/**
	 * Download report for the filtered cheque grid in the detail view. - If
	 * filtered list has only ACCEPTED cheques → download RES only. - If filtered
	 * list has only REJECTED cheques → download RRF only. - If filtered list has
	 * both → download both. - PENDING-only or empty → warn, nothing to download.
	 */
	private void downloadFilteredCheques(String batchId, List<ReportChequeDetailDTO> cheques) {
		if (cheques == null || cheques.isEmpty()) {
			Clients.showNotification("No cheques to download.", "warning", null, "top_center", 3000);
			return;
		}

		// Build ChequeReportDTOs from the filtered grid rows
		List<ChequeReportDTO> resData = cheques
				.stream().filter(c -> "ACCEPTED".equalsIgnoreCase(c.getStatus())).map(c -> new ChequeReportDTO(batchId,
						c.getChequeNo(), c.getChequeDate(), c.getMicrCode(), c.getPayeeName(), c.getAmount(), null))
				.collect(Collectors.toList());

		List<ChequeReportDTO> rrfData = cheques.stream().filter(c -> "REJECTED".equalsIgnoreCase(c.getStatus()))
				.map(c -> new ChequeReportDTO(batchId, c.getChequeNo(), c.getChequeDate(), c.getMicrCode(),
						c.getPayeeName(), c.getAmount(), c.getReason()))
				.collect(Collectors.toList());

		boolean hasRes = !resData.isEmpty();
		boolean hasRrf = !rrfData.isEmpty();

		if (!hasRes && !hasRrf) {
			Clients.showNotification("No accepted or rejected cheques in current view to download.", "warning", null,
					"top_center", 3500);
			return;
		}

		try {
			byte[] resPdf = hasRes ? reportService.generatePdfFromData(resData, "/reports/res_report.jrxml") : null;
			byte[] rrfPdf = hasRrf ? reportService.generatePdfFromData(rrfData, "/reports/rrf_report.jrxml") : null;

			String suffix = batchId;

			if (hasRes && hasRrf) {
				Filedownload.save(resPdf, "application/pdf", "RES_" + suffix + ".pdf");
				String b64 = java.util.Base64.getEncoder().encodeToString(rrfPdf);
				String js = "setTimeout(function(){" + "  var a = document.createElement('a');"
						+ "  a.href = 'data:application/pdf;base64," + b64 + "';" + "  a.download = 'RRF_" + suffix
						+ ".pdf';" + "  document.body.appendChild(a);" + "  a.click();"
						+ "  document.body.removeChild(a);" + "}, 800);";
				Clients.evalJavaScript(js);
			} else if (hasRes) {
				Filedownload.save(resPdf, "application/pdf", "RES_" + suffix + ".pdf");
			} else {
				Filedownload.save(rrfPdf, "application/pdf", "RRF_" + suffix + ".pdf");
			}

			Clients.showNotification("Report(s) downloaded successfully.", "info", null, "top_center", 2500);

		} catch (Exception ex) {
			ex.printStackTrace();
			Clients.showNotification("Failed to generate report: " + ex.getMessage(), "error", null, "top_center",
					4000);
		}
	}

	private void downloadReportForBatches(List<InwardBatchDTO> batches) {
		List<String> batchIds = batches.stream().map(InwardBatchDTO::getBatchId).collect(Collectors.toList());

		try {
			byte[] resPdf = reportService.generateBulkResReport(batchIds);
			byte[] rrfPdf = reportService.generateBulkRrfReport(batchIds);

			boolean hasRes = resPdf != null && resPdf.length > 0;
			boolean hasRrf = rrfPdf != null && rrfPdf.length > 0;

			if (!hasRes && !hasRrf) {
				Clients.showNotification("No accepted or returned cheques found for the selected batch(es).", "warning",
						null, "top_center", 3500);
				return;
			}

			String suffix = batchIds.size() == 1 ? batchIds.get(0) : sdfDisplay.format(new Date());

			if (hasRes && hasRrf) {
				Filedownload.save(resPdf, "application/pdf", "RES_" + suffix + ".pdf");

				String b64 = java.util.Base64.getEncoder().encodeToString(rrfPdf);
				String js = "setTimeout(function(){" + "  var a = document.createElement('a');"
						+ "  a.href = 'data:application/pdf;base64," + b64 + "';" + "  a.download = 'RRF_" + suffix
						+ ".pdf';" + "  document.body.appendChild(a);" + "  a.click();"
						+ "  document.body.removeChild(a);" + "}, 800);";
				Clients.evalJavaScript(js);

			} else if (hasRes) {
				Filedownload.save(resPdf, "application/pdf", "RES_" + suffix + ".pdf");
			} else {
				Filedownload.save(rrfPdf, "application/pdf", "RRF_" + suffix + ".pdf");
			}

			Clients.showNotification("Report(s) downloaded successfully.", "info", null, "top_center", 2500);

		} catch (Exception ex) {
			ex.printStackTrace();
			Clients.showNotification("Failed to generate report: " + ex.getMessage(), "error", null, "top_center",
					4000);
		}
	}

	// ══════════════════════════════════════════════════════════════
	// Date helpers
	// ══════════════════════════════════════════════════════════════

	private Date today() {
		Calendar cal = Calendar.getInstance();
		cal.set(Calendar.HOUR_OF_DAY, 0);
		cal.set(Calendar.MINUTE, 0);
		cal.set(Calendar.SECOND, 0);
		cal.set(Calendar.MILLISECOND, 0);
		return cal.getTime();
	}

	private Date toStartOfDay(Date date) {
		Calendar cal = Calendar.getInstance();
		cal.setTime(date);
		cal.set(Calendar.HOUR_OF_DAY, 0);
		cal.set(Calendar.MINUTE, 0);
		cal.set(Calendar.SECOND, 0);
		cal.set(Calendar.MILLISECOND, 0);
		return cal.getTime();
	}

	private Date toEndOfDay(Date date) {
		Calendar cal = Calendar.getInstance();
		cal.setTime(date);
		cal.set(Calendar.HOUR_OF_DAY, 23);
		cal.set(Calendar.MINUTE, 59);
		cal.set(Calendar.SECOND, 59);
		cal.set(Calendar.MILLISECOND, 999);
		return cal.getTime();
	}

	// ══════════════════════════════════════════════════════════════
	// Formatting helpers
	// ══════════════════════════════════════════════════════════════

	private String nullSafe(String val) {
		return val != null ? val : "-";
	}

	private String formatIndian(BigDecimal amount) {
		long intPart = amount.longValue();
		String intStr = formatIndianInteger(intPart);
		int scale = amount.scale();
		if (scale > 0) {
			String dec = amount.toPlainString();
			int dot = dec.indexOf('.');
			return intStr + (dot >= 0 ? dec.substring(dot) : "");
		}
		return intStr;
	}

	private String formatIndianInteger(long num) {
		if (num < 0)
			return "-" + formatIndianInteger(-num);
		if (num < 1000)
			return String.valueOf(num);
		String s = String.valueOf(num);
		int len = s.length();
		StringBuilder sb = new StringBuilder();
		sb.insert(0, s.substring(len - 3));
		int remaining = len - 3;
		while (remaining > 0) {
			int start = Math.max(0, remaining - 2);
			sb.insert(0, ",");
			sb.insert(0, s.substring(start, remaining));
			remaining = start;
		}
		return sb.toString();
	}
}