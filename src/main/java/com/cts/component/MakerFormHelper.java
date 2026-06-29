package com.cts.component;

/**
 * File    : MakerFormHelper.java
 * Package : com.cts.component
 * Purpose : Populates and manages the cheque repair form in the Maker Repair Workspace.
 *           Handles field population, live preview updates (amount-in-words, MICR line),
 *           applying field values back to the entity before saving, and
 *           mode switching between editable (Maker) and read-only (Checker) views.
 * Author  : Ramana
 * Date    : 24-06-2025
 */

import java.math.BigDecimal;
import java.text.SimpleDateFormat;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;

import org.zkoss.zk.ui.util.Clients;
import org.zkoss.zul.Datebox;
import org.zkoss.zul.Label;
import org.zkoss.zul.Textbox;

import com.cts.inward.entity.InwardCheque;
import com.cts.util.ChequeRoutingUtil;

public class MakerFormHelper {

    private final Label   chequePill;
    private final Label   warnTitle;
    private final Label   warnRolePill;
    private final Label   warnReasonShort;
    private final Label   warnDetail;
    private final Label   warnCbsReason;
    private final Label   imgBank;
    private final Label   imgDate;
    private final Label   imgPayee;
    private final Label   imgWords;
    private final Label   imgAmt;
    private final Label   imgMicr1;
    private final Label   imgMicr2;
    private final Label   micrStripLive;
    private final Label   imgBackAcc;
    private final Textbox fieldChequeNo;
    private final Textbox fieldCity;
    private final Textbox fieldBank;
    private final Textbox fieldBranch;
    private final Textbox fieldTc;
    private final Textbox fieldAmount;
    private final Label   fieldWords;
    private final Datebox fieldDate;
    private final Textbox fieldAcc;
    private final Textbox fieldPayee;
    private final Textbox fieldRemarks;

    public MakerFormHelper(
            Label chequePill,
            Label warnTitle, Label warnRolePill, Label warnReasonShort,
            Label warnDetail, Label warnCbsReason,
            Label imgBank, Label imgDate, Label imgPayee, Label imgWords, Label imgAmt,
            Label imgMicr1, Label imgMicr2, Label micrStripLive, Label imgBackAcc,
            Textbox fieldChequeNo,
            Textbox fieldCity, Textbox fieldBank, Textbox fieldBranch,
            Textbox fieldTc, Textbox fieldAmount, Label fieldWords,
            Datebox fieldDate, Textbox fieldAcc,
            Textbox fieldPayee, Textbox fieldRemarks) {

        this.chequePill      = chequePill;
        this.warnTitle       = warnTitle;
        this.warnRolePill    = warnRolePill;
        this.warnReasonShort = warnReasonShort;
        this.warnDetail      = warnDetail;
        this.warnCbsReason   = warnCbsReason;
        this.imgBank         = imgBank;
        this.imgDate         = imgDate;
        this.imgPayee        = imgPayee;
        this.imgWords        = imgWords;
        this.imgAmt          = imgAmt;
        this.imgMicr1        = imgMicr1;
        this.imgMicr2        = imgMicr2;
        this.micrStripLive   = micrStripLive;
        this.imgBackAcc      = imgBackAcc;
        this.fieldChequeNo   = fieldChequeNo;
        this.fieldCity       = fieldCity;
        this.fieldBank       = fieldBank;
        this.fieldBranch     = fieldBranch;
        this.fieldTc         = fieldTc;
        this.fieldAmount     = fieldAmount;
        this.fieldWords      = fieldWords;
        this.fieldDate       = fieldDate;
        this.fieldAcc        = fieldAcc;
        this.fieldPayee      = fieldPayee;
        this.fieldRemarks    = fieldRemarks;
    }

    // ── Page population ────────────────────────────────────────────────────────

