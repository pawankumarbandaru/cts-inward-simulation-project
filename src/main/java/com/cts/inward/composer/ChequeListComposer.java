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

/**
 * Composer for chequeList macro component.
 *
 * FIX 1 — Race condition:
 * batchDbId is read from the macro component's own ZK attribute set by
 * MakerBatchDetailComposer before this composer runs. No EventQueue race.
 *
 * FIX 2 — Status filter enum mismatch:
 * ChequeStatus enum values are "Normal" and "Micr_error".
 * The old chequeList.zul filter listitem had value="MICR_ERROR" which
 * never matched ChequeStatus.Micr_error.name() == "Micr_error".
 * matchesStatus() now uses ChequeStatus.name() directly for comparison
 * and the ZUL filter values are corrected to match ("Normal", "Micr_error").
 */
public class ChequeListComposer extends SelectorComposer<Component> {

    private final InwardChequeMICRService inwardChequeService
            = new InwardChequeServiceMICRImpl();

    @Wire Listbox lbChequeList;
    @Wire Listbox lbStatusFilter;
    @Wire Textbox tbSearch;
    @Wire Paging  pgChequeList;
    @Wire Datebox dpChequeDate;

    private Long currentBatchId             = null;
    private List<InwardCheque> allCheques      = new ArrayList<>();
    private List<InwardCheque> filteredCheques = new ArrayList<>();

    private static final int PAGE_SIZE = 8;
    private String currentRole = "MAKER";

    // Used to display cheque_date as dd-MM-yyyy in the cheque list grid
    private static final DateTimeFormatter CHEQUE_DATE_FORMAT =
            DateTimeFormatter.ofPattern("dd-MM-yyyy");
    
    @Override
    public void doAfterCompose(Component comp) throws Exception {
        super.doAfterCompose(comp);

        pgChequeList.setPageSize(PAGE_SIZE);

        // ── Read batchDbId from the macro component wrapper attribute ──
        // MakerBatchDetailComposer sets this before HtmlMacroComponent.afterCompose()
        // calls us, so it is always present and there is no timing issue.
        Object attr = comp.getSpaceOwner().getAttribute("batchDbId");

        if (attr instanceof Long) {
            currentBatchId = (Long) attr;
        } else if (attr instanceof String) {
            try { currentBatchId = Long.parseLong((String) attr); }
            catch (NumberFormatException e) { currentBatchId = null; }
        }
        
        // ── Read role from Desktop ────────────────────────────────────────────
        Object roleAttr = Executions.getCurrent()
                                    .getDesktop()
                                    .getAttribute("userRole");

        if (roleAttr instanceof String && !((String) roleAttr).isEmpty()) {
            currentRole = ((String) roleAttr).toUpperCase().trim();
        }

        System.out.println(
            "ChequeListComposer: Desktop="
            + Executions.getCurrent().getDesktop().getId()
            + " role="	
            + currentRole
        );
        
        System.out.println("ChequeListComposer: batchDbId from attribute = " + currentBatchId);

        
        // Load immediately if we have a valid id
        if (currentBatchId != null && currentBatchId > 0) {
            loadCheques();
        }

        // Fallback / cross-page: also subscribe to EventQueue
        EventQueues.lookup("batchContext", EventQueues.DESKTOP, true)
                   .subscribe((Event event) -> {
                       if (event.getData() instanceof Long) {
                           Long incoming = (Long) event.getData();
                           if (incoming != null && incoming > 0) {
                        	    currentBatchId = incoming;
                        	    loadCheques();
                        	}
                       }
                   });

        // Reload after a cheque is saved
        EventQueues.lookup("chequeStatusUpdated", EventQueues.DESKTOP, true)
                   .subscribe((Event event) -> loadCheques());
    }

    // ===================================================
    // Load All Cheques from DB
    // ===================================================

    private void loadCheques() {
        if (currentBatchId == null || currentBatchId <= 0) return;

        System.out.println("ChequeListComposer: loading for role=" + currentRole);

        switch (currentRole) {
            case "TV1":
                allCheques = inwardChequeService.TV1_ChequesList(currentBatchId);
                break;
            case "TV2":
                allCheques = inwardChequeService.TV2_ChequesList(currentBatchId);
                break;
            default: // MAKER
                allCheques = inwardChequeService.getChequesByBatchId(currentBatchId);
                break;
        }

        filteredCheques = new ArrayList<>(allCheques);
        pgChequeList.setTotalSize(filteredCheques.size());
        pgChequeList.setActivePage(0);
        renderPage(0);
    }
    
    
    // ===================================================
    // Render a Specific Page
    // ===================================================

    private void renderPage(int pageIndex) {
        int fromIndex = pageIndex * PAGE_SIZE;
        int toIndex   = Math.min(fromIndex + PAGE_SIZE, filteredCheques.size());

        List<InwardCheque> pageData = filteredCheques.subList(fromIndex, toIndex);

        lbChequeList.getItems().clear();

        int rowNum = fromIndex + 1;
        for (InwardCheque cheque : pageData) {
        	
            lbChequeList.appendChild(buildRow(cheque, rowNum++));
        }
    }

