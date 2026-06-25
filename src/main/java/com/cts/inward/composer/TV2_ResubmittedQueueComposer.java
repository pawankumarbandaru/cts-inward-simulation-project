package com.cts.inward.composer;

/**
 * File    : TV2_ResubmittedQueueComposer.java
 * Package : com.cts.inward.composer
 * Purpose : Handles the Verifier 2 (Branch Manager) "Resubmitted Queue" screen for
 *           high-value cheques (amount >= Rs.5,00,000). Loads cheques resubmitted by
 *           the Maker after correction, renders the grid, applies search/batch/edited
 *           filters, handles paging, and opens the Review popup.
 * Author  : Ramana
 * Date    : 24-06-2025
 */

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
import org.zkoss.zul.Button;
import org.zkoss.zul.Div;
import org.zkoss.zul.Label;
import org.zkoss.zul.Listbox;
import org.zkoss.zul.Listitem;
import org.zkoss.zul.Paging;
import org.zkoss.zul.Row;
import org.zkoss.zul.Rows;
import org.zkoss.zul.Textbox;
import org.zkoss.zul.Window;

import com.cts.inward.entity.InwardCheque;
import com.cts.inward.service.TV2_ResubmittedChequeService;
import com.cts.inward.service.TV2_ResubmittedChequeServiceImpl;
import com.cts.util.CtsUiBridge;

public class TV2_ResubmittedQueueComposer extends SelectorComposer<Component> {

    private final TV2_ResubmittedChequeService v2Service = new TV2_ResubmittedChequeServiceImpl();

    @Wire("#v2-count-badge")      private Label   countBadge;
    @Wire("#v2-alert-banner")     private Div     alertBanner;
    @Wire("#v2-rows")             private Rows    gridRows;
    @Wire("#v2-paging")           private Paging  paging;
    @Wire("#v2-search")           private Textbox searchBox;
    @Wire("#v2-edited-filter")    private Listbox editedFilter;
    @Wire("#v2-batch-filter")     private Listbox batchFilter;
    @Wire("#sb-v2-count")         private Label   statusBarCount;
    @Wire("#v2-empty-row")        private Row     emptyRow;
    @Wire("#v2-success-banner")   private Div     successBanner;

    // successText is a native <n:div> in the ZUL — content is set via JS bridge
    private static final String SUCCESS_TEXT_ID = "v2-success-text";

    private List<InwardCheque> allCheques;

    // Holds whatever list is currently on screen (full or filtered).
    // Needed so paging can re-slice the same list without hitting the DB again.
    private List<InwardCheque> currentList = new ArrayList<>();

    // ── Lifecycle ──────────────────────────────────────────────────────────

    @Override
    public void doAfterCompose(Component comp) throws Exception {
        super.doAfterCompose(comp);
        loadData();
    }

    // ── Load ───────────────────────────────────────────────────────────────

    /**
     * Loads all resubmitted cheques for Verifier 2 from DB, refreshes badge,
     * populates batch dropdown, and renders the first page.
     */
    private void loadData() {
        allCheques = v2Service.getResubmittedChequesForVerifier2();
        updateBadgeCount(allCheques.size());
        populateBatchFilter(allCheques);
        renderTable(allCheques);
        showSuccessBannerIfAny();
    }

    /**
     * Fills the Batch Number dropdown with distinct batch IDs from the loaded list.
     * "All Batches" is always the first option.
     */
    private void populateBatchFilter(List<InwardCheque> cheques) {
        if (batchFilter == null) return;

        batchFilter.getItems().clear();

        Listitem allItem = new Listitem("All Batches");
        allItem.setValue("");
        allItem.setSelected(true);
        batchFilter.appendChild(allItem);

        cheques.stream()
            .map(c -> (c.getBatch() != null) ? c.getBatch().getBatchId() : null)
            .filter(id -> id != null && !id.isBlank())
            .distinct()
            .sorted(Comparator.naturalOrder())
            .forEach(batchId -> {
                Listitem item = new Listitem(batchId);
                item.setValue(batchId);
                batchFilter.appendChild(item);
            });
    }

    /**
     * Checks the session for a success message set by TV2_ReviewWorkspaceComposer
     * after approve/reject/return. Shows the banner if a message is present.
     */
    private void showSuccessBannerIfAny() {
        if (successBanner == null) return;

        Object msg = Executions.getCurrent()
            .getSession().getAttribute("v2SuccessMessage");

        if (msg != null) {
            Executions.getCurrent().getSession().removeAttribute("v2SuccessMessage");

            // Plain <n:div> + JS bridge avoids "Failed to mount: Unknown stub" —
            // happened earlier when this ran in the same response as the grid rebuild.
            CtsUiBridge.setBannerText(SUCCESS_TEXT_ID, msg.toString());
            successBanner.setVisible(true);
        } else {
            successBanner.setVisible(false);
        }
    }

