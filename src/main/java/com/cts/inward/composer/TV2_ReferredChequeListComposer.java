package com.cts.inward.composer;

import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.zkoss.zk.ui.Component;
import org.zkoss.zk.ui.Executions;
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
import org.zkoss.zul.event.PagingEvent;

import com.cts.inward.entity.InwardCheque;
import com.cts.inward.service.InwardChequeService;
import com.cts.inward.service.InwardChequeServiceImpl;

public class TV2_ReferredChequeListComposer extends SelectorComposer<Component> {

    private final InwardChequeService inwardChequeService
            = new InwardChequeServiceImpl();

    @Wire Listbox lbReferredChequeList;
    @Wire Paging  pgReferredChequeList;
    
    private Component self;
    
    private List<InwardCheque> referredCheques = new ArrayList<>();

    private static final int PAGE_SIZE = 5;

    @Override
    public void doAfterCompose(Component comp) throws Exception {
        super.doAfterCompose(comp);
        
        self = comp;
        
        pgReferredChequeList.setPageSize(PAGE_SIZE);

        loadReferredCheques();
        listenForChequeSelected();
    }

    // ===================================================
    // Dao -> Service -> Composer
    // ===================================================
    private void loadReferredCheques() {
        referredCheques = inwardChequeService.getReferredChequesForTV2();

        pgReferredChequeList.setTotalSize(referredCheques.size());
        pgReferredChequeList.setActivePage(0);

        renderPage(0);
    }

    // ===================================================
    // Render Page
    // ===================================================
    private void renderPage(int pageIndex) {
        int fromIndex = pageIndex * PAGE_SIZE;
        int toIndex   = Math.min(fromIndex + PAGE_SIZE, referredCheques.size());

        List<InwardCheque> pageData = referredCheques.subList(fromIndex, toIndex);

        lbReferredChequeList.getItems().clear();

        int rowNum = fromIndex + 1;
        for (InwardCheque cheque : pageData) {
            lbReferredChequeList.appendChild(buildRow(cheque, rowNum++));
        }
    }

    private Listitem buildRow(InwardCheque cheque, int rowNum) {
        Listitem item = new Listitem();
        item.setValue(cheque);

        item.appendChild(new Listcell(String.valueOf(rowNum)));

        Listcell cellChequeNo = new Listcell();
        Label lblChequeNo = new Label(nullSafe(cheque.getChequeNo()));
        lblChequeNo.setSclass("cheque-no-link");
        cellChequeNo.appendChild(lblChequeNo);
        item.appendChild(cellChequeNo);

        Listcell cellAmount = new Listcell();
        Label lblAmount = new Label(
            cheque.getAmount() != null
            ? "Rs. " + cheque.getAmount().toPlainString() : "-"
        );
        lblAmount.setSclass("amount-label");
        cellAmount.appendChild(lblAmount);
        item.appendChild(cellAmount);

        Listcell cellDate = new Listcell();
        Label lblDate = new Label(
            cheque.getChequeDate() != null
            ? cheque.getChequeDate().format(DateTimeFormatter.ofPattern("dd/MM/yyyy")) : "-"
        );
        lblDate.setSclass("date-label");
        cellDate.appendChild(lblDate);
        item.appendChild(cellDate);

        Listcell cellStatus = new Listcell();
        Label lblStatus = new Label(
            cheque.getChequeStatus() != null ? cheque.getChequeStatus().name() : "-"
        );
        lblStatus.setSclass("badge-micr-error");
        cellStatus.appendChild(lblStatus);
        item.appendChild(cellStatus);

        return item;
    }

    @Listen("onPaging = #pgReferredChequeList")
    public void onPageChange(PagingEvent event) {
        renderPage(event.getActivePage());
    }

    @Listen("onSelect = #lbReferredChequeList")
    public void onChequeSelect() {
        if (lbReferredChequeList.getSelectedItem() == null) return;

        InwardCheque selected = (InwardCheque)
                lbReferredChequeList.getSelectedItem().getValue();

        EventQueues.lookup("chequeSelected", EventQueues.DESKTOP, true)
                   .publish(new Event("onChequeSelected", null, selected));
    }

    // ===================================================
    // Popup Handling
    // ===================================================
    private void listenForChequeSelected() {
        EventQueues.lookup("chequeSelected", EventQueues.DESKTOP, true)
                   .subscribe((Event event) -> {
                       InwardCheque cheque = (InwardCheque) event.getData();
                       openChequePopup(cheque);
                   });
    }

    private void openChequePopup(InwardCheque cheque) {
        try {
            Component existing = self.getFellowIfAny("chequeEditPopupWindow");
            if (existing != null) {
                existing.detach();
            }

            Map<String, Object> args = new HashMap<>();
            args.put("selectedCheque", cheque);
            Executions.createComponents("/component/chequeEditPopup.zul", self, args);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private String nullSafe(String value) {
        return (value != null) ? value : "-";
    }
}