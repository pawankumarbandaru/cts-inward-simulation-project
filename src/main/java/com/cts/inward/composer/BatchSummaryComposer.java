package com.cts.inward.composer;

import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.Map;

import org.zkoss.zk.ui.Component;
import org.zkoss.zk.ui.Executions;
import org.zkoss.zk.ui.event.Event;
import org.zkoss.zk.ui.event.EventQueues;
import org.zkoss.zk.ui.select.SelectorComposer;
import org.zkoss.zk.ui.select.annotation.Listen;
import org.zkoss.zk.ui.select.annotation.Wire;
import org.zkoss.zul.Button;
import org.zkoss.zul.Div;
import org.zkoss.zul.Label;

import com.cts.composer.DashboardComposer;
import com.cts.inward.entity.InwardBatch;
import com.cts.inward.enums.BatchStatus;
import com.cts.inward.service.InwardChequeMICRService;
import com.cts.inward.service.InwardChequeServiceMICRImpl;

/**
 * Composer for the shared Batch Summary macro component.
 *
 * Reads two attributes from the macro component wrapper:
 *   batchDbId — set by parent composer (MakerBatchDetailComposer or TV1BatchDetailComposer)
 *   role      — set statically in the parent ZUL ("MAKER" or "CHECKER")
 *
 * MAKER behaviour:
 *   - lblSecondaryLabel = "MICR Errors"
 *   - lblSecondaryCount = cheques where ChequeStatus = Repair
 *   - divCbsAction visible, button enabled when MICR errors = 0
 *
 * CHECKER behaviour:
 *   - lblSecondaryLabel = "TV1 Cheques"
 *   - lblSecondaryCount = cheques where sendTo = TV_1
 *   - divCbsAction hidden always
 */
public class BatchSummaryComposer extends SelectorComposer<Component> {

    private static final long serialVersionUID = 1L;

    // ── Role Constants ────────────────────────────────────────────────────────
    private static final String ROLE_MAKER = "MAKER";
    private static final String ROLE_TV1   = "TV1";
    private static final String ROLE_TV2   = "TV2";

    // ── Service ──────────────────────────────────────────────────────────────
    private final InwardChequeMICRService inwardChequeService
            = new InwardChequeServiceMICRImpl();

    // ── Wired Components ──────────────────────────────────────────────────────
    @Wire Label  lblBatchId;
    @Wire Label  lblUploadTime;
    @Wire Label  lblTotalCount;
    @Wire Label  lblSecondaryLabel;   // "MICR Errors" or "TV1 Cheques"
    @Wire Label  lblSecondaryCount;   // count value for the above label
    @Wire Div    divCbsAction;        // hidden entirely for CHECKER role
    @Wire Button btnSendToCbs;

    // ── State ─────────────────────────────────────────────────────────────────
    private Long   currentBatchId = null;
    private String currentRole    = ROLE_MAKER;  // safe default
    
 // Used to display the batch's createdAt timestamp as the Upload Time
    private static final DateTimeFormatter UPLOAD_TIME_FMT =
            DateTimeFormatter.ofPattern("dd-MM-yyyy hh:mm a");

    // ─────────────────────────────────────────────────────────────────────────
    // Lifecycle
    // ─────────────────────────────────────────────────────────────────────────

