package com.cts.inward.dto;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public class ChequeReportDTO {

    private String        batchId;   // ← used for grouping in JRXML
    private String        chequeNo;
    private LocalDateTime chequeDate;
    private String        micrCode;
    private String        payeeName;
    private BigDecimal    amount;
    private String        reason;

    /** Required by Hibernate HQL 'new' projection */
    public ChequeReportDTO(String batchId, String chequeNo, LocalDateTime chequeDate,
                           String micrCode, String payeeName, BigDecimal amount, String reason) {
        this.batchId    = batchId;
        this.chequeNo   = chequeNo;
        this.chequeDate = chequeDate;
        this.micrCode   = micrCode;
        this.payeeName  = payeeName;
        this.amount     = amount;
        this.reason     = reason;
    }

    public String getBatchId()                 { return batchId; }
    public void   setBatchId(String v)         { this.batchId = v; }

    public String getChequeNo()                { return chequeNo; }
    public void   setChequeNo(String v)        { this.chequeNo = v; }

    public LocalDateTime getChequeDate()       { return chequeDate; }
    public void          setChequeDate(LocalDateTime v) { this.chequeDate = v; }

    public String getMicrCode()                { return micrCode; }
    public void   setMicrCode(String v)        { this.micrCode = v; }

    public String getPayeeName()               { return payeeName; }
    public void   setPayeeName(String v)       { this.payeeName = v; }

    public BigDecimal getAmount()              { return amount; }
    public void       setAmount(BigDecimal v)  { this.amount = v; }

    public String getReason()                  { return reason; }
    public void   setReason(String v)          { this.reason = v; }
}
