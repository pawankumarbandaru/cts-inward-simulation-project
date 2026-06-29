package com.cts.inward.composer;

/**
 * File    : TV2_ReviewWorkspaceComposer.java
 * Package : com.cts.inward.composer
 * Purpose : Handles the Verifier 2 (Branch Manager) review popup for high-value cheques
 *           (amount > Rs.1,00,000). The verifier can Approve, Reject, or Return the
 *           cheque to the Maker with remarks.
 *           Opened as an overlapped Window by TV2_ResubmittedQueueComposer, which passes
 *           itself and the modal Window via createComponents() args.
 * Author  : Ramana
 * Date    : 24-06-2025
 */

import java.util.Map;

import org.zkoss.zk.ui.Component;
import org.zkoss.zk.ui.Executions;
import org.zkoss.zk.ui.select.SelectorComposer;
import org.zkoss.zk.ui.select.annotation.Listen;
import org.zkoss.zk.ui.select.annotation.Wire;
import org.zkoss.zul.Button;
import org.zkoss.zul.Combobox;
import org.zkoss.zul.Comboitem;
import org.zkoss.zul.Datebox;
import org.zkoss.zul.Div;
import org.zkoss.zul.Label;
import org.zkoss.zul.Messagebox;
import org.zkoss.zul.Textbox;
import org.zkoss.zul.Window;

import com.cts.component.MakerChequeImageLoader;
import com.cts.component.MakerFormHelper;
import com.cts.inward.dto.V2LoadResult;
import com.cts.inward.entity.InwardCheque;
import com.cts.inward.service.BatchProcessingService;
import com.cts.inward.service.BatchProcessingServiceImpl;
import com.cts.inward.service.TV2_ResubmittedChequeService;
import com.cts.inward.service.TV2_ResubmittedChequeServiceImpl;
import com.cts.util.CtsUiBridge;

public class TV2_ReviewWorkspaceComposer extends SelectorComposer<Component> {

    private final TV2_ResubmittedChequeService v2Service    = new TV2_ResubmittedChequeServiceImpl();
    private final BatchProcessingService batchService= new BatchProcessingServiceImpl();

    private InwardCheque               currentCheque;
    private MakerFormHelper       formHelper;
    private MakerChequeImageLoader imageLoader;

    // Received from queue composer via createComponents() args
    private TV2_ResubmittedQueueComposer queueComposer;
    private Window                                  modalWindow;

    // ── Mode bar & nav ─────────────────────────────────────────────────────
    @Wire("#v2-review-chq-pill")    private Label   chequePill;
    @Wire("#v2-btn-back-to-queue")  private Button  btnBack;

    // ── Correction summary labels ──────────────────────────────────────────
    @Wire("#v2-original-remark")    private Label   originalRemark;
    @Wire("#v2-maker-remark")       private Label   makerRemark;

    // ── Cheque image + audit trail ─────────────────────────────────────────
    // IDs come from chequeImagePanel.zul macro — they do NOT have a v2- prefix
    @Wire("#chq-real-front")         private org.zkoss.zul.Image realFrontImg;
    @Wire("#chq-real-back")          private org.zkoss.zul.Image realBackImg;
    @Wire("#chq-img-placeholder")    private Div     imgPlaceholder;
    @Wire("#chq-real-wrapper")       private Div     realImgWrapper;
    @Wire("#chq-real-back-wrapper")  private Div     realBackWrapper;
    @Wire("#chq-back-placeholder")   private Div     backPlaceholder;
    @Wire("#v2-audit-trail-rows")    private Div     auditTrailRows;

    // ── Show Front / Show Back toggle (chequeImagePanel.zul macro) ──────────
    // FIX: these were missing entirely — buttons rendered but nothing was
    // wired to them, so clicking "Show Back" did nothing. Added the same
    // way M_RepairWorkspaceComposer already does it for the maker page.
    @Wire("#chq-btn-front")          private Button  btnShowFront;
    @Wire("#chq-btn-back")           private Button  btnShowBack;
    @Wire("#chq-front-panel")        private Div     frontPanel;
    @Wire("#chq-back-panel")         private Div     backPanel;
    @Wire("#chq-side-label")         private Label   sideLabel;

    // ── MICR fields (shared IDs with maker macro component) ────────────────
    @Wire("#rcb-f-chqno")    private Textbox fieldChequeNo;
    @Wire("#rcb-f-city")     private Textbox fieldCity;
    @Wire("#rcb-f-bank")     private Textbox fieldBank;
    @Wire("#rcb-f-branch")   private Textbox fieldBranch;
    @Wire("#rcb-f-tc")       private Textbox fieldTc;