    @Override
    public void doAfterCompose(Component comp) throws Exception {
        super.doAfterCompose(comp);

        // ── Read batchDbId ────────────────────────────────────────────────────
        Object batchAttr = comp.getSpaceOwner().getAttribute("batchDbId");

        if (batchAttr instanceof Long) {
            currentBatchId = (Long) batchAttr;
        } else if (batchAttr instanceof String) {
            try {
                currentBatchId = Long.parseLong((String) batchAttr);
            } catch (NumberFormatException e) {
                currentBatchId = null;
            }
        }

     // ── Read role from session ────────────────────────────────────────────
        Object roleAttr = Executions.getCurrent().getDesktop().getAttribute("userRole");
        System.out.println(
        	    "BatchSummaryComposer Desktop Role = "
        	    + comp.getDesktop().getAttribute("userRole"));
        if (roleAttr instanceof String && !((String) roleAttr).isEmpty()) {
            currentRole = ((String) roleAttr).toUpperCase().trim();
        }

        System.out.println("BatchSummaryComposer: batchDbId=" + currentBatchId
                + "  role=" + currentRole);

        // ── Apply role layout once ────────────────────────────────────────────
        applyRoleLayout();

        // ── Load data if batch id is ready ────────────────────────────────────
        if (currentBatchId != null && currentBatchId > 0) {
            loadBatchSummary();
        }

        // ── Listen: batch selection change from dashboard list ─────────────────
        EventQueues.lookup("batchContext", EventQueues.DESKTOP, true)
                .subscribe((Event event) -> {
                    if (event.getData() instanceof Long) {
                        Long incoming = (Long) event.getData();
                        if (incoming != null && incoming > 0) {
                            currentBatchId = incoming;
                            loadBatchSummary();
                        }
                    }
                });

        // ── Listen: cheque status updated — refresh counts ─────────────────────
        EventQueues.lookup("chequeStatusUpdated", EventQueues.DESKTOP, true)
                .subscribe((Event event) -> loadBatchSummary());
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Role Layout — runs once on load
    // ─────────────────────────────────────────────────────────────────────────

    private void applyRoleLayout() {

        if (ROLE_TV1.equals(currentRole)
                || ROLE_TV2.equals(currentRole)) {

            lblSecondaryLabel.setValue(
                    ROLE_TV1.equals(currentRole)
                    ? "TV1 Cheques"
                    : "TV2 Cheques");

            divCbsAction.setVisible(false);

        } else {

            lblSecondaryLabel.setValue("MICR Errors");
            divCbsAction.setVisible(true);
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Load Batch Summary
    // ─────────────────────────────────────────────────────────────────────────

    private void loadBatchSummary() {
        if (currentBatchId == null || currentBatchId <= 0) return;

        long totalCount     = inwardChequeService.getTotalChequeCount(currentBatchId,currentRole);
        long secondaryCount = fetchSecondaryCount();
        
        InwardBatch batch = inwardChequeService.getBatchById(currentBatchId);

        lblBatchId.setValue("Batch-" + currentBatchId);
        lblUploadTime.setValue(batch != null && batch.getCreatedAt() != null
                ? batch.getCreatedAt().format(UPLOAD_TIME_FMT) : "--:-- --");
        lblTotalCount.setValue(String.valueOf(totalCount));
        lblSecondaryCount.setValue(String.valueOf(secondaryCount));

        // CBS button evaluation only makes sense for MAKER
        if (ROLE_MAKER.equals(currentRole)) {
            evaluateCbsButton();
        }
    }

    /**
     * Returns count based on role:
     *   MAKER   → MICR error count  (ChequeStatus = Repair)
     *   CHECKER → TV1 cheque count  (sendTo = TV_1)
     */
    private long fetchSecondaryCount() {

        if (ROLE_TV1.equals(currentRole)
                || ROLE_TV2.equals(currentRole)) {

            return inwardChequeService
                    .getChequeCountByRole(currentBatchId, currentRole);
        }

        return inwardChequeService.getMicrErrorCount(currentBatchId);
    }

    // ─────────────────────────────────────────────────────────────────────────
    // CBS Button State — MAKER only
    // ─────────────────────────────────────────────────────────────────────────

    private void evaluateCbsButton() {

        BatchStatus status =
                inwardChequeService.getBatchStatus(currentBatchId);

        long nonNormalCount =
                inwardChequeService.getNonNormalChequeCount(currentBatchId);

        boolean allNormal = (nonNormalCount == 0);

        // Once forwarded to checker or CBS,
        // Maker must not be able to resend it.
        if (BatchStatus.PendingAtChecker.equals(status)
                || BatchStatus.Cleared.equals(status)) {

            btnSendToCbs.setDisabled(true);
            btnSendToCbs.setSclass(
                    "btn-cbs-action btn-cbs-disabled");

            btnSendToCbs.setLabel(
                    BatchStatus.PendingAtChecker.equals(status)
                            ? "Forwarded to Checker"
                            : "Already Sent to CBS");

            return;
        }

        btnSendToCbs.setDisabled(!allNormal);

        btnSendToCbs.setSclass(
                allNormal
                        ? "btn-cbs-action btn-cbs-enabled"
                        : "btn-cbs-action btn-cbs-disabled");

        btnSendToCbs.setLabel("Send to CBS Validation");
    }
    
    // ─────────────────────────────────────────────────────────────────────────
    // CBS Button Click — MAKER only
    // ─────────────────────────────────────────────────────────────────────────

    @Listen("onClick = #btnSendToCbs")
    public void onSendToCbs() {
        if (currentBatchId == null || currentBatchId <= 0) return;

        inwardChequeService.updateBatchStatus(
                currentBatchId,
                BatchStatus.Cleared);

        // Lock the button immediately in this view too, in case the
        // Maker doesn't navigate away right after clicking.
        evaluateCbsButton();
        
        Map<String, Object> args = new HashMap<>();
        args.put("cbsBatchDbId", currentBatchId);

        DashboardComposer.getInstance()
                         .loadPage("/zul/inward/cbsValidation.zul", args);
    }
}