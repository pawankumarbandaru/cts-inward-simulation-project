package com.cts.inward.composer;

import java.util.HashMap;
import java.util.Map;

import org.zkoss.zk.ui.Component;
import org.zkoss.zk.ui.Executions;
import org.zkoss.zk.ui.event.Event;
import org.zkoss.zk.ui.event.EventListener;
import org.zkoss.zk.ui.event.EventQueue;
import org.zkoss.zk.ui.event.EventQueues;
import org.zkoss.zk.ui.select.SelectorComposer;
import org.zkoss.zk.ui.select.annotation.Wire;
import org.zkoss.zul.Label;

import com.cts.inward.entity.InwardBatch;
import com.cts.inward.entity.InwardCheque;
import com.cts.inward.enums.BatchStatus;
import com.cts.inward.service.BatchProcessingService;
import com.cts.inward.service.BatchProcessingServiceImpl;
import com.cts.inward.service.InwardChequeMICRService;
import com.cts.inward.service.InwardChequeServiceMICRImpl;

/**
 * Single batch detail composer used by all three roles.
 *
 * Role is read from ZK Session attribute "userRole" set at login:
 *   MAKER   → loads all cheques, shows CBS button
 *   TV1     → loads cheques where sendTo = TV_1, hides CBS button
 *   TV2     → loads cheques where sendTo = TV_2, hides CBS button
 *
 * Role is passed to macro components via setAttribute() so that
 * BatchSummaryComposer and ChequeListComposer can behave accordingly.
 *
 * Popup opened depends on role:
 *   MAKER → chequeEditPopup.zul
 *   TV1   → TV1ChequeEditPopup.zul
 *   TV2   → TV2ChequeEditPopup.zul
 */
public class BatchDetailComposer extends SelectorComposer<Component> {

    private static final long serialVersionUID = 1L;

    // ── Session key — matches LoginComposer ───────────────────────────────────
    private static final String SESS_USER_ROLE = "userRole";

//    // ── Popup paths ───────────────────────────────────────────────────────────
//    private static final String POPUP_MAKER   = "/component/chequeEditPopup.zul";
//    private static final String POPUP_TV1     = "/component/TV1ChequeEditPopup.zul";
//    private static final String POPUP_TV2     = "/component/TV2ChequeEditPopup.zul";

    // ── Services ──────────────────────────────────────────────────────────────
    private final InwardChequeMICRService chequeService = new InwardChequeServiceMICRImpl();
  
    private final BatchProcessingService  batchService  = new BatchProcessingServiceImpl();

    @Wire Label lblPageSubtitle;

    private Long      currentBatchId = null;
    private String    currentRole    = "MAKER"; // safe default
    private Component self;
    
 // Keeps track of this composer's own subscription so it can be removed
    // before re-subscribing. Without this, every time this page is loaded
    // (batch click, sidebar nav, return from CBS, etc.) a NEW listener was
    // added to the desktop-scoped "chequeSelected" queue, and old listeners
    // were never cleaned up. A single click then fired the popup creation
    // once per accumulated listener, causing duplicate
    // <window id="chequeEditPopupWindow"> components and the ZK
    // "Not unique in ID space" error.
    private EventListener<Event> chequeSelectedListener;


