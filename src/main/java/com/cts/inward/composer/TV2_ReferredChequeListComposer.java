package com.cts.inward.composer;

/**
 * File    : TV2_ReferredChequeListComposer.java
 * Package : com.cts.inward.composer
 * Purpose : Handles the Verifier 2 (Branch Manager) "Referred Cheques" screen.
 *           This is a READ-ONLY tracking list — cheques TV2 has already sent
 *           back to the Maker for correction (cheque_status = Repair,
 *           send_to = TV_2, decision = REFERRED). These are NOT awaiting any
 *           TV2 action; they are sitting with the Maker until resubmitted.
 *           Same grid/search/batch-filter/paging pattern as
 *           TV2_ResubmittedQueueComposer, minus the Review action and the
 *           "Edited by Maker" filter (not relevant until the maker acts).
 * Author  : Ramana
 */

import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import org.zkoss.zk.ui.Component;
import org.zkoss.zk.ui.Executions;
import org.zkoss.zk.ui.event.Events;
import org.zkoss.zk.ui.select.SelectorComposer;
import org.zkoss.zk.ui.select.annotation.Listen;
import org.zkoss.zk.ui.select.annotation.Wire;
import org.zkoss.zul.Label;
import org.zkoss.zul.Listbox;
import org.zkoss.zul.Listitem;
import org.zkoss.zul.Paging;
import org.zkoss.zul.Row;
import org.zkoss.zul.Rows;
import org.zkoss.zul.Textbox;

import com.cts.inward.entity.InwardCheque;
import com.cts.inward.service.InwardChequeService;
import com.cts.inward.service.InwardChequeServiceImpl;

public class TV2_ReferredChequeListComposer extends SelectorComposer<Component> {

	private final InwardChequeService inwardChequeService = new InwardChequeServiceImpl();

	@Wire("#ref-count-badge")
	private Label countBadge;
	@Wire("#ref-rows")
	private Rows gridRows;
	@Wire("#ref-paging")
	private Paging paging;
	@Wire("#ref-search")
	private Textbox searchBox;
	@Wire("#ref-batch-filter")
	private Listbox batchFilter;
	@Wire("#ref-empty-row")
	private Row emptyRow;

	private List<InwardCheque> allCheques;

	// Holds whatever list is currently on screen (full or filtered) so
	// paging can re-slice it without hitting the DB again.
	private List<InwardCheque> currentList = new ArrayList<>();

	// Root component — needed as the parent when opening the cheque popup.
	private Component self;

	// ── Lifecycle ──────────────────────────────────────────────────────────

	@Override
	public void doAfterCompose(Component comp) throws Exception {
		super.doAfterCompose(comp);
		self = comp;
		loadData();
		
		org.zkoss.zk.ui.event.EventQueues
        .lookup("chequeStatusUpdated", org.zkoss.zk.ui.event.EventQueues.DESKTOP, true)
        .subscribe(event -> loadData());
	}

	// ── Load ───────────────────────────────────────────────────────────────

	/**
	 * Loads all cheques currently referred-to-maker by TV2, refreshes the badge,
	 * populates the batch dropdown, and renders the first page.
	 */
	private void loadData() {
		allCheques = inwardChequeService.getReferredChequesForTV2();
		updateBadgeCount(allCheques.size());
		populateBatchFilter(allCheques);
		renderTable(allCheques);
	}

	/**
	 * Fills the Batch Number dropdown with distinct batch IDs from the loaded list.
	 * "All Batches" is always the first option.
	 */
	private void populateBatchFilter(List<InwardCheque> cheques) {
		if (batchFilter == null)
			return;

		batchFilter.getItems().clear();

		Listitem allItem = new Listitem("All Batches");
		allItem.setValue("");
		allItem.setSelected(true);
		batchFilter.appendChild(allItem);

		cheques.stream().map(c -> (c.getBatch() != null) ? c.getBatch().getBatchId() : null)
				.filter(id -> id != null && !id.isBlank()).distinct().sorted(Comparator.naturalOrder())
				.forEach(batchId -> {
					Listitem item = new Listitem(batchId);
					item.setValue(batchId);
					batchFilter.appendChild(item);
				});
	}