    /**
     * Populates all UI fields and labels from the loaded InwardCheque.
     * Called once when the Maker Repair Workspace opens.
     *
     * @param cheque the cheque to display
     */
    public void populatePage(InwardCheque cheque) {
        if (cheque == null) return;

        // Title bar
        chequePill.setValue("Cheque " + safeStr(cheque.getChequeNo()));

        warnTitle.setValue("RETURNED BY CHECKER");

        // Show CRITICAL for high-value (TV_2) cheques, URGENT for normal (TV_1)
        boolean isHighValue = cheque.getAmount() != null
            && "TV_2".equals(ChequeRoutingUtil.routeForAsString(cheque.getAmount().doubleValue()));
        warnRolePill.setValue(isHighValue ? "CRITICAL" : "URGENT");

        String referReason = cheque.getSendbackReason() != null
            ? cheque.getSendbackReason() : "—";
        warnReasonShort.setValue("Checker Reason: " + referReason);

        // warnDetail is hidden — kept wired in case a second info line is needed in future
        warnDetail.setVisible(false);

        // warnCbsReason is hidden — kept wired in case CBS reason needs to be shown later
        if (warnCbsReason != null) warnCbsReason.setVisible(false);

        // Cheque image overlay labels
        imgBank.setValue(safeStr(cheque.getPresentingBank()));
        imgDate.setValue(cheque.getChequeDate() != null
            ? cheque.getChequeDate().format(DateTimeFormatter.ofPattern("dd/MM/yyyy")) : "—");
        imgPayee.setValue(safeStr(cheque.getPayeeName()));
        imgWords.setValue(safeStr(cheque.getAmountInWords()));
        imgAmt.setValue(cheque.getAmount() != null
            ? "₹" + String.format("%,.2f", cheque.getAmount()) : "—");

        // MICR band display
        String micrBand = buildMicrBandDisplay(cheque);
        imgMicr1.setValue("⑆" + safeStr(cheque.getChequeNo()) + "⑆");
        imgMicr2.setValue(micrBand + "⑈");
        micrStripLive.setValue(
            "⑆" + safeStr(cheque.getChequeNo()) + "⑆ "
            + micrBand + "⑈ ⑉" + safeStr(cheque.getTransactionCode(), "29") + "⑉"
        );
        imgBackAcc.setValue(safeStr(cheque.getAccountNo()));

        // Editable form fields
        fieldChequeNo.setValue(safeStr(cheque.getChequeNo()));
        fieldCity.setValue(cheque.getMicrCityCode());
        fieldBank.setValue(cheque.getMicrBankCode());
        fieldBranch.setValue(cheque.getMicrBranchCode());
        fieldTc.setValue(safeStr(cheque.getTransactionCode(), "29"));

        String amtStr = cheque.getAmount() != null
            ? String.valueOf(cheque.getAmount().longValue()) : "";
        fieldAmount.setValue(amtStr);
        fieldWords.setValue(cheque.getAmount() != null
            ? convertAmountToWords(cheque.getAmount().longValue()) : "—");

        fieldAcc.setValue(safeStr(cheque.getAccountNo()));
        fieldPayee.setValue(safeStr(cheque.getPayeeName()));
        fieldRemarks.setValue("");

        if (cheque.getChequeDate() != null) {
            try {
                String dateStr = cheque.getChequeDate()
                    .format(DateTimeFormatter.ofPattern("dd/MM/yyyy"));
                java.util.Date parsedDate = new SimpleDateFormat("dd/MM/yyyy").parse(dateStr);
                fieldDate.setValue(parsedDate);
            } catch (Exception ex) {
                fieldDate.setValue(null);
            }
        }
    }

    // ── Live preview updates ──────────────────────────────────────────────────

    /**
     * Updates the "Amount in Words" label as the maker types in the Amount field.
     * Does NOT update the entity — that happens in applyFieldsToEntity() on Save.
     *
     * @param typedAmount the current raw string from the Amount textbox
     */
    public void updateAmountInWordsLive(String typedAmount) {
        if (fieldWords == null) return;

        if (typedAmount == null || typedAmount.trim().isEmpty()) {
            fieldWords.setValue("—");
            return;
        }

        try {
            long amt = Long.parseLong(typedAmount.trim());
            fieldWords.setValue(convertAmountToWords(amt));
        } catch (NumberFormatException ex) {
            fieldWords.setValue("—");
        }
    }

    /**
     * Updates the live MICR line preview as the maker types in any MICR field.
     * Shows "—" for fields that don't yet match the expected format,
     * so the partial value doesn't produce a confusing half-built MICR string.
     *
     * @param chequeNo current cheque number field value
     * @param city     current city code field value
     * @param bank     current bank code field value
     * @param branch   current branch code field value
     * @param tc       current transaction code field value
     */
    public void updateMicrLinePreviewLive(String chequeNo, String city,
                                           String bank, String branch, String tc) {
        if (micrStripLive == null) return;

        String chq    = safeOrDash(chequeNo, "[0-9]+");
        String cityV  = safeOrDash(city,     "[0-9]{3}");
        String bankV  = safeOrDash(bank,     "[0-9]{3}");
        String branchV= safeOrDash(branch,   "[0-9]{3}");
        String tcV    = safeOrDash(tc,        "[0-9]{2}");

        micrStripLive.setValue(
            "⑆" + chq + "⑆ " + cityV + bankV + branchV + "⑈ ⑉" + tcV + "⑉"
        );
    }