    // ── Cheque detail fields (shared IDs with maker macro component) ────────
    @Wire("#rcb-f-amount")         private Textbox  fieldAmount;
    @Wire("#rcb-f-date")           private Datebox  fieldDate;
    @Wire("#rcb-f-acc")            private Textbox  fieldAcc;
    @Wire("#rcb-f-payee")          private Textbox  fieldPayee;
    @Wire("#rcb-f-remarks-select") private Combobox fieldRemarksSelect;
    @Wire("#rcb-f-remarks")        private Textbox  fieldRemarks;
    @Wire("#rcb-f-words")          private Label    fieldWords;

    // ── CBS result area ────────────────────────────────────────────────────
    // ── MICR auto-display field (read-only: City + Bank + Branch) ──────────
    @Wire("#rcb-f-micr-auto")        private Textbox fieldMicrAuto;

    // ── Action section ─────────────────────────────────────────────────────
    @Wire("#v2-action-btn-row")        private Div      actionBtnRow;
    @Wire("#v2-reason-box")            private Div      reasonBox;
    @Wire("#v2-action-remarks")        private Combobox actionRemarks;
    @Wire("#v2-action-remarks-other")  private Textbox  actionRemarksOther;
    @Wire("#v2-remarks-error")         private Label    remarksError;
    @Wire("#v2-btn-approve")           private Button   btnApprove;
    @Wire("#v2-btn-return")            private Button   btnReturn;
    @Wire("#v2-btn-reject")            private Button   btnReject;
    @Wire("#v2-btn-confirm-action")    private Button   btnConfirmAction;
    @Wire("#v2-btn-cancel-action")     private Button   btnCancelAction;

    /** Which action ("reject" / "return") the reason box is currently open for. */
    private String pendingAction;

    /** Reasons shown ONLY when "Return to Maker" is clicked. */
    private static final String[] RETURN_REASONS = {
        "MICR number needs to be corrected",
        "Account number does not match records",
        "Cheque date is incorrect or missing",
        "Amount entered does not match physical cheque",
        "Payee name is incomplete or incorrect",
        "Transaction code mismatch",
        "Multiple fields require correction — see details"
    };

    /** Reasons shown ONLY when "Reject" is clicked. */
    private static final String[] REJECT_REASONS = {
        "Cheque is damaged / illegible",
        "Duplicate cheque detected",
        "Instrument expired (stale cheque)",
        "Signature mismatch — refer to drawer",
        "Insufficient funds confirmed via CBS",
        "High-value cheque — additional verification failed"
    };

    // ── Lifecycle ──────────────────────────────────────────────────────────

    @Override
    public void doAfterCompose(Component comp) throws Exception {
        super.doAfterCompose(comp);

        // Args are only available during doAfterCompose — store as fields
        @SuppressWarnings("unchecked")
        Map<String, Object> args = (Map<String, Object>) Executions.getCurrent().getArg();

        if (args != null) {
            Object qc = args.get("queueComposer");
            if (qc instanceof TV2_ResubmittedQueueComposer) {
                this.queueComposer = (TV2_ResubmittedQueueComposer) qc;
            }

            Object mw = args.get("modalWindow");
            if (mw instanceof Window) {
                this.modalWindow = (Window) mw;
            }
        }

        imageLoader  = new MakerChequeImageLoader(
            realFrontImg, realBackImg, realImgWrapper, imgPlaceholder,
            realBackWrapper, backPlaceholder);

        // MakerFormHelper constructed with nulls for image-overlay labels —
        // V2 reviewer page does not have those. Used only for setCheckerMode()
        // and populateCheckerFields().
        formHelper = new MakerFormHelper(
            /* chequePill      */ chequePill,
            /* warnTitle       */ null,
            /* warnRolePill    */ null,
            /* warnReasonShort */ null,
            /* warnDetail      */ null,
            /* warnCbsReason   */ null,  // V2 review page has no CBS reason warn label
            /* imgBank         */ null,
            /* imgDate         */ null,
            /* imgPayee        */ null,
            /* imgWords        */ null,
            /* imgAmt          */ null,
            /* imgMicr1        */ null,
            /* imgMicr2        */ null,
            /* micrStripLive   */ null,
            /* imgBackAcc      */ null,
            fieldChequeNo, fieldCity, fieldBank, fieldBranch,
            fieldTc, fieldAmount, fieldWords,
            fieldDate, fieldAcc, fieldPayee, fieldRemarks
        );

        loadChequeFromSession();
    }

