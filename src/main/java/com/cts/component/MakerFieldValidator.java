package com.cts.component;

/**
 * File    : MakerFieldValidator.java
 * Package : com.cts.component
 * Purpose : Validates all editable cheque fields in the Maker Repair Workspace.
 *
 *           TWO modes of validation:
 *
 *   1. validateAll()         — full pass on Save button click (all fields at once)
 *   2. validateFieldLive()   — single-field live validation on every keystroke
 *                              (ON_CHANGING) and on blur (ON_CHANGE).
 *                              Shows red border + error label WHILE typing.
 *                              Clears instantly as soon as the value becomes valid.
 *
 *           Correction Remarks validation is NOT done here — handled separately
 *           in M_RepairWorkspaceComposer on the second Save click because the
 *           remarks combobox is hidden on the first click.
 *
 * Author  : Ramana
 */

import org.zkoss.zul.Combobox;
import org.zkoss.zul.Datebox;
import org.zkoss.zul.Label;
import org.zkoss.zul.Textbox;

public class MakerFieldValidator {

    // ── Field constants — used as keys in validateFieldLive() ────────────────
    public static final String FIELD_CHEQUE_NO = "chequeNo";
    public static final String FIELD_CITY      = "city";
    public static final String FIELD_BANK      = "bank";
    public static final String FIELD_BRANCH    = "branch";
    public static final String FIELD_TC        = "tc";
    public static final String FIELD_AMOUNT    = "amount";
    public static final String FIELD_DATE      = "date";
    public static final String FIELD_ACC       = "acc";
    public static final String FIELD_PAYEE     = "payee";

    // ── Wired form fields ────────────────────────────────────────────────────
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

    // ── Inline error labels ──────────────────────────────────────────────────
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

    // ── Constructor ──────────────────────────────────────────────────────────

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

    // ═══════════════════════════════════════════════════════════════════════════
    // PUBLIC API
    // ═══════════════════════════════════════════════════════════════════════════

    /**
     * FULL VALIDATION — called on Save button click (Step 1).
     *
     * Checks every field in one pass.
     * Shows red border + error label on every invalid field simultaneously.
     * Returns true only if ALL fields are valid.
     *
     * The approach: set valid=true at start, then set valid=false on any failure.
     * We do NOT short-circuit (no early return) so ALL errors show at once,
     * not just the first one.
     */
    public boolean validateAll() {
        boolean valid = true;

        if (!validateChequeNoField())  valid = false;
        if (!validateCityField())      valid = false;
        if (!validateBankField())      valid = false;
        if (!validateBranchField())    valid = false;
        if (!validateTcField())        valid = false;
        if (!validateAmountField())    valid = false;
        if (!validateDateField())      valid = false;
        if (!validateAccField())       valid = false;
        if (!validatePayeeField())     valid = false;

        return valid;
    }