    // ── Apply to entity ───────────────────────────────────────────────────────

    /**
     * Reads all UI field values into the InwardCheque entity before saving.
     * Also tracks which fields changed so the Checker screen can highlight the edited row.
     *
     * @param cheque the entity to update
     * @param amount the parsed BigDecimal amount from the form field
     */
    public void applyFieldsToEntity(InwardCheque cheque, BigDecimal amount) {

        List<String> changedFields = new ArrayList<>();

        String newChequeNo = fieldChequeNo.getValue().trim();
        if (!newChequeNo.equals(safeStr(cheque.getChequeNo()))) changedFields.add("cheque_no");
        cheque.setChequeNo(newChequeNo);

        // Use compareTo (not equals) to ignore BigDecimal scale differences
        if (cheque.getAmount() == null || cheque.getAmount().compareTo(amount) != 0) changedFields.add("amount");
        cheque.setAmount(amount);

        String newCity   = fieldCity.getValue().trim();
        String newBank   = fieldBank.getValue().trim();
        String newBranch = fieldBranch.getValue().trim();
        if (!newCity.equals(cheque.getMicrCityCode())
                || !newBank.equals(cheque.getMicrBankCode())
                || !newBranch.equals(cheque.getMicrBranchCode())) {
            changedFields.add("micr_code");
        }
        cheque.setMicrCode(rebuildMicrCode(cheque.getMicrCode(), newCity, newBank, newBranch));

        String newTc = fieldTc.getValue().trim();
        if (!newTc.equals(safeStr(cheque.getTransactionCode()))) changedFields.add("transaction_code");
        cheque.setTransactionCode(newTc);

        String newAcc = fieldAcc.getValue().trim();
        if (!newAcc.equals(safeStr(cheque.getAccountNo()))) changedFields.add("account_no");
        cheque.setAccountNo(newAcc);

        String newPayee = fieldPayee.getValue().trim();
        if (!newPayee.equals(safeStr(cheque.getPayeeName()))) changedFields.add("payee_name");
        cheque.setPayeeName(newPayee);

        cheque.setMakerCorrectionRemark(fieldRemarks.getValue().trim());

        // Derive amount-in-words from the new amount
        try {
            String words = convertAmountToWords(amount.longValue());
            fieldWords.setValue(words);
            cheque.setAmountInWords(words);
        } catch (Exception ex) {
            fieldWords.setValue("—");
        }

        if (fieldDate.getValue() != null) {
            try {
                String dateStr = new SimpleDateFormat("dd/MM/yyyy").format(fieldDate.getValue());
                LocalDateTime parsedDt = LocalDateTime.parse(
                    dateStr + " 00:00",
                    DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm")
                );
                if (!parsedDt.equals(cheque.getChequeDate())) changedFields.add("cheque_date");
                cheque.setChequeDate(parsedDt);
            } catch (Exception ex) {
                // Keep existing date if parsing fails
            }
        }

        if (!changedFields.isEmpty()) {
            cheque.setIsEditedByMaker(true);
            cheque.setEditedFields(String.join(",", changedFields));
        }
    }

    // ── MICR helpers ──────────────────────────────────────────────────────────

    /**
     * Updates the MICR code display via JavaScript bridge.
     * Zero-pads each of city, bank, branch to 3 digits before concatenating.
     */
    public void updateMicrDisplay(InwardCheque cheque) {
        if (cheque == null) return;

        String city   = safeStr(cheque.getMicrCityCode());
        String bank   = safeStr(cheque.getMicrBankCode());
        String branch = safeStr(cheque.getMicrBranchCode());

        if (city.isEmpty() && bank.isEmpty() && branch.isEmpty()) {
            Clients.evalJavaScript("if(window.CTS_MICR) CTS_MICR.set('—');");
            return;
        }

        String cityPad   = String.format("%3s", city).replace(' ', '0');
        String bankPad   = String.format("%3s", bank).replace(' ', '0');
        String branchPad = String.format("%3s", branch).replace(' ', '0');
        String micrCode  = cityPad + bankPad + branchPad;

        Clients.evalJavaScript("if(window.CTS_MICR) CTS_MICR.set('" + micrCode + "');");
    }