    @Override
    public void doAfterCompose(Component comp) throws Exception {
        super.doAfterCompose(comp);
        self = comp;

     // ── STEP 1: Read role from Desktop ────────────────────────────────────
        Object roleAttr = Executions.getCurrent()
                                    .getDesktop()
                                    .getAttribute("userRole");

        if (roleAttr instanceof String && !((String) roleAttr).isEmpty()) {
            currentRole = ((String) roleAttr).toUpperCase().trim();
        }

        System.out.println(
            "BatchDetailComposer: Desktop="
            + Executions.getCurrent().getDesktop().getId()
            + " role="
            + currentRole
        );

        // ── STEP 2: Resolve batchDbId ─────────────────────────────────────────

        // a) String batchId from Desktop (checker/tv2 dashboard row click)
        String batchIdFromDesktop = (String) Executions.getCurrent()
                .getDesktop().getAttribute("batchId");
        if (batchIdFromDesktop != null && !batchIdFromDesktop.trim().isEmpty()) {
            InwardBatch batch = batchService.findByBatchId(batchIdFromDesktop.trim());
            if (batch != null) {
                currentBatchId = batch.getId();
                System.out.println("BatchDetailComposer: resolved from Desktop batchId = "
                        + currentBatchId);
            }
        }
        Executions.getCurrent().getDesktop().removeAttribute("batchId");

        // b) Numeric batchDbId from Desktop / execution / URL
        if (currentBatchId == null) {
            Object batchDbIdArg = Executions.getCurrent()
                    .getDesktop().getAttribute("batchDbId");
            if (batchDbIdArg == null)
                batchDbIdArg = Executions.getCurrent().getAttribute("batchDbId");
            if (batchDbIdArg == null)
                batchDbIdArg = Executions.getCurrent().getParameter("batchDbId");

            if (batchDbIdArg instanceof Long) {
                currentBatchId = (Long) batchDbIdArg;
            } else if (batchDbIdArg instanceof String) {
                try { currentBatchId = Long.parseLong((String) batchDbIdArg); }
                catch (NumberFormatException e) { currentBatchId = null; }
            }
        }

        // c) String batchId from URL parameter
//        if (currentBatchId == null) {
//            String batchIdParam = Executions.getCurrent().getParameter("batchId");
//            if (batchIdParam != null && !batchIdParam.trim().isEmpty()) {
//                InwardBatch batch = batchService.findByBatchId(batchIdParam.trim());
//                if (batch != null) currentBatchId = batch.getId();
//            }
//        }

        // d) Fallback — latest batch (sidebar navigation, no id passed)
        if (currentBatchId == null || currentBatchId <= 0) {
            currentBatchId = chequeService.resolveLatestBatchId();
        }

        Executions.getCurrent().getDesktop().removeAttribute("batchDbId");

        System.out.println("BatchDetailComposer: resolved batchDbId = " + currentBatchId);

        // ── STEP 3: Pass batchDbId to macro wrappers ──────────────────────────
        // BatchSummaryComposer and ChequeListComposer read these via
        // comp.getSpaceOwner().getAttribute("batchDbId")
        try {
            comp.getFellow("batchSummaryMacro")
                .setAttribute("batchDbId", currentBatchId);
        } catch (Exception e) {
            System.err.println("Could not find batchSummaryMacro: " + e.getMessage());
        }
        try {
            comp.getFellow("chequeListMacro")
                .setAttribute("batchDbId", currentBatchId);
        } catch (Exception e) {
            System.err.println("Could not find chequeListMacro: " + e.getMessage());
        }

        // ── Resolve and publish filter mode for the chequeList macro ─────────
        
        String filterMode = "all";
        if ("MAKER".equals(currentRole) && currentBatchId != null && currentBatchId > 0) {
            BatchStatus status = chequeService.getBatchStatus(currentBatchId);
            boolean stillWithMaker =
                    !BatchStatus.PendingAtChecker.equals(status)
                    && !BatchStatus.Cleared.equals(status)
                    && !BatchStatus.ClearedAtChecker.equals(status);
            filterMode = stillWithMaker ? "micr" : "all";
        }

        EventQueues.lookup("filterModeContext", EventQueues.DESKTOP, true)
                   .publish(new Event("onFilterModeResolved", null, filterMode));
        // ── 4. Update page subtitle ───────────────────────────────────────
        lblPageSubtitle.setVisible(false);


        // ── STEP 4: Subscribe for cheque row clicks ───────────────────────────
        listenForChequeSelected();

        // ── STEP 5: Publish batch context ─────────────────────────────────────
        EventQueues.lookup("batchContext", EventQueues.DESKTOP, true)
                .publish(new Event("onBatchResolved", null, currentBatchId));

        // ── STEP 6: Update page subtitle ──────────────────────────────────────
        try {
            long total = chequeService.getTotalChequeCount(currentBatchId, currentRole);
            lblPageSubtitle.setValue("Batch IW-2026-" + currentBatchId
                    + " - " + total + " cheques");
        } catch (Exception e) {
            lblPageSubtitle.setValue("Batch IW-2026-" + currentBatchId);
            System.err.println("BatchDetailComposer: could not get cheque count: "
                    + e.getMessage());
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Resolve latest batch from DB
    // ─────────────────────────────────────────────────────────────────────────

    
    // ─────────────────────────────────────────────────────────────────────────
    // Cheque row click → open correct popup based on role
    // ─────────────────────────────────────────────────────────────────────────

    private void listenForChequeSelected() {
        EventQueue<Event> queue = EventQueues.lookup("chequeSelected", EventQueues.DESKTOP, true);

        // Remove this composer's own previous subscription (if this page was
        // loaded before in the same desktop session) so listeners never pile
        // up across repeated page visits.
        if (chequeSelectedListener != null) {
            queue.unsubscribe(chequeSelectedListener);
        }

        chequeSelectedListener = (Event event) -> {
            InwardCheque cheque = (InwardCheque) event.getData();
            openChequePopup(cheque);
        };
        queue.subscribe(chequeSelectedListener);
    }

    private void openChequePopup(InwardCheque cheque) {
        try {
            Component existing = self.getFellowIfAny("chequeEditPopupWindow");
            if (existing != null) existing.detach();

            Map<String, Object> args = new HashMap<>();
            args.put("selectedCheque", cheque);
            args.put("userRole", currentRole);

            Executions.createComponents(
                    POPUP_PATH,
                    self,
                    args);

        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    /**
     * Returns correct popup ZUL path based on current role.
     */
    private static final String POPUP_PATH =
            "/component/chequeEditPopup.zul";
}