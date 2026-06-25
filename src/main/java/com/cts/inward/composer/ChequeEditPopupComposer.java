package com.cts.inward.composer;


import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;

import org.zkoss.zk.ui.Component;
import org.zkoss.zk.ui.Desktop;
import org.zkoss.zk.ui.Executions;
import org.zkoss.zk.ui.event.Event;
import org.zkoss.zk.ui.event.EventQueues;
import org.zkoss.zk.ui.select.SelectorComposer;
import org.zkoss.zk.ui.select.annotation.Listen;
import org.zkoss.zk.ui.select.annotation.Wire;
import org.zkoss.zul.Button;
import org.zkoss.zul.Combobox;
import org.zkoss.zul.Comboitem;
import org.zkoss.zul.Div;
import org.zkoss.zul.Image;
import org.zkoss.zul.Label;
import org.zkoss.zul.Messagebox;
import org.zkoss.zul.Textbox;
import org.zkoss.zul.Window;
import org.zkoss.zul.impl.InputElement;

import com.cts.inward.dao.InwardChequeDaoImpl;
import com.cts.inward.entity.InwardCheque;
import com.cts.inward.enums.BatchStatus;
import com.cts.inward.enums.ChequeStatus;
import com.cts.inward.enums.DecisionStatus;
import com.cts.inward.enums.SendTo;
import com.cts.inward.service.BatchProcessingService;
import com.cts.inward.service.BatchProcessingServiceImpl;
import com.cts.inward.service.InwardChequeMICRService;
import com.cts.inward.service.InwardChequeServiceMICRImpl;



public class ChequeEditPopupComposer extends SelectorComposer<Component> {

    private static final long serialVersionUID = 1L;

    private static final Logger LOGGER = Logger.getLogger(ChequeEditPopupComposer.class.getName());

    // =============================================
    // Services
    // =============================================
    private final InwardChequeMICRService chequeService     = new InwardChequeServiceMICRImpl();

    @Wire Window chequeEditPopupWindow;

    // Header
    @Wire Label  lblPopupTitle;
    @Wire Label  lblPopupBadge;

    // Image section — front/rear toggle
    @Wire Div    divFrontView;
    @Wire Div    divRearView;
    @Wire Image  imgFront;
    @Wire Image  imgRear;
    @Wire Div    divFrontFallback;
    @Wire Div    divRearFallback;
    @Wire Button btnSwitchImage;
    @Wire Label  lblSideTag;

    // Fallback card labels (shown when no image URL)
    @Wire Label lblCardBank;
    @Wire Label lblCardPayee;
    @Wire Label lblCardAmount;
    @Wire Label lblCardMicr;

    // MICR fields
    @Wire Textbox tbChequeNo;
    @Wire Textbox tbCityCode;
    @Wire Textbox tbBankCode;
    @Wire Textbox tbBranchCode;
    @Wire Textbox tbMicrNumber;        // READ-ONLY (auto-derived)
    @Wire Textbox tbTransactionCode;

    // Cheque detail fields
    @Wire Textbox tbAccountNo;         // NEW — ported from teammate's file; must exist in chequeEditPopup.zul
    @Wire Textbox tbClearingDate;
    @Wire Textbox tbAmount;
    @Wire Textbox tbPayeeName;
    @Wire Textbox tbAmountInWords;     // READ-ONLY

    // Validation messages
    @Wire Label lblMicrValidationMsg;
    @Wire Label lblAmountValidationMsg;


    // Error Reason
    @Wire Div      divErrorReason;
    @Wire Div	   divOtherErrorReason;
    @Wire Combobox cbChequeRejectionReason;
    @Wire Textbox  tbOtherReason;
    @Wire Button   btnConfirmReject;
    @Wire Button   btnCancelReject;

    // Refer Reason
    @Wire Div      divReferReason;
    @Wire Div      divOtherReferReason;
    @Wire Combobox cbChequeReferralReason;
    @Wire Textbox  tbOtherReferReason;
    @Wire Button   btnConfirmRefer;
    @Wire Button   btnCancelRefer;

    // Send Back Reason
    @Wire Div 		divSendBackReason;
    @Wire Div 		divOtherSendBackReason;
    @Wire Combobox	cbChequeSendBackReason;
    @Wire Textbox 	tbOtherSendBackReason;
    @Wire Button	btnConfirmSendBack;
    @Wire Button 	btnCancelSendBack;
    
    @Wire("#lb-refer-reason")
    private Label lbReferReason;

    @Wire("#divReferReasonBanner")
    private Div divReferReasonBanner;

    // Decision Buttons
    @Wire("#btnRefer")
    private Button btnRefer;

    @Wire("#btnReject")
    private Button btnReject;

    @Wire("#btnApprove")
    private Button btnApprove;

    @Wire("#btnSendBack")
    private Button btnSendBack;

    @Wire("#btnSaveChanges")
    private Button btnSaveChnages;

    private String currentRole;

    private String popupSource;

    // =============================================
    // State
    // =============================================
    private InwardCheque        selectedCheque = null;
    private Map<String, String> originalValues = new HashMap<>();
    private boolean             showingFront   = true;

    // CSS constants — must match maker.css
    private static final String CSS_NORMAL          = "popup-field-input";
    private static final String CSS_READONLY        = "popup-field-input popup-field-readonly";
    private static final String CSS_EDITED          = "popup-field-input popup-field-edited";
    private static final String CSS_ERROR           = "popup-field-input popup-field-error";
    private static final String CSS_READONLY_EDITED = "popup-field-input popup-field-readonly popup-field-readonly-edited";
    private static final String CSS_MSG_HIDDEN      = "popup-validation-msg popup-validation-msg-hidden";
    private static final String CSS_MSG_VISIBLE     = "popup-validation-msg";

