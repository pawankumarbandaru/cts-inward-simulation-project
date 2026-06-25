package com.cts.exceptions;

/**
 * File    : ChequeNotFoundException.java
 * Package : com.cts.exceptions
 * Purpose : Thrown when a cheque number does not exist in the database, or exists
 *           but is no longer in an actionable state (e.g. already resubmitted by someone else).
 *           Used in: InwardChequeServiceImpl — resubmitToChecker(), generateRRF(), getChequeForRepair()
 * Author  : Ramana
 * Date    : 24-06-2025
 */
public class ChequeNotFoundException extends CtsBusinessException {

    private static final long serialVersionUID = 1L;

    public ChequeNotFoundException(String chequeNo) {
        super("Cheque not found or no longer pending correction: " + chequeNo);
    }
}