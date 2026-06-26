package com.cts.inward.composer;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

import org.zkoss.zk.ui.Component;
import org.zkoss.zk.ui.event.Event;
import org.zkoss.zk.ui.event.EventQueues;
import org.zkoss.zk.ui.select.SelectorComposer;
import org.zkoss.zk.ui.select.annotation.Listen;
import org.zkoss.zk.ui.select.annotation.Wire;
import org.zkoss.zul.Label;
import org.zkoss.zul.Listbox;
import org.zkoss.zul.Listcell;
import org.zkoss.zul.Listitem;
import org.zkoss.zul.Paging;
import org.zkoss.zul.Textbox;
import org.zkoss.zul.Timer;
import org.zkoss.zul.event.PagingEvent;

import com.cts.inward.dto.CbsAccountData;
import com.cts.inward.dto.CbsValidationResult;
import com.cts.inward.entity.InwardCheque;
import com.cts.inward.enums.CbsValidation;
import com.cts.inward.service.CbsValidationService;
import com.cts.inward.service.CbsValidationServiceImpl;
import com.cts.inward.service.InwardChequeMICRService;
import com.cts.inward.service.InwardChequeServiceMICRImpl;

public class CbsChequeListComposer extends SelectorComposer<Component> {

	private static final long serialVersionUID = 1L;
	private static final int PAGE_SIZE = 8;

	private final InwardChequeMICRService chequeService = new InwardChequeServiceMICRImpl();
	private final CbsValidationService cbsService = new CbsValidationServiceImpl();

	@Wire
	Listbox lbCbsChequeList;
	@Wire
	Paging pgCbsChequeList;
	@Wire
	Textbox tbCbsSearch;
	@Wire
	Listbox lbCbsStatusFilter;
	@Wire
	Timer cbsProcessTimer;

	private List<CbsResultRow> allResults = new ArrayList<>();
	private List<CbsResultRow> filteredResults = new ArrayList<>();

	private Long currentBatchId = null;
	private int processingIndex = 0; // which cheque in allResults to process next

	/**
	 * Subscribes to the "batchContext" queue to receive batchDbId and trigger Phase
	 * 1 loading, and to "removeInvalidCheques" to filter out rejected rows after
	 * Return to RRF. Related to: CBS Validation page — wires this composer to
	 * page-level events.
	 */
	@Override
	public void doAfterCompose(Component comp) throws Exception {
		super.doAfterCompose(comp);

		pgCbsChequeList.setPageSize(PAGE_SIZE);
		cbsProcessTimer.stop();

		// Subscribe to batchContext queue
		EventQueues.lookup("batchContext", EventQueues.DESKTOP, true).subscribe((Event event) -> {
			Object data = event.getData();
			if (data instanceof Long) {
				currentBatchId = (Long) data;
				loadAllChequesAndStartProcessing();
			}
		});

		// Subscribe to removeInvalidCheques
		// Fired by CbsValidationComposer after saving REJECTED decisions to DB.
		EventQueues.lookup("removeInvalidCheques", EventQueues.DESKTOP, true).subscribe((Event event) -> {
			removeInvalidRowsFromList();
		});
	}

	/**
	 * Loads all cheques for the batch from DB, builds a CbsResultRow per cheque
	 * with result=null ("Processing..."), renders all rows immediately, then starts
	 * the timer for Phase 2. Related to: CBS Validation page Phase 1 — populates
	 * the full list before validation begins.
	 */
	private void loadAllChequesAndStartProcessing() {
		if (currentBatchId == null || currentBatchId <= 0)
			return;

		List<InwardCheque> cheques;
		try {
			cheques = chequeService.getChequesByBatchId(currentBatchId);
		} catch (Exception e) {
			System.err.println("CbsChequeListComposer: error loading cheques: " + e.getMessage());
			return;
		}

		if (cheques == null || cheques.isEmpty())
			return;

		// Reset state
		allResults = new ArrayList<>();
		filteredResults = new ArrayList<>();
		processingIndex = 0;

		// Build a result row for every cheque — result is null = "Processing..."
		for (InwardCheque cheque : cheques) {
			allResults.add(new CbsResultRow(cheque));
		}

		System.out.println(
				"CbsChequeListComposer: loaded " + allResults.size() + " cheques — showing all as Processing...");

		// Render all rows right now (all with "Processing..." status)
		applyFilterAndRefresh();

		// Start timer — Phase 2 will update each row's status one by one
		cbsProcessTimer.start();
	}

