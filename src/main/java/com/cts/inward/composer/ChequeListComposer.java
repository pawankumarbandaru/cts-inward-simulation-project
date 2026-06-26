package com.cts.inward.composer;

import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

import org.zkoss.zk.ui.Component;
import org.zkoss.zk.ui.Executions;
import org.zkoss.zk.ui.event.Event;
import org.zkoss.zk.ui.event.EventQueues;
import org.zkoss.zk.ui.select.SelectorComposer;
import org.zkoss.zk.ui.select.annotation.Listen;
import org.zkoss.zk.ui.select.annotation.Wire;
import org.zkoss.zul.Datebox;
import org.zkoss.zul.Label;
import org.zkoss.zul.Listbox;
import org.zkoss.zul.Listcell;
import org.zkoss.zul.Listitem;
import org.zkoss.zul.Paging;
import org.zkoss.zul.Textbox;
import org.zkoss.zul.event.PagingEvent;

import com.cts.inward.entity.InwardCheque;
import com.cts.inward.enums.ChequeStatus;
import com.cts.inward.service.InwardChequeMICRService;
import com.cts.inward.service.InwardChequeServiceMICRImpl;

public class ChequeListComposer extends SelectorComposer<Component> {

	private final InwardChequeMICRService inwardChequeService = new InwardChequeServiceMICRImpl();

	@Wire
	Listbox lbChequeList;
	@Wire
	Listbox lbStatusFilter;
	@Wire
	Textbox tbSearch;
	@Wire
	Paging pgChequeList;
	@Wire
	Datebox dpChequeDate;

	private Long currentBatchId = null;
	private List<InwardCheque> allCheques = new ArrayList<>();
	private List<InwardCheque> filteredCheques = new ArrayList<>();

	private static final int PAGE_SIZE = 8;
	private String currentRole = "MAKER";

	private static final DateTimeFormatter CHEQUE_DATE_FORMAT = DateTimeFormatter.ofPattern("dd-MM-yyyy");

	/**
	 * Reads batchDbId from the macro wrapper attribute and role from Desktop, loads
	 * cheques immediately if batchId is available, then subscribes to batchContext
	 * and chequeStatusUpdated queues . Related to: MICR page — renders the
	 * paginated cheque table below the summary card.
	 */
	@Override
	public void doAfterCompose(Component comp) throws Exception {
		super.doAfterCompose(comp);

		pgChequeList.setPageSize(PAGE_SIZE);

		Object attr = comp.getSpaceOwner().getAttribute("batchDbId");

		if (attr instanceof Long) {
			currentBatchId = (Long) attr;
		} else if (attr instanceof String) {
			try {
				currentBatchId = Long.parseLong((String) attr);
			} catch (NumberFormatException e) {
				currentBatchId = null;
			}
		}

		Object roleAttr = Executions.getCurrent().getDesktop().getAttribute("userRole");

		if (roleAttr instanceof String && !((String) roleAttr).isEmpty()) {
			currentRole = ((String) roleAttr).toUpperCase().trim();
		}

		System.out.println(
				"ChequeListComposer: Desktop=" + Executions.getCurrent().getDesktop().getId() + " role=" + currentRole);

		System.out.println("ChequeListComposer: batchDbId from attribute = " + currentBatchId);

		if (currentBatchId != null && currentBatchId > 0) {
			loadCheques();
		}

		EventQueues.lookup("batchContext", EventQueues.DESKTOP, true).subscribe((Event event) -> {
			if (event.getData() instanceof Long) {
				Long incoming = (Long) event.getData();
				if (incoming != null && incoming > 0) {
					currentBatchId = incoming;
					loadCheques();
				}
			}
		});

		EventQueues.lookup("chequeStatusUpdated", EventQueues.DESKTOP, true).subscribe((Event event) -> loadCheques());
	}

