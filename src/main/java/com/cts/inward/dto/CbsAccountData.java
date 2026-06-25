package com.cts.inward.dto;

import java.util.HashMap;
import java.util.Map;

/**
 * Represents account information fetched from CBS Firebase.
 *
 * Matches the actual Firebase JSON structure:
 * {
 *   "accountNo"         : "10012345678",
 *   "accountName"       : "Ramesh Kumar",          ← NOT accountHolderName
 *   "accountStatus"     : "ACTIVE",                ← String, NOT boolean active
 *   "balance"           : 500000.0,                ← double amount, NOT sufficientFunds flag
 *   "stopPayment"       : false,                   ← NOT stopPaymentMarked
 *   "bankName"          : "State Bank of India",
 *   "branchName"        : "Chennai Main Branch",
 *   "registeredCheques" : {                        ← nested map keyed by chequeNo string
 *     "100001" : {
 *       "chequeNo"      : "100001",
 *       "amount"        : 50000.0,
 *       "amountInWords" : "...",
 *       "chequeDate"    : "2026-06-06",
 *       "micr"          : { "cityCode":"600", "bankCode":"002", "branchCode":"001" },
 *       "status"        : "ACTIVE",
 *       "presented"     : false,
 *       "paid"          : false
 *     }
 *   }
 * }
 */
public class CbsAccountData {

    private String              accountNo;
    private String              accountName;       // was accountHolderName — FIXED
    private String              accountStatus;     // "ACTIVE" / "INACTIVE" — was boolean — FIXED
    private double              balance;           // actual amount — was boolean sufficientFunds — FIXED
    private boolean             stopPayment;       // was stopPaymentMarked — FIXED
    private String              bankName;
    private String              branchName;

    /**
     * Nested cheque records from registeredCheques.
     * Key = chequeNo string, Value = CbsRegisteredCheque.
     * Used by validateCheque() to check if a specific cheque exists in CBS.
     */
    private Map<String, CbsRegisteredCheque> registeredCheques = new HashMap<>();

    // ── Constructors ─────────────────────────────────────────────────────

    public CbsAccountData() {}

    // ── Getters & Setters ────────────────────────────────────────────────

    public String getAccountNo()                    { return accountNo; }
    public void   setAccountNo(String v)            { this.accountNo = v; }

    public String getAccountName()                  { return accountName; }
    public void   setAccountName(String v)          { this.accountName = v; }

    public String getAccountStatus()                { return accountStatus; }
    public void   setAccountStatus(String v)        { this.accountStatus = v; }

    /** Returns true if accountStatus equals "ACTIVE" (case-insensitive). */
    public boolean isActive() {
        return "ACTIVE".equalsIgnoreCase(accountStatus);
    }

    public double  getBalance()                     { return balance; }
    public void    setBalance(double v)             { this.balance = v; }

    public boolean isStopPayment()                  { return stopPayment; }
    public void    setStopPayment(boolean v)        { this.stopPayment = v; }

    public String  getBankName()                    { return bankName; }
    public void    setBankName(String v)            { this.bankName = v; }

    public String  getBranchName()                  { return branchName; }
    public void    setBranchName(String v)          { this.branchName = v; }

    public Map<String, CbsRegisteredCheque> getRegisteredCheques() {
        return registeredCheques;
    }
    public void setRegisteredCheques(Map<String, CbsRegisteredCheque> v) {
        this.registeredCheques = v != null ? v : new HashMap<>();
    }

    /**
     * Convenience: returns the registered cheque for the given cheque number,
     * or null if not found in CBS.
     */
    public CbsRegisteredCheque getCheque(String chequeNo) {
        if (chequeNo == null || registeredCheques == null) return null;
        return registeredCheques.get(chequeNo.trim());
    }

    // ── Inner class: registered cheque node ──────────────────────────────

    public static class CbsRegisteredCheque {
        private String  chequeNo;
        private double  amount;
        private String  amountInWords;
        private String  chequeDate;
        private String  status;       // "ACTIVE" / etc.
        private boolean presented;
        private boolean paid;

        public String  getChequeNo()             { return chequeNo; }
        public void    setChequeNo(String v)     { this.chequeNo = v; }

        public double  getAmount()               { return amount; }
        public void    setAmount(double v)       { this.amount = v; }

        public String  getAmountInWords()        { return amountInWords; }
        public void    setAmountInWords(String v){ this.amountInWords = v; }

        public String  getChequeDate()           { return chequeDate; }
        public void    setChequeDate(String v)   { this.chequeDate = v; }

        public String  getStatus()               { return status; }
        public void    setStatus(String v)       { this.status = v; }

        public boolean isPresented()             { return presented; }
        public void    setPresented(boolean v)   { this.presented = v; }

        public boolean isPaid()                  { return paid; }
        public void    setPaid(boolean v)        { this.paid = v; }

        public boolean isActive() {
            return "ACTIVE".equalsIgnoreCase(status);
        }
    }
}