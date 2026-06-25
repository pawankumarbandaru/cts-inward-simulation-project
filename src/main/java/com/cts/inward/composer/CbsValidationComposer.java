package com.cts.inward.composer;
import java.util.List;

import org.zkoss.zk.ui.Component;
import org.zkoss.zk.ui.Executions;
import org.zkoss.zk.ui.event.Event;
import org.zkoss.zk.ui.event.EventQueues;
import org.zkoss.zk.ui.select.SelectorComposer;
import org.zkoss.zk.ui.select.annotation.Listen;
import org.zkoss.zk.ui.select.annotation.Wire;
import org.zkoss.zul.Button;
import org.zkoss.zul.Label;
import org.zkoss.zul.Messagebox;

import com.cts.inward.dao.InwardBatchDao;
import com.cts.inward.dao.InwardBatchDaoImpl;
import com.cts.inward.entity.InwardBatch;
import com.cts.inward.entity.InwardCheque;
import com.cts.inward.enums.BatchStatus;
import com.cts.inward.service.InwardChequeMICRService;
import com.cts.inward.service.InwardChequeServiceMICRImpl;

/**
 * CbsValidationComposer
 * ──────────────────────
 * Composer for cbsValidation.zul — the CBS Validation page.
 *
 * Responsibilities:
 *   1. Resolve batchDbId and publish to "batchContext" EventQueue
 *   2. Set page subtitle label
 *   3. Listen for "cbsValidationComplete" event from CbsChequeListComposer
 *      → receives long[] {validCount, invalidCount}
 *      → enables/shows the correct button(s) based on counts
 *   4. Handle "Return to RRF" button click:
 *      → loads all INVALID cheques for this batch
 *      → sets decision = REJECTED on each and saves to DB
 *      → publishes "removeInvalidCheques" so CbsChequeListComposer
 *        removes those rows from the in-memory list
 *      → re-evaluates button state (if no invalid remain → show Forward)
 *   5. Handle "Forward to Tv1 and Tv2" button click
 *
 * Button rules:
 *   ┌─────────────────────┬──────────────────────────────────────────┐
 *   │ Button              │ Active when                              │
 *   ├─────────────────────┼──────────────────────────────────────────┤
 *   │ Return to RRF       │ invalidCount > 0                         │
 *   │ Forward to Tv1 Tv2  │ invalidCount == 0  (all valid)           │
 *   └─────────────────────┴──────────────────────────────────────────┘
 *   Both buttons are hidden (visible=false) until validation completes.
 */
public class CbsValidationComposer extends SelectorComposer<Component> {

    private static final long serialVersionUID = 1L;

    // ── Services / DAOs ──────────────────────────────────────────────────
    private final InwardChequeMICRService chequeService = new InwardChequeServiceMICRImpl();
    private final InwardBatchDao          batchDao      = new InwardBatchDaoImpl();

    // ── Wired UI components (only what lives in cbsValidation.zul itself) ─
    @Wire Label  lblCbsPageSubtitle;
    @Wire Button btnReturnToRrf;
    @Wire Button btnForwardTv1Tv2;

    // ── State ─────────────────────────────────────────────────────────────
    private Long currentBatchId = null;

    // ── Lifecycle ─────────────────────────────────────────────────────────