	/**
	 * Fetches cheques from DB filtered by role (MAKER = all, TV1 = sendTo TV_1, TV2
	 * = sendTo TV_2), resets filteredCheques, and re-renders the first page.
	 * Related to: MICR page — populates the main cheque table for the current batch
	 * and role.
	 */
	private void loadCheques() {
		if (currentBatchId == null || currentBatchId <= 0)
			return;

		System.out.println("ChequeListComposer: loading for role=" + currentRole);

		switch (currentRole) {
		case "TV1":
			allCheques = inwardChequeService.TV1_ChequesList(currentBatchId);
			break;
		case "TV2":
			allCheques = inwardChequeService.TV2_ChequesList(currentBatchId);
			break;
		default:
			allCheques = inwardChequeService.getChequesByBatchId(currentBatchId);
			break;
		}

		filteredCheques = new ArrayList<>(allCheques);
		pgChequeList.setTotalSize(filteredCheques.size());
		pgChequeList.setActivePage(0);
		renderPage(0);
	}

	/**
	 * Slices filteredCheques to the requested page window and rebuilds the listbox
	 * rows; clears existing items before appending new ones. Returns: void — side
	 * effect is updated listbox UI for the given page index.
	 */
	private void renderPage(int pageIndex) {
		int fromIndex = pageIndex * PAGE_SIZE;
		int toIndex = Math.min(fromIndex + PAGE_SIZE, filteredCheques.size());

		List<InwardCheque> pageData = filteredCheques.subList(fromIndex, toIndex);

		lbChequeList.getItems().clear();

		int rowNum = fromIndex + 1;
		for (InwardCheque cheque : pageData) {

			lbChequeList.appendChild(buildRow(cheque, rowNum++));
		}
	}

	/**
	 * Constructs a single Listitem for one cheque with cells for row number, cheque
	 * number (as a clickable link style), masked account, amount, and MICR status
	 * badge. Returns: Listitem — a fully built row ready to be appended to the
	 * listbox.
	 */
	private Listitem buildRow(InwardCheque cheque, int rowNum) {
		Listitem item = new Listitem();

		if (inwardChequeService.isMakerEditedAndPendingReview(cheque)) {
			item.setSclass("row-maker-edited");
		}

		item.setValue(cheque);

		item.appendChild(new Listcell(String.valueOf(rowNum)));

		Listcell cellChequeNo = new Listcell();
		Label lblChequeNo = new Label(nullSafe(cheque.getChequeNo()));
		lblChequeNo.setSclass("cheque-no-link");
		cellChequeNo.appendChild(lblChequeNo);
		item.appendChild(cellChequeNo);

		item.appendChild(new Listcell(inwardChequeService.formatChequeDate(cheque.getChequeDate())));

		String accNo = cheque.getAccountNo();
		String masked = (accNo != null && accNo.length() >= 4) ? "****" + accNo.substring(accNo.length() - 4) : "****";
		item.appendChild(new Listcell(masked));

		Listcell cellAmount = new Listcell();
		Label lblAmount = new Label(cheque.getAmount() != null ? "Rs. " + cheque.getAmount().toPlainString() : "-");
		lblAmount.setSclass("amount-label");
		cellAmount.appendChild(lblAmount);
		item.appendChild(cellAmount);

		Listcell cellStatus = new Listcell();
		String statusText = cheque.getChequeStatus() != null ? cheque.getChequeStatus().name() : "-";
		Label lblStatus = new Label(statusText);
		lblStatus.setSclass(inwardChequeService.isMicrError(cheque) ? "badge-micr-error" : "badge-normal");
		cellStatus.appendChild(lblStatus);
		item.appendChild(cellStatus);

		return item;
	}

	/** Triggers applyFilter() when the date picker value changes. */
	@Listen("onChange = #dpChequeDate")
	public void onDateFilterChange() {
		applyFilter();
	}

	/** Triggers applyFilter() when the search textbox value changes. */
	@Listen("onChange = #tbSearch")
	public void onSearch() {
		applyFilter();
	}

	/** Triggers applyFilter() when a status filter option is selected. */
	@Listen("onSelect = #lbStatusFilter")
	public void onFilterChange() {
		applyFilter();
	}

