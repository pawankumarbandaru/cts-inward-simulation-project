package com.cts.inward.composer;

/**
 * File    : M_ReturnedByCheckerComposer.java
 * Package : com.cts.inward.composer
 * Purpose : Handles the "Returned By Checker" queue screen for the Maker role.
 *           Loads cheques that were sent back by the Checker, renders the grid,
 *           applies search/batch filters, handles paging, and opens the Repair popup.
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
import org.zkoss.zk.ui.event.EventQueues;
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
import org.zkoss.zk.ui.event.EventQueues;
import org.zkoss.zk.ui.event.Event;

import com.cts.inward.entity.InwardCheque;
import com.cts.inward.service.InwardChequeService;
import com.cts.inward.service.InwardChequeServiceImpl;
import com.cts.util.CtsUiBridge;

public class M_ReturnedByCheckerComposer extends SelectorComposer<Component> {

    private final InwardChequeService service = new InwardChequeServiceImpl();

    @Wire("#rcb-count-badge")    private Label   countBadge;
    @Wire("#rcb-alert-banner")   private Div     alertBanner;
    @Wire("#rcb-rows")           private Rows    gridRows;
    @Wire("#rcb-paging")         private Paging  paging;
    @Wire("#rcb-search")         private Textbox searchBox;
    @Wire("#rcb-batch-filter")   private Listbox batchFilter;
    @Wire("#sb-rcb-count")       private Label   statusBarCount;
    @Wire("#rcb-empty")          private Row     emptyRow;
    @Wire("#rcb-success-banner") private Div     successBanner;

    // successText is a native <n:div> in the ZUL — content is set via JS bridge
    private static final String SUCCESS_TEXT_ID = "rcb-success-text";

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
     * Loads all cheques needing correction from DB, refreshes badge,
     * populates batch dropdown, and renders the first page.
     */
    private void loadData() {
        allCheques = service.getChequesNeedingCorrection();
        updateBadgeCount(allCheques.size());
        populateBatchFilter(allCheques);
        renderTable(allCheques);
        showSuccessBannerIfAny();
    }

    // ── Paging ─────────────────────────────────────────────────────────────

    /**
     * Fired when the user clicks a page button (prev / next / number).
     * Re-slices currentList to the new page without hitting the DB.
     */
    @Listen("onPaging = #rcb-paging")
    public void onPaging() {
        renderCurrentPage();
    }

    // ── Filter helpers ──────────────────────────────────────────────────────

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
            .map(cheque -> (cheque.getBatch() != null) ? cheque.getBatch().getBatchId() : null)
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
     * Checks the session for a success message set by M_RepairWorkspaceComposer
     * after resubmit or RRF. Shows the banner if a message is present.
     */
    private void showSuccessBannerIfAny() {
        if (successBanner == null) return;

        Object msg = Executions.getCurrent()
            .getSession().getAttribute("rcbSuccessMessage");

        if (msg != null) {
            Executions.getCurrent().getSession().removeAttribute("rcbSuccessMessage");
            CtsUiBridge.setBannerText(SUCCESS_TEXT_ID, msg.toString());
            successBanner.setVisible(true);
        } else {
            successBanner.setVisible(false);
        }
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
     * emptyRow is never removed from the DOM — only its visibility is toggled.
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
     * Columns: # | Batch Number | Cheque Number | Amount (₹) | Checker SendBack Reason | Action
     */
    private Row buildTableRow(InwardCheque cheque, int rowNum) {
        Row row = new Row();

        if (cheque.isEditedByMaker()) {
            row.setSclass("maker-edited");
        }

        final String chequeNo = cheque.getChequeNo();

        // Col 1 — Serial number
        Label numLabel = new Label(String.valueOf(rowNum));
        numLabel.setSclass("chk-col-num");
        row.appendChild(numLabel);

        // Col 2 — Batch Number
        String batchId = (cheque.getBatch() != null) ? cheque.getBatch().getBatchId() : "—";
        Label batchLabel = new Label(batchId);
        batchLabel.setSclass("chk-col-text");
        row.appendChild(batchLabel);

        // Col 3 — Cheque Number
        Label chqLabel = new Label(cheque.getChequeNo());
        chqLabel.setSclass("chk-col-chqno");
        row.appendChild(chqLabel);

        // Col 4 — Amount (₹)
        String amountStr = cheque.getAmount() != null
            ?  String.format("%,.2f", cheque.getAmount()) : "—";
        Label amountLabel = new Label(amountStr);
        amountLabel.setSclass("chk-col-amount");
        row.appendChild(amountLabel);

        // Col 5 — Checker SendBack Reason
        String referReason = cheque.getSendbackReason();
        Label reasonLabel = new Label(
            (referReason != null && !referReason.isBlank()) ? referReason : "—");
        reasonLabel.setSclass("chk-col-text");
        row.appendChild(reasonLabel);

        // Col 6 — Action button
        Button btn = new Button("Re-correct");
        btn.setSclass("rcb-btn-correct");
        btn.addEventListener(Events.ON_CLICK, event -> {
            event.stopPropagation();
            openRepairModal(chequeNo);
        });
        row.appendChild(btn);

        return row;
    }

    // ── Popup ──────────────────────────────────────────────────────────────

    /**
     * Opens the Maker Repair Workspace as an overlapped popup Window.
     * Passes this composer and the Window in args so the workspace can
     * call back onModalClosed() when the maker is done.
     */
    private void openRepairModal(String chequeNo) {

        Executions.getCurrent().getSession()
            .setAttribute("rcbSelectedChequeNo", chequeNo);

        CtsUiBridge.showBackdrop("rcb-modal-backdrop");

        Window win = new Window();
        win.setTitle("");
        win.setBorder("none");
        win.setSizable(false);
        win.setClosable(false);
        win.setDraggable("false");
        win.setSclass("rcb-repair-modal-win cts-modal-window");
        win.setWidth("880px");
        win.setHeight("95vh");
        win.setMode("overlapped");
        win.setStyle(
            "position:fixed;" +
            "top:50%;left:50%;" +
            "transform:translate(-50%,-50%);" +
            "z-index:1000;" +
            "overflow:hidden;" +
            "border-radius:10px;" +
            "box-shadow:0 20px 60px rgba(0,0,0,0.4);"
        );

        win.setPage(getSelf().getPage());

        Map<String, Object> args = new HashMap<>();
        args.put("queueComposer", this);
        args.put("modalWindow",   win);

        Executions.createComponents(
            "/zul/inward/M_RepairWorkspace.zul",
            win,
            args
        );
    }

    /**
     * Callback called by M_RepairWorkspaceComposer after any action (resubmit / RRF / close).
     * Sets the success message in session first, then reloads the queue — because
     * loadData() calls showSuccessBannerIfAny() internally.
     */
    public void onModalClosed(String successMessage) {
        if (successMessage != null) {
            Executions.getCurrent().getSession()
                .setAttribute("rcbSuccessMessage", successMessage);
        }
        loadData();
    }

    // ── Event Listeners ────────────────────────────────────────────────────

    @Listen("onClick = #btn-refresh")
    public void onRefresh() {
        searchBox.setValue("");
        // Do NOT reset batchFilter.setSelectedIndex(0) here — loadData() rebuilds
        // the filter list from scratch, so the old selected item is already gone.
        loadData();
    }

    @Listen("onClick = #btn-search")
    public void onSearchBtn() { applyFilters(); }

    // Triggers search on Enter key inside the search textbox
    @Listen("onOK = #rcb-search")
    public void onSearchEnter() { applyFilters(); }

    @Listen("onSelect = #rcb-batch-filter")
    public void onBatchFilterChange() { applyFilters(); }

    @Listen("onClick = #btn-clear-filter")
    public void onClearFilter() {
        searchBox.setValue("");
        if (batchFilter != null) batchFilter.setSelectedIndex(0);
        renderTable(allCheques);
    }

    @Listen("onClick = #rcb-success-dismiss")
    public void onDismissBanner() {
        if (successBanner != null) successBanner.setVisible(false);
    }

    // ── Filter Logic ───────────────────────────────────────────────────────

    /**
     * Filters allCheques by the current search text and selected batch number,
     * then re-renders the table with matching results.
     */
    private void applyFilters() {
        String searchText = searchBox.getValue() != null
            ? searchBox.getValue().trim().toLowerCase() : "";
        String batchVal = getSelectedValue(batchFilter);

        List<InwardCheque> filtered = allCheques.stream()
            .filter(c -> {
                if (!searchText.isEmpty()) {
                    boolean matchChq = c.getChequeNo() != null
                        && c.getChequeNo().toLowerCase().contains(searchText);
                    String bId = (c.getBatch() != null) ? c.getBatch().getBatchId() : "";
                    boolean matchBatch = bId.toLowerCase().contains(searchText);
                    if (!matchChq && !matchBatch) return false;
                }
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
     * Updates the pending count badge shown on the page header and sidebar.
     */
    private void updateBadgeCount(int count) {
        String label = count + " Cheque" + (count != 1 ? "s" : "") + " Waiting";
        if (countBadge     != null) countBadge.setValue(label);
        if (statusBarCount != null) statusBarCount.setValue(String.valueOf(count));
        Executions.getCurrent().getSession().setAttribute("rcbPendingCount", count);

        // Tell the sidebar (and any other listener) the count changed, so the
        // sidebar badge re-reads its number. Same queue + scope every other
        // part of the app uses.
        EventQueues.lookup("chequeStatusUpdated", EventQueues.DESKTOP, true)
                   .publish(new Event("onChequeStatusUpdated"));
    }

    /**
     * Reads the selected value from a select-mold Listbox.
     * Returns "" (never null) so callers can safely call .isEmpty() or .equals().
     */
    private String getSelectedValue(Listbox lb) {
        if (lb == null) return "";
        Listitem selected = lb.getSelectedItem();
        if (selected == null) return "";
        Object val = selected.getValue();
        return val != null ? val.toString() : "";
    }
}