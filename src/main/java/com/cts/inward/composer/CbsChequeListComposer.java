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

/**
 * CbsChequeListComposer ───────────────────── TWO-PHASE approach:
 *
 * PHASE 1 — Load all cheques immediately on page open. All cheque rows appear
 * at once with status = "Processing..." User can see all cheques right away.
 *
 * PHASE 2 — Timer processes CBS validation one cheque at a time. Every 700ms
 * the timer picks the next cheque, calls Firebase CBS, gets the result, then
 * directly updates only that row's status label and reason label in the listbox
 * — no full re-render. User sees each cheque's status change from
 * "Processing..." → "VALID" or "INVALID" one by one.
 *
 * WHY direct label update instead of full re-render: Re-rendering the whole
 * listbox on every tick would reset scroll position and flicker the screen.
 * Since each CbsResultRow stores a direct reference to its statusLabel and
 * reasonLabel components, we can update just those two labels — smooth and
 * efficient.
 */
public class CbsChequeListComposer extends SelectorComposer<Component> {

	private static final long serialVersionUID = 1L;
	private static final int PAGE_SIZE = 8;

	// ── Services ──────────────────────────────────────────────────────────
	private final InwardChequeMICRService chequeService = new InwardChequeServiceMICRImpl();
	private final CbsValidationService cbsService = new CbsValidationServiceImpl();

	// ── Wired UI components ───────────────────────────────────────────────
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

	// ── In-memory lists ───────────────────────────────────────────────────
	// allResults : all rows (all cheques, including "Processing..." ones)
	// filteredResults : subset after filter + search applied (used for display)
	private List<CbsResultRow> allResults = new ArrayList<>();
	private List<CbsResultRow> filteredResults = new ArrayList<>();

	// ── State ─────────────────────────────────────────────────────────────
	private Long currentBatchId = null;
	private int processingIndex = 0; // which cheque in allResults to process next

	// ── Lifecycle ─────────────────────────────────────────────────────────

	@Override
	public void doAfterCompose(Component comp) throws Exception {
		super.doAfterCompose(comp);

		pgCbsChequeList.setPageSize(PAGE_SIZE);
		cbsProcessTimer.stop();

		// Subscribe to batchContext queue
		// CbsValidationComposer publishes batchDbId here after page is rendered
		EventQueues.lookup("batchContext", EventQueues.DESKTOP, true).subscribe((Event event) -> {
			Object data = event.getData();
			if (data instanceof Long) {
				currentBatchId = (Long) data;
				loadAllChequesAndStartProcessing();
			}
		});

		// Subscribe to removeInvalidCheques
		// Fired by CbsValidationComposer after saving REJECTED decisions to DB.
		// NOTE: EventQueues.DESKTOP scope subscribers are executed on the ZK
		// event thread — they already have a valid execution context.
		// Do NOT use Executions.activate/deactivate here; it causes a deadlock
		// by trying to acquire the desktop lock that is already held.
		EventQueues.lookup("removeInvalidCheques", EventQueues.DESKTOP, true).subscribe((Event event) -> {
			removeInvalidRowsFromList();
		});
	}

	// ══════════════════════════════════════════════════════════════════════
	// PHASE 1 — Load all cheques at once, show "Processing..." status
	// ══════════════════════════════════════════════════════════════════════