	/**
	 * Combines keyword, status, and date filters against allCheques using stream
	 * predicates, updates filteredCheques, resets pagination, and re-renders page
	 * 0. Related to: MICR page — drives the search/filter toolbar above the cheque
	 * table.
	 */
	private void applyFilter() {
		String keyword = tbSearch.getValue().trim().toLowerCase();

		Listitem selectedFilter = lbStatusFilter.getSelectedItem();
		String status = (selectedFilter != null) ? selectedFilter.getValue().toString() : "ALL";

		java.time.LocalDate selectedDate = dpChequeDate.getValue() != null
				? dpChequeDate.getValue().toInstant().atZone(java.time.ZoneId.systemDefault()).toLocalDate()
				: null;

		filteredCheques = allCheques.stream().filter(
				c -> matchesKeyword(c, keyword) && matchesStatus(c, status) && matchesDateFilter(c, selectedDate))
				.collect(Collectors.toList());

		pgChequeList.setTotalSize(filteredCheques.size());
		pgChequeList.setActivePage(0);
		renderPage(0);
	}

	/**
	 * Returns true if the cheque matches the keyword against chequeNo, accountNo,
	 * transactionCode, amount, or formatted cheque date; returns true if keyword is
	 * empty.
	 */
	private boolean matchesKeyword(InwardCheque c, String keyword) {
		if (keyword.isEmpty())
			return true;
		return contains(c.getChequeNo(), keyword) || contains(c.getAccountNo(), keyword)
				|| contains(c.getTransactionCode(), keyword) || matchesAmount(c, keyword)
				|| matchesChequeDate(c, keyword);
	}

	/** Returns true if the cheque's plain-string amount contains the keyword. */
	private boolean matchesAmount(InwardCheque c, String keyword) {
		if (c.getAmount() == null)
			return false;
		return c.getAmount().toPlainString().contains(keyword);
	}

	/**
	 * Matches the search keyword against the formatted cheque date (dd-MM-yyyy).
	 * Reuses formatChequeDate() so the search always matches what's shown on
	 * screen.
	 */
	private boolean matchesChequeDate(InwardCheque c, String keyword) {
		return inwardChequeService.formatChequeDate(c.getChequeDate()).contains(keyword);
	}

	/**
	 * Returns true if the cheque date matches the selected date picker value;
	 * returns true when no date is selected (no date filter active).
	 */
	private boolean matchesDateFilter(InwardCheque c, java.time.LocalDate selectedDate) {
		if (selectedDate == null)
			return true;
		if (c.getChequeDate() == null)
			return false;
		return c.getChequeDate().toLocalDate().equals(selectedDate);
	}

	/**
	 * Returns true if the cheque's status enum name matches the selected filter
	 * value; always returns true for "ALL"; uses enum name directly to avoid
	 * case-mismatch bugs.
	 */
	private boolean matchesStatus(InwardCheque c, String status) {
		if ("ALL".equals(status))
			return true;
		if (c.getChequeStatus() == null)
			return false;
		return status.equals(c.getChequeStatus().name());
	}

	/**
	 * Renders the page corresponding to the pagination event's active page index.
	 */
	@Listen("onPaging = #pgChequeList")
	public void onPageChange(PagingEvent event) {
		renderPage(event.getActivePage());
	}

	/**
	 * Publishes the selected InwardCheque entity to the "chequeSelected" desktop
	 * EventQueue so BatchDetailComposer can open the correct edit popup. Related
	 * to: MICR page — triggered when user clicks a cheque row in the table.
	 */
	@Listen("onSelect = #lbChequeList")
	public void onChequeSelect() {
		if (lbChequeList.getSelectedItem() == null)
			return;

		InwardCheque selected = (InwardCheque) lbChequeList.getSelectedItem().getValue();

		EventQueues.lookup("chequeSelected", EventQueues.DESKTOP, true)
				.publish(new Event("onChequeSelected", null, selected));
	}

	/**
	 * Returns true if the field is non-null and contains the keyword
	 * (case-insensitive).
	 */
	private boolean contains(String field, String keyword) {
		return field != null && field.toLowerCase().contains(keyword);
	}

	/** Returns the value if non-null, otherwise returns "-". */
	private String nullSafe(String value) {
		return (value != null) ? value : "-";
	}

}