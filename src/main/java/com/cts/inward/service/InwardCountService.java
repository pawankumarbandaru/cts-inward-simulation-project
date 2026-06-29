package com.cts.inward.service;

import com.cts.inward.dao.InwardChequeDao;
import com.cts.inward.dao.InwardChequeDaoImpl;

/**
 * Small service that returns the "work waiting" counts shown as badges
 * on the sidebar and dashboard for each role.
 *
 * All three queries already exist in InwardChequeDao — this service just
 * exposes them in one place so the UI composers have a single call.
 */
public class InwardCountService {

    private final InwardChequeDao chequeDao = new InwardChequeDaoImpl();

    /** MAKER: cheques returned by checker, waiting for maker correction. */
    public long getMakerPendingCount() {
        try {
            return chequeDao.countNeedingCorrection();
        } catch (Exception e) {
            System.err.println("InwardCountService: maker count failed - " + e.getMessage());
            return 0L;
        }
    }

    /** TV1: cheques resubmitted by maker, waiting for TV1 review. */
    public long getTv1ResubmittedCount() {
        try {
            return chequeDao.countResubmittedForChecker();
        } catch (Exception e) {
            System.err.println("InwardCountService: TV1 count failed - " + e.getMessage());
            return 0L;
        }
    }

    /** TV2: cheques resubmitted by maker, waiting for TV2 review. */
    public long getTv2ResubmittedCount() {
        try {
            return chequeDao.countResubmittedForVerifier2();
        } catch (Exception e) {
            System.err.println("InwardCountService: TV2 count failed - " + e.getMessage());
            return 0L;
        }
    }
    
    /** TV2: cheques referred (sitting with maker for correction) — tracking count. */
    public long getTv2ReferredCount() {
        try {
            return chequeDao.countReferredCheques();
        } catch (Exception e) {
            System.err.println("InwardCountService: TV2 referred count failed - " + e.getMessage());
            return 0L;
        }
    }
}