package com.cts.component;

/**
 * File    : MakerFieldValidator.java
 * Package : com.cts.component
 * Purpose : Validates all editable cheque fields in the Maker Repair Workspace.
 *           Shows inline error labels and red borders on invalid fields.
 *           Correction Remarks validation is NOT done here — it is handled
 *           separately in M_RepairWorkspaceComposer on the second Save click,
 *           because the remarks combobox is hidden on the first click.
 * Author  : Ramana
 * Date    : 24-06-2025
 */

import org.zkoss.zul.Combobox;
import org.zkoss.zul.Datebox;
import org.zkoss.zul.Label;
import org.zkoss.zul.Textbox;

public class MakerFieldValidator {

    private final Textbox  fieldChequeNo;
    private final Textbox  fieldCity;
    private final Textbox  fieldBank;
    private final Textbox  fieldBranch;
    private final Textbox  fieldTc;
    private final Textbox  fieldAmount;
    private final Datebox  fieldDate;
    private final Textbox  fieldAcc;
    private final Textbox  fieldPayee;
    private final Combobox fieldRemarksSelect;
    private final Textbox  fieldRemarks;

    private final Label errChequeNo;
    private final Label errCity;
    private final Label errBank;
    private final Label errBranch;
    private final Label errTc;
    private final Label errAmount;
    private final Label errDate;
    private final Label errAcc;
    private final Label errPayee;
    private final Label errRemarks;

    public MakerFieldValidator(
            Textbox fieldChequeNo, Textbox fieldCity,   Textbox fieldBank,
            Textbox fieldBranch,   Textbox fieldTc,     Textbox fieldAmount,
            Datebox fieldDate,     Textbox fieldAcc,    Textbox fieldPayee,
            Combobox fieldRemarksSelect, Textbox fieldRemarks,
            Label errChequeNo, Label errCity,   Label errBank,
            Label errBranch,   Label errTc,     Label errAmount,
            Label errDate,     Label errAcc,    Label errPayee,
            Label errRemarks) {

        this.fieldChequeNo      = fieldChequeNo;
        this.fieldCity          = fieldCity;
        this.fieldBank          = fieldBank;
        this.fieldBranch        = fieldBranch;
        this.fieldTc            = fieldTc;
        this.fieldAmount        = fieldAmount;
        this.fieldDate          = fieldDate;
        this.fieldAcc           = fieldAcc;
        this.fieldPayee         = fieldPayee;
        this.fieldRemarksSelect = fieldRemarksSelect;
        this.fieldRemarks       = fieldRemarks;

        this.errChequeNo = errChequeNo;
        this.errCity     = errCity;
        this.errBank     = errBank;
        this.errBranch   = errBranch;
        this.errTc       = errTc;
        this.errAmount   = errAmount;
        this.errDate     = errDate;
        this.errAcc      = errAcc;
        this.errPayee    = errPayee;
        this.errRemarks  = errRemarks;
    }

    /**
     * Validates all cheque fields in one pass.
     * Shows a red border and error label for each invalid field.
     * Returns true only if every field is valid.
     */
    public boolean validateAll() {
        boolean valid = true;

        // Cheque No — required, digits only, 3–12 chars
        String chequeNo = fieldChequeNo.getValue().trim();
        if (chequeNo.isEmpty()) {
            valid = showError(fieldChequeNo, errChequeNo, "Cheque number is required");
        } else if (!chequeNo.matches("[0-9]+")) {
            valid = showError(fieldChequeNo, errChequeNo, "Digits only");
        } else if (chequeNo.length() < 3 || chequeNo.length() > 12) {
            valid = showError(fieldChequeNo, errChequeNo, "Must be 3–12 digits");
        } else {
            clearError(fieldChequeNo, errChequeNo);
        }

        // City Code — required, exactly 3 digits (MICR city code)
        String city = fieldCity.getValue().trim();
        if (city.isEmpty()) {
            valid = showError(fieldCity, errCity, "City code is required");
        } else if (!city.matches("[0-9]{3}")) {
            valid = showError(fieldCity, errCity, "Exactly 3 digits required");
        } else {
            clearError(fieldCity, errCity);
        }

        // Bank Code — required, exactly 3 digits (MICR bank code)
        String bank = fieldBank.getValue().trim();
        if (bank.isEmpty()) {
            valid = showError(fieldBank, errBank, "Bank code is required");
        } else if (!bank.matches("[0-9]{3}")) {
            valid = showError(fieldBank, errBank, "Exactly 3 digits required");
        } else {
            clearError(fieldBank, errBank);
        }

        // Branch Code — required, exactly 3 digits (MICR branch code)
        String branch = fieldBranch.getValue().trim();
        if (branch.isEmpty()) {
            valid = showError(fieldBranch, errBranch, "Branch code is required");
        } else if (!branch.matches("[0-9]{3}")) {
            valid = showError(fieldBranch, errBranch, "Exactly 3 digits required");
        } else {
            clearError(fieldBranch, errBranch);
        }

        // Transaction Code — required, digits only
        String tc = fieldTc.getValue().trim();
        if (tc.isEmpty()) {
            valid = showError(fieldTc, errTc, "Transaction code is required");
        } else if (!tc.matches("[0-9]+")) {
            valid = showError(fieldTc, errTc, "Digits only");
        } else {
            clearError(fieldTc, errTc);
        }

        // Amount — required, digits only, must be > 0
        String amtStr = fieldAmount.getValue().trim();
        if (amtStr.isEmpty()) {
            valid = showError(fieldAmount, errAmount, "Amount is required");
        } else if (!amtStr.matches("[0-9]+")) {
            valid = showError(fieldAmount, errAmount, "Digits only");
        } else if (Long.parseLong(amtStr) <= 0) {
            valid = showError(fieldAmount, errAmount, "Amount must be greater than 0");
        } else {
            clearError(fieldAmount, errAmount);
        }

        // Cheque Date — required, not in the future, not older than 90 days
        if (fieldDate.getValue() == null) {
            valid = showError(null, errDate, "Cheque date is required");
        } else {
            java.util.Date selected = fieldDate.getValue();
            java.util.Date today    = new java.util.Date();
            long diffDays = (today.getTime() - selected.getTime()) / (1000L * 60 * 60 * 24);
            if (selected.after(today)) {
                valid = showError(null, errDate, "Date cannot be in the future");
            } else if (diffDays > 90) {
                valid = showError(null, errDate, "Cheque is older than 90 days");
            } else {
                clearError(null, errDate);
            }
        }

        // Account No — required, digits only, max 18 digits
        String acc = fieldAcc.getValue().trim();
        if (acc.isEmpty()) {
            valid = showError(fieldAcc, errAcc, "Account number is required");
        } else if (!acc.matches("[0-9]+")) {
            valid = showError(fieldAcc, errAcc, "Digits only");
        } else if (acc.length() > 18) {
            valid = showError(fieldAcc, errAcc, "Max 18 digits");
        } else {
            clearError(fieldAcc, errAcc);
        }

        // Payee Name — required, 2–100 chars
        String payee = fieldPayee.getValue().trim();
        if (payee.isEmpty()) {
            valid = showError(fieldPayee, errPayee, "Payee name is required");
        } else if (payee.length() < 2) {
            valid = showError(fieldPayee, errPayee, "At least 2 characters required");
        } else if (payee.length() > 100) {
            valid = showError(fieldPayee, errPayee, "Max 100 characters");
        } else {
            clearError(fieldPayee, errPayee);
        }

        return valid;
    }

    // ── Private helpers ───────────────────────────────────────────────────────

    /**
     * Marks the field with a red border and shows the error label.
     * getStyle() is null-checked because ZK returns null when no inline style is set.
     * Returns false so callers can write: valid = showError(...);
     */
    private boolean showError(Textbox field, Label errLabel, String message) {
        if (field != null) {
            String existing = field.getStyle();
            String base = existing != null
                ? existing.replace("border-color:#D1D5DB", "") : "";
            field.setStyle(base + ";border-color:#EF4444;");
        }
        if (errLabel != null) {
            errLabel.setValue(message);
            errLabel.setVisible(true);
        }
        return false;
    }

    /** Clears the red border and hides the error label for a field that is now valid. */
    private void clearError(Textbox field, Label errLabel) {
        if (field != null) {
            String style = field.getStyle();
            if (style != null) {
                field.setStyle(style.replace(";border-color:#EF4444;", "")
                                    .replace("border-color:#EF4444;", ""));
            }
        }
        if (errLabel != null) {
            errLabel.setValue("");
            errLabel.setVisible(false);
        }
    }

    /** Same as showError() but for Combobox fields. */
    private boolean showComboboxError(Combobox field, Label errLabel, String message) {
        if (field != null) {
            String existing = field.getStyle();
            String base = existing != null ? existing : "";
            field.setStyle(base + ";border-color:#EF4444;");
        }
        if (errLabel != null) {
            errLabel.setValue(message);
            errLabel.setVisible(true);
        }
        return false;
    }

    /** Same as clearError() but for Combobox fields. */
    private void clearComboboxError(Combobox field, Label errLabel) {
        if (field != null) {
            String style = field.getStyle();
            if (style != null) {
                field.setStyle(style.replace(";border-color:#EF4444;", "")
                                    .replace("border-color:#EF4444;", ""));
            }
        }
        if (errLabel != null) {
            errLabel.setValue("");
            errLabel.setVisible(false);
        }
    }
}