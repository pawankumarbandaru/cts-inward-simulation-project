package com.cts.exceptions;

/**
 * File    : InvalidChequeDataException.java
 * Package : com.cts.exceptions
 * Purpose : Thrown when cheque data is missing or invalid at the service layer.
 *           This is the server-side safety net behind MakerFieldValidator's UI checks,
 *           ensuring the DB never receives bad data even if saveCorrections() is called
 *           without going through the UI validator first.
 *           Used in: InwardChequeServiceImpl.saveCorrections(), getChequesByBatchId(), updateCheque()
 * Author  : Ramana
 * Date    : 24-06-2025
 */
public class InvalidChequeDataException extends CtsBusinessException {

    private static final long serialVersionUID = 1L;

    public InvalidChequeDataException(String reason) {
        super("Invalid cheque data: " + reason);
    }
}