	// ── Paging ─────────────────────────────────────────────────────────────

	@Listen("onPaging = #ref-paging")
	public void onPaging() {
		renderCurrentPage();
	}

	// ── Table Rendering ────────────────────────────────────────────────────

	/**
	 * Entry point for all render calls (initial load, filter, clear). Resets paging
	 * to page 1 and renders the first page of the given list.
	 */
	private void renderTable(List<InwardCheque> list) {
		currentList = (list != null) ? list : new ArrayList<>();

		paging.setActivePage(0);
		paging.setTotalSize(currentList.size());

		renderCurrentPage();
	}

	/**
	 * Paints only the rows belonging to the currently active page. emptyRow is
	 * never removed from the DOM — only its visibility is toggled.
	 */
	private void renderCurrentPage() {
		List<Component> toRemove = new ArrayList<>();
		for (Component child : gridRows.getChildren()) {
			if (child != emptyRow)
				toRemove.add(child);
		}
		for (Component child : toRemove) {
			gridRows.removeChild(child);
		}

		if (currentList.isEmpty()) {
			emptyRow.setVisible(true);
			return;
		}

		emptyRow.setVisible(false);

		int pageSize = paging.getPageSize();
		int activePg = paging.getActivePage();
		int start = activePg * pageSize;
		int end = Math.min(start + pageSize, currentList.size());

		int rowNum = start + 1;
		for (int i = start; i < end; i++) {
			gridRows.appendChild(buildTableRow(currentList.get(i), rowNum++));
		}
	}

	/**
	 * Builds one grid row for the given cheque. Columns: # | Cheque No. | Batch ID
	 * | Amount (₹) | Cheque Date | Status
	 */
	private Row buildTableRow(InwardCheque cheque, int rowNum) {
		Row row = new Row();

		// Col 1 — Row number
		Label numLabel = new Label(String.valueOf(rowNum));
		numLabel.setSclass("chk-col-num");
		row.appendChild(numLabel);

		// Col 2 — Cheque Number
		Label chqLabel = new Label(nullSafe(cheque.getChequeNo()));
		chqLabel.setSclass("chk-col-chqno");
		row.appendChild(chqLabel);

		// Col 3 — Batch ID
		String batchId = (cheque.getBatch() != null) ? cheque.getBatch().getBatchId() : "—";
		Label batchLabel = new Label(batchId);
		batchLabel.setSclass("chk-col-text");
		row.appendChild(batchLabel);

		// Col 4 — Amount (₹)
		String amountStr = cheque.getAmount() != null ? String.format("%,.2f", cheque.getAmount()) : "—";
		Label amountLabel = new Label(amountStr);
		amountLabel.setSclass("chk-col-amount v2-col-amount-hv");
		row.appendChild(amountLabel);

		// Col 5 — Cheque Date
        String dateStr = cheque.getChequeDate() != null
            ? cheque.getChequeDate().format(DateTimeFormatter.ofPattern("dd/MM/yyyy")) : "—";
        Label dateLabel = new Label(dateStr);
        dateLabel.setSclass("chk-col-text");
        row.appendChild(dateLabel);

        // Col 6 — Refer Reason (why the checker/BM sent it back to Maker)
        String referReason = nullSafe(cheque.getReferReason());
        if (referReason.isEmpty()) {
            referReason = "—";
        }
        Label reasonLabel = new Label(referReason);
        reasonLabel.setSclass("chk-col-remark");
        row.appendChild(reasonLabel);

        // Col 7 — Status badge
        String status = cheque.getChequeStatus() != null ? cheque.getChequeStatus().name() : "—";
        Label statusBadge = new Label(status);
        statusBadge.setSclass("chk-badge-tracking");
        row.appendChild(statusBadge);

		// Click row to view cheque details (read-only) — same popup the old
		// listbox-based version opened on row select.
		row.setSclass(row.getSclass() == null ? "rcb-row-clickable" : row.getSclass() + " rcb-row-clickable");
		row.addEventListener(Events.ON_CLICK, event -> openChequePopup(cheque));

		return row;
	}