	/**
	 * Loads all cheques for the batch from DB. Creates a CbsResultRow for each with
	 * result=null (Processing...). Renders ALL rows immediately into the listbox.
	 * Then starts the timer for Phase 2.
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

	// ══════════════════════════════════════════════════════════════════════
	// PHASE 2 — Timer: validate one cheque per tick, update its row live
	// ══════════════════════════════════════════════════════════════════════

	/**
	 * Fires every 700ms (set in ZUL: delay="700").
	 *
	 * Picks the next unprocessed row from allResults. Calls Firebase CBS → gets
	 * validation result. Saves to DB. Then directly updates that row's statusLabel
	 * and reasonLabel in the listbox — no full re-render needed.
	 *
	 * WHY direct label update: Each CbsResultRow stores a reference to its own
	 * statusLabel and reasonLabel components. We update those labels directly. ZK
	 * sends only those 2 component changes to the browser — smooth, no flicker, no
	 * scroll reset.
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

		// ── Fetch from Firebase CBS ───────────────────────────────────
		CbsAccountData cbsData = null;
		String fetchErr = null;
		try {
			cbsData = cbsService.fetchAccountData(cheque.getAccountNo());
		} catch (Exception e) {
			fetchErr = "CBS Fetch Error";
			System.err.println(
					"CbsChequeListComposer: CBS fetch failed for " + cheque.getChequeNo() + ": " + e.getMessage());
		}

		// ── Validate ──────────────────────────────────────────────────
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

		// ── Persist to DB ─────────────────────────────────────────────
		// Use a targeted native-SQL update by ID instead of
		// chequeService.updateCheque() (session.merge() on this detached
		// entity fails silently because of the lazy @ManyToOne batch
		// association, so cbsValidation was never actually saved).
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
		// holderName is already set from DB in Phase 1 — no update needed.
		row.result = result;

		// ── Directly update this row's status + reason labels ─────────
		// Only works if the row is currently visible in the listbox.
		// If the row is on a different page, it will show correctly
		// when the user navigates to that page (renderPage reads row.result).
		updateRowLabels(row);

		// Notify batch summary to refresh counts
		EventQueues.lookup("chequeStatusUpdated", EventQueues.DESKTOP, true)
				.publish(new Event("onChequeStatusUpdated", null, null));
	}

	/**
	 * Directly updates the statusLabel and reasonLabel of one row.
	 *
	 * If the row is currently visible (statusLabel is not null and is attached to a
	 * live page), the update is instant. If the row is on a different page (not
	 * rendered), we skip — renderPage() will pick up row.result when the user
	 * navigates there.
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
			// Row may have been detached from DOM (e.g. page changed) — safe to ignore
			System.err.println("CbsChequeListComposer: label update skipped for " + row.cheque.getChequeNo() + ": "
					+ e.getMessage());
		}
	}

	// ── Validation complete ───────────────────────────────────────────────

	private void onValidationComplete() {
		List<CbsValidationResult> results = allResults.stream().map(row -> row.result).filter(result -> result != null)
				.collect(Collectors.toList());

		long[] counts = cbsService.countValidAndInvalid(results);

		System.out.println("CbsChequeListComposer: all done. valid=" + counts[0] + " invalid=" + counts[1]);

		EventQueues.lookup("cbsValidationComplete", EventQueues.DESKTOP, true)
				.publish(new Event("onCbsValidationComplete", null, counts));
	}

	// ── Remove INVALID rows after Return to RRF ───────────────────────────

	private void removeInvalidRowsFromList() {
		allResults = allResults.stream().filter(row -> row.result != null && row.result.isValid())
				.collect(Collectors.toList());
		System.out.println("CbsChequeListComposer: invalid rows removed. remaining=" + allResults.size());
		applyFilterAndRefresh();
	}

	// ── Filter & Search ───────────────────────────────────────────────────

	@Listen("onChange = #tbCbsSearch")
	public void onSearch() {
		applyFilterAndRefresh();
	}

	@Listen("onSelect = #lbCbsStatusFilter")
	public void onFilterChange() {
		applyFilterAndRefresh();
	}

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

	private String getSelectedStatus() {
		if (lbCbsStatusFilter == null)
			return "ALL";
		Listitem selected = lbCbsStatusFilter.getSelectedItem();
		return (selected != null) ? selected.getValue().toString() : "ALL";
	}

	private boolean matchesStatus(CbsResultRow row, String status) {
		if ("ALL".equals(status))
			return true;
		// While still Processing, only show in ALL filter
		if (row.result == null)
			return "ALL".equals(status);
		if ("VALID".equals(status))
			return row.result.isValid();
		if ("INVALID".equals(status))
			return !row.result.isValid();
		return true;
	}

	private boolean matchesKeyword(CbsResultRow row, String keyword) {
		if (keyword.isEmpty())
			return true;
		return contains(row.cheque.getChequeNo(), keyword) || contains(row.holderName, keyword);
	}

	private boolean contains(String field, String keyword) {
		return field != null && field.toLowerCase().contains(keyword);
	}

	// ── Pagination ────────────────────────────────────────────────────────

	@Listen("onPaging = #pgCbsChequeList")
	public void onPageChange(PagingEvent event) {
		renderPage(event.getActivePage());
	}

	/**
	 * Renders one page slice of filteredResults into the listbox. Clears and
	 * rebuilds only the visible page. Each row's Label references (statusLabel,
	 * reasonLabel) are set here so Phase 2 timer can update them directly.
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

	// ── Row Builder ───────────────────────────────────────────────────────

	/**
	 * Builds one listitem for a CbsResultRow.
	 *
	 * Stores references to statusLabel and reasonLabel directly in the row. Phase 2
	 * timer uses these to update only those two labels live. holderName is already
	 * set from DB in Phase 1 — shown immediately.
	 */
	private Listitem buildRow(CbsResultRow row, int rowNum) {
		Listitem item = new Listitem();

		// Col 1: Row number (matches MICR page "#" column)
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

		// Col 4: Account Holder Name — loaded from DB in Phase 1, shown immediately
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
		row.statusLabel = statusLabel; // store reference
		Listcell statusCell = new Listcell();
		statusCell.appendChild(statusLabel);
		item.appendChild(statusCell);

		// Col 6: Reason
		Label reasonLabel = new Label(row.result != null ? nullSafe(row.result.getReason()) : "");
		row.reasonLabel = reasonLabel; // store reference
		Listcell reasonCell = new Listcell();
		reasonLabel.setSclass("cbs-reason-label");
		reasonCell.appendChild(reasonLabel);
		item.appendChild(reasonCell);

		return item;
	}

	// ── Helpers ───────────────────────────────────────────────────────────



	private String nullSafe(String value) {
		return value != null ? value : "-";
	}

	// ── Inner class: one row in the CBS validation list ───────────────────

	/**
	 * Holds all data for one cheque row.
	 *
	 * result starts as null = "Processing..." After CBS validation, result is set
	 * with the actual outcome.
	 *
	 * statusLabel and reasonLabel are references to the actual ZK Label components
	 * rendered in the listbox for this row. Phase 2 timer uses these to update the
	 * row live without re-rendering.
	 */
	private static class CbsResultRow {
		final InwardCheque cheque;

		// These are set after CBS validation completes for this cheque
		CbsValidationResult result = null; // null = still processing
		String holderName = "-";

		// Direct references to the rendered ZK label components
		// Set by buildRow(), used by updateRowLabels()
		Label statusLabel = null;
		Label reasonLabel = null;

		CbsResultRow(InwardCheque cheque) {
			this.cheque = cheque;
			// Load holder name immediately from DB — no CBS call needed.
			// payeeName is already stored in the inward_cheque table.
			this.holderName = (cheque.getPayeeName() != null && !cheque.getPayeeName().isBlank())
					? cheque.getPayeeName()
					: "-";
		}
	}
}