    /**
     * Returns the 9-digit MICR band string (city + bank + branch) for display.
     */
    public String buildMicrBandDisplay(InwardCheque cheque) {
        return cheque.getMicrCityCode()
             + cheque.getMicrBankCode()
             + cheque.getMicrBranchCode();
    }

    /**
     * Replaces the last 9 characters of the original MICR code with the new city+bank+branch.
     * If the original code is shorter than 9 characters, returns the new band directly.
     */
    private String rebuildMicrCode(String originalMicr, String city, String bank, String branch) {
        String newBand = city + bank + branch;
        if (originalMicr != null && originalMicr.length() > 9) {
            String prefix = originalMicr.substring(0, originalMicr.length() - 9);
            return prefix + newBand;
        }
        return newBand;
    }

    // ── Mode setters ──────────────────────────────────────────────────────────

    /**
     * Unlocks form fields for Maker editing.
     * Shared macros set fields to readonly by default to protect the Checker view.
     * Call this BEFORE populatePage() in the Maker composer.
     */
    public void setEditMode() {
        // Remove readonly from ALL editable fields.
        // ZUL macros set readonly="true" on acc/date/amount/payee to protect
        // the Checker view. We unlock them here for the Maker repair workspace.
        // chequeNo, city, bank, branch, tc do NOT have readonly in ZUL but we
        // clear it defensively in case the ZUL ever changes.
        fieldChequeNo.setReadonly(false);
        fieldCity    .setReadonly(false);
        fieldBank    .setReadonly(false);
        fieldBranch  .setReadonly(false);
        fieldTc      .setReadonly(false);
        fieldAcc     .setReadonly(false);
        fieldDate    .setReadonly(false);
        fieldAmount  .setReadonly(false);
        fieldPayee   .setReadonly(false);

        // Remove ZK inline constraints from all textbox fields.
        // The ZUL macros declare constraint="/^[0-9]*$/: Digits only" on chequeNo,
        // city, bank, branch, tc, amount, and acc. ZK enforces these constraints
        // BEFORE firing ON_CHANGING — so a mid-keystroke partial value that fails
        // the regex (e.g. non-digit typed) blocks our Java live-validation listener
        // from ever receiving the event. We handle all validation in Java
        // (MakerFieldValidator) so ZK constraints are redundant and harmful here.
        fieldChequeNo.setConstraint((org.zkoss.zul.Constraint) null);
        fieldCity    .setConstraint((org.zkoss.zul.Constraint) null);
        fieldBank    .setConstraint((org.zkoss.zul.Constraint) null);
        fieldBranch  .setConstraint((org.zkoss.zul.Constraint) null);
        fieldTc      .setConstraint((org.zkoss.zul.Constraint) null);
        fieldAmount  .setConstraint((org.zkoss.zul.Constraint) null);
        fieldAcc     .setConstraint((org.zkoss.zul.Constraint) null);
    }

    /**
     * Strips ZK constraints and disables all fields for Checker read-only view.
     * Call this BEFORE populateCheckerFields() so setValue() does not throw
     * WrongValueException from regex constraints.
     */
    public void setCheckerMode() {
        fieldChequeNo.setConstraint((org.zkoss.zul.Constraint) null);
        fieldCity    .setConstraint((org.zkoss.zul.Constraint) null);
        fieldBank    .setConstraint((org.zkoss.zul.Constraint) null);
        fieldBranch  .setConstraint((org.zkoss.zul.Constraint) null);
        fieldTc      .setConstraint((org.zkoss.zul.Constraint) null);
        fieldAmount  .setConstraint((org.zkoss.zul.Constraint) null);
        fieldAcc     .setConstraint((org.zkoss.zul.Constraint) null);

        fieldChequeNo.setDisabled(true);
        fieldCity    .setDisabled(true);
        fieldBank    .setDisabled(true);
        fieldBranch  .setDisabled(true);
        fieldTc      .setDisabled(true);
        fieldAmount  .setDisabled(true);
        fieldDate    .setDisabled(true);
        fieldAcc     .setDisabled(true);
        fieldPayee   .setDisabled(true);
        fieldRemarks .setDisabled(true);
    }

