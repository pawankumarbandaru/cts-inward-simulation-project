package com.cts.component;

import static org.junit.jupiter.api.Assertions.*;


import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.zkoss.zul.Combobox;
import org.zkoss.zul.Datebox;
import org.zkoss.zul.Label;
import org.zkoss.zul.Textbox;

/**
 * Unit tests for MakerFieldValidator.
 *
 * IMPORTANT NOTE FOR YOUR MENTOR / SETUP:
 *   MakerFieldValidator only calls getValue()/setValue()/setStyle()/setVisible()
 *   on plain ZK component objects — it never needs a live browser Desktop/Session.
 *   That means we can create REAL Textbox/Label/Datebox objects with `new`
 *   (not mocks) and test the validator exactly like a normal POJO. This is the
 *   simplest possible kind of unit test — no Mockito needed here at all.
 *
 * Each test sets up ONE field as invalid and checks validateAll() returns false
 * AND that the matching error label became visible with the right message.
 */
class MakerFieldValidatorTest {

    private Textbox fieldChequeNo, fieldCity, fieldBank, fieldBranch, fieldTc,
                    fieldAmount, fieldAcc, fieldPayee, fieldRemarks;
    private Datebox fieldDate;
    private Combobox fieldRemarksSelect;

    private Label errChequeNo, errCity, errBank, errBranch, errTc,
                  errAmount, errDate, errAcc, errPayee, errRemarks;

    private MakerFieldValidator validator;

    @BeforeEach
    void setUp() {
        fieldChequeNo = new Textbox();
        fieldCity     = new Textbox();
        fieldBank     = new Textbox();
        fieldBranch   = new Textbox();
        fieldTc       = new Textbox();
        fieldAmount   = new Textbox();
        fieldAcc      = new Textbox();
        fieldPayee    = new Textbox();
        fieldRemarks  = new Textbox();
        fieldDate     = new Datebox();
        fieldRemarksSelect = new Combobox();

        errChequeNo = new Label();
        errCity     = new Label();
        errBank     = new Label();
        errBranch   = new Label();
        errTc       = new Label();
        errAmount   = new Label();
        errDate     = new Label();
        errAcc      = new Label();
        errPayee    = new Label();
        errRemarks  = new Label();

        validator = new MakerFieldValidator(
            fieldChequeNo, fieldCity, fieldBank, fieldBranch, fieldTc,
            fieldAmount, fieldDate, fieldAcc, fieldPayee,
            fieldRemarksSelect, fieldRemarks,
            errChequeNo, errCity, errBank, errBranch, errTc,
            errAmount, errDate, errAcc, errPayee, errRemarks
        );

        fillAllFieldsWithValidValues();
    }

    /** Sets every field to a value that PASSES validation, so each test below
     *  only needs to break ONE field to isolate that rule. */
    private void fillAllFieldsWithValidValues() {
        fieldChequeNo.setValue("123456");
        fieldCity.setValue("400");
        fieldBank.setValue("011");
        fieldBranch.setValue("002");
        fieldTc.setValue("10");
        fieldAmount.setValue("5000");
        fieldDate.setValue(new java.util.Date()); // today
        fieldAcc.setValue("123456789012");
        fieldPayee.setValue("Ramana");
    }

    @Test
    @DisplayName("All fields valid -> validateAll returns true")
    void validateAll_allFieldsValid_returnsTrue() {
        assertTrue(validator.validateAll());
    }

    @Test
    @DisplayName("Empty cheque number -> invalid, correct error message shown")
    void validateAll_emptyChequeNo_returnsFalseWithMessage() {
        fieldChequeNo.setValue("");

        boolean result = validator.validateAll();

        assertFalse(result);
        assertTrue(errChequeNo.isVisible());
        assertEquals("Cheque number is required", errChequeNo.getValue());
    }

    @Test
    @DisplayName("Cheque number with letters -> invalid, 'Digits only' message")
    void validateAll_chequeNoWithLetters_returnsFalse() {
        fieldChequeNo.setValue("AB123");

        assertFalse(validator.validateAll());
        assertEquals("Digits only", errChequeNo.getValue());
    }

    @Test
    @DisplayName("City code not exactly 3 digits -> invalid")
    void validateAll_cityCodeWrongLength_returnsFalse() {
        fieldCity.setValue("12"); // only 2 digits, rule requires exactly 3

        assertFalse(validator.validateAll());
        assertEquals("Exactly 3 digits required", errCity.getValue());
    }

    @Test
    @DisplayName("Amount is zero -> invalid, 'must be greater than 0'")
    void validateAll_zeroAmount_returnsFalse() {
        fieldAmount.setValue("0");

        assertFalse(validator.validateAll());
        assertEquals("Amount must be greater than 0", errAmount.getValue());
    }

    @Test
    @DisplayName("Cheque date in the future -> invalid")
    void validateAll_futureDate_returnsFalse() {
        java.util.Calendar cal = java.util.Calendar.getInstance();
        cal.add(java.util.Calendar.DAY_OF_MONTH, 5); // 5 days from today
        fieldDate.setValue(cal.getTime());

        assertFalse(validator.validateAll());
        assertEquals("Date cannot be in the future", errDate.getValue());
    }

    @Test
    @DisplayName("Cheque date older than 90 days -> invalid")
    void validateAll_chequeOlderThan90Days_returnsFalse() {
        java.util.Calendar cal = java.util.Calendar.getInstance();
        cal.add(java.util.Calendar.DAY_OF_MONTH, -100); // 100 days old
        fieldDate.setValue(cal.getTime());

        assertFalse(validator.validateAll());
        assertEquals("Cheque is older than 90 days", errDate.getValue());
    }

    @Test
    @DisplayName("Payee name only 1 character -> invalid")
    void validateAll_payeeTooShort_returnsFalse() {
        fieldPayee.setValue("R");

        assertFalse(validator.validateAll());
        assertEquals("At least 2 characters required", errPayee.getValue());
    }

    @Test
    @DisplayName("Account number with 19 digits -> invalid (max 18)")
    void validateAll_accountNumberTooLong_returnsFalse() {
        fieldAcc.setValue("1234567890123456789"); // 19 digits

        assertFalse(validator.validateAll());
        assertEquals("Max 18 digits", errAcc.getValue());
    }

    @Test
    @DisplayName("Fixing a previously invalid field clears its error label")
    void validateAll_fixInvalidField_clearsErrorLabel() {
        fieldChequeNo.setValue("");
        validator.validateAll();
        assertTrue(errChequeNo.isVisible());

        // Now fix it and re-validate
        fieldChequeNo.setValue("987654");
        validator.validateAll();

        assertFalse(errChequeNo.isVisible());
        assertEquals("", errChequeNo.getValue());
    }
}