    // ── Paging ─────────────────────────────────────────────────────────────

    /**
     * Fired when the user clicks a page button (prev / next / number).
     * Re-slices currentList to the new page without hitting the DB.
     */
    @Listen("onPaging = #v2-paging")
    public void onPaging() {
        renderCurrentPage();
    }

    // ── Table Rendering ────────────────────────────────────────────────────

    /**
     * Entry point for all render calls (initial load, filter, clear).
     * Resets paging to page 1 and renders the first page of the given list.
     */
    private void renderTable(List<InwardCheque> list) {
        currentList = (list != null) ? list : new ArrayList<>();

        paging.setActivePage(0);
        paging.setTotalSize(currentList.size());
        alertBanner.setVisible(!currentList.isEmpty());

        renderCurrentPage();
    }

    /**
     * Paints only the rows belonging to the currently active page.
     * Row numbers continue correctly across pages (e.g., page 2 starts at 11 for pageSize=10).
     * emptyRow is never removed from the DOM — only its visibility is toggled, since
     * detaching and re-appending it orphans its client stub ("Failed to mount" error).
     */
    private void renderCurrentPage() {
        List<Component> toRemove = new ArrayList<>();
        for (Component child : gridRows.getChildren()) {
            if (child != emptyRow) toRemove.add(child);
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
        int start    = activePg * pageSize;
        int end      = Math.min(start + pageSize, currentList.size());

        int rowNum = start + 1;
        for (int i = start; i < end; i++) {
            gridRows.appendChild(buildTableRow(currentList.get(i), rowNum++));
        }
    }

    /**
     * Builds one grid row for the given cheque.
     * Columns: # | Cheque Number | Batch ID | Amount (₹) | Checker Remark | Edited | Action
     */
    private Row buildTableRow(InwardCheque cheque, int rowNum) {
        Row row = new Row();

        if (cheque.isEditedByMaker()) {
            row.setSclass("maker-edited");
        }

        final String chequeNo = cheque.getChequeNo();

        // Col 1 — Row number
        Label numLabel = new Label(String.valueOf(rowNum));
        numLabel.setSclass("chk-col-num");
        row.appendChild(numLabel);

        // Col 2 — Cheque Number
        Label chqLabel = new Label(cheque.getChequeNo());
        chqLabel.setSclass("chk-col-chqno");
        row.appendChild(chqLabel);

        // Col 3 — Batch ID
        String batchId = (cheque.getBatch() != null) ? cheque.getBatch().getBatchId() : "—";
        Label batchLabel = new Label(batchId);
        batchLabel.setSclass("chk-col-text");
        row.appendChild(batchLabel);

        // Col 4 — Amount (₹)
        String amountStr = cheque.getAmount() != null
            ? "₹ " + String.format("%,.2f", cheque.getAmount()) : "—";
        Label amountLabel = new Label(amountStr);
        amountLabel.setSclass("chk-col-amount v2-col-amount-hv");
        row.appendChild(amountLabel);

        // Col 5 — Checker Remark (truncated; full text shown in the review popup)
        String remark = cheque.getSendbackReason() != null
            ? cheque.getSendbackReason() : "—";
        if (remark.length() > 80) remark = remark.substring(0, 80) + "...";
        Label remarkLabel = new Label(remark);
        remarkLabel.setSclass("chk-col-remark");
        row.appendChild(remarkLabel);

        // Col 6 — Edited badge (tooltip lists which fields the maker changed)
        if (cheque.isEditedByMaker()) {
            String fields = cheque.getEditedFields() != null
                ? cheque.getEditedFields() : "fields updated";
            Label editedBadge = new Label("Edited");
            editedBadge.setSclass("chk-badge-edited");
            editedBadge.setTooltiptext("Changed: " + fields);
            row.appendChild(editedBadge);
        } else {
            Label dash = new Label("—");
            dash.setSclass("chk-col-meta");
            row.appendChild(dash);
        }

        // Col 7 — Action button
        Button btn = new Button("Review");
        btn.setSclass("v2-btn-review");
        btn.addEventListener(Events.ON_CLICK, event -> {
            event.stopPropagation();
            openReviewModal(chequeNo);
        });
        row.appendChild(btn);

        return row;
    }

    // ── Popup ──────────────────────────────────────────────────────────────

    /**
     * Opens the Verifier 2 Review Workspace as an overlapped popup Window.
     * Passes this composer and the Window in args so the workspace can
     * call back onModalClosed() when the verifier is done.
     * Window size (880px × 95vh) matches the Maker Repair Workspace.
     */
    private void openReviewModal(String chequeNo) {

        Executions.getCurrent().getSession()
            .setAttribute("v2SelectedChequeNo", chequeNo);

        CtsUiBridge.showBackdrop("v2-modal-backdrop");

        Window win = new Window();
        win.setTitle("");
        win.setBorder("none");
        win.setSizable(false);
        win.setClosable(false);
        win.setDraggable("false");
        win.setSclass("v2-review-modal-win cts-modal-window");
        win.setWidth("880px");
        win.setHeight("95vh");
        win.setStyle(
            "position:fixed;" +
            "top:50%;left:50%;" +
            "transform:translate(-50%,-50%);" +
            "z-index:1000;"
        );

        win.setPage(getSelf().getPage());

        Map<String, Object> args = new HashMap<>();
        args.put("queueComposer", this);
        args.put("modalWindow",   win);

        Executions.createComponents(
            "/zul/inward/TV2_ReviewWorkspace.zul",
            win,
            args
        );
    }

    /**
     * Callback called by TV2_ReviewWorkspaceComposer after any action (approve / reject / return / close).
     * Sets the success message in session first, then reloads the queue — because
     * loadData() calls showSuccessBannerIfAny() internally.
     */
    public void onModalClosed(String successMessage) {
        if (successMessage != null) {
            Executions.getCurrent().getSession()
                .setAttribute("v2SuccessMessage", successMessage);
        }
        loadData();
    }

    // ── Event Listeners ────────────────────────────────────────────────────

    @Listen("onClick = #v2-btn-refresh")
    public void onRefresh() {
        searchBox.setValue("");
        editedFilter.setSelectedIndex(0);
        // Do NOT reset batchFilter.setSelectedIndex(0) here — loadData() rebuilds
        // the filter list from scratch, so the old selected item is already gone.
        loadData();
    }

    @Listen("onClick = #v2-btn-search")
    public void onSearchBtn() { applyFilters(); }

    // Triggers search on Enter key inside the search textbox
    @Listen("onOK = #v2-search")
    public void onSearchEnter() { applyFilters(); }

    @Listen("onSelect = #v2-edited-filter")
    public void onEditedFilterChange() { applyFilters(); }

    @Listen("onSelect = #v2-batch-filter")
    public void onBatchFilterChange() { applyFilters(); }

    @Listen("onClick = #v2-btn-clear-filter")
    public void onClearFilter() {
        searchBox.setValue("");
        editedFilter.setSelectedIndex(0);
        if (batchFilter != null) batchFilter.setSelectedIndex(0);
        renderTable(allCheques);
    }

    @Listen("onClick = #v2-success-dismiss")
    public void onDismissBanner() {
        if (successBanner != null) successBanner.setVisible(false);
    }

    // ── Filter Logic ───────────────────────────────────────────────────────

    /**
     * Filters allCheques by search text, edited-by-maker status, and selected batch,
     * then re-renders the table with matching results.
     */
    private void applyFilters() {
        String searchText = searchBox.getValue() != null
            ? searchBox.getValue().trim().toLowerCase() : "";
        String editedVal = getSelectedValue(editedFilter);
        String batchVal  = getSelectedValue(batchFilter);

        List<InwardCheque> filtered = allCheques.stream()
            .filter(c -> {
                if (!searchText.isEmpty()) {
                    boolean matchChq = c.getChequeNo() != null
                        && c.getChequeNo().toLowerCase().contains(searchText);
                    String bId = (c.getBatch() != null) ? c.getBatch().getBatchId() : "";
                    boolean matchBatch = bId.toLowerCase().contains(searchText);
                    if (!matchChq && !matchBatch) return false;
                }

                if ("YES".equals(editedVal) && !c.isEditedByMaker()) return false;
                if ("NO".equals(editedVal)  &&  c.isEditedByMaker()) return false;

                if (!batchVal.isEmpty()) {
                    String bId = (c.getBatch() != null) ? c.getBatch().getBatchId() : "";
                    if (!batchVal.equals(bId)) return false;
                }
                return true;
            })
            .collect(Collectors.toList());

        renderTable(filtered);
    }

    // ── Helpers ────────────────────────────────────────────────────────────

    /**
     * Updates the pending count badge shown on the page header and status bar.
     */
    private void updateBadgeCount(int count) {
        String label = count + " Cheque" + (count != 1 ? "s" : "") + " Awaiting";
        if (countBadge     != null) countBadge.setValue(label);
        if (statusBarCount != null) statusBarCount.setValue(String.valueOf(count));
    }

    /**
     * Reads the selected value from a select-mold Listbox.
     * Returns "" (never null) so callers can safely call .isEmpty() or .equals().
     */
    private String getSelectedValue(Listbox lb) {
        Listitem selected = lb.getSelectedItem();
        if (selected == null) return "";
        Object val = selected.getValue();
        return val != null ? val.toString() : "";
    }
}