	// ── Popup ──────────────────────────────────────────────────────────────

	/**
	 * Opens the read-only cheque detail popup for the clicked row. Re-attaches
	 * fresh each time — detaches any stale instance first so repeated clicks don't
	 * stack multiple windows.
	 */
	private void openChequePopup(InwardCheque cheque) {
		try {
			Component existing = self.getFellowIfAny("chequeEditPopupWindow");
			if (existing != null) {
				existing.detach();
			}

			Map<String, Object> args = new HashMap<>();
			args.put("selectedCheque", cheque);
			// FIX: pass role explicitly so ChequeEditPopupComposer enters the TV2
			// case in updateButtonState() — desktop attribute alone is unreliable
			// here because this page shares a session with TV1.
			args.put("currentRole", "TV2");
			// FIX: tell the popup this is the Referred tab (tracking only) so it
			// hides the SendBack button — cheque is already sitting with Maker.
			args.put("popupSource", "REFERRED_TAB");
			Executions.createComponents("/component/chequeEditPopup.zul", self, args);
		} catch (Exception e) {
			e.printStackTrace();
		}
	}

	// ── Event Listeners ────────────────────────────────────────────────────

	@Listen("onClick = #ref-btn-refresh")
	public void onRefresh() {
		searchBox.setValue("");
		loadData();
	}

	@Listen("onClick = #ref-btn-search")
	public void onSearchBtn() {
		applyFilters();
	}

	// Triggers search on Enter key inside the search textbox
	@Listen("onOK = #ref-search")
	public void onSearchEnter() {
		applyFilters();
	}

	@Listen("onSelect = #ref-batch-filter")
	public void onBatchFilterChange() {
		applyFilters();
	}

	@Listen("onClick = #ref-btn-clear-filter")
	public void onClearFilter() {
		searchBox.setValue("");
		if (batchFilter != null)
			batchFilter.setSelectedIndex(0);
		renderTable(allCheques);
	}

	// ── Filter Logic ───────────────────────────────────────────────────────

	/**
	 * Filters allCheques by search text and selected batch, then re-renders the
	 * table with matching results.
	 */
	private void applyFilters() {
		String searchText = searchBox.getValue() != null ? searchBox.getValue().trim().toLowerCase() : "";
		String batchVal = getSelectedValue(batchFilter);

		List<InwardCheque> filtered = allCheques.stream().filter(c -> {
			if (!searchText.isEmpty()) {
				boolean matchChq = c.getChequeNo() != null && c.getChequeNo().toLowerCase().contains(searchText);
				String bId = (c.getBatch() != null) ? c.getBatch().getBatchId() : "";
				boolean matchBatch = bId.toLowerCase().contains(searchText);
				if (!matchChq && !matchBatch)
					return false;
			}

			if (!batchVal.isEmpty()) {
				String bId = (c.getBatch() != null) ? c.getBatch().getBatchId() : "";
				if (!batchVal.equals(bId))
					return false;
			}
			return true;
		}).collect(Collectors.toList());

		renderTable(filtered);
	}

	// ── Helpers ────────────────────────────────────────────────────────────

	private void updateBadgeCount(int count) {
		String label = count + " Cheque" + (count != 1 ? "s" : "") + " Referred";
		if (countBadge != null)
			countBadge.setValue(label);
	}

	/**
	 * Reads the selected value from a select-mold Listbox. Returns "" (never null)
	 * so callers can safely call .isEmpty() or .equals().
	 */
	private String getSelectedValue(Listbox lb) {
		if (lb == null)
			return "";
		Listitem selected = lb.getSelectedItem();
		if (selected == null)
			return "";
		Object val = selected.getValue();
		return val != null ? val.toString() : "";
	}

	private String nullSafe(String value) {
		return (value != null) ? value : "—";
	}
}