	/**
	 * Called every 700ms by the ZK Timer; fetches CBS data for the next unprocessed
	 * cheque, validates it, persists the result via native SQL, updates the row's
	 * labels in-place, and stops the timer + fires "cbsValidationComplete" when all
	 * cheques are processed. Related to: CBS Validation page Phase 2 — drives live
	 * per-cheque status updates.
	 */
	@Listen("onTimer = #cbsProcessTimer")
	public void onTimerTick() {
		if (processingIndex >= allResults.size()) {
			cbsProcessTimer.stop();
			onValidationComplete();
			return;
		}

		CbsResultRow row = allResults.get(processingIndex);
		processingIndex++;

		InwardCheque cheque = row.cheque;

		System.out.println("CbsChequeListComposer: processing " + processingIndex + "/" + allResults.size() + " → "
				+ cheque.getChequeNo());

		CbsAccountData cbsData = null;
		String fetchErr = null;
		try {
			cbsData = cbsService.fetchAccountData(cheque.getAccountNo());
		} catch (Exception e) {
			fetchErr = "CBS Fetch Error";
			System.err.println(
					"CbsChequeListComposer: CBS fetch failed for " + cheque.getChequeNo() + ": " + e.getMessage());
		}

		CbsValidationResult result;
		if (fetchErr != null) {
			result = CbsValidationResult.failure("Missing CBS Data");
		} else {
			try {
				result = cbsService.validateCheque(cheque, cbsData);
			} catch (Exception e) {
				result = CbsValidationResult.failure("Validation Error");
			}
		}

		CbsValidation cbsResult = result.isValid() ? CbsValidation.Valid : CbsValidation.Invalid;
		try {
			chequeService.updateCbsValidationResult(cheque.getId(), cbsResult, result.getReason());
			cheque.setCbsValidation(cbsResult);
			cheque.setErrorReason(result.getReason());
		} catch (Exception e) {
			System.err.println(
					"CbsChequeListComposer: DB update failed for " + cheque.getChequeNo() + ": " + e.getMessage());
		}

		// ── Store result in the row ───────────────────────────────────
		row.result = result;

		// ── Directly update this row's status + reason labels ─────────
		updateRowLabels(row);

		// Notify batch summary to refresh counts
		EventQueues.lookup("chequeStatusUpdated", EventQueues.DESKTOP, true)
				.publish(new Event("onChequeStatusUpdated", null, null));
	}

	/**
	 * Directly updates the statusLabel text/sclass and reasonLabel text for one row
	 * Related to: CBS Validation page — provides flicker-free per-row live updates.
	 */
	private void updateRowLabels(CbsResultRow row) {
		if (row.statusLabel == null || row.reasonLabel == null)
			return;

		try {
			if (row.result.isValid()) {
				row.statusLabel.setValue("VALID");
				row.statusLabel.setSclass("cbs-status-badge cbs-badge-valid");
			} else {
				row.statusLabel.setValue("INVALID");
				row.statusLabel.setSclass("cbs-status-badge cbs-badge-invalid");
			}
			row.reasonLabel.setValue(nullSafe(row.result.getReason()));
		} catch (Exception e) {
			System.err.println("CbsChequeListComposer: label update skipped for " + row.cheque.getChequeNo() + ": "
					+ e.getMessage());
		}
	}

	/**
	 * Collects valid/invalid counts from all completed results and publishes them
	 * to the "cbsValidationComplete" queue so CbsValidationComposer can enable the
	 * action buttons. Related to: CBS Validation page — signals the end processing.
	 */
	private void onValidationComplete() {
		List<CbsValidationResult> results = allResults.stream().map(row -> row.result).filter(result -> result != null)
				.collect(Collectors.toList());

		long[] counts = cbsService.countValidAndInvalid(results);

		System.out.println("CbsChequeListComposer: all done. valid=" + counts[0] + " invalid=" + counts[1]);

		EventQueues.lookup("cbsValidationComplete", EventQueues.DESKTOP, true)
				.publish(new Event("onCbsValidationComplete", null, counts));
	}