    @Override
    public void doAfterCompose(Component comp) throws Exception {
        super.doAfterCompose(comp);

        String role = (String) Executions.getCurrent()
                .getDesktop()
                .getAttribute("role");
        
        // ── 1. Resolve batchDbId ─────────────────────────────────────────
        Object batchDbIdArg = Executions.getCurrent().getDesktop()
                                        .getAttribute("cbsBatchDbId");
        if (batchDbIdArg == null) {
            batchDbIdArg = Executions.getCurrent().getAttribute("cbsBatchDbId");
        }
        if (batchDbIdArg == null) {
            batchDbIdArg = Executions.getCurrent().getParameter("cbsBatchDbId");
        }

        if (batchDbIdArg instanceof Long) {
            currentBatchId = (Long) batchDbIdArg;
        } else if (batchDbIdArg instanceof String) {
            try { currentBatchId = Long.parseLong((String) batchDbIdArg); }
            catch (NumberFormatException e) { currentBatchId = null; }
        }

        if (currentBatchId == null || currentBatchId <= 0) {
            currentBatchId = resolveLatestBatchId();
        }

        Executions.getCurrent().getDesktop().removeAttribute("cbsBatchDbId");

        System.out.println("CbsValidationComposer: batchDbId = " + currentBatchId);

        // ── 2. Batch summary macro ────────────────────────────────────────
        try {
            comp.getFellow("cbsBatchSummaryMacro")
                .setAttribute("batchDbId", currentBatchId);
        } catch (Exception e) {
            System.err.println("CbsValidationComposer: cbsBatchSummaryMacro not found");
        }

        // ── 3. Page subtitle ──────────────────────────────────────────────
        try {
            long total = chequeService.getTotalChequeCount(currentBatchId,role);
            lblCbsPageSubtitle.setValue("CBS Validation " + currentBatchId + " · " + total + " cheques");
        } catch (Exception e) {
            lblCbsPageSubtitle.setValue("CBS Validation " + currentBatchId);
        }

        // ── 4. Both buttons hidden until validation completes ─────────────
        btnReturnToRrf.setVisible(false);
        btnForwardTv1Tv2.setVisible(false);

        // ── 5. Publish batchDbId so macro composers start their work ──────
        EventQueues.lookup("batchContext", EventQueues.DESKTOP, true)
                   .publish(new Event("onBatchResolved", null, currentBatchId));

        // ── 6. Listen for cbsValidationComplete ───────────────────────────
        // CbsChequeListComposer publishes this when all cheques are processed.
        // The event data is long[] { validCount, invalidCount }.
        EventQueues.lookup("cbsValidationComplete", EventQueues.DESKTOP, true)
                   .subscribe((Event event) -> {
                       long[] summary = (long[]) event.getData();
                       updateButtonState(summary[0], summary[1]);
                   });
    }

    // ── Button state management ───────────────────────────────────────────

    /**
     * Decides which buttons to show and enable based on validation counts.
     *
     * Rule:
     *   invalid > 0  → show "Return to RRF"  (enabled), hide Forward
     *   invalid == 0 → show "Forward to Tv1 Tv2" (enabled), hide Return to RRF
     *
     * Both buttons are always shown after validation — only one is active.
     */
    private void updateButtonState(long validCount, long invalidCount) {
        System.out.println("CbsValidationComposer: updateButtonState "
                + "valid=" + validCount + " invalid=" + invalidCount);

        if (invalidCount > 0) {
            // Some cheques are invalid → only Return to RRF should be active
            btnReturnToRrf.setVisible(true);
            btnReturnToRrf.setDisabled(false);

            btnForwardTv1Tv2.setVisible(true);
            btnForwardTv1Tv2.setDisabled(true);   // visible but greyed out

        } else {
            // All cheques are valid → only Forward button active
            btnReturnToRrf.setVisible(true);
            btnReturnToRrf.setDisabled(true);      // visible but greyed out

            btnForwardTv1Tv2.setVisible(true);
            btnForwardTv1Tv2.setDisabled(false);
        }
    }

    // ── Return to RRF button ──────────────────────────────────────────────

    @Listen("onClick = #btnReturnToRrf")
    public void onReturnToRrf() {
        Messagebox.show(
            "Mark all INVALID cheques as REJECTED and remove them from this list?",
            "Confirm Return to RRF",
            Messagebox.YES | Messagebox.NO,
            Messagebox.QUESTION,
            (Event e) -> {
                if (Messagebox.ON_YES.equals(e.getName())) {
                    processReturnToRrf();
                }
            }
        );
    }