    // ===================================================
    // Build One Table Row
    // ===================================================

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
	
	        item.appendChild(
	        	    new Listcell(
	        	        inwardChequeService.formatChequeDate(
	        	            cheque.getChequeDate()
	        	        )
	        	    )
	        	);
	
	        String accNo  = cheque.getAccountNo();
	        String masked = (accNo != null && accNo.length() >= 4)
	                ? "****" + accNo.substring(accNo.length() - 4)
	                : "****";
	        item.appendChild(new Listcell(masked));
	
	        Listcell cellAmount = new Listcell();
	        Label lblAmount = new Label(
	            cheque.getAmount() != null
	            ? "Rs. " + cheque.getAmount().toPlainString() : "-"
	        );
	        lblAmount.setSclass("amount-label");
	        cellAmount.appendChild(lblAmount);
	        item.appendChild(cellAmount);
	
	        Listcell cellStatus = new Listcell();
	        String statusText = cheque.getChequeStatus() != null
	                ? cheque.getChequeStatus().name() : "-";
	        Label lblStatus = new Label(statusText);
	        lblStatus.setSclass(
	        	    inwardChequeService.isMicrError(cheque)
	        	        ? "badge-micr-error"
	        	        : "badge-normal"
	        	);
	        cellStatus.appendChild(lblStatus);
	        item.appendChild(cellStatus);
	
	        return item;
	    }

    @Listen("onChange = #dpChequeDate")
    public void onDateFilterChange() { applyFilter(); }
    @Listen("onChange = #tbSearch")
    public void onSearch() { applyFilter(); }

    @Listen("onSelect = #lbStatusFilter")
    public void onFilterChange() { applyFilter(); }

    private void applyFilter() {
        String keyword = tbSearch.getValue().trim().toLowerCase();

        Listitem selectedFilter = lbStatusFilter.getSelectedItem();
        String status = (selectedFilter != null)
                ? selectedFilter.getValue().toString() : "ALL";

        java.time.LocalDate selectedDate = dpChequeDate.getValue() != null
                ? dpChequeDate.getValue().toInstant()
                        .atZone(java.time.ZoneId.systemDefault())
                        .toLocalDate()
                : null;

        filteredCheques = allCheques.stream()
            .filter(c -> matchesKeyword(c, keyword)
                      && matchesStatus(c, status)
                      && matchesDateFilter(c, selectedDate))
            .collect(Collectors.toList());

        pgChequeList.setTotalSize(filteredCheques.size());
        pgChequeList.setActivePage(0);
        renderPage(0);
    }

    private boolean matchesKeyword(InwardCheque c, String keyword) {
        if (keyword.isEmpty()) return true;
        return contains(c.getChequeNo(), keyword)
            || contains(c.getAccountNo(), keyword)
            || contains(c.getTransactionCode(), keyword)
            || matchesAmount(c, keyword)
            || matchesChequeDate(c, keyword);
    }

    private boolean matchesAmount(InwardCheque c, String keyword) {
        if (c.getAmount() == null) return false;
        return c.getAmount().toPlainString().contains(keyword);
    }
    
	 	// Matches the search keyword against the formatted cheque date (dd-MM-yyyy).
	    // Reuses formatChequeDate() so the search always matches what's shown on screen.
    private boolean matchesChequeDate(InwardCheque c, String keyword) {
        return inwardChequeService
                .formatChequeDate(c.getChequeDate())
                .contains(keyword);
    }
	    
	    private boolean matchesDateFilter(InwardCheque c, java.time.LocalDate selectedDate) {
	        if (selectedDate == null) return true;
	        if (c.getChequeDate() == null) return false;
	        return c.getChequeDate().toLocalDate().equals(selectedDate);
	    }

	    

	private boolean matchesStatus(InwardCheque c, String status) {
        if ("ALL".equals(status)) return true;
        if (c.getChequeStatus() == null) return false;
        // FIX: compare against enum name directly — "Normal" or "Micr_error"
        // The ZUL listitem values must also use these exact names (see chequeList.zul)
        return status.equals(c.getChequeStatus().name());
    }

    @Listen("onPaging = #pgChequeList")
    public void onPageChange(PagingEvent event) {
        renderPage(event.getActivePage());
    }

    @Listen("onSelect = #lbChequeList")
    public void onChequeSelect() {
        if (lbChequeList.getSelectedItem() == null) return;

        InwardCheque selected = (InwardCheque)
                lbChequeList.getSelectedItem().getValue();

        EventQueues.lookup("chequeSelected", EventQueues.DESKTOP, true)
                   .publish(new Event("onChequeSelected", null, selected));
    }


    private boolean contains(String field, String keyword) {
        return field != null && field.toLowerCase().contains(keyword);
    }

    private String nullSafe(String value) {
        return (value != null) ? value : "-";
    }
    
}