    // ── Load Cheque ────────────────────────────────────────────────────────

    /**
     * Reads "v2SelectedChequeNo" from session — set by the V2 queue composer.
     * Closes the modal with a plain message if the cheque cannot be loaded.
     */
    private void loadChequeFromSession() {
        String chequeNo = (String) Executions.getCurrent()
            .getSession().getAttribute("v2SelectedChequeNo");

        if (chequeNo == null || chequeNo.isBlank()) {
            closeModal("No cheque selected. Please click Review from the queue.");
            return;
        }

        // Clear session key — prevents stale re-open on browser back
        Executions.getCurrent().getSession().removeAttribute("v2SelectedChequeNo");

        V2LoadResult result = v2Service.getChequeForV2Review(chequeNo);

        if (!result.isOk()) {
            closeModal(result.getMessage());
            return;
        }

        currentCheque = result.getCheque();

        populatePage();
        imageLoader.load(currentCheque);
    }

    // ── Populate Page ──────────────────────────────────────────────────────

    private void populatePage() {

        // Disable fields before populating — avoids ZK constraint violations
        formHelper.setCheckerMode();
        if (fieldRemarksSelect != null) fieldRemarksSelect.setDisabled(true);

        // Correction summary labels
        // FIX: checker stores the return reason in checker_sendback_reason (setSendbackReason),
        // NOT checker_refer_reason (setReferReason). Use getSendbackReason() here.
        originalRemark.setValue(
            currentCheque.getSendbackReason() != null && !currentCheque.getSendbackReason().isBlank()
            ? currentCheque.getSendbackReason() : "No remark recorded.");

        makerRemark.setValue(
            currentCheque.getMakerCorrectionRemark() != null
            ? currentCheque.getMakerCorrectionRemark() : "No correction remarks from maker.");

        chequePill.setValue("Cheque " + currentCheque.getChequeNo());

        // Populate fields and highlight any maker-edited ones
        formHelper.populateCheckerFields(currentCheque);

        // Auto-fill MICR NO. (read-only display) — City + Bank + Branch
        populateMicrAuto();

        // Highlight edited fields directly via server-side sclass (no client JS)
        // Banner removed — per-field highlight + "Edited" badge already shows this.
        if (currentCheque.isEditedByMaker()) {
            String fields = currentCheque.getEditedFields() != null
                ? currentCheque.getEditedFields() : "";
            CtsUiBridge.applyEditedFieldHighlight(
                fields,
                fieldChequeNo, fieldCity, fieldBank, fieldBranch, fieldTc,
                fieldAmount, fieldDate, fieldAcc, fieldPayee
            );
        }
    }

    // ── MICR Auto-Fill ────────────────────────────────────────────────────
    //
    // FIX: MICR NO. (AUTO) was always blank on this review page — never
    // wired or computed here at all. Same formula already used on the
    // maker side: MICR = City + Bank + Branch (concatenated). This page
    // is read-only, so it only needs to be computed ONCE after the
    // fields load — no live listeners needed.

    private void populateMicrAuto() {
        if (fieldMicrAuto == null) return;

        String city   = fieldCity.getValue()   == null ? "" : fieldCity.getValue().trim();
        String bank   = fieldBank.getValue()   == null ? "" : fieldBank.getValue().trim();
        String branch = fieldBranch.getValue() == null ? "" : fieldBranch.getValue().trim();

        fieldMicrAuto.setValue(city + bank + branch);
    }

    // ── Navigation ─────────────────────────────────────────────────────────

    @Listen("onClick = #v2-btn-back-to-queue")
    public void onBackToQueue() { closeModal(); }

    // ── Show Front / Show Back toggle ────────────────────────────────────
    // FIX: previously not wired at all in this composer — same fix already
    // applied in M_RepairWorkspaceComposer (maker side) and
    // TV1_ReviewWorkspaceComposer (checker side).

