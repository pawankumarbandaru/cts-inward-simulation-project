package com.cts.inward.dto;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * DTO used by the Inward Report batch-detail view.
 * Carries every column shown in the cheque grid:
 *   Cheque No | Account No | Payee Name | Amount | Status | Reason
 * Also carries micrCode and chequeDate so the PDF report can populate
 * the MICR and Date columns correctly when downloading filtered cheques.
 */
public class ReportChequeDetailDTO {

    private String        chequeNo;
    private String        accountNo;
    private String        payeeName;
    private BigDecimal    amount;
    private String        status;      // "ACCEPTED" | "REJECTED" | "PENDING"
    private String        reason;      // errorReason — null for accepted / pending
    private String        micrCode;    // for PDF report
    private LocalDateTime chequeDate;  // for PDF report

    /** Required by Hibernate HQL 'new' projection */
    public ReportChequeDetailDTO(String chequeNo,
                                 String accountNo,
                                 String payeeName,
                                 BigDecimal amount,
                                 String status,
                                 String reason,
                                 String micrCode,
                                 LocalDateTime chequeDate) {
        this.chequeNo   = chequeNo;
        this.accountNo  = accountNo;
        this.payeeName  = payeeName;
        this.amount     = amount;
        this.status     = status;
        this.reason     = reason;
        this.micrCode   = micrCode;
        this.chequeDate = chequeDate;
    }

    public String        getChequeNo()               { return chequeNo; }
    public void          setChequeNo(String v)        { this.chequeNo = v; }

    public String        getAccountNo()              { return accountNo; }
    public void          setAccountNo(String v)       { this.accountNo = v; }

    public String        getPayeeName()              { return payeeName; }
    public void          setPayeeName(String v)       { this.payeeName = v; }

    public BigDecimal    getAmount()                 { return amount; }
    public void          setAmount(BigDecimal v)      { this.amount = v; }

    public String        getStatus()                 { return status; }
    public void          setStatus(String v)          { this.status = v; }

    public String        getReason()                 { return reason; }
    public void          setReason(String v)          { this.reason = v; }

    public String        getMicrCode()               { return micrCode; }
    public void          setMicrCode(String v)        { this.micrCode = v; }

    public LocalDateTime getChequeDate()             { return chequeDate; }
    public void          setChequeDate(LocalDateTime v) { this.chequeDate = v; }
}