    /**
     * Loads all INVALID cheques for this batch from the DB.
     * Sets decision = REJECTED on each one and saves.
     * Then fires "removeInvalidCheques" so CbsChequeListComposer
     * removes those rows from its in-memory list and refreshes the view.
     * Finally re-evaluates button state — since all invalid are now gone,
     * Forward button should become active.
     */
    private void processReturnToRrf() {
        if (currentBatchId == null) return;

        try {
            // First count how many invalid cheques exist — for the success message
            List<InwardCheque> invalidCheques =
                    chequeService.getInvalidChequesByBatchId(currentBatchId);

            if (invalidCheques == null || invalidCheques.isEmpty()) {
                Messagebox.show("No invalid cheques found.",
                        "Return to RRF", Messagebox.OK, Messagebox.INFORMATION);
                return;
            }

            int count = invalidCheques.size();

            // Use native SQL bulk UPDATE to set decision = REJECTED.
            // This avoids the PostgreSQL custom ENUM cast issue that occurs
            // when Hibernate tries to save via session.merge() with
            // @Enumerated(EnumType.STRING) on a custom-type column.
            chequeService.markInvalidChequesAsRejected(currentBatchId);

            System.out.println("CbsValidationComposer: marked "
                    + count + " cheques as REJECTED for batchId=" + currentBatchId);

            // Tell CbsChequeListComposer to remove INVALID rows from its list
            EventQueues.lookup("removeInvalidCheques", EventQueues.DESKTOP, true)
                       .publish(new Event("onRemoveInvalidCheques", null, null));

            // All invalid removed → Forward button becomes active
            updateButtonState(1L, 0L);

            Messagebox.show(
                count + " cheque(s) marked as REJECTED and removed from list.",
                "Return to RRF",
                Messagebox.OK,
                Messagebox.INFORMATION
            );

        } catch (Exception e) {
            System.err.println("CbsValidationComposer: Return to RRF failed: "
                    + e.getMessage());
            e.printStackTrace();
            Messagebox.show("Error processing Return to RRF: " + e.getMessage(),
                    "Error", Messagebox.OK, Messagebox.ERROR);
        }
    }

    // ── Forward to Tv1 and Tv2 button ────────────────────────────────────

    /**
     * Threshold amount used to route cheques between TV1 and TV2.
     *   amount <= THRESHOLD_AMOUNT  → TV_1
     *   amount >  THRESHOLD_AMOUNT  → TV_2
     *
     * TODO: move to application.properties if this needs to be configurable
     * without a rebuild.
     */
    private static final java.math.BigDecimal THRESHOLD_AMOUNT = new java.math.BigDecimal("100000");

    @Listen("onClick = #btnForwardTv1Tv2")
    public void onForwardTv1Tv2() {
        if (currentBatchId == null) return;

        try {
            chequeService.updateBatchStatus(currentBatchId, BatchStatus.PendingAtChecker);

            long[] counts = chequeService.forwardToTvQueuesByThreshold(currentBatchId, THRESHOLD_AMOUNT);
            long tv1Count = counts[0];
            long tv2Count = counts[1];
            System.out.println("CbsValidationComposer: forwarded batchId=" + currentBatchId
                    + " to TV1/TV2 with threshold=" + THRESHOLD_AMOUNT
                    + " (TV1=" + tv1Count + ", TV2=" + tv2Count + ")");

            // Disable both buttons — this batch has now been forwarded
            btnReturnToRrf.setDisabled(true);
            btnForwardTv1Tv2.setDisabled(true);

            Messagebox.show(
                tv1Count + " cheque(s) sent to TV1 and " + tv2Count + " cheque(s) sent to TV2.",
                "Forward to Tv1 and Tv2",
                Messagebox.OK,
                Messagebox.INFORMATION
            );

        } catch (Exception e) {
            System.err.println("CbsValidationComposer: Forward to Tv1/Tv2 failed: "
                    + e.getMessage());
            e.printStackTrace();
            Messagebox.show("Error forwarding cheques: " + e.getMessage(),
                    "Error", Messagebox.OK, Messagebox.ERROR);
        }
    }

    // ── Helpers ───────────────────────────────────────────────────────────

    private Long resolveLatestBatchId() {
        try {
            InwardBatch latest = batchDao.findLatest();
            if (latest != null) return latest.getId();
        } catch (Exception e) {
            System.err.println("CbsValidationComposer: could not load latest batch: "
                    + e.getMessage());
        }
        return null;
    }
}