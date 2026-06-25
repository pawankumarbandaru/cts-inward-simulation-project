package com.cts.exceptions;

/**
 * File    : CbsValidationException.java
 * Package : com.cts.exceptions
 * Purpose : Thrown when something goes wrong while recording a CBS validation result
 *           (e.g. missing cheque ID, DB write failed).
 *           NOT used for normal CBS business failures like INSUFFICIENT_FUNDS —
 *           those are expected outcomes and are handled in MakerCbsValidator.handleFail().
 *           Used in: InwardChequeServiceImpl.saveCbsResult()
 * Author  : Ramana
 * Date    : 24-06-2025
 */
public class CbsValidationException extends CtsBusinessException {

    private static final long serialVersionUID = 1L;

    public CbsValidationException(String reason) {
        super("CBS validation could not be processed: " + reason);
    }

    public CbsValidationException(String reason, Throwable cause) {
        super("CBS validation could not be processed: " + reason, cause);
    }
}