    @Listen("onClick = #chq-btn-front")
    public void onShowFront() {
        if (frontPanel != null) frontPanel.setVisible(true);
        if (backPanel  != null) backPanel.setVisible(false);
        if (sideLabel  != null) sideLabel.setValue("■ FRONT IMAGE");

        if (btnShowFront != null)
            btnShowFront.setStyle(
                "height:24px;padding:0 10px;background:#1a3a6e;color:#fff;" +
                "border:none;border-radius:4px;font-size:11px;font-weight:600;" +
                "cursor:pointer;font-family:inherit;");
        if (btnShowBack != null)
            btnShowBack.setStyle(
                "height:24px;padding:0 10px;background:#E8EDF3;color:#4B5563;" +
                "border:1px solid #C0C8D4;border-radius:4px;font-size:11px;" +
                "font-weight:600;cursor:pointer;font-family:inherit;");
    }

    @Listen("onClick = #chq-btn-back")
    public void onShowBack() {
        if (frontPanel != null) frontPanel.setVisible(false);
        if (backPanel  != null) backPanel.setVisible(true);
        if (sideLabel  != null) sideLabel.setValue("■ BACK IMAGE");

        if (btnShowBack != null)
            btnShowBack.setStyle(
                "height:24px;padding:0 10px;background:#1a3a6e;color:#fff;" +
                "border:none;border-radius:4px;font-size:11px;font-weight:600;" +
                "cursor:pointer;font-family:inherit;");
        if (btnShowFront != null)
            btnShowFront.setStyle(
                "height:24px;padding:0 10px;background:#E8EDF3;color:#4B5563;" +
                "border:1px solid #C0C8D4;border-radius:4px;font-size:11px;" +
                "font-weight:600;cursor:pointer;font-family:inherit;");
    }

    // ── Action Listeners ───────────────────────────────────────────────────

    /**
     * Approve needs no reason — it just runs. (Brought in line with the
     * Checker page: Approve no longer forces a remark to be picked.)
     */
    @Listen("onClick = #v2-btn-approve")
    public void onApprove() {
        if (currentCheque == null) { showError("No cheque loaded."); return; }

        disableActionButtons();

        try {
            String chequeNo = currentCheque.getChequeNo();

            v2Service.approveCheque(chequeNo, getCurrentUserId(), "");
            
            // Get batch id from the current cheque
            Long batchId = currentCheque.getBatch().getId();
            // Recalculate batch status
            batchService.updateBatchStatusIfCompleted(batchId);

            closeModal("Cheque " + chequeNo + " approved by Branch Manager. Status: ACCEPTED.");

        } catch (Exception ex) {
            showError("Error approving cheque: " + ex.getMessage());
            enableActionButtons();
        }
    }

    /** Opens the reason box pre-filled with ONLY the Return-to-Maker reasons. */
    @Listen("onClick = #v2-btn-return")
    public void onReturnToMaker() {
        showReasonBox("return");
    }

    /** Opens the reason box pre-filled with ONLY the Reject reasons. */
    @Listen("onClick = #v2-btn-reject")
    public void onReject() {
        showReasonBox("reject");
    }

    /**
     * Hides the 3 action buttons and shows the reason box, with the combobox
     * cleared and re-filled with ONLY the list belonging to this action —
     * Return and Reject reasons are never shown together.
     *
     * NOTE: the "Return Reason" / "Reject Reason" title label was removed
     * from the markup per user request — only the dropdown + Confirm/Cancel
     * row remains now.
     */
    private void showReasonBox(String action) {
        pendingAction = action;
        populateReasonOptions(action);

        actionRemarksOther.setValue("");
        remarksError.setVisible(false);

        actionBtnRow.setVisible(false);
        reasonBox.setVisible(true);
    }

    /** Clears the combobox and re-adds only the reasons for this one action. */
    private void populateReasonOptions(String action) {
        actionRemarks.getChildren().clear();
        actionRemarks.setValue("");

        String[] reasons = "reject".equals(action) ? REJECT_REASONS : RETURN_REASONS;
        for (String reason : reasons) {
            actionRemarks.appendChild(new Comboitem(reason));
        }
        actionRemarks.appendChild(new Comboitem("Other"));
    }

    /** Back to the 3 action buttons — no reason picked yet, nothing submitted. */
    @Listen("onClick = #v2-btn-cancel-action")
    public void onCancelAction() {
        pendingAction = null;
        reasonBox.setVisible(false);
        actionBtnRow.setVisible(true);
        remarksError.setVisible(false);
    }

    /** Validates the chosen reason, then actually submits Return or Reject. */
    @Listen("onClick = #v2-btn-confirm-action")
    public void onConfirmAction() {
        if (currentCheque == null) { showError("No cheque loaded."); return; }
        if (pendingAction == null) { onCancelAction(); return; }

        String remarks = getResolvedRemarks();
        if (!validateRemarks(remarks)) return;

        btnConfirmAction.setDisabled(true);
        btnCancelAction.setDisabled(true);

        try {
            String chequeNo = currentCheque.getChequeNo();

            if ("reject".equals(pendingAction)) {
                v2Service.rejectCheque(chequeNo, getCurrentUserId(), remarks);
                closeModal("Cheque " + chequeNo + " rejected by Branch Manager. Status: REJECTED.");
            } else {
                v2Service.returnToMaker(chequeNo, getCurrentUserId(), remarks);
                closeModal("Cheque " + chequeNo + " returned to maker for correction.");
            }
            
            // Get batch id from the current cheque
            Long batchId = currentCheque.getBatch().getId();
            // Recalculate batch status
            batchService.updateBatchStatusIfCompleted(batchId);


        } catch (Exception ex) {
            String actionLabel = "reject".equals(pendingAction) ? "rejecting" : "returning";
            showError("Error " + actionLabel + " cheque: " + ex.getMessage());
            btnConfirmAction.setDisabled(false);
            btnCancelAction.setDisabled(false);
        }
    }

    // ── Helpers ────────────────────────────────────────────────────────────

    private boolean validateRemarks(String remarks) {
        if (remarks == null || remarks.trim().isEmpty()) {
            remarksError.setVisible(true);
            actionRemarks.setFocus(true);
            return false;
        }
        remarksError.setVisible(false);
        return true;
    }

    /**
     * Resolves the final remark text from the Combobox + "Other" textbox.
     * If the selected value is "Other", uses the free-text content instead.
     */
    private String getResolvedRemarks() {
        String selected = actionRemarks.getValue() != null
            ? actionRemarks.getValue().trim() : "";
        if ("Other".equalsIgnoreCase(selected)) {
            String other = actionRemarksOther != null
                ? actionRemarksOther.getValue().trim() : "";
            return other.isEmpty() ? selected : other;
        }
        return selected;
    }

    private void disableActionButtons() {
        btnApprove.setDisabled(true);
        btnReturn.setDisabled(true);
        btnReject.setDisabled(true);
    }

    private void enableActionButtons() {
        btnApprove.setDisabled(false);
        btnReturn.setDisabled(false);
        btnReject.setDisabled(false);
    }

    private String getCurrentUserId() {
        Object user = Executions.getCurrent()
            .getSession().getAttribute("loggedInUser");
        return user != null ? user.toString() : "verifier2";
    }

    /**
     * Closes the popup and triggers queue refresh.
     *
     * ORDER MATTERS HERE:
     *   1. Detach the popup window FIRST.
     *   2. THEN refresh the queue grid via queueComposer.onModalClosed().
     *
     * WHY: onModalClosed() rebuilds the entire queue grid AND the Batch
     * Number dropdown — a lot of brand-new components in one shot. Doing
     * that BEFORE detaching the popup meant one single response had to
     * both tear down this popup's whole component tree AND create a large
     * batch of new ones elsewhere on the page at the same time. That
     * combination is what caused "Failed to mount: Unknown stub" when
     * closing the popup. Detaching the popup first keeps that step clean
     * and isolated, so the queue refresh after it runs against an
     * otherwise untouched page.
     */
    private void closeModal(String message) {

        CtsUiBridge.removeBackdrop("v2-modal-backdrop");

        boolean windowDetached = false;

        if (this.modalWindow != null) {
            this.modalWindow.detach();
            windowDetached = true;
        } else {
            Component parent = getSelf().getParent();
            while (parent != null) {
                if (parent instanceof Window) {
                    parent.detach();
                    windowDetached = true;
                    break;
                }
                parent = parent.getParent();
            }
        }

        if (this.queueComposer != null) {
            this.queueComposer.onModalClosed(message);
        } else if (message != null) {
            Executions.getCurrent().getSession()
                .setAttribute("v2SuccessMessage", message);
        }

        // Standalone full-page mode (no popup window found at all) — redirect back
        if (!windowDetached) {
            Executions.sendRedirect("/zul/inward/TV2_ResubmittedQueue.zul");
        }
    }

    private void closeModal() { closeModal(null); }

    private void showError(String message) {
        Messagebox.show(message, "Error", Messagebox.OK, Messagebox.ERROR);
    }
}