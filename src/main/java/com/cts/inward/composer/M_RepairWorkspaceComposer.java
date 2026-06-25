package com.cts.inward.composer;

/**
 * File    : M_RepairWorkspaceComposer.java
 * Package : com.cts.inward.composer
 * Purpose : Handles the "Edit Cheque" repair popup opened when a Maker clicks
 *           Re-correct on a cheque returned by the Checker.
 *           Implements a two-step Save flow:
 *             Step 1 — validate fields → show Correction Remarks combobox
 *             Step 2 — pick remark → save to DB → unlock CBS Validation button
 * Author  : Ramana
 * Date    : 24-06-2025
 */

import java.math.BigDecimal;
import java.util.Map;
import java.util.Optional;

import org.zkoss.zk.ui.Component;
import org.zkoss.zk.ui.Executions;
import org.zkoss.zk.ui.select.SelectorComposer;
import org.zkoss.zk.ui.select.annotation.Listen;
import org.zkoss.zk.ui.select.annotation.Wire;
import org.zkoss.zul.Button;
import org.zkoss.zul.Combobox;
import org.zkoss.zul.Datebox;
import org.zkoss.zul.Div;
import org.zkoss.zul.Label;
import org.zkoss.zul.Messagebox;
import org.zkoss.zul.Textbox;
import org.zkoss.zul.Window;

import com.cts.component.MakerCbsValidator;
import com.cts.component.MakerChequeImageLoader;
import com.cts.component.MakerFieldValidator;
import com.cts.component.MakerFormHelper;
import com.cts.inward.entity.InwardCheque;
import com.cts.inward.service.InwardChequeService;
import com.cts.inward.service.InwardChequeServiceImpl;
import com.cts.util.CtsUiBridge;

public class M_RepairWorkspaceComposer extends SelectorComposer<Component> {

    private final InwardChequeService chequeService = new InwardChequeServiceImpl();

    private InwardCheque currentCheque;

    private MakerChequeImageLoader imageLoader;
    private MakerCbsValidator      cbsValidator;
    private MakerFieldValidator    fieldValidator;
    private MakerFormHelper        formHelper;

    // Set from args passed by M_ReturnedByCheckerComposer when it opens the popup
    private M_ReturnedByCheckerComposer queueComposer;
    private Window                      modalWindow;

    // Tracks two-step Save flow: false = first click (show remarks), true = second click (save)
    private boolean remarksShowing = false;

    // ── Wired components ────────────────────────────────────────────────────

    @Wire("#rcb-page-root")            private Div     pageRoot;
    @Wire("#rcb-repair-chq-pill")      private Label   chequePill;

    // Hidden labels used only for data binding — DO NOT REMOVE
    @Wire("#rcb-warn-title")           private Label   warnTitle;
    @Wire("#rcb-warn-role-pill")       private Label   warnRolePill;
    @Wire("#rcb-warn-reason-short")    private Label   warnReasonShort;
    @Wire("#rcb-warn-detail")          private Label   warnDetail;
    @Wire("#rcb-warn-cbs-reason")      private Label   warnCbsReason;

    // Image card overlay labels — DO NOT REMOVE
    @Wire("#rcb-img-bank")             private Label   imgBank;
    @Wire("#rcb-img-date")             private Label   imgDate;
    @Wire("#rcb-img-payee")            private Label   imgPayee;
    @Wire("#rcb-img-words")            private Label   imgWords;
    @Wire("#rcb-img-amt")              private Label   imgAmt;
    @Wire("#rcb-f-words")              private Label   fieldWords;
    @Wire("#rcb-img-micr1")            private Label   imgMicr1;
    @Wire("#rcb-img-micr2")            private Label   imgMicr2;
    @Wire("#rcb-micr-strip-live")      private Label   micrStripLive;
    @Wire("#rcb-img-back-acc")         private Label   imgBackAcc;

    // Cheque image panel (from chequeImagePanel.zul macro)
    @Wire("#chq-real-front")           private org.zkoss.zul.Image realFrontImg;
    @Wire("#chq-real-back")            private org.zkoss.zul.Image realBackImg;
    @Wire("#chq-img-placeholder")      private Div     imgPlaceholder;
    @Wire("#chq-real-wrapper")         private Div     realImgWrapper;
    @Wire("#chq-real-back-wrapper")    private Div     realBackWrapper;
    @Wire("#chq-back-placeholder")     private Div     backPlaceholder;

    // Front / Back image toggle buttons
    @Wire("#chq-btn-front")            private Button  btnShowFront;
    @Wire("#chq-btn-back")             private Button  btnShowBack;
    @Wire("#chq-front-panel")          private Div     frontPanel;
    @Wire("#chq-back-panel")           private Div     backPanel;
    @Wire("#chq-side-label")           private Label   sideLabel;

    // Editable form fields
    @Wire("#rcb-f-chqno")              private Textbox  fieldChequeNo;
    @Wire("#rcb-f-city")               private Textbox  fieldCity;
    @Wire("#rcb-f-bank")               private Textbox  fieldBank;
    @Wire("#rcb-f-branch")             private Textbox  fieldBranch;
    @Wire("#rcb-f-tc")                 private Textbox  fieldTc;
    @Wire("#rcb-f-amount")             private Textbox  fieldAmount;
    @Wire("#rcb-f-date")               private Datebox  fieldDate;
    @Wire("#rcb-f-acc")                private Textbox  fieldAcc;
    @Wire("#rcb-f-payee")              private Textbox  fieldPayee;
    @Wire("#rcb-f-micr-auto")          private Textbox  fieldMicrAuto;

    // Correction Remarks combobox and "Other" freetext
    @Wire("#rcb-f-remarks-select")     private Combobox fieldRemarks;
    @Wire("#rcb-f-remarks")            private Textbox  fieldRemarksOther;

    // Inline field error labels
    @Wire("#err-rcb-f-chqno")          private Label errChequeNo;
    @Wire("#err-rcb-f-city")           private Label errCity;
    @Wire("#err-rcb-f-bank")           private Label errBank;
    @Wire("#err-rcb-f-branch")         private Label errBranch;
    @Wire("#err-rcb-f-tc")             private Label errTc;
    @Wire("#err-rcb-f-amount")         private Label errAmount;
    @Wire("#err-rcb-f-date")           private Label errDate;
    @Wire("#err-rcb-f-acc")            private Label errAcc;
    @Wire("#err-rcb-f-payee")          private Label errPayee;
    @Wire("#err-rcb-f-remarks")        private Label errRemarks;

    // Action bar buttons and areas
    @Wire("#btn-save-cbs")             private Button btnSaveCbs;
    @Wire("#btn-cbs")                  private Button btnCbs;
    @Wire("#btn-edit-fields")          private Button btnEditFields;
    @Wire("#rcb-cbs-result-area")      private Div    cbsResultArea;
    @Wire("#mrw-editable-zone")        private Div    editableZone;

    // Wraps the Correction Remarks label + combobox — toggled as one unit
    @Wire("#mrw-remarks-wrap")         private Div    remarksWrap;

    // ── Lifecycle ────────────────────────────────────────────────────────────

    @Override
    public void doAfterCompose(Component comp) throws Exception {
        super.doAfterCompose(comp);

        readArgs();
        initHelpers();
        attachLiveListeners();
        loadChequeFromSession();
    }

    /**
     * Reads the queueComposer and modalWindow references passed from
     * M_ReturnedByCheckerComposer when it opened this popup.
     */
    private void readArgs() {
        @SuppressWarnings("unchecked")
        Map<String, Object> args = (Map<String, Object>) Executions.getCurrent().getArg();

        if (args != null) {
            Object qc = args.get("queueComposer");
            if (qc instanceof M_ReturnedByCheckerComposer) {
                this.queueComposer = (M_ReturnedByCheckerComposer) qc;
            }
            Object mw = args.get("modalWindow");
            if (mw instanceof Window) {
                this.modalWindow = (Window) mw;
            }
        }
    }

    /**
     * Creates all helper objects — image loader, field validator, and form helper.
     * Each helper receives only the wired components it needs.
     */
    private void initHelpers() {
        imageLoader = new MakerChequeImageLoader(
            realFrontImg, realBackImg, realImgWrapper, imgPlaceholder,
            realBackWrapper, backPlaceholder);

        fieldValidator = new MakerFieldValidator(
            fieldChequeNo, fieldCity, fieldBank, fieldBranch, fieldTc,
            fieldAmount, fieldDate, fieldAcc, fieldPayee,
            fieldRemarks, fieldRemarksOther,
            errChequeNo, errCity, errBank, errBranch, errTc,
            errAmount, errDate, errAcc, errPayee, errRemarks
        );

        formHelper = new MakerFormHelper(
            chequePill,
            warnTitle, warnRolePill, warnReasonShort, warnDetail, warnCbsReason,
            imgBank, imgDate, imgPayee, imgWords, imgAmt,
            imgMicr1, imgMicr2, micrStripLive, imgBackAcc,
            fieldChequeNo, fieldCity, fieldBank, fieldBranch, fieldTc,
            fieldAmount, fieldWords, fieldDate, fieldAcc, fieldPayee,
            fieldRemarksOther
        );
    }

    /**
     * Attaches live update listeners:
     * - Amount field → updates "Amount in Words" label as the maker types
     * - MICR fields  → updates the live MICR line preview as the maker types
     * - City/Bank/Branch → rebuilds the MICR AUTO number field on every change
     */
    private void attachLiveListeners() {

        // Amount in Words — live update on each keystroke
        fieldAmount.addEventListener(
            org.zkoss.zk.ui.event.Events.ON_CHANGING,
            event -> {
                org.zkoss.zk.ui.event.InputEvent ie =
                    (org.zkoss.zk.ui.event.InputEvent) event;
                formHelper.updateAmountInWordsLive(ie.getValue());
            }
        );

        // MICR line preview — updates when any of the 5 MICR fields changes
        org.zkoss.zk.ui.event.EventListener<org.zkoss.zk.ui.event.Event> micrLiveListener =
            event -> formHelper.updateMicrLinePreviewLive(
                fieldChequeNo.getValue(), fieldCity.getValue(),
                fieldBank.getValue(), fieldBranch.getValue(), fieldTc.getValue()
            );
        fieldChequeNo.addEventListener(org.zkoss.zk.ui.event.Events.ON_CHANGING, micrLiveListener);
        fieldCity.addEventListener(org.zkoss.zk.ui.event.Events.ON_CHANGING, micrLiveListener);
        fieldBank.addEventListener(org.zkoss.zk.ui.event.Events.ON_CHANGING, micrLiveListener);
        fieldBranch.addEventListener(org.zkoss.zk.ui.event.Events.ON_CHANGING, micrLiveListener);
        fieldTc.addEventListener(org.zkoss.zk.ui.event.Events.ON_CHANGING, micrLiveListener);

        // MICR AUTO field — concatenates city + bank + branch on every keystroke
        org.zkoss.zk.ui.event.EventListener<org.zkoss.zk.ui.event.Event> micrAutoListener =
            event -> {
                if (fieldMicrAuto == null) return;
                String city   = fieldCity.getValue()   == null ? "" : fieldCity.getValue().trim();
                String bank   = fieldBank.getValue()   == null ? "" : fieldBank.getValue().trim();
                String branch = fieldBranch.getValue() == null ? "" : fieldBranch.getValue().trim();
                fieldMicrAuto.setValue(city + bank + branch);
            };
        fieldCity.addEventListener(org.zkoss.zk.ui.event.Events.ON_CHANGING, micrAutoListener);
        fieldCity.addEventListener(org.zkoss.zk.ui.event.Events.ON_CHANGE,   micrAutoListener);
        fieldBank.addEventListener(org.zkoss.zk.ui.event.Events.ON_CHANGING, micrAutoListener);
        fieldBank.addEventListener(org.zkoss.zk.ui.event.Events.ON_CHANGE,   micrAutoListener);
        fieldBranch.addEventListener(org.zkoss.zk.ui.event.Events.ON_CHANGING, micrAutoListener);
        fieldBranch.addEventListener(org.zkoss.zk.ui.event.Events.ON_CHANGE,   micrAutoListener);
    }

    // ── Load cheque ──────────────────────────────────────────────────────────

    /**
     * Reads the selected cheque number from session (set by the queue composer),
     * loads the full cheque record from DB, and populates all form fields.
     * Closes the popup with an error message if the cheque is no longer actionable.
     */
    private void loadChequeFromSession() {
        String chequeNo = (String) Executions.getCurrent()
            .getSession().getAttribute("rcbSelectedChequeNo");

        if (chequeNo == null || chequeNo.isBlank()) {
            closeModal("No cheque selected. Click Re-correct from the queue.");
            return;
        }

        Executions.getCurrent().getSession().removeAttribute("rcbSelectedChequeNo");

        Optional<InwardCheque> found = chequeService.getChequeForRepair(chequeNo);

        if (found.isEmpty()) {
            closeModal("Cheque " + chequeNo + " is no longer pending. It may already be resubmitted.");
            return;
        }

        currentCheque = found.get();

        formHelper.setEditMode();
        formHelper.populatePage(currentCheque);
        imageLoader.load(currentCheque);

        // Populate MICR AUTO field on initial load
        if (fieldMicrAuto != null) {
            String city   = fieldCity   != null && fieldCity.getValue()   != null ? fieldCity.getValue().trim()   : "";
            String bank   = fieldBank   != null && fieldBank.getValue()   != null ? fieldBank.getValue().trim()   : "";
            String branch = fieldBranch != null && fieldBranch.getValue() != null ? fieldBranch.getValue().trim() : "";
            fieldMicrAuto.setValue(city + bank + branch);
        }

        // CBS starts disabled — enabled only after a successful Save
        if (btnCbs != null) {
            btnCbs.setVisible(true);
            btnCbs.setDisabled(true);
        }

        // Edit button hidden on load — appears after a successful Save
        if (btnEditFields != null) {
            btnEditFields.setVisible(false);
        }

        // Remarks section hidden until first Save click
        if (remarksWrap != null) remarksWrap.setVisible(false);

        btnSaveCbs.setDisabled(false);
        btnSaveCbs.setLabel("💾 Save");
    }

    // ── Image toggle ─────────────────────────────────────────────────────────

    /**
     * Shows the front cheque image and highlights the Front button.
     * Server-side toggle is used because ZK mangles element IDs, so
     * document.getElementById() from JS would return null.
     */
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

    /** Shows the back cheque image and highlights the Back button. */
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

    // ── Navigation ───────────────────────────────────────────────────────────

    @Listen("onClick = #btn-back-to-queue")
    public void onBackToQueue() { closeModal(null); }

    // ── Save (Step 1 and Step 2) ─────────────────────────────────────────────

    /**
     * Handles the Save button click. Uses a two-step flow:
     *
     * Click 1 (remarksShowing = false):
     *   Validates all cheque fields. If valid, shows the Correction Remarks
     *   combobox and waits for the maker to pick a reason.
     *
     * Click 2 (remarksShowing = true):
     *   Validates that a remark is selected, saves corrections to DB,
     *   then unlocks the CBS Validation and Edit buttons.
     */
    @Listen("onClick = #btn-save-cbs")
    public void onSave() {

        if (currentCheque == null) {
            showError("No cheque loaded.");
            return;
        }

        // ── First click: validate fields and reveal Correction Remarks ──────
        if (!remarksShowing) {

            if (!fieldValidator.validateAll()) {
                CtsUiBridge.scrollToTop("repair-content-area");
                return;
            }

            if (remarksWrap != null) remarksWrap.setVisible(true);
            remarksShowing = true;
            return;
        }

        // ── Second click: validate remark and save to DB ─────────────────────
        String resolvedRemark = getResolvedMakerRemark();
        if (resolvedRemark == null || resolvedRemark.trim().isEmpty()) {
            if (errRemarks != null) {
                errRemarks.setValue("Correction remark is required");
                errRemarks.setVisible(true);
            }
            return;
        }
        if (errRemarks != null) errRemarks.setVisible(false);

        BigDecimal amount;
        try {
            amount = new BigDecimal(fieldAmount.getValue().trim());
        } catch (NumberFormatException e) {
            showError("Invalid amount. Please enter digits only.");
            return;
        }

        btnSaveCbs.setDisabled(true);
        btnSaveCbs.setLabel("Saving...");

        formHelper.applyFieldsToEntity(currentCheque, amount);
        currentCheque.setMakerCorrectionRemark(resolvedRemark);

        try {
            chequeService.saveCorrections(currentCheque);
        } catch (Exception ex) {
            showError("Error saving changes: " + ex.getMessage());
            btnSaveCbs.setDisabled(false);
            btnSaveCbs.setLabel("💾 Save");
            return;
        }

        // Save successful — update action bar state
        remarksShowing = false;

        // Keep remarks visible but locked so maker can still see the selected reason
        if (remarksWrap != null) remarksWrap.setVisible(true);

        btnSaveCbs.setLabel("✔ Saved");
        btnSaveCbs.setDisabled(true);

        if (btnEditFields != null) {
            btnEditFields.setVisible(true);
            btnEditFields.setDisabled(false);
        }
        if (btnCbs != null) {
            btnCbs.setVisible(true);
            btnCbs.setDisabled(false);
        }

        // Lock fields — maker must click Edit to change anything again
        lockFormFields();
    }

 

    // ── Edit (re-open fields after Save) ─────────────────────────────────────

    /**
     * Unlocks all fields so the maker can correct and save again.
     * Restores the previously saved remark in the combobox.
     * Sets remarksShowing = true so the next Save goes straight to step 2.
     */
    @Listen("onClick = #btn-edit-fields")
    public void onEditFields() {

        unlockFormFields();

        if (editableZone != null) editableZone.setVisible(true);

        if (fieldRemarks != null && currentCheque != null) {
            String savedRemark = currentCheque.getMakerCorrectionRemark();
            String remarkText  = savedRemark != null ? savedRemark.trim() : "";

            // FIX: setValue() alone does not set selectedItem on a Combobox.
            // On the next Save click, getSelectedItem() returns null and
            // getResolvedMakerRemark() sees an empty value → "remark required" error.
            // Solution: find the matching comboitem by label and select it explicitly.
            boolean matched = false;
            if (!remarkText.isEmpty()) {
                for (Object obj : fieldRemarks.getItems()) {
                    if (obj instanceof org.zkoss.zul.Comboitem) {
                        org.zkoss.zul.Comboitem item = (org.zkoss.zul.Comboitem) obj;
                        if (remarkText.equalsIgnoreCase(item.getLabel().trim())) {
                            fieldRemarks.setSelectedItem(item);
                            matched = true;
                            break;
                        }
                    }
                }
            }
            // If no matching item found (e.g. custom "Other" text), just set the value
            if (!matched) {
                fieldRemarks.setValue(remarkText);
            }
        }
        if (errRemarks != null) errRemarks.setVisible(false);

        if (remarksWrap != null) remarksWrap.setVisible(true);
        remarksShowing = true;

        btnEditFields.setVisible(false);

        // Keep CBS visible but disabled until maker saves again
        if (btnCbs != null) {
            btnCbs.setDisabled(true);
            btnCbs.setVisible(true);
        }

        btnSaveCbs.setDisabled(false);
        btnSaveCbs.setLabel("💾 Save");
    }

    // ── Field lock / unlock ──────────────────────────────────────────────────

    /** Disables all editable fields — called after a successful Save. */
    private void lockFormFields() {
        fieldChequeNo.setDisabled(true);
        fieldCity.setDisabled(true);
        fieldBank.setDisabled(true);
        fieldBranch.setDisabled(true);
        fieldTc.setDisabled(true);
        fieldAcc.setDisabled(true);
        fieldDate.setDisabled(true);
        fieldAmount.setDisabled(true);
        fieldPayee.setDisabled(true);
        fieldRemarks.setDisabled(true);
        fieldRemarksOther.setDisabled(true);
    }

    /** Re-enables all editable fields — called when the maker clicks Edit. */
    private void unlockFormFields() {
        fieldChequeNo.setDisabled(false);
        fieldCity.setDisabled(false);
        fieldBank.setDisabled(false);
        fieldBranch.setDisabled(false);
        fieldTc.setDisabled(false);
        fieldAcc.setDisabled(false);
        fieldDate.setDisabled(false);
        fieldAmount.setDisabled(false);
        fieldPayee.setDisabled(false);
        fieldRemarks.setDisabled(false);
        fieldRemarksOther.setDisabled(false);
    }

    // ── CBS Validation ───────────────────────────────────────────────────────

    /**
     * Runs CBS validation against the saved cheque data.
     * Opens the CBS result popup via MakerCbsValidator.
     * On pass → calls executeSubmit(); on fail → calls onGenerateRRF().
     */
    @Listen("onClick = #btn-cbs")
    public void onRunCbs() {

        if (currentCheque == null) {
            showError("No cheque loaded.");
            return;
        }

        BigDecimal amount = currentCheque.getAmount();
        if (amount == null) {
            showError("Amount not available. Please save the cheque first.");
            return;
        }

        btnCbs.setDisabled(true);
        btnCbs.setLabel("Validating...");

        cbsValidator = new MakerCbsValidator(
            pageRoot,
            getCurrentUserId(),
            new MakerCbsValidator.CbsResultCallback() {
                @Override
                public void onCbsPassed(double amt, String destination) {
                    executeSubmit(amt, destination);
                }
                @Override
                public void onCbsFailed(String failureCode) {
                    onGenerateRRF(failureCode);
                }
            }
        );

        cbsValidator.run(
            currentCheque,
            fieldAcc.getValue().trim(),
            fieldChequeNo.getValue().trim(),
            amount.doubleValue()
        );

        btnCbs.setDisabled(false);
        btnCbs.setLabel("🔁 Run CBS Validation");
    }

    // ── Submit and RRF ───────────────────────────────────────────────────────

    /**
     * Called when CBS validation passes. Resubmits the cheque to the Checker
     * queue and closes the popup with a success message.
     */
    private void executeSubmit(double amount, String destination) {
        try {
            String userId = getCurrentUserId();
            chequeService.resubmitToChecker(currentCheque.getChequeNo(), userId);
            String chequeNo = currentCheque.getChequeNo();
            closeModal("Cheque " + chequeNo
                + " resubmitted to " + destination + ". Status: Resubmitted.");
        } catch (Exception ex) {
            showError("Error submitting cheque: " + ex.getMessage());
        }
    }

    /**
     * Called when CBS validation fails. Generates an RRF for the cheque
     * and closes the popup with a failure reason message.
     */
    private void onGenerateRRF(String cbsFailureCode) {
        try {
            String userId = getCurrentUserId();
            chequeService.generateRRF(currentCheque.getChequeNo(), userId);
            String chequeNo = currentCheque.getChequeNo();
            String reason   = MakerCbsValidator.getFailureMessage(cbsFailureCode);
            closeModal("RRF generated for cheque " + chequeNo + ". Reason: " + reason + ".");
        } catch (Exception ex) {
            showError("Error generating RRF: " + ex.getMessage());
        }
    }

    // ── Close popup ──────────────────────────────────────────────────────────

    /**
     * Detaches the modal window and triggers the queue refresh.
     * Popup is detached FIRST, then the queue refreshes — doing it in the
     * other order caused ZK stub errors because both operations were happening
     * in the same response.
     */
    private void closeModal(String message) {

        CtsUiBridge.removeBackdrop("rcb-modal-backdrop");

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
                .setAttribute("rcbSuccessMessage", message);
        }

        if (!windowDetached) {
            Executions.sendRedirect("/zul/inward/M_ReturnedByChecker.zul");
        }
    }

    // ── Private helpers ──────────────────────────────────────────────────────

    /**
     * Returns the maker's correction remark.
     * If "Other" is selected in the combobox, returns the freetext value instead.
     */

    /**
     * Clears the "Correction remark is required" error label as soon as
     * the user selects or types in the Correction Remarks combobox.
     * Without this, the error stayed visible even after a valid selection.
     */
    @Listen("onSelect = #rcb-f-remarks-select; onChange = #rcb-f-remarks-select")
    public void onRemarkSelected() {
        if (errRemarks != null) {
            errRemarks.setVisible(false);
        }
    }

    private String getResolvedMakerRemark() {
        if (fieldRemarks == null) return "";

        // FIX: ZK Combobox.getSelectedItem() is set immediately when user picks
        // from the dropdown. getValue() only updates on blur/type — not reliable
        // for dropdown-only selection. Always check getSelectedItem() first.
        String selected = "";
        if (fieldRemarks.getSelectedItem() != null) {
            selected = fieldRemarks.getSelectedItem().getLabel().trim();
        } else if (fieldRemarks.getValue() != null) {
            selected = fieldRemarks.getValue().trim();
        }

        if ("Other".equalsIgnoreCase(selected)) {
            String other = fieldRemarksOther != null
                ? fieldRemarksOther.getValue().trim() : "";
            return other.isEmpty() ? selected : other;
        }
        return selected;
    }

    /** Returns the logged-in user ID from session, or a default fallback. */
    private String getCurrentUserId() {
        Object user = Executions.getCurrent().getSession().getAttribute("loggedInUser");
        return user != null ? user.toString() : "maker.in";
    }

    private void showError(String message) {
        Messagebox.show(message, "Error", Messagebox.OK, Messagebox.ERROR);
    }
}