    /**
     * Populates only the form input fields — used by the Checker page.
     * Call setCheckerMode() BEFORE this to strip constraints first.
     *
     * @param cheque the cheque to display
     */
    public void populateCheckerFields(InwardCheque cheque) {
        if (cheque == null) return;

        fieldChequeNo.setValue(safeStr(cheque.getChequeNo()));
        fieldCity    .setValue(safeStr(cheque.getMicrCityCode()));
        fieldBank    .setValue(safeStr(cheque.getMicrBankCode()));
        fieldBranch  .setValue(safeStr(cheque.getMicrBranchCode()));
        fieldTc      .setValue(safeStr(cheque.getTransactionCode(), "29"));

        fieldAmount.setValue(
            cheque.getAmount() != null ? cheque.getAmount().toPlainString() : "");
        fieldAcc    .setValue(safeStr(cheque.getAccountNo()));
        fieldPayee  .setValue(safeStr(cheque.getPayeeName()));
        fieldRemarks.setValue(
            cheque.getMakerCorrectionRemark() != null ? cheque.getMakerCorrectionRemark() : "");

        if (fieldWords != null) {
            fieldWords.setValue(
                cheque.getAmountInWords() != null ? cheque.getAmountInWords() : "—");
        }

        if (cheque.getChequeDate() != null) {
            try {
                fieldDate.setValue(java.sql.Date.valueOf(cheque.getChequeDate().toLocalDate()));
            } catch (Exception ex) {
                fieldDate.setValue(null);
            }
        }
    }

    // ── Amount in Words ───────────────────────────────────────────────────────

    /**
     * Converts a numeric amount to its Indian English words representation.
     * Supports up to crores. Returns "—" for amounts <= 0.
     *
     * @param amount the amount in paise/rupees (whole number)
     * @return the amount in words, e.g. "Twenty Five Thousand Only"
     */
    public String convertAmountToWords(long amount) {
        if (amount <= 0) return "—";
        String[] ones = { "", "One", "Two", "Three", "Four", "Five", "Six", "Seven",
            "Eight", "Nine", "Ten", "Eleven", "Twelve", "Thirteen", "Fourteen",
            "Fifteen", "Sixteen", "Seventeen", "Eighteen", "Nineteen" };
        String[] tens = { "", "", "Twenty", "Thirty", "Forty", "Fifty",
            "Sixty", "Seventy", "Eighty", "Ninety" };

        if (amount < 20)
            return ones[(int) amount] + " Only";
        if (amount < 100)
            return tens[(int)(amount / 10)]
                 + (amount % 10 != 0 ? " " + ones[(int)(amount % 10)] : "") + " Only";
        if (amount < 1000)
            return ones[(int)(amount / 100)] + " Hundred"
                 + (amount % 100 != 0 ? " " + convertAmountToWords(amount % 100).replace(" Only", "") : "")
                 + " Only";
        if (amount < 100000)
            return convertAmountToWords(amount / 1000).replace(" Only", "") + " Thousand"
                 + (amount % 1000 != 0 ? " " + convertAmountToWords(amount % 1000).replace(" Only", "") : "")
                 + " Only";
        if (amount < 10000000)
            return convertAmountToWords(amount / 100000).replace(" Only", "")
                 + (amount / 100000 == 1 ? " Lakh" : " Lakhs")
                 + (amount % 100000 != 0 ? " " + convertAmountToWords(amount % 100000).replace(" Only", "") : "")
                 + " Only";
        return convertAmountToWords(amount / 10000000).replace(" Only", "") + " Crore"
             + (amount % 10000000 != 0 ? " " + convertAmountToWords(amount % 10000000).replace(" Only", "") : "")
             + " Only";
    }

    // ── Private helpers ────────────────────────────────────────────────────────

    private String safeStr(String val)                  { return val != null ? val : ""; }
    private String safeStr(String val, String fallback) { return (val != null && !val.isBlank()) ? val : fallback; }

    /**
     * Returns the trimmed value if it fully matches the given regex pattern,
     * otherwise returns "—". Used only in live MICR preview to avoid showing
     * partial/invalid values mid-typing.
     */
    private String safeOrDash(String value, String pattern) {
        if (value == null) return "—";
        String trimmed = value.trim();
        return trimmed.matches(pattern) ? trimmed : "—";
    }
}