	/**
	 * Removes all INVALID rows from allResults (keeping only valid ones) and
	 * re-renders the list after "Return to RRF" has been confirmed and saved to DB.
	 * Related to: CBS Validation page — cleans up the list after RRF action.
	 */
	private void removeInvalidRowsFromList() {
		allResults = allResults.stream().filter(row -> row.result != null && row.result.isValid())
				.collect(Collectors.toList());
		System.out.println("CbsChequeListComposer: invalid rows removed. remaining=" + allResults.size());
		applyFilterAndRefresh();
	}

	/**
	 * Triggers applyFilterAndRefresh() whenever the search textbox value changes.
	 */
	@Listen("onChange = #tbCbsSearch")
	public void onSearch() {
		applyFilterAndRefresh();
	}

	/**
	 * Triggers applyFilterAndRefresh() whenever a status filter option is selected.
	 */
	@Listen("onSelect = #lbCbsStatusFilter")
	public void onFilterChange() {
		applyFilterAndRefresh();
	}

	/**
	 * Applies the current keyword and status filter to allResults, updates
	 * filteredResults, clamps the active page, and re-renders the current page.
	 * Related to: CBS Validation page — drives the search/filteron the CBS list.
	 */
	private void applyFilterAndRefresh() {
		String keyword = tbCbsSearch != null ? tbCbsSearch.getValue().trim().toLowerCase() : "";
		String statusFilter = getSelectedStatus();

		filteredResults = allResults.stream().filter(row -> matchesStatus(row, statusFilter))
				.filter(row -> matchesKeyword(row, keyword)).collect(Collectors.toList());

		pgCbsChequeList.setTotalSize(filteredResults.size());

		int currentPage = pgCbsChequeList.getActivePage();
		int maxPage = filteredResults.isEmpty() ? 0 : (filteredResults.size() - 1) / PAGE_SIZE;
		if (currentPage > maxPage) {
			pgCbsChequeList.setActivePage(maxPage);
		}

		renderPage(pgCbsChequeList.getActivePage());
	}

	/**
	 * Returns the value of the currently selected status filter listitem, or "ALL"
	 * if nothing is selected or the listbox is null.
	 */
	private String getSelectedStatus() {
		if (lbCbsStatusFilter == null)
			return "ALL";
		Listitem selected = lbCbsStatusFilter.getSelectedItem();
		return (selected != null) ? selected.getValue().toString() : "ALL";
	}

	/**
	 * Returns true if the row matches the status filter: ALL passes everything,
	 * VALID/INVALID match row.result; rows still Processing... only show under ALL.
	 */
	private boolean matchesStatus(CbsResultRow row, String status) {
		if ("ALL".equals(status))
			return true;
		if (row.result == null)
			return "ALL".equals(status);
		if ("VALID".equals(status))
			return row.result.isValid();
		if ("INVALID".equals(status))
			return !row.result.isValid();
		return true;
	}

	/**
	 * Returns true if the cheque number or holder name contains the keyword; always
	 * returns true if the keyword is empty.
	 */
	private boolean matchesKeyword(CbsResultRow row, String keyword) {
		if (keyword.isEmpty())
			return true;
		return contains(row.cheque.getChequeNo(), keyword) || contains(row.holderName, keyword);
	}

	/**
	 * Returns true if the field is non-null and contains the keyword
	 * (case-insensitive).
	 */
	private boolean contains(String field, String keyword) {
		return field != null && field.toLowerCase().contains(keyword);
	}

	/**
	 * Renders the page corresponding to the pagination event's active page index.
	 */
	@Listen("onPaging = #pgCbsChequeList")
	public void onPageChange(PagingEvent event) {
		renderPage(event.getActivePage());
	}

	/**
	 * Clears the listbox and rebuilds one page slice of filteredResults; stores
	 * direct Label references (statusLabel, reasonLabel) in each CbsResultRow for
	 * live Phase 2 updates. Related to: CBS Validation page — renders the visible
	 * cheque rows with current CBS status.
	 */
	private void renderPage(int pageIndex) {
		lbCbsChequeList.getItems().clear();

		int from = pageIndex * PAGE_SIZE;
		int to = Math.min(from + PAGE_SIZE, filteredResults.size());

		int rowNum = from + 1;
		for (int i = from; i < to; i++) {
			CbsResultRow row = filteredResults.get(i);
			Listitem item = buildRow(row, rowNum++);
			lbCbsChequeList.appendChild(item);
		}
	}

	/**
	 * Builds a single Listitem for a CbsResultRow with columns for row number,
	 * cheque number, masked account, holder name, CBS status badge, and reason;
	 * stores statusLabel and reasonLabel references in the row for direct Phase 2
	 * updates. Returns: Listitem — a fully built CBS row ready to be appended to
	 * the listbox.
	 */
	private Listitem buildRow(CbsResultRow row, int rowNum) {
		Listitem item = new Listitem();

		// Col 1: Row number
		item.appendChild(new Listcell(String.valueOf(rowNum)));

		// Col 2: Cheque Number
		Listcell cellChequeNo = new Listcell();
		Label lblChequeNo = new Label(nullSafe(row.cheque.getChequeNo()));
		lblChequeNo.setSclass("cheque-no-link");
		cellChequeNo.appendChild(lblChequeNo);
		item.appendChild(cellChequeNo);

		// Col 3: Account Number masked
		String accNo = row.cheque.getAccountNo();
		String masked = (accNo != null && accNo.length() >= 4) ? "****" + accNo.substring(accNo.length() - 4) : "****";
		item.appendChild(new Listcell(masked));

		// Col 4: Account Holder Name — loaded from DB .
		Label holderNameLabel = new Label(nullSafe(row.holderName));
		holderNameLabel.setSclass("cell-normal");
		Listcell holderCell = new Listcell();
		holderCell.appendChild(holderNameLabel);
		item.appendChild(holderCell);

		// Col 5: Status — "Processing..." if not yet validated
		Label statusLabel;
		if (row.result == null) {
			statusLabel = new Label("Processing...");
			statusLabel.setSclass("cbs-status-badge cbs-badge-processing");
		} else if (row.result.isValid()) {
			statusLabel = new Label("VALID");
			statusLabel.setSclass("cbs-status-badge cbs-badge-valid");
		} else {
			statusLabel = new Label("INVALID");
			statusLabel.setSclass("cbs-status-badge cbs-badge-invalid");
		}
		row.statusLabel = statusLabel; // store reference for live update
		Listcell statusCell = new Listcell();
		statusCell.appendChild(statusLabel);
		item.appendChild(statusCell);

		// Col 6: Reason
		Label reasonLabel = new Label(row.result != null ? nullSafe(row.result.getReason()) : "");
		row.reasonLabel = reasonLabel; // store reference for live update
		Listcell reasonCell = new Listcell();
		reasonLabel.setSclass("cbs-reason-label");
		reasonCell.appendChild(reasonLabel);
		item.appendChild(reasonCell);

		return item;
	}

	/** Returns the value if non-null, otherwise returns "-". */
	private String nullSafe(String value) {
		return value != null ? value : "-";
	}

	/**
	 * Holds all data for one cheque row in the CBS validation list. result starts
	 * as null ("Processing..."); statusLabel and reasonLabel are live ZK
	 *
	 */
	private static class CbsResultRow {
		final InwardCheque cheque;

		// Set after CBS validation completes for this cheque; null = still processing
		CbsValidationResult result = null;
		String holderName = "-";

		// Direct references to the rendered ZK label components
		// Set by buildRow(), used by updateRowLabels()
		Label statusLabel = null;
		Label reasonLabel = null;

		CbsResultRow(InwardCheque cheque) {
			this.cheque = cheque;

			this.holderName = (cheque.getPayeeName() != null && !cheque.getPayeeName().isBlank())
					? cheque.getPayeeName()
					: "-";
		}
	}
}