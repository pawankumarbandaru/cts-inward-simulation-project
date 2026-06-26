package com.cts.inward.service;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import java.math.BigDecimal;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.cts.exceptions.ChequeNotFoundException;
import com.cts.exceptions.InvalidChequeDataException;
import com.cts.inward.dao.InwardBatchDao;
import com.cts.inward.dao.InwardChequeDao;
import com.cts.inward.entity.InwardCheque;
import com.cts.inward.enums.ChequeStatus;
import com.cts.inward.enums.DecisionStatus;
import com.cts.inward.enums.SendTo;

/**
 * Unit tests for InwardChequeServiceImpl — the Maker-side business logic.
 *
 * HOW THESE TESTS WORK (read this before running):
 *   - We never touch the real PostgreSQL database.
 *   - InwardChequeDao / InwardBatchDao are MOCKED using Mockito — fake objects
 *     that just return whatever we tell them to ("when(...).thenReturn(...)").
 *   - We use the SECOND constructor of InwardChequeServiceImpl (the one that
 *     accepts DAOs) to inject these fakes.
 *   - This way each test only checks ONE thing: "did the service apply the
 *     correct business rule," completely independent of the database, Tomcat,
 *     or ZK being available.
 *
 * NAMING CONVENTION used below: methodUnderTest_condition_expectedResult()
 * This is a widely used, readable naming style for test methods.
 */
class InwardChequeServiceImplTest {

    private InwardChequeDao mockChequeDao;
    private InwardBatchDao  mockBatchDao;
    private InwardChequeServiceImpl service;

    @BeforeEach
    void setUp() {
        mockChequeDao = mock(InwardChequeDao.class);
        mockBatchDao  = mock(InwardBatchDao.class);
        service = new InwardChequeServiceImpl(mockBatchDao, mockChequeDao);
    }

    // ── saveCorrections() ────────────────────────────────────────────────

    @Test
    @DisplayName("saveCorrections: valid cheque -> DAO save is called exactly once")
    void saveCorrections_validCheque_callsDaoOnce() {
        InwardCheque cheque = new InwardCheque();
        cheque.setChequeNo("123456");
        cheque.setAmount(new BigDecimal("5000.00"));

        service.saveCorrections(cheque);

        verify(mockChequeDao, times(1)).saveCorrections(cheque);
    }

    @Test
    @DisplayName("saveCorrections: null cheque -> throws InvalidChequeDataException, DAO never called")
    void saveCorrections_nullCheque_throwsAndSkipsDao() {
        assertThrows(InvalidChequeDataException.class,
            () -> service.saveCorrections(null));

        verify(mockChequeDao, never()).saveCorrections(any());
    }

    @Test
    @DisplayName("saveCorrections: blank cheque number -> throws InvalidChequeDataException")
    void saveCorrections_blankChequeNo_throws() {
        InwardCheque cheque = new InwardCheque();
        cheque.setChequeNo("   ");
        cheque.setAmount(new BigDecimal("100"));

        assertThrows(InvalidChequeDataException.class,
            () -> service.saveCorrections(cheque));
    }

    @Test
    @DisplayName("saveCorrections: zero amount -> throws InvalidChequeDataException")
    void saveCorrections_zeroAmount_throws() {
        InwardCheque cheque = new InwardCheque();
        cheque.setChequeNo("123456");
        cheque.setAmount(BigDecimal.ZERO);

        assertThrows(InvalidChequeDataException.class,
            () -> service.saveCorrections(cheque));
    }

    // ── resubmitToChecker() ──────────────────────────────────────────────

    @Test
    @DisplayName("resubmitToChecker: cheque exists -> status/decision/sendTo updated, DAO called")
    void resubmitToChecker_chequeExists_updatesAndCallsDao() {
        InwardCheque cheque = new InwardCheque();
        cheque.setChequeNo("CHQ001");
        cheque.setAmount(new BigDecimal("50000")); // below 1,00,000 -> should route to TV_1

        when(mockChequeDao.findByChequeNumber("CHQ001")).thenReturn(Optional.of(cheque));

        service.resubmitToChecker("CHQ001", "maker.ramana");

        assertEquals(ChequeStatus.Resubmitted, cheque.getChequeStatus());
        assertEquals(DecisionStatus.REFERRED, cheque.getDecision());
        assertEquals(SendTo.TV_1, cheque.getSendTo());
        assertEquals("maker.ramana", cheque.getResubmittedBy());
        verify(mockChequeDao, times(1)).resubmitToChecker(cheque);
    }

    @Test
    @DisplayName("resubmitToChecker: high value cheque (>1,00,000) -> routed to TV_2")
    void resubmitToChecker_highValueCheque_routesToTv2() {
        InwardCheque cheque = new InwardCheque();
        cheque.setChequeNo("CHQ002");
        cheque.setAmount(new BigDecimal("150000")); // above 1,00,000

        when(mockChequeDao.findByChequeNumber("CHQ002")).thenReturn(Optional.of(cheque));

        service.resubmitToChecker("CHQ002", "maker.ramana");

        assertEquals(SendTo.TV_2, cheque.getSendTo());
    }

    @Test
    @DisplayName("resubmitToChecker: cheque does not exist -> throws ChequeNotFoundException")
    void resubmitToChecker_chequeMissing_throwsNotFound() {
        when(mockChequeDao.findByChequeNumber("GHOST")).thenReturn(Optional.empty());

        assertThrows(ChequeNotFoundException.class,
            () -> service.resubmitToChecker("GHOST", "maker.ramana"));

        verify(mockChequeDao, never()).resubmitToChecker(any());
    }

    // ── generateRRF() ────────────────────────────────────────────────────

    @Test
    @DisplayName("generateRRF: cheque exists -> decision set to REJECTED, DAO called")
    void generateRRF_chequeExists_setsRejectedAndCallsDao() {
        InwardCheque cheque = new InwardCheque();
        cheque.setChequeNo("CHQ003");

        when(mockChequeDao.findByChequeNumber("CHQ003")).thenReturn(Optional.of(cheque));

        service.generateRRF("CHQ003", "maker.ramana");

        assertEquals(DecisionStatus.REJECTED, cheque.getDecision());
        verify(mockChequeDao, times(1)).generateRRF(cheque);
    }

    @Test
    @DisplayName("generateRRF: cheque does not exist -> throws ChequeNotFoundException")
    void generateRRF_chequeMissing_throwsNotFound() {
        when(mockChequeDao.findByChequeNumber("GHOST")).thenReturn(Optional.empty());

        assertThrows(ChequeNotFoundException.class,
            () -> service.generateRRF("GHOST", "maker.ramana"));
    }

    // ── getChequeForRepair() ─────────────────────────────────────────────

    @Test
    @DisplayName("getChequeForRepair: cheque is in correct repair state -> returned")
    void getChequeForRepair_correctState_returnsCheque() {
        InwardCheque cheque = new InwardCheque();
        cheque.setChequeStatus(ChequeStatus.Repair);
        cheque.setDecision(DecisionStatus.REFERRED);
        cheque.setSendTo(SendTo.MAKER);

        when(mockChequeDao.findByChequeNumber("CHQ004")).thenReturn(Optional.of(cheque));

        Optional<InwardCheque> result = service.getChequeForRepair("CHQ004");

        assertTrue(result.isPresent());
    }

    @Test
    @DisplayName("getChequeForRepair: cheque already resubmitted (wrong state) -> empty")
    void getChequeForRepair_wrongState_returnsEmpty() {
        InwardCheque cheque = new InwardCheque();
        cheque.setChequeStatus(ChequeStatus.Resubmitted); // not "Repair" anymore
        cheque.setDecision(DecisionStatus.REFERRED);
        cheque.setSendTo(SendTo.MAKER);

        when(mockChequeDao.findByChequeNumber("CHQ005")).thenReturn(Optional.of(cheque));

        Optional<InwardCheque> result = service.getChequeForRepair("CHQ005");

        assertTrue(result.isEmpty());
    }

    @Test
    @DisplayName("getChequeForRepair: blank cheque number -> empty, DAO never called")
    void getChequeForRepair_blankInput_returnsEmptyWithoutDaoCall() {
        Optional<InwardCheque> result = service.getChequeForRepair("   ");

        assertTrue(result.isEmpty());
        verify(mockChequeDao, never()).findByChequeNumber(any());
    }
}
