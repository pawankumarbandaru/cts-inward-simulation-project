package com.cts.inward.composer;

/**
 * File    : TV1_ResubmittedQueueComposer.java
 * Package : com.cts.inward.composer
 * Purpose : Handles the Checker (Verifier 1) "Resubmitted Queue" screen.
 *           Loads cheques resubmitted by the Maker after correction, renders the grid,
 *           applies search/batch filters, handles paging, and opens the Review popup.
 *           Also shows CBS Status per row — even if CBS failed at the Maker, the
 *           cheque can still be sent back for review, so the checker should see it.
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
import com.cts.inward.service.TV1_ResubmittedChequeService;
import com.cts.inward.service.TV1_ResubmittedChequeServiceImpl;
import com.cts.util.CtsUiBridge;

public class TV1_ResubmittedQueueComposer extends SelectorComposer<Component> {

    private final TV1_ResubmittedChequeService checkerService = new TV1_ResubmittedChequeServiceImpl();

    @Wire("#chk-count-badge")      private Label   countBadge;
    @Wire("#chk-alert-banner")     private Div     alertBanner;
    @Wire("#chk-rows")             private Rows    gridRows;
    @Wire("#chk-paging")           private Paging  paging;
    @Wire("#chk-search")           private Textbox searchBox;
    @Wire("#chk-edited-filter")    private Listbox editedFilter;
    @Wire("#chk-batch-filter")     private Listbox batchFilter;
    @Wire("#sb-chk-count")         private Label   statusBarCount;
    @Wire("#chk-empty-row")        private Row     emptyRow;
    @Wire("#chk-success-banner")   private Div     successBanner;

    // Content is set via CtsUiBridge.setBannerText("chk-success-text", ...) using JavaScript.
    private static final String SUCCESS_TEXT_ID = "chk-success-text";

    private List<InwardCheque> allCheques;

    // Tracks whichever list is currently displayed (full list OR filtered result).
    // Needed so that onPaging() can re-render the correct page without re-running filters.
    private List<InwardCheque> currentList = new ArrayList<>();

    // ── Lifecycle ──────────────────────────────────────────────────────────

    @Override
    public void doAfterCompose(Component comp) throws Exception {
        super.doAfterCompose(comp);
        loadData();
    }

    // ── Load ───────────────────────────────────────────────────────────────

    private void loadData() {
        allCheques = checkerService.getResubmittedChequesForChecker();
        updateBadgeCount(allCheques.size());
        populateBatchFilter(allCheques);
        renderTable(allCheques);
        showSuccessBannerIfAny();
    }

    /**
     * Fills the "Batch Number" dropdown with the distinct batch IDs found
     * in the currently loaded cheque list, so the checker only ever sees
     * batch numbers that actually have cheques waiting — no stale/fake values.
     * "All Batches" is always kept as the first option.
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

    private void showSuccessBannerIfAny() {
        if (successBanner == null) return;

        Object msg = Executions.getCurrent()
            .getSession().getAttribute("checkerSuccessMessage");

        if (msg != null) {
            Executions.getCurrent().getSession().removeAttribute("checkerSuccessMessage");
            // FIX: use setBannerText() instead of Html.setContent()
            // Avoids "Failed to mount: Unknown stub" error from ZK Html component stub
            CtsUiBridge.setBannerText(SUCCESS_TEXT_ID, msg.toString());
            successBanner.setVisible(true);
        } else {
            successBanner.setVisible(false);
        }
    }

    // ── Paging event ───────────────────────────────────────────────────────

    /**
     * Fired whenever the user clicks a page button (prev / next / number).
     * We do NOT reload from DB — we just re-slice currentList to the new page.
     */
    @Listen("onPaging = #chk-paging")
    public void onPaging() {
        renderCurrentPage();
    }

    // ── Table Rendering ────────────────────────────────────────────────────

    /**
     * Entry point for all render calls (initial load, filter, clear).
     * Stores the full list, resets paging to page 0, then paints the first page.
     */
    private void renderTable(List<InwardCheque> list) {
        currentList = (list != null) ? list : new ArrayList<>();

        // Always jump back to page 1 when the list changes (new filter, refresh, etc.)
        paging.setActivePage(0);
        paging.setTotalSize(currentList.size());

        alertBanner.setVisible(!currentList.isEmpty());

        renderCurrentPage();
    }

    /**
     * Paints only the rows that belong to the active page.
     * Called by renderTable() on first load, and by onPaging() on page change.
     */
    private void renderCurrentPage() {
        // Remove only data rows — never detach emptyRow from DOM.
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
        int activePg = paging.getActivePage();  // 0-based
        int start    = activePg * pageSize;
        int end      = Math.min(start + pageSize, currentList.size());

        // Row numbers shown in # column count continuously across pages
        int rowNum = start + 1;
        for (int i = start; i < end; i++) {
            gridRows.appendChild(buildTableRow(currentList.get(i), rowNum++));
        }
    }

    /**
     * COLUMN MAP:
     *  1. #   2. Cheque No.   3. Batch ID   4. Amount (₹)
     *  5. Checker Sendback Reason   6. Edited   7. Action
     *
     * 
     */
    private Row buildTableRow(InwardCheque cheque, int rowNum) {
        Row row = new Row();

        if (cheque.isEditedByMaker()) {
            row.setSclass("maker-edited");
        }

        final String chequeNo = cheque.getChequeNo();
        // FIX: removed row.addEventListener(ON_CLICK, ...) — clicking anywhere
        // on the row used to open the review modal. Now only the "Review"
        // button below does that, as intended.

        // Col 1 — Row number
        Label numLabel = new Label(String.valueOf(rowNum));
        numLabel.setSclass("chk-col-num");
        row.appendChild(numLabel);

        // Col 2 — Cheque No.
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
        amountLabel.setSclass("chk-col-amount");
        row.appendChild(amountLabel);

        // Col 5 — Checker refer reason (truncated to 80 chars)
        String remark = cheque.getSendbackReason() != null
            ? cheque.getSendbackReason() : "—";
        if (remark.length() > 80) remark = remark.substring(0, 80) + "...";
        Label remarkLabel = new Label(remark);
        remarkLabel.setSclass("chk-col-remark");
        row.appendChild(remarkLabel);

        // Col 6 — Edited badge
        if (cheque.isEditedByMaker()) {
            String fields = cheque.getEditedFields() != null
                ? cheque.getEditedFields() : "fields updated";
            Label editedLabel = new Label("Edited");
            editedLabel.setSclass("chk-badge-edited");
            editedLabel.setTooltiptext("Changed: " + fields);
            row.appendChild(editedLabel);
        } else {
            Label dash = new Label("—");
            dash.setSclass("chk-col-meta");
            row.appendChild(dash);
        }

        // Col 7 — Action button
        Button btn = new Button("Review");
        btn.setSclass("chk-btn-review");
        btn.addEventListener(Events.ON_CLICK, event -> {
            event.stopPropagation();
            openReviewModal(chequeNo);
        });
        row.appendChild(btn);

        return row;
    }

    // ── Popup ──────────────────────────────────────────────────────────────

    private void openReviewModal(String chequeNo) {
        Executions.getCurrent().getSession()
            .setAttribute("checkerSelectedChequeNo", chequeNo);

        CtsUiBridge.showBackdrop("chk-modal-backdrop");

        Window win = new Window();
        win.setTitle("");
        win.setBorder("none");
        win.setSizable(false);
        win.setClosable(false);
        win.setDraggable("false");
        win.setSclass("chk-review-modal-win cts-modal-window");
        win.setWidth("880px");
        win.setHeight("95vh");
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
            "/zul/inward/TV1_ReviewWorkspace.zul",
            win,
            args
        );
    }

    public void onModalClosed(String successMessage) {
        // FIX: set session attribute FIRST, then call loadData().
        // loadData() internally calls showSuccessBannerIfAny() which reads the session.
        // Old code set the attribute AFTER loadData() — so banner never showed.
        if (successMessage != null) {
            Executions.getCurrent().getSession()
                .setAttribute("checkerSuccessMessage", successMessage);
        }
        loadData();
    }

    // ── Event Listeners ────────────────────────────────────────────────────

    @Listen("onClick = #chk-btn-refresh")
    public void onRefresh() {
        searchBox.setValue("");
        editedFilter.setSelectedIndex(0); // editedFilter is static, never rebuilt — safe to reset directly
        loadData();
    }

    @Listen("onClick = #chk-btn-search")
    public void onSearchBtn() { applyFilters(); }

    @Listen("onOK = #chk-search")
    public void onSearchEnter() { applyFilters(); }

    @Listen("onSelect = #chk-edited-filter")
    public void onEditedFilterChange() { applyFilters(); }

    @Listen("onSelect = #chk-batch-filter")
    public void onBatchFilterChange() { applyFilters(); }

    @Listen("onClick = #chk-btn-clear-filter")
    public void onClearFilter() {
        searchBox.setValue("");
        editedFilter.setSelectedIndex(0);
        if (batchFilter != null) batchFilter.setSelectedIndex(0);
        renderTable(allCheques);
    }

    @Listen("onClick = #chk-success-dismiss")
    public void onDismissBanner() {
        if (successBanner != null) successBanner.setVisible(false);
    }

    // ── Filter Logic ───────────────────────────────────────────────────────

    /**
     * Filters by search text, edited status, and batch number.
     */
    private void applyFilters() {
        String searchText = searchBox.getValue() != null
            ? searchBox.getValue().trim().toLowerCase() : "";
        String editedVal  = getSelectedValue(editedFilter);
        String batchVal   = getSelectedValue(batchFilter);

        List<InwardCheque> filtered = allCheques.stream()
            .filter(c -> {
                // Search filter
                if (!searchText.isEmpty()) {
                    boolean matchChq = c.getChequeNo() != null
                        && c.getChequeNo().toLowerCase().contains(searchText);
                    String bId = (c.getBatch() != null) ? c.getBatch().getBatchId() : "";
                    boolean matchBatch = bId.toLowerCase().contains(searchText);
                    if (!matchChq && !matchBatch) return false;
                }
                // Edited filter
                if ("YES".equals(editedVal) && !c.isEditedByMaker()) return false;
                if ("NO".equals(editedVal)  &&  c.isEditedByMaker()) return false;

                // Batch Number filter — exact match against the selected batch
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

    private void updateBadgeCount(int count) {
        String label = count + " Cheque" + (count != 1 ? "s" : "") + " Awaiting";
        if (countBadge     != null) countBadge.setValue(label);
        if (statusBarCount != null) statusBarCount.setValue(String.valueOf(count));
    }

    private String getSelectedValue(Listbox lb) {
        Listitem selected = lb.getSelectedItem();
        if (selected == null) return "";
        Object val = selected.getValue();
        return val != null ? val.toString() : "";
    }

    /**
     * Returns a CBS validation status badge for the checker.
     *
     * WHY useful for checker:
     *   If CBS is Invalid the checker knows the maker fixed fields but CBS
     *   still failed — they should review more carefully before deciding.
     */
    private Label buildCbsStatusBadge(InwardCheque cheque) {
        Label badge = new Label();
        if (cheque.getCbsValidation() == null) {
            badge.setValue("CBS: Pending");
            badge.setSclass("cbs-badge-pending");
        } else {
            switch (cheque.getCbsValidation()) {
                case Valid:
                    badge.setValue("CBS: Valid");
                    badge.setSclass("cbs-badge-valid");
                    break;
                case Invalid:
                    badge.setValue("CBS: Invalid");
                    badge.setSclass("cbs-badge-invalid");
                    break;
                default:
                    badge.setValue("CBS: —");
                    badge.setSclass("chk-col-meta");
            }
        }
        return badge;
    }
}