    /**
     * LIVE SINGLE-FIELD VALIDATION — called on ON_CHANGING and ON_CHANGE
     * for each individual field.
     *
     * Validates only the field named by fieldKey.
     * Shows error immediately while typing if value is wrong.
     * Clears error immediately as soon as value becomes valid.
     *
     * For ON_CHANGING (mid-keystroke): we use the in-progress value
     * passed as typedValue parameter, NOT getValue() — because getValue()
     * has not updated yet when ON_CHANGING fires.
     *
     * For ON_CHANGE (blur): typedValue == null → we call getValue() ourselves.
     *
     * @param fieldKey   One of the FIELD_* constants defined above.
     * @param typedValue The current text from the InputEvent, or null for blur.
     */
    public void validateFieldLive(String fieldKey, String typedValue) {
        switch (fieldKey) {
            case FIELD_CHEQUE_NO: validateChequeNoLive(typedValue); break;
            case FIELD_CITY:      validateCityLive(typedValue);     break;
            case FIELD_BANK:      validateBankLive(typedValue);     break;
            case FIELD_BRANCH:    validateBranchLive(typedValue);   break;
            case FIELD_TC:        validateTcLive(typedValue);       break;
            case FIELD_AMOUNT:    validateAmountLive(typedValue);   break;
            case FIELD_DATE:      validateDateField();               break; // date uses Datebox — no typedValue
            case FIELD_ACC:       validateAccLive(typedValue);      break;
            case FIELD_PAYEE:     validatePayeeLive(typedValue);    break;
            default:              break;
        }
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // FULL-VALIDATION PRIVATE METHODS  (used by validateAll)
    // Each returns true = valid, false = invalid
    // ═══════════════════════════════════════════════════════════════════════════

    private boolean validateChequeNoField() {
        String v = safeGet(fieldChequeNo);
        if (v.isEmpty())
            return showError(fieldChequeNo, errChequeNo, "Cheque number is required");
        if (!v.matches("[0-9]+"))
            return showError(fieldChequeNo, errChequeNo, "Digits only");
        if (v.length() != 6)
            return showError(fieldChequeNo, errChequeNo, "Must be exactly 6 digits");
        clearError(fieldChequeNo, errChequeNo);
        return true;
    }

    private boolean validateCityField() {
        String v = safeGet(fieldCity);
        if (v.isEmpty())
            return showError(fieldCity, errCity, "City code is required");
        if (!v.matches("[0-9]{3}"))
            return showError(fieldCity, errCity, "Exactly 3 digits required");
        clearError(fieldCity, errCity);
        return true;
    }

    private boolean validateBankField() {
        String v = safeGet(fieldBank);
        if (v.isEmpty())
            return showError(fieldBank, errBank, "Bank code is required");
        if (!v.matches("[0-9]{3}"))
            return showError(fieldBank, errBank, "Exactly 3 digits required");
        clearError(fieldBank, errBank);
        return true;
    }

    private boolean validateBranchField() {
        String v = safeGet(fieldBranch);
        if (v.isEmpty())
            return showError(fieldBranch, errBranch, "Branch code is required");
        if (!v.matches("[0-9]{3}"))
            return showError(fieldBranch, errBranch, "Exactly 3 digits required");
        clearError(fieldBranch, errBranch);
        return true;
    }

    private boolean validateTcField() {
        String v = safeGet(fieldTc);
        if (v.isEmpty())
            return showError(fieldTc, errTc, "Transaction code is required");
        if (!v.matches("[0-9]+"))
            return showError(fieldTc, errTc, "Digits only");
        if (v.length() != 2)
            return showError(fieldTc, errTc, "Must be exactly 2 digits");
        clearError(fieldTc, errTc);
        return true;
    }

    private boolean validateAmountField() {
        String v = safeGet(fieldAmount);
        if (v.isEmpty())
            return showError(fieldAmount, errAmount, "Amount is required");
        if (!v.matches("[0-9]+"))
            return showError(fieldAmount, errAmount, "Digits only, no decimals");
        if (Long.parseLong(v) <= 0)
            return showError(fieldAmount, errAmount, "Amount must be greater than 0");
        clearError(fieldAmount, errAmount);
        return true;
    }

    private boolean validateDateField() {
        if (fieldDate == null) return true;
        if (fieldDate.getValue() == null) {
            showDateError(errDate, "Cheque date is required");
            return false;
        }
        java.util.Date selected = fieldDate.getValue();
        java.util.Date today    = new java.util.Date();
        long diffDays = (today.getTime() - selected.getTime()) / (1000L * 60 * 60 * 24);
        if (selected.after(today)) {
            showDateError(errDate, "Date cannot be in the future");
            return false;
        }
        if (diffDays > 90) {
            showDateError(errDate, "Cheque is older than 90 days");
            return false;
        }
        clearDateError(errDate);
        return true;
    }

    private boolean validateAccField() {
        String v = safeGet(fieldAcc);
        if (v.isEmpty())
            return showError(fieldAcc, errAcc, "Account number is required");
        if (!v.matches("[0-9]+"))
            return showError(fieldAcc, errAcc, "Digits only");
        if (v.length() != 15)
            return showError(fieldAcc, errAcc, "Must be exactly 15 digits");
        clearError(fieldAcc, errAcc);
        return true;
    }

    private boolean validatePayeeField() {
        String v = safeGet(fieldPayee);
        if (v.isEmpty())
            return showError(fieldPayee, errPayee, "Payee name is required");
        if (v.length() < 2)
            return showError(fieldPayee, errPayee, "At least 2 characters required");
        if (v.length() > 100)
            return showError(fieldPayee, errPayee, "Max 100 characters");
        clearError(fieldPayee, errPayee);
        return true;
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // LIVE-VALIDATION PRIVATE METHODS  (used by validateFieldLive)
    //
    // These use the typedValue param (in-progress text from InputEvent),
    // falling back to getValue() if typedValue is null (blur case).
    // They do NOT return boolean — they just show/clear the error in real time.
    // ═══════════════════════════════════════════════════════════════════════════

    private void validateChequeNoLive(String raw) {
        String v = resolve(raw, fieldChequeNo);
        if (v.isEmpty()) {
            clearError(fieldChequeNo, errChequeNo);
            return;
        }
        if (!v.matches("[0-9]+")) {
            showError(fieldChequeNo, errChequeNo, "Digits only");
        } else if (v.length() > 6) {
            showError(fieldChequeNo, errChequeNo, "Max 6 digits");
        } else if (v.length() == 6) {
            // Exactly 6 digits → valid
            clearError(fieldChequeNo, errChequeNo);
        } else {
            // 1–5 digits: still typing
            showError(fieldChequeNo, errChequeNo, "Need exactly 6 digits");
        }
    }

    private void validateCityLive(String raw) {
        String v = resolve(raw, fieldCity);
        if (v.isEmpty())                   { clearError(fieldCity, errCity); return; }
        if (!v.matches("[0-9]+"))          { showError(fieldCity, errCity, "Digits only"); return; }
        if (v.length() > 3)                { showError(fieldCity, errCity, "Exactly 3 digits"); return; }
        if (v.matches("[0-9]{3}"))         { clearError(fieldCity, errCity); return; }
        // 1–2 digits while still typing → gentle hint
        showError(fieldCity, errCity, "Need exactly 3 digits");
    }

    private void validateBankLive(String raw) {
        String v = resolve(raw, fieldBank);
        if (v.isEmpty())                   { clearError(fieldBank, errBank); return; }
        if (!v.matches("[0-9]+"))          { showError(fieldBank, errBank, "Digits only"); return; }
        if (v.length() > 3)                { showError(fieldBank, errBank, "Exactly 3 digits"); return; }
        if (v.matches("[0-9]{3}"))         { clearError(fieldBank, errBank); return; }
        showError(fieldBank, errBank, "Need exactly 3 digits");
    }

    private void validateBranchLive(String raw) {
        String v = resolve(raw, fieldBranch);
        if (v.isEmpty())                   { clearError(fieldBranch, errBranch); return; }
        if (!v.matches("[0-9]+"))          { showError(fieldBranch, errBranch, "Digits only"); return; }
        if (v.length() > 3)                { showError(fieldBranch, errBranch, "Exactly 3 digits"); return; }
        if (v.matches("[0-9]{3}"))         { clearError(fieldBranch, errBranch); return; }
        showError(fieldBranch, errBranch, "Need exactly 3 digits");
    }

    private void validateTcLive(String raw) {
        String v = resolve(raw, fieldTc);
        if (v.isEmpty())          { clearError(fieldTc, errTc); return; }
        if (!v.matches("[0-9]+")) { showError(fieldTc, errTc, "Digits only"); return; }
        if (v.length() > 2)      { showError(fieldTc, errTc, "Max 2 digits"); return; }
        if (v.length() == 2)     { clearError(fieldTc, errTc); return; }
        // 1 digit — still typing, gentle hint
        showError(fieldTc, errTc, "Need exactly 2 digits");
    }

    private void validateAmountLive(String raw) {
        String v = resolve(raw, fieldAmount);
        if (v.isEmpty())                   { clearError(fieldAmount, errAmount); return; }
        if (!v.matches("[0-9]+"))          { showError(fieldAmount, errAmount, "Digits only, no decimals"); return; }
        try {
            if (Long.parseLong(v) <= 0)    { showError(fieldAmount, errAmount, "Must be greater than 0"); return; }
        } catch (NumberFormatException e)  { showError(fieldAmount, errAmount, "Too large — enter a valid amount"); return; }
        clearError(fieldAmount, errAmount);
    }

    private void validateAccLive(String raw) {
        String v = resolve(raw, fieldAcc);
        if (v.isEmpty())          { clearError(fieldAcc, errAcc); return; }
        if (!v.matches("[0-9]+")) { showError(fieldAcc, errAcc, "Digits only"); return; }
        if (v.length() > 15)     { showError(fieldAcc, errAcc, "Max 15 digits"); return; }
        if (v.length() == 15)    { clearError(fieldAcc, errAcc); return; }
        // Less than 15 — still typing, gentle live hint
        showError(fieldAcc, errAcc, "Need exactly 15 digits (" + v.length() + "/15)");
    }

    private void validatePayeeLive(String raw) {
        String v = resolve(raw, fieldPayee);
        if (v.isEmpty())                   { clearError(fieldPayee, errPayee); return; }
        if (v.length() > 100)              { showError(fieldPayee, errPayee, "Max 100 characters"); return; }
        if (v.length() >= 2)               { clearError(fieldPayee, errPayee); return; }
        // 1 char — still typing
        showError(fieldPayee, errPayee, "At least 2 characters required");
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // SHARED SHOW / CLEAR HELPERS
    // ═══════════════════════════════════════════════════════════════════════════

    /**
     * Applies a red border to the field and shows the error label.
     *
     * Why the replace dance on existing style?
     * ZK Textbox getStyle() returns whatever was previously set via setStyle().
     * If we blindly append ";border-color:#EF4444;" every call, we accumulate
     * duplicate declarations:
     *   "font-size:13px;border-color:#EF4444;;border-color:#EF4444;"
     * The replace strips any old border-color first, then adds the new one.
     *
     * Returns false so callers can write: valid = showError(...)
     */
    private boolean showError(Textbox field, Label errLabel, String message) {
        if (field != null) {
            String existing = field.getStyle();
            String base = existing != null
                ? existing
                    .replace(";border-color:#EF4444;", "")
                    .replace("border-color:#EF4444;", "")
                    .replace(";border-color:#22C55E;", "")
                    .replace("border-color:#22C55E;", "")
                : "";
            field.setStyle(base + ";border-color:#EF4444;box-shadow:0 0 0 2px rgba(239,68,68,0.15);");
        }
        if (errLabel != null) {
            errLabel.setValue(message);
            errLabel.setVisible(true);
        }
        return false;
    }

    /**
     * Removes the red border and hides the error label.
     * Applies a subtle green border so the maker sees the field is now valid.
     */
    private void clearError(Textbox field, Label errLabel) {
        if (field != null) {
            String style = field.getStyle();
            if (style != null) {
                String cleaned = style
                    .replace(";border-color:#EF4444;box-shadow:0 0 0 2px rgba(239,68,68,0.15);", "")
                    .replace(";border-color:#EF4444;", "")
                    .replace("border-color:#EF4444;", "")
                    .replace(";border-color:#22C55E;box-shadow:0 0 0 2px rgba(34,197,94,0.12);", "")
                    .replace(";border-color:#22C55E;", "")
                    .replace("border-color:#22C55E;", "");
                field.setStyle(cleaned + ";border-color:#22C55E;box-shadow:0 0 0 2px rgba(34,197,94,0.12);");
            } else {
                field.setStyle("border-color:#22C55E;box-shadow:0 0 0 2px rgba(34,197,94,0.12);");
            }
        }
        if (errLabel != null) {
            errLabel.setValue("");
            errLabel.setVisible(false);
        }
    }

    /** showError variant for Datebox (no inline-style border trick — Datebox renders differently). */
    private void showDateError(Label errLabel, String message) {
        if (errLabel != null) {
            errLabel.setValue(message);
            errLabel.setVisible(true);
        }
    }

    /** clearError variant for Datebox. */
    private void clearDateError(Label errLabel) {
        if (errLabel != null) {
            errLabel.setValue("");
            errLabel.setVisible(false);
        }
    }

    /** showError variant for Combobox fields. */
    private boolean showComboboxError(Combobox field, Label errLabel, String message) {
        if (field != null) {
            String existing = field.getStyle();
            String base = existing != null
                ? existing
                    .replace(";border-color:#EF4444;", "")
                    .replace("border-color:#EF4444;", "")
                : "";
            field.setStyle(base + ";border-color:#EF4444;box-shadow:0 0 0 2px rgba(239,68,68,0.15);");
        }
        if (errLabel != null) {
            errLabel.setValue(message);
            errLabel.setVisible(true);
        }
        return false;
    }

    /** clearError variant for Combobox fields. */
    private void clearComboboxError(Combobox field, Label errLabel) {
        if (field != null) {
            String style = field.getStyle();
            if (style != null) {
                field.setStyle(style
                    .replace(";border-color:#EF4444;box-shadow:0 0 0 2px rgba(239,68,68,0.15);", "")
                    .replace(";border-color:#EF4444;", "")
                    .replace("border-color:#EF4444;", ""));
            }
        }
        if (errLabel != null) {
            errLabel.setValue("");
            errLabel.setVisible(false);
        }
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // UTILITY HELPERS
    // ═══════════════════════════════════════════════════════════════════════════

    /**
     * Safe getValue() — returns trimmed string, never null.
     * ZK Textbox.getValue() can return null if not set.
     */
    private String safeGet(Textbox field) {
        if (field == null || field.getValue() == null) return "";
        return field.getValue().trim();
    }

    /**
     * For live validation:
     * If typedValue != null  → use it (from ON_CHANGING InputEvent, trim it)
     * If typedValue == null  → use getValue() (from ON_CHANGE / blur)
     *
     * Why not always use getValue()?
     * ON_CHANGING fires BEFORE getValue() is updated with the latest keystroke.
     * So getValue() returns the text BEFORE the keystroke.
     * InputEvent.getValue() gives the text INCLUDING the latest keystroke.
     */
    private String resolve(String typedValue, Textbox field) {
        if (typedValue != null) return typedValue.trim();
        return safeGet(field);
    }
}