    private static final DateTimeFormatter DISPLAY_FMT = DateTimeFormatter.ofPattern("dd/MM/yyyy");

    // Group of fields that should be locked/unlocked together.
    // tbMicrNumber and tbAmountInWords are deliberately excluded —
    // they're always auto-derived/read-only regardless of edit mode.
    private List<InputElement> editableFields;
    
    
    // DoAfter Compose Method
    // Used to create for wiring
    @Override
    public void doAfterCompose(Component comp) throws Exception {
        super.doAfterCompose(comp);

        currentRole =
        	    (String) Executions.getCurrent()
        	                       .getDesktop()
        	                       .getAttribute("userRole");

        // Always start showing front
        divFrontView.setVisible(true);
        divRearView.setVisible(false);
        showingFront = true;

        editableFields = Arrays.asList(
            tbChequeNo,
            tbCityCode,
            tbBankCode,
            tbBranchCode,
            tbTransactionCode,
            tbAccountNo,
            tbClearingDate,
            tbAmount,
            tbPayeeName
        );

        // Locked by default until updateButtonState() decides otherwise
        setFieldsEditable(false);

        Map<?, ?> args = Executions.getCurrent().getArg();
        popupSource = (String) args.get("popupSource");
        if (args != null && args.containsKey("selectedCheque")) {
            Object cheque = args.get("selectedCheque");
            if (cheque instanceof InwardCheque) {
                selectedCheque = (InwardCheque) cheque;
                if (selectedCheque != null) {
                    populateAllFields(selectedCheque);
                    updateButtonState();
                }
            }
        }
    }

    // =============================================
    // Switch Front ↔ Rear
    // =============================================

    @Listen("onClick = #btnSwitchImage")
    public void onSwitchImage() {
        if (showingFront) {
            divFrontView.setVisible(false);
            divRearView.setVisible(true);
            btnSwitchImage.setLabel("⇄ Show Front");
            lblSideTag.setValue("■ REAR SIDE");
            showingFront = false;
        } else {
            divFrontView.setVisible(true);
            divRearView.setVisible(false);
            btnSwitchImage.setLabel("⇄ Show Back");
            lblSideTag.setValue("■ FRONT SIDE");
            showingFront = true;
        }
    }

    // =============================================
    // Populate fields from entity
    // =============================================

    private void populateAllFields(InwardCheque cheque) {

        // Header
        lblPopupTitle.setValue("View Cheque - " + safe(cheque.getChequeNo()));
        String status = cheque.getChequeStatus() != null
                ? cheque.getChequeStatus().name()
                : "UNKNOWN";

        lblPopupBadge.setValue(status);
        lblPopupBadge.setSclass("popup-badge badge-popup-normal");

        // Load real images from Supabase URL
        loadImages(cheque);

        // Fallback card labels (only visible if image URL is null)
        lblCardBank.setValue(safe(cheque.getPresentingBank()));
        lblCardPayee.setValue(safe(cheque.getPayeeName()));
        lblCardAmount.setValue(cheque.getAmount() != null
                ? "Rs. " + cheque.getAmount().toPlainString() : "Rs. 0.00");
        lblCardMicr.setValue(":" + safe(cheque.getChequeNo()) + ": " + safe(cheque.getMicrCode()) + "=");

        // MICR — extract city(3) + bank(3) + branch(3) via the shared service rule
        String rawMicr     = safe(cheque.getMicrCode());
        String[] micrParts = chequeService.parseMicrParts(rawMicr);
        String cityCode    = micrParts[0];
        String bankCode    = micrParts[1];
        String branchCode  = micrParts[2];

        tbChequeNo.setValue(safe(cheque.getChequeNo()));
        tbCityCode.setValue(cityCode);
        tbBankCode.setValue(bankCode);
        tbBranchCode.setValue(branchCode);
        tbMicrNumber.setValue(cityCode + bankCode + branchCode);
        tbTransactionCode.setValue(safe(cheque.getTransactionCode()));
        tbAccountNo.setValue(safe(cheque.getAccountNo()));

        // Cheque details
        tbClearingDate.setValue(
        		cheque.getChequeDate() != null
        	        ? cheque.getChequeDate().format(DISPLAY_FMT)
        	        : ""
        	);
        tbAmount.setValue(cheque.getAmount() != null ? cheque.getAmount().toPlainString() : "");
        tbPayeeName.setValue(safe(cheque.getPayeeName()));

        // Amount in words
        String stored = safe(cheque.getAmountInWords());
        tbAmountInWords.setValue(!stored.isEmpty() ? stored
                : (cheque.getAmount() != null ? chequeService.convertAmountToWords(cheque.getAmount()) : ""));
        
     // Show refer reason from previous checker decision (if any)
        String referReason = cheque.getReferReason();
        if (referReason != null && !referReason.trim().isEmpty()) {
            lbReferReason.setValue("📋 Refer Reason: " + referReason);
            divReferReasonBanner.setVisible(true);
        } else {
            divReferReasonBanner.setVisible(false);
        }
        
        clearAllValidationMessages();
        captureOriginalValues();
        
        restoreEditedHighlights(cheque.getId());

        // Only Maker edits live; TV1/TV2 are read-only reviewers, so wiring
        // change listeners for them would be dead weight at best and could
        // mask the fact that their fields are locked. updateButtonState()
        // (called right after this method) re-applies field lock state per
        // role; listeners are attached only when MAKER unlocks the fields.
        if ("MAKER".equals(currentRole)) {
            attachEditListeners();
            attachMicrListeners();
            attachAmountListener();
            runInitialFieldValidation();
        }
    }

    // =============================================
    // Image Loading
    // =============================================

    private void loadImages(InwardCheque cheque) {
        String frontUrl = cheque.getFrontImagePath();
        String rearUrl  = cheque.getRearImagePath();

        // Front
        if (frontUrl != null && !frontUrl.trim().isEmpty()) {
            imgFront.setSrc(frontUrl.trim());
            imgFront.setStyle("display:block; width:100%; height:auto; object-fit:contain; max-height:380px;");
            divFrontFallback.setVisible(false);
        } else {
            imgFront.setStyle("display:none;");
            divFrontFallback.setVisible(true);
        }

        // Rear
        if (rearUrl != null && !rearUrl.trim().isEmpty()) {
            imgRear.setSrc(rearUrl.trim());
            imgRear.setStyle("display:block; width:100%; height:auto; object-fit:contain; max-height:380px;");
            divRearFallback.setVisible(false);
        } else {
            imgRear.setStyle("display:none;");
            divRearFallback.setVisible(true);
        }
    }

    @Listen("onClick = #btnApprove")
    public void onApprove() {
        updateChequeStatus(DecisionStatus.ACCEPTED, ChequeStatus.Ready, SendTo.TV_1);
    }

    @Listen("onClick = #btnSendBack")
    public void onSendBack() {
        divSendBackReason.setVisible(true);
        btnConfirmSendBack.setVisible(true);
        btnCancelSendBack.setVisible(true);
        // Hide action buttons while send-back section is open
        btnSendBack.setVisible(false);
        btnApprove.setVisible(false);
        btnReject.setVisible(false);
        btnRefer.setVisible(false);
    }

    @Listen("onClick = #btnReject")
    public void onReject() {
        divErrorReason.setVisible(true);
        btnSendBack.setVisible(false);
        btnApprove.setVisible(false);
        btnReject.setVisible(false);
        btnRefer.setVisible(false);
    }

    @Listen("onClick = #btnRefer")
    public void onRefer() {
        divReferReason.setVisible(true);
        btnConfirmRefer.setVisible(true);
        btnCancelRefer.setVisible(true);
        btnSendBack.setVisible(false);
        btnApprove.setVisible(false);
        btnReject.setVisible(false);
        btnRefer.setVisible(false);
    }

    /**
     * MAKER's "Save Changes" — runs full validation via chequeService, then
     * delegates the actual save (including MICR-repair record handling and
     * the DAO update) to chequeService.saveChequeEdit(), which is the same
     * orchestration method the teammate's Maker-only composer used.
     */
    @Listen("onClick = #btnSaveChanges")
    public void onSaveChanges() {
        if (selectedCheque == null) {
            showError("No cheque loaded. Please close and reopen.");
            return;
        }
        if (!isValid()) {
            return;
        }
        try {
            chequeService.saveChequeEdit(
                    selectedCheque,
                    tbChequeNo.getValue().trim(),
                    tbCityCode.getValue().trim(),
                    tbBankCode.getValue().trim(),
                    tbBranchCode.getValue().trim(),
                    tbTransactionCode.getValue().trim(),
                    tbAccountNo.getValue().trim(),
                    tbClearingDate.getValue().trim(),
                    tbAmount.getValue().trim(),
                    tbAmountInWords.getValue().trim());

            // saveChequeEdit() sets ChequeStatus.Normal internally but does
            // not touch Decision/SendTo — Maker save always routes back to
            // MAKER with a fresh PENDING decision so it re-enters the TV1
            // queue cleanly.
            selectedCheque.setDecision(DecisionStatus.PENDING);
            selectedCheque.setSendTo(SendTo.MAKER);
            new InwardChequeDaoImpl().updateMICR(selectedCheque);
            
            persistHighlightsToDesktop(selectedCheque.getId());  
            resetErrorStyles();

            Object batchPayload = selectedCheque.getBatch() != null
                    ? selectedCheque.getBatch().getId() : null;
            EventQueues.lookup("chequeStatusUpdated", EventQueues.DESKTOP, true)
                       .publish(new Event("onChequeStatusUpdated", null, batchPayload));

            Messagebox.show(
                "Cheque " + selectedCheque.getChequeNo() + " saved successfully.",
                "Saved", Messagebox.OK, Messagebox.INFORMATION,
                event -> chequeEditPopupWindow.detach());

        } catch (DateTimeParseException e) {
            markFieldError(tbClearingDate);
            showError("Date format invalid. Expected: dd/MM/yyyy  e.g. 10/06/2026");
        } catch (NumberFormatException e) {
            markFieldError(tbAmount);
            showError("Amount must be a valid number. Example: 35000.00");
        } catch (Exception e) {
            LOGGER.log(Level.SEVERE, "Failed to save cheque edit for cheque "
                    + (selectedCheque != null ? selectedCheque.getChequeNo() : "unknown"), e);
            showError("Save failed: " + e.getMessage());
        }
    }

    @Listen("onClick = #btnPopupClose")
    public void onPopupDismiss() {
        chequeEditPopupWindow.detach();
    }

    // =============================================
    // Decision/status update routine — TV1/TV2 only
    // =============================================
    // Used by Approve, Reject, Refer, and Send Back. Maker's Save Changes
    // does NOT go through this method — it has its own save path above
    // (chequeService.saveChequeEdit) since it edits cheque content rather
    // than just the decision/status/sendTo triple.
    private void updateChequeStatus(DecisionStatus newDecision, ChequeStatus newChequeStatus, SendTo newSendTo) {
        if (selectedCheque == null || selectedCheque.getId() == null) {
            Messagebox.show("No cheque loaded. Cannot update status.",
                    "Error", Messagebox.OK, Messagebox.ERROR);
            return;
        }
        try {
            // Set the new decision on the in-memory entity, then let
            // InwardChequeMICRDaoImpl.update() re-fetch the managed entity
            // inside its own session and persist the change. This avoids
            // detached-entity / session-closed issues entirely.
            selectedCheque.setDecision(newDecision);
            selectedCheque.setChequeStatus(newChequeStatus);
            selectedCheque.setSendTo(newSendTo);
            new InwardChequeDaoImpl().updateMICR(selectedCheque);
            
         // CHECK WHETHER BATCH CAN BE CLEARED
            if (selectedCheque.getBatch() != null) {
                Long batchId = selectedCheque.getBatch().getId();

                BatchProcessingService batchService = new BatchProcessingServiceImpl();
                batchService.updateBatchStatusIfCompleted(batchId);
            }

            Object batchPayload = selectedCheque.getBatch() != null
                    ? selectedCheque.getBatch().getId() : null;

            EventQueues.lookup("chequeStatusUpdated", EventQueues.DESKTOP, true)
                       .publish(new Event("onChequeStatusUpdated", null, batchPayload));

            if (newSendTo == SendTo.MAKER) {
                Messagebox.show(
                    "Cheque " + selectedCheque.getChequeNo()
                        + " marked as " + newDecision + " back to MAKER.",
                    "Status Updated", Messagebox.OK, Messagebox.INFORMATION,
                    event -> chequeEditPopupWindow.detach());
            } else {
                Messagebox.show(
                    "Cheque " + selectedCheque.getChequeNo()
                        + " marked as " + newDecision + ".",
                    "Status Updated", Messagebox.OK, Messagebox.INFORMATION,
                    event -> chequeEditPopupWindow.detach());
            }

        } catch (Exception e) {
            LOGGER.log(Level.SEVERE, "Failed to update cheque status", e);
            Messagebox.show("Failed to update status: " + e.getMessage(),
                    "Error", Messagebox.OK, Messagebox.ERROR);
        }
    }

    @Listen("onSelect = #cbChequeRejectionReason")
    public void onErrorReasonSelected() {
        Comboitem selected = cbChequeRejectionReason.getSelectedItem();
        boolean isOther = selected != null && "Other".equals(selected.getLabel());

        if (isOther) {
            tbOtherReason.setVisible(true);
            divOtherErrorReason.setVisible(true);
        } else {
            tbOtherReason.setVisible(false);
            divOtherErrorReason.setVisible(false);
        }

        btnConfirmReject.setVisible(true);
        btnCancelReject.setVisible(true);
    }



    @Listen("onClick = #btnConfirmReject")
    public void onConfirmReject() {
        Comboitem selected = cbChequeRejectionReason.getSelectedItem();

        if (selected == null) {
            Messagebox.show("Please select a reason for rejection.",
                    "Validation", Messagebox.OK, Messagebox.EXCLAMATION);
            return;
        }

        String reason;

        if ("Other".equals(selected.getLabel())) {

            reason = tbOtherReason.getValue() != null ? tbOtherReason.getValue().trim() : "";
            if (reason.isEmpty()) {
                Messagebox.show("Please enter a reason.",
                        "Validation", Messagebox.OK, Messagebox.EXCLAMATION);
                return;
            }
        } else {
            reason = selected.getLabel();
        }

        if (selectedCheque != null) {
            selectedCheque.setRejectReason(reason);
        }

        updateChequeStatus(DecisionStatus.REJECTED, ChequeStatus.Reject, SendTo.TV_1);
    }

    @Listen("onClick = #btnCancelReject")
    public void onCancelReject() {
    	resetActionArea();
    }


    @Listen("onSelect = #cbChequeReferralReason")
    public void onReferkReasonSelected() {
        Comboitem selected = cbChequeReferralReason.getSelectedItem();
        boolean isOther = selected != null && "Other".equals(selected.getLabel());

        if (isOther) {
        	tbOtherReferReason.setVisible(true);
        	divOtherReferReason.setVisible(true);
        } else {
        	tbOtherReferReason.setVisible(false);
        	divOtherReferReason.setVisible(false);
        }

        btnConfirmRefer.setVisible(true);
        btnCancelRefer.setVisible(true);
    }

    @Listen("onClick = #btnConfirmRefer")
    public void onConfirmRefer() {
        Comboitem selected = cbChequeReferralReason.getSelectedItem();

        if (selected == null) {
            Messagebox.show("Please select a reason for referral.",
                    "Validation", Messagebox.OK, Messagebox.EXCLAMATION);
            return;
        }

        String reason;

        if ("Other".equals(selected.getLabel())) {
            reason = tbOtherReferReason.getValue() != null ? tbOtherReferReason.getValue().trim() : "";
            if (reason.isEmpty()) {
                Messagebox.show("Please enter a reason.",
                        "Validation", Messagebox.OK, Messagebox.EXCLAMATION);
                return;
            }
        } else {
            reason = selected.getLabel();
        }

        if (selectedCheque != null) {
            selectedCheque.setReferReason(reason);
        }

        updateChequeStatus(DecisionStatus.REFERRED, ChequeStatus.Repair, SendTo.TV_2);
    }

    @Listen("onClick = #btnCancelRefer")
    public void onCancelRefer() {
    	resetActionArea();
    }


    @Listen("onSelect = #cbChequeSendBackReason")
    public void onSendBackReasonSelected() {
        Comboitem selected = cbChequeSendBackReason.getSelectedItem();
        boolean isOther = selected != null && "Other".equals(selected.getLabel());

        if (isOther) {
        	tbOtherSendBackReason.setVisible(true);
        	divOtherSendBackReason.setVisible(true);
        } else {
        	tbOtherSendBackReason.setVisible(false);
            divOtherSendBackReason.setVisible(false);
        }

        btnConfirmSendBack.setVisible(true);
        btnCancelSendBack.setVisible(true);
    }

    @Listen("onClick = #btnConfirmSendBack")
    public void onConfirmSendBack() {
        Comboitem selected = cbChequeSendBackReason.getSelectedItem();

        if (selected == null) {
            Messagebox.show("Please select a reason for referral.",
                    "Validation", Messagebox.OK, Messagebox.EXCLAMATION);
            return;
        }

        String reason;

        if ("Other".equals(selected.getLabel())) {
            reason = tbOtherSendBackReason.getValue() != null ? tbOtherSendBackReason.getValue().trim() : "";
            if (reason.isEmpty()) {
                Messagebox.show("Please enter a reason.",
                        "Validation", Messagebox.OK, Messagebox.EXCLAMATION);
                return;
            }
        } else {
            reason = selected.getLabel();
        }

        if (selectedCheque != null) {
            selectedCheque.setSendbackReason(reason);
        }

        updateChequeStatus(DecisionStatus.REFERRED, ChequeStatus.Repair, SendTo.MAKER);
    }

    @Listen("onClick = #btnCancelSendBack")
    public void onCancelSendBack() {
    	resetActionArea();
    }


    private void resetActionArea() {
        // ── Hide all reason sections ──
        divErrorReason.setVisible(false);
        divReferReason.setVisible(false);
        divSendBackReason.setVisible(false);

        // ── Clear Rejection inputs ──
        cbChequeRejectionReason.setSelectedIndex(-1);
        tbOtherReason.setValue("");
        tbOtherReason.setVisible(false);
        divOtherErrorReason.setVisible(false);
        btnConfirmReject.setVisible(false);
        btnCancelReject.setVisible(false);

        // ── Clear Referral inputs ──
        cbChequeReferralReason.setSelectedIndex(-1);
        tbOtherReferReason.setValue("");
        tbOtherReferReason.setVisible(false);
        divOtherReferReason.setVisible(false);
        btnConfirmRefer.setVisible(false);
        btnCancelRefer.setVisible(false);

        // ── Clear Send Back inputs ──
        cbChequeSendBackReason.setSelectedIndex(-1);
        tbOtherSendBackReason.setValue("");
        tbOtherSendBackReason.setVisible(false);
        divOtherSendBackReason.setVisible(false);
        btnConfirmSendBack.setVisible(false);
        btnCancelSendBack.setVisible(false);

        // ── Restore main action buttons ──
        btnApprove.setVisible(true);
        btnReject.setVisible(true);
        if(selectedCheque.getSendTo() == SendTo.TV_1) {
        	btnRefer.setVisible(true);
        }else {
        	btnRefer.setVisible(false);
        }
        btnSendBack.setVisible(true);
    }


    // =============================================
    // Editable / Locked toggle for the field group
    // =============================================
    // Call with true to unlock fields for editing, false to lock them
    // read-only. This is the single place that controls whether the
    // popup's MICR/detail fields can be typed into.
    private void setFieldsEditable(boolean editable) {
        for (InputElement field : editableFields) {
            field.setReadonly(!editable);
            if (editable) {
                field.setSclass(CSS_NORMAL);
            } else {
                field.setSclass(CSS_READONLY);
            }
        }
    }


    private void updateButtonState() {

        if (selectedCheque == null) {
            return;
        }

        LOGGER.log(Level.FINE, "Role={0} Decision={1} Status={2} SendTo={3} CBS={4}",
                new Object[] { currentRole, selectedCheque.getDecision(),
                        selectedCheque.getChequeStatus(), selectedCheque.getSendTo(),
                        selectedCheque.getCbsValidation() });

        switch (currentRole) {

            // ==================================================
            // MAKER
            // ==================================================
        case "MAKER":

            // FIX: isLocked check now lives here — inside the MAKER
            // case only. TV1/TV2 cases are completely unaffected.
            boolean locked = selectedCheque.getBatch() != null
                    && BatchStatus.PendingAtChecker.equals(selectedCheque.getBatch().getBatchStatus());

            setFieldsEditable(!locked);

            btnApprove.setVisible(false);
            btnReject.setVisible(false);
            btnRefer.setVisible(false);
            btnSendBack.setVisible(false);

            btnSaveChnages.setVisible(true);
            btnSaveChnages.setDisabled(locked);

            // Attach listeners and run validation only when MAKER
            // can actually edit — avoids double-attach and avoids
            // running validation on a read-only locked popup.
            if (!locked) {
                attachEditListeners();
                attachMicrListeners();
                attachAmountListener();
                runInitialFieldValidation();
            }

            break;

            // ==================================================
            // TV1
            // ==================================================
//            case "TV1":

//                // TV1 only reviews
//                setFieldsEditable(false);
//
//                btnApprove.setVisible(true);
//                btnReject.setVisible(true);
//                btnRefer.setVisible(true);
//                btnSendBack.setVisible(true);
//
//                btnApprove.setDisabled(false);
//                btnReject.setDisabled(false);
//                btnRefer.setDisabled(false);
//                btnSendBack.setDisabled(false);
//
//                btnSaveChnages.setVisible(false);
//
//                break;
            	
            case "TV1":
                setFieldsEditable(false);
                btnSaveChnages.setVisible(false);

                boolean tv1CanAct = SendTo.TV_1.equals(selectedCheque.getSendTo())
                        && DecisionStatus.PENDING.equals(selectedCheque.getDecision())
                        && ChequeStatus.Processed.equals(selectedCheque.getChequeStatus());

                btnApprove.setVisible(tv1CanAct);
                btnReject.setVisible(tv1CanAct);
                btnRefer.setVisible(tv1CanAct);
                btnSendBack.setVisible(tv1CanAct);

                if (tv1CanAct) {
                    btnApprove.setDisabled(false);
                    btnReject.setDisabled(false);
                    btnRefer.setDisabled(false);
                    btnSendBack.setDisabled(false);
                }
                break;
                
            case "TV2":
                setFieldsEditable(false);
                btnSaveChnages.setVisible(false);
                btnRefer.setVisible(false); // TV2 never refers

                boolean hasReferReason = selectedCheque.getReferReason() != null
                        && !selectedCheque.getReferReason().trim().isEmpty();

                boolean tv2CanAct = SendTo.TV_2.equals(selectedCheque.getSendTo())
                        && (ChequeStatus.Processed.equals(selectedCheque.getChequeStatus())
                        		||
                        		ChequeStatus.Repair.equals(selectedCheque.getChequeStatus()))
                        && (DecisionStatus.PENDING.equals(selectedCheque.getDecision())
                                || (DecisionStatus.REFERRED.equals(selectedCheque.getDecision())
                                        && hasReferReason));

                btnApprove.setVisible(tv2CanAct);
                btnReject.setVisible(tv2CanAct);

                boolean showSendBack = tv2CanAct && !"REFERRED_TAB".equals(popupSource);
                btnSendBack.setVisible(showSendBack);

                if (tv2CanAct) {
                    btnApprove.setDisabled(false);
                    btnReject.setDisabled(false);
                    btnSendBack.setDisabled(false);
                }
                break;

            // ==================================================
            // DEFAULT
            // ==================================================
            default:

                setFieldsEditable(false);

                btnApprove.setVisible(false);
                btnReject.setVisible(false);
                btnRefer.setVisible(false);
                btnSendBack.setVisible(false);
                btnSaveChnages.setVisible(false);

                break;
        }
    }

    // =============================================
    // Maker edit validation — delegated to chequeService
    // =============================================
    // Every format/business rule below comes from InwardChequeServiceMICRImpl
    // (validateChequeNumber, validateCodePart, validateTransactionCode,
    // validateAccountNumber, validateAmountValue, isMicrMatch,
    // buildMicrMismatchMessage, getChequeIdentityBlockingMessage,
    // getChequeDetailsBlockingMessage, generateAmountInWordsDisplay) so the
    // rules live in exactly one place and match the teammate's original
    // Maker-only composer. These checks only run for MAKER, and only ever
    // gate the Save Changes path. TV1/TV2 fields are locked read-only by
    // updateButtonState(), so none of this can interfere with their
    // Approve/Reject/Refer/Send Back actions.

    private void runInitialFieldValidation() {
        checkFieldFormat(tbChequeNo, "tbChequeNo");
        checkFieldFormat(tbCityCode, "tbCityCode");
        checkFieldFormat(tbBankCode, "tbBankCode");
        checkFieldFormat(tbBranchCode, "tbBranchCode");
        checkFieldFormat(tbTransactionCode, "tbTransactionCode");
        checkFieldFormat(tbAccountNo, "tbAccountNo");
        checkFieldFormat(tbAmount, "tbAmount");
        validateMicrRealTime();
    }

    /**
     * Validates one field's value via chequeService.validateField(fieldId,
     * value) and applies/clears the red error highlight accordingly.
     * Returns true if the field is valid.
     */
    private boolean checkFieldFormat(Textbox tb, String fieldId) {
        String value = tb.getValue() != null ? tb.getValue().trim() : "";
        String error = chequeService.validateField(fieldId, value);

        if (error != null) {
            markFieldError(tb);
            if ("tbCityCode".equals(fieldId) || "tbBankCode".equals(fieldId) || "tbBranchCode".equals(fieldId)) {
                showMicrMsg(error);
            } else if ("tbAmount".equals(fieldId)) {
                showAmountMsg(error);
            }
            return false;
        } else {
            updateHighlight(tb, fieldId);
            return true;
        }
    }

    private void attachEditListeners() {
        Textbox[] formatFields = { tbChequeNo, tbTransactionCode, tbAccountNo };
        String[]  formatIds    = { "tbChequeNo", "tbTransactionCode", "tbAccountNo" };
        for (int i = 0; i < formatFields.length; i++) {
            final Textbox tb  = formatFields[i];
            final String  fid = formatIds[i];
            tb.addEventListener("onChange", e -> checkFieldFormat(tb, fid));
        }
        tbClearingDate.addEventListener("onChange", e -> updateHighlight(tbClearingDate, "tbClearingDate"));
        tbPayeeName.addEventListener("onChange", e -> updateHighlight(tbPayeeName, "tbPayeeName"));
    }

    private void attachMicrListeners() {
        Textbox[] parts = { tbCityCode, tbBankCode, tbBranchCode };
        String[] pids = { "tbCityCode", "tbBankCode", "tbBranchCode" };
        for (int i = 0; i < parts.length; i++) {
            final Textbox tb = parts[i];
            final String fid = pids[i];
            tb.addEventListener("onChanging", e -> recomputeMicrNumber());
            tb.addEventListener("onChange", e -> {
                recomputeMicrNumber();
                boolean formatOk = checkFieldFormat(tb, fid);
                if (formatOk)
                    validateMicrRealTime();
            });
        }
    }

    private void attachAmountListener() {
        tbAmount.addEventListener("onChanging", e -> autoGenerateAmountInWords());
        tbAmount.addEventListener("onChange", e -> {
            checkFieldFormat(tbAmount, "tbAmount");
            autoGenerateAmountInWords();
        });
    }
    private void recomputeMicrNumber() {
        String computed = chequeService.buildMicrCode(
                tbCityCode.getValue(), tbBankCode.getValue(), tbBranchCode.getValue());
        tbMicrNumber.setValue(computed);
        updateHighlight(tbMicrNumber, "tbMicrNumber");
    }

    private void validateMicrRealTime() {
        String city   = tbCityCode.getValue().trim();
        String bank   = tbBankCode.getValue().trim();
        String branch = tbBranchCode.getValue().trim();
        if (city.isEmpty() || bank.isEmpty() || branch.isEmpty()) {
            clearMicrValidation();
            return;
        }
        String displayed = tbMicrNumber.getValue().trim();
        if (!chequeService.isMicrMatch(city, bank, branch, displayed)) {
            markFieldError(tbCityCode); markFieldError(tbBankCode);
            markFieldError(tbBranchCode); markFieldError(tbMicrNumber);
            showMicrMsg(chequeService.buildMicrMismatchMessage(city, bank, branch, displayed));
        } else {
            clearMicrValidation();
        }
    }

    /**
     * Regenerates the Amount-in-Words field from the current Amount value
     * via chequeService.generateAmountInWordsDisplay(). Amount's own
     * format/negative/number checks are handled by checkFieldFormat(tbAmount,
     * "tbAmount") — called by the listener right before this method — so
     * this method only needs to worry about producing (or clearing) the
     * words text.
     */
    private void autoGenerateAmountInWords() {
        String raw = tbAmount.getValue().trim();
        String generated = chequeService.generateAmountInWordsDisplay(raw);
        tbAmountInWords.setValue(generated);
        if (!generated.isEmpty()) {
        	
            updateHighlight(tbAmountInWords, "tbAmountInWords");
        }
    }

    /** Final gatekeeper, run only on Maker's Save Changes click. */
    private boolean isValid() {
        clearAllValidationMessages();

        boolean chequeNoOk = checkFieldFormat(tbChequeNo, "tbChequeNo");
        boolean cityOk     = checkFieldFormat(tbCityCode, "tbCityCode");
        boolean bankOk     = checkFieldFormat(tbBankCode, "tbBankCode");
        boolean branchOk   = checkFieldFormat(tbBranchCode, "tbBranchCode");
        boolean tcOk       = checkFieldFormat(tbTransactionCode, "tbTransactionCode");
        boolean accNoOk    = checkFieldFormat(tbAccountNo, "tbAccountNo");
        boolean amountOk   = checkFieldFormat(tbAmount, "tbAmount");

        String identityBlockMsg = chequeService.getChequeIdentityBlockingMessage(chequeNoOk, cityOk, bankOk, branchOk);
        if (identityBlockMsg != null) { showError(identityBlockMsg); return false; }

        // Cross-field MICR check — only meaningful once each part is itself valid
        String city      = tbCityCode.getValue().trim();
        String bank      = tbBankCode.getValue().trim();
        String branch    = tbBranchCode.getValue().trim();
        String displayed = tbMicrNumber.getValue().trim();
        if (!chequeService.isMicrMatch(city, bank, branch, displayed)) {
            markFieldError(tbCityCode); markFieldError(tbBankCode);
            markFieldError(tbBranchCode); markFieldError(tbMicrNumber);
            showMicrMsg(chequeService.buildMicrMismatchMessage(city, bank, branch, displayed));
            return false;
        }

        String detailsBlockMsg = chequeService.getChequeDetailsBlockingMessage(tcOk, accNoOk, amountOk);
        if (detailsBlockMsg != null) { showError(detailsBlockMsg); return false; }

        if (tbClearingDate.getValue() != null && !tbClearingDate.getValue().trim().isEmpty()) {
            try {
                chequeService.parseChequeDate(tbClearingDate.getValue().trim());
            } catch (DateTimeParseException e) {
                markFieldError(tbClearingDate);
                showError("Date format invalid. Expected: dd/MM/yyyy  e.g. 10/06/2026");
                return false;
            }
        }
        if (tbAmountInWords.getValue().trim().isEmpty()) {
            autoGenerateAmountInWords();
            if (tbAmountInWords.getValue().trim().isEmpty()) {
                markFieldError(tbAmount);
                showAmountMsg("Amount in Words could not be generated.");
                return false;
            }
        }

        return true;
    }

    // =============================================
    // Original value capture / highlight helpers
    // =============================================

    private void captureOriginalValues() {
        originalValues.clear();
        originalValues.put("tbChequeNo",        tbChequeNo.getValue());
        originalValues.put("tbCityCode",         tbCityCode.getValue());
        originalValues.put("tbBankCode",         tbBankCode.getValue());
        originalValues.put("tbBranchCode",       tbBranchCode.getValue());
        originalValues.put("tbMicrNumber",       tbMicrNumber.getValue());
        originalValues.put("tbTransactionCode",  tbTransactionCode.getValue());
        originalValues.put("tbAccountNo",        tbAccountNo.getValue());
        originalValues.put("tbClearingDate",     tbClearingDate.getValue());
        originalValues.put("tbAmount",           tbAmount.getValue());
        originalValues.put("tbPayeeName",        tbPayeeName.getValue());
        originalValues.put("tbAmountInWords",    tbAmountInWords.getValue());
    }

    private void markFieldError(Textbox tb) { tb.setSclass(CSS_ERROR); }

    private void applyEditedStyle(Textbox tb) {
        if (CSS_ERROR.equals(tb.getSclass())) return;
        tb.setSclass(tb.isReadonly() ? CSS_READONLY_EDITED : CSS_EDITED);
    }

    private void updateHighlight(Textbox tb, String fieldId) {
        if (CSS_ERROR.equals(tb.getSclass())) return;
        String original = originalValues.getOrDefault(fieldId, "");
        if (!tb.getValue().equals(original)) {
            applyEditedStyle(tb);
        } else {
            tb.setSclass(tb.isReadonly() ? CSS_READONLY : CSS_NORMAL);
        }
    }

    private void resetErrorStyles() {
        String[] ids = { "tbChequeNo", "tbCityCode", "tbBankCode", "tbBranchCode",
                "tbMicrNumber", "tbTransactionCode", "tbAccountNo",
                "tbClearingDate", "tbAmount", "tbPayeeName", "tbAmountInWords" };
        Textbox[] boxes = { tbChequeNo, tbCityCode, tbBankCode, tbBranchCode,
                tbMicrNumber, tbTransactionCode, tbAccountNo,
                tbClearingDate, tbAmount, tbPayeeName, tbAmountInWords };
        for (int i = 0; i < ids.length; i++) {
            if (boxes[i] != null && CSS_ERROR.equals(boxes[i].getSclass())) {
                updateHighlight(boxes[i], ids[i]);
            }
        }
    }

    private void clearAllValidationMessages() {
        lblMicrValidationMsg.setValue("");   lblMicrValidationMsg.setSclass(CSS_MSG_HIDDEN);
        lblAmountValidationMsg.setValue(""); lblAmountValidationMsg.setSclass(CSS_MSG_HIDDEN);
    }
    private void clearMicrValidation()   { lblMicrValidationMsg.setValue("");   lblMicrValidationMsg.setSclass(CSS_MSG_HIDDEN); }
    private void showMicrMsg(String m)   { lblMicrValidationMsg.setValue(m);    lblMicrValidationMsg.setSclass(CSS_MSG_VISIBLE); }
    private void showAmountMsg(String m) { lblAmountValidationMsg.setValue(m);  lblAmountValidationMsg.setSclass(CSS_MSG_VISIBLE); }
    private void showError(String m)     { Messagebox.show(m, "Validation Error", Messagebox.OK, Messagebox.ERROR); }

    private String safe(String v) { return v != null ? v : ""; }
    
    private static final String DESKTOP_ATTR_PREFIX = "editedFields_";
    private String[] allFieldIds() {
        return new String[] { "tbChequeNo", "tbCityCode", "tbBankCode", "tbBranchCode", "tbMicrNumber",
                "tbTransactionCode", "tbAccountNo", "tbChequeDate", "tbAmount", "tbAmountInWords" };
    }

    private Textbox tbById(String id) {
        switch (id) {
        case "tbChequeNo":
            return tbChequeNo;
        case "tbCityCode":
            return tbCityCode;
        case "tbBankCode":
            return tbBankCode;
        case "tbBranchCode":
            return tbBranchCode;
        case "tbMicrNumber":
            return tbMicrNumber;
        case "tbTransactionCode":
            return tbTransactionCode;
        case "tbAccountNo":
            return tbAccountNo;
        case "tbChequeDate":
            return tbClearingDate;
        case "tbAmount":
            return tbAmount;
        case "tbAmountInWords":
            return tbAmountInWords;
        default:
            return null;
        }
    }
    
    @SuppressWarnings("unchecked")
    private void restoreEditedHighlights(Long chequeId) {
        if (chequeId == null)
            return;
        Desktop dt = chequeEditPopupWindow.getDesktop();
        Object stored = dt.getAttribute(DESKTOP_ATTR_PREFIX + chequeId);
        if (!(stored instanceof Map))
            return;
        Map<String, Boolean> editedMap = (Map<String, Boolean>) stored;
        for (String id : allFieldIds()) {
            if (Boolean.TRUE.equals(editedMap.get(id))) {
                Textbox tb = tbById(id);
                if (tb != null)
                    applyEditedStyle(tb);
            }
        }
    }
    
    private void persistHighlightsToDesktop(Long chequeId) {
        if (chequeId == null)
            return;
        Desktop dt = chequeEditPopupWindow.getDesktop();
        String key = DESKTOP_ATTR_PREFIX + chequeId;

        @SuppressWarnings("unchecked")
        Map<String, Boolean> editedMap = (Map<String, Boolean>) dt.getAttribute(key);
        if (editedMap == null)
            editedMap = new HashMap<>();

        for (String id : allFieldIds()) {
            Textbox tb = tbById(id);
            if (tb == null)
                continue;
            if (!tb.getValue().equals(originalValues.getOrDefault(id, ""))) {
                editedMap.put(id, Boolean.TRUE);
            } else {
                editedMap.remove(id);
            }
        }
        dt.setAttribute(key, editedMap);
    }

}