package com.cts.inward.dao;

import com.cts.inward.dto.DashboardStatsDTO;
import com.cts.inward.entity.InwardBatch;
import com.cts.inward.enums.BatchStatus;
import com.cts.inward.enums.CbsValidation;
import com.cts.inward.enums.ChequeStatus;
import com.cts.inward.enums.DecisionStatus;
import com.cts.inward.enums.SendTo;
import com.cts.util.HibernateUtil;
import org.hibernate.Session;
import org.hibernate.Transaction;
import org.hibernate.query.Query;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.Date;
import java.util.List;

public class InwardBatchDaoImpl implements InwardBatchDao {

	/**
	 * 
	 * Fetches dashboard statistics for a given date range.
	 * 
	 * Input: fromDate - Start date/time for filtering batches. toDate - End
	 * date/time for filtering batches.
	 * 
	 * 
	 * Count total batches created within the date range. Count batches with status
	 * CLEARED. Count batches with status PENDING. Count batches with status DRAFT.
	 * 
	 * Populate DashboardStatsDTO with these counts. Returns: DashboardStatsDTO
	 * containing: - Total batches - Cleared batches - Pending batches - Draft
	 * batches
	 * 
	 */
	// MakerDashboard
	@Override
	public DashboardStatsDTO getDashboardStats(Date fromDate, Date toDate) {

		LocalDateTime fromLdt = fromDate != null ? fromDate.toInstant().atZone(ZoneId.systemDefault()).toLocalDateTime()
				: null;

		LocalDateTime toLdt = toDate != null ? toDate.toInstant().atZone(ZoneId.systemDefault()).toLocalDateTime()
				: null;

		try (Session session = HibernateUtil.getSessionFactory().openSession()) {

			Long totalBatches = session
					.createQuery("select count(b.id) from InwardBatch b "
							+ "where b.createdAt between :fromDate and :toDate", Long.class)
					.setParameter("fromDate", fromLdt).setParameter("toDate", toLdt).uniqueResult();

			Long clearedBatches = session
					.createQuery("select count(b.id) from InwardBatch b " + "where b.batchStatus = :status "
							+ "and b.createdAt between :fromDate and :toDate", Long.class)
					.setParameter("status", BatchStatus.Cleared).setParameter("fromDate", fromLdt)
					.setParameter("toDate", toLdt).uniqueResult();

			Long pendingBatches = session
					.createQuery("select count(b.id) from InwardBatch b " + "where b.batchStatus = :status "
							+ "and b.createdAt between :fromDate and :toDate", Long.class)
					.setParameter("status", BatchStatus.Pending).setParameter("fromDate", fromLdt)
					.setParameter("toDate", toLdt).uniqueResult();

			Long draftBatches = session
					.createQuery("select count(b.id) from InwardBatch b " + "where b.batchStatus = :status "
							+ "and b.createdAt between :fromDate and :toDate", Long.class)
					.setParameter("status", BatchStatus.Draft).setParameter("fromDate", fromLdt)
					.setParameter("toDate", toLdt).uniqueResult();

			return new DashboardStatsDTO(totalBatches == null ? 0 : totalBatches,
					clearedBatches == null ? 0 : clearedBatches, pendingBatches == null ? 0 : pendingBatches,
					draftBatches == null ? 0 : draftBatches);
		}
	}

	// MakerDashboardService
	@Override
	public List<InwardBatch> getAllBatches() {

		Session session = null;
		try {
			session = HibernateUtil.getSessionFactory().openSession();

			System.out.println("SESSION OPENED");

			Object count = session.createNativeQuery("select count(*) from inward_batch").getSingleResult();
			System.out.println("DB COUNT = " + count);

			List<InwardBatch> list = session
					.createQuery("from InwardBatch b where b.batchStatus = :status", InwardBatch.class)
					.setParameter("status", BatchStatus.Draft).list();
			System.out.println("ENTITY COUNT = " + list.size());

			return list;

		} catch (Exception e) {
			System.out.println("DAO ERROR OCCURRED");
			e.printStackTrace();
			return java.util.Collections.emptyList();
		} finally {
			if (session != null)
				session.close();
		}
	}

	// Used by MakerDashboard
	@Override
	public List<InwardBatch> searchBatches(String batchId, BatchStatus status, Date fromDate, Date toDate) {

		LocalDateTime fromLdt = fromDate != null ? fromDate.toInstant().atZone(ZoneId.systemDefault()).toLocalDateTime()
				: null;

		LocalDateTime toLdt = toDate != null ? toDate.toInstant().atZone(ZoneId.systemDefault()).toLocalDateTime()
				: null;

		Session session = null;

		try {
			session = HibernateUtil.getSessionFactory().openSession();

			StringBuilder hql = new StringBuilder("FROM InwardBatch b WHERE 1=1 ");

			if (batchId != null && !batchId.trim().isEmpty()) {
				hql.append("AND LOWER(b.batchId) LIKE :batchId ");
			}

			if (status != null) {
				hql.append("AND b.batchStatus = :status ");
			}

			if (fromLdt != null) {
				hql.append("AND b.createdAt >= :fromDate ");
			}

			if (toLdt != null) {
				hql.append("AND b.createdAt <= :toDate ");
			}

			hql.append("ORDER BY b.createdAt DESC");

			Query<InwardBatch> query = session.createQuery(hql.toString(), InwardBatch.class);

			if (batchId != null && !batchId.trim().isEmpty()) {
				query.setParameter("batchId", "%" + batchId.toLowerCase() + "%");
			}

			if (status != null) {
				query.setParameter("status", status);
			}

			if (fromLdt != null) {
				query.setParameter("fromDate", fromLdt);
			}

			if (toLdt != null) {
				query.setParameter("toDate", toLdt);
			}

			return query.list();

		} finally {
			if (session != null) {
				session.close();
			}
		}
	}

	// MakerDashboardService
	@Override
	public java.math.BigDecimal getTotalAmountByBatchId(Long batchId) {
		try (Session session = HibernateUtil.getSessionFactory().openSession()) {
			java.math.BigDecimal result = session
					.createQuery("SELECT COALESCE(SUM(c.amount), 0) FROM InwardCheque c WHERE c.batch.id = :batchId",
							java.math.BigDecimal.class)
					.setParameter("batchId", batchId).uniqueResult();
			return result != null ? result : java.math.BigDecimal.ZERO;
		}
	}

	// makerDashboardService
	@Override
	public Long getAcceptedCountByBatchId(Long batchId) {
		try (Session session = HibernateUtil.getSessionFactory().openSession()) {
			Long result = session
					.createQuery("SELECT COUNT(c) FROM InwardCheque c " + "WHERE c.batch.id = :batchId "
							+ "AND c.decision = :decision " + "AND c.chequeStatus = :status", Long.class)
					.setParameter("batchId", batchId).setParameter("decision", DecisionStatus.ACCEPTED)
					.setParameter("status", ChequeStatus.Ready).uniqueResult();

			return result != null ? result : 0L;
		}
	}

	// MakerDashboardService
	@Override
	public Long getRejectedCountByBatchId(Long batchId) {
		try (Session session = HibernateUtil.getSessionFactory().openSession()) {
			Long result = session
					.createQuery("SELECT COUNT(c) FROM InwardCheque c "
							+ "WHERE c.batch.id = :batchId AND c.decision = :decision", Long.class)
					.setParameter("batchId", batchId).setParameter("decision", DecisionStatus.REJECTED).uniqueResult();
			return result != null ? result : 0L;
		}
	}

	// MakerDahboardService
	@Override
	public Long getInvalidCountByBatchId(Long batchId) {
	    try (Session session = HibernateUtil.getSessionFactory().openSession()) {
	        return session.createQuery(
	                "SELECT COUNT(c) FROM InwardCheque c " +
	                "WHERE c.batch.id = :batchId " +
	                "AND (c.chequeStatus = :status " +
	                "OR c.cbsValidation = :cbsValidation)",
	                Long.class)
	            .setParameter("batchId", batchId)
	            .setParameter("status", ChequeStatus.Repair)
	            .setParameter("cbsValidation", CbsValidation.Invalid)
	            .uniqueResult();
	    }
	}

	@Override
	public void save(InwardBatch batch) {

		Transaction tx = null;
		try (Session session = HibernateUtil.getSessionFactory().openSession()) {
			tx = session.beginTransaction();
			session.persist(batch);
			tx.commit();
		} catch (Exception e) {
			if (tx != null)
				tx.rollback();
			throw new RuntimeException("Failed to save batch : " + e.getMessage(), e);
		}
	}

	// ── CHANGE 5 : Fixed update() — re-fetches managed entity inside its own
	// session
	//
	// OLD CODE : session.merge(batch)
	// → batch came from findByBatchId() whose try-with-resources already closed
	// the session. So batch is DETACHED. Hibernate tries to use the old
	// closed connection to check entity state → "LogicalConnection is closed"
	//
	// FIX : Re-fetch a fresh managed InwardBatch by batchId inside THIS session.
	// Then copy only the fields we want to update onto the managed entity.
	// session.merge(managed) works because managed lives in the same open session.
	// ────────────────────────────────────────────────────────────────────────────────
	@Override
	public void update(InwardBatch batch) {

		Transaction tx = null;
		try (Session session = HibernateUtil.getSessionFactory().openSession()) {

			tx = session.beginTransaction();

			// Re-fetch inside this session so we always have a live managed entity
			InwardBatch managed = session
					.createQuery("FROM InwardBatch b WHERE b.batchId = :batchId", InwardBatch.class)
					.setParameter("batchId", batch.getBatchId()).uniqueResult();

			if (managed != null) {
				// Copy only the fields that should be updated
				managed.setSuccessCount(batch.getSuccessCount());
				managed.setTotalCheques(batch.getTotalCheques());
				managed.setBatchStatus(batch.getBatchStatus());
				managed.setMicrRepairCount(batch.getMicrRepairCount());
				session.merge(managed); // merge on managed entity — safe
			}

			tx.commit();

		} catch (Exception e) {
			if (tx != null)
				tx.rollback();
			throw new RuntimeException("Failed to update batch : " + e.getMessage(), e);
		}
	}

	// ─────────────────────────────────────────────────────────────────────
	// for MICR
	@Override
	public InwardBatch findByBatchId(String batchId) {
		try (Session session = HibernateUtil.getSessionFactory().openSession()) {
			return session.createQuery("FROM InwardBatch b WHERE b.batchId = :batchId", InwardBatch.class)
					.setParameter("batchId", batchId).uniqueResult();
		}
	}
	
	@Override
	public void updateBatchStatus(Long id, BatchStatus status) {

		try (Session session = HibernateUtil.getSessionFactory().openSession()) {

			session.beginTransaction();

			session.createMutationQuery("update InwardBatch b " + "set b.batchStatus = :status " + "where b.id = :id")
					.setParameter("status", status).setParameter("id", id).executeUpdate();

			session.getTransaction().commit();
		}
	}

	@Override
	public List<InwardBatch> findAll() {
		try (Session session = HibernateUtil.getSessionFactory().openSession()) {
			return session.createQuery("FROM InwardBatch ORDER BY createdAt DESC", InwardBatch.class).list();
		}
	}

	@Override
	public InwardBatch findLatest() {
		try (Session session = HibernateUtil.getSessionFactory().openSession()) {
			return session.createQuery("FROM InwardBatch ORDER BY id DESC", InwardBatch.class).setMaxResults(1)
					.uniqueResult();
		}
	}

	

	@Override
	public InwardBatch findById(Long id) {
		try (Session session = HibernateUtil.getSessionFactory().openSession()) {
			return session.get(InwardBatch.class, id);
		}
	}

	// ── Batch list for a queue on a specific day ───────────────────────────
	@Override
	public List<InwardBatch> getBatchesByDate(Date date) {
		LocalDateTime from = startOfDay(date);
		LocalDateTime to = endOfDay(date);

		try (Session session = HibernateUtil.getSessionFactory().openSession()) {
			return session
					.createQuery("select distinct c.batch " + "from InwardCheque c " + "where c.sendTo = :sendTo "
							+ "and c.batch.createdAt between :from and :to " + "order by c.batch.createdAt desc",
							InwardBatch.class)
					.setParameter("sendTo", SendTo.TV_1).setParameter("from", from).setParameter("to", to).list();
		}
	}

	// ── KPI: total assigned batches for the day ────────────────────────────
	@Override
	public Long getAssignedBatchCountForTV1(Date date) {

		LocalDateTime from = startOfDay(date);
		LocalDateTime to = endOfDay(date);

		try (Session session = HibernateUtil.getSessionFactory().openSession()) {

			return session
					.createQuery(
							"select count(distinct c.batch.id) " + "from InwardCheque c " + "where c.sendTo = :sendTo "
									+ "and c.batch.createdAt between :from and :to "
									+ "and c.batch.batchStatus = :batchStatus " + "and c.chequeStatus = :chequeStatus "
									+ "and c.cbsValidation = :cbsValidation " + "and c.decision = :decision",
							Long.class)
					.setParameter("sendTo", SendTo.TV_1).setParameter("from", from).setParameter("to", to)
					.setParameter("batchStatus", BatchStatus.PendingAtChecker)
					.setParameter("chequeStatus", ChequeStatus.Processed)
					.setParameter("cbsValidation", CbsValidation.Valid).setParameter("decision", DecisionStatus.PENDING)
					.uniqueResult();
		}
	}

	// ── KPI: pending batches (not yet Cleared) ─────────────────────────────
	@Override
	public Long getPendingBatchCountTV1(Date date) {

		LocalDateTime from = startOfDay(date);
		LocalDateTime to = endOfDay(date);

		try (Session session = HibernateUtil.getSessionFactory().openSession()) {

			return session
					.createQuery(
							"select count(distinct c.batch.id) " + "from InwardCheque c " + "where c.sendTo = :sendTo "
									+ "and c.batch.createdAt between :from and :to "
									+ "and c.batch.batchStatus = :batchStatus " + "and c.chequeStatus = :chequeStatus "
									+ "and c.cbsValidation = :cbsValidation " + "and c.decision = :decision",
							Long.class)
					.setParameter("sendTo", SendTo.TV_1).setParameter("from", from).setParameter("to", to)
					.setParameter("batchStatus", BatchStatus.PendingAtChecker)
					.setParameter("chequeStatus", ChequeStatus.Processed)
					.setParameter("cbsValidation", CbsValidation.Valid).setParameter("decision", DecisionStatus.PENDING)
					.uniqueResult();
		}
	}

	// ── KPI: cleared batches ───────────────────────────────────────────────
	// Counts distinct batches that have been successfully validated
	// and assigned to a checker queue (TV_1 or TV_2).
	@Override
	public Long getClearedBatchCountTV1(Date date) {

	    LocalDateTime from = startOfDay(date);
	    LocalDateTime to = endOfDay(date);

	    try (Session session = HibernateUtil.getSessionFactory().openSession()) {

	        return session.createQuery(
	                "select count(distinct b.id) " +
	                "from InwardBatch b " +
	                "where b.createdAt between :from and :to " +
	                "and not exists (" +
	                "   select c.id " +
	                "   from InwardCheque c " +
	                "   where c.batch.id = b.id " +
	                "   and c.cbsValidation = :cbsValidation " +
	                "   and c.amount <= :limit " +
	                "   and c.chequeStatus = :processed" +
	                ")",
	                Long.class)
	            .setParameter("from", from)
	            .setParameter("to", to)
	            .setParameter("limit", new BigDecimal("100000"))
	            .setParameter("cbsValidation", CbsValidation.Valid)
	            .setParameter("processed", ChequeStatus.Processed)
	            .uniqueResult();
	    }
	}
	
	// ── KPI: pending cheques ───────────────────────────────────────────────
	// Pending = decision IS NULL OR decision = PENDING
	@Override
	public Long getPendingChequeCountTV1(Date date) {

		LocalDateTime from = startOfDay(date);
		LocalDateTime to = endOfDay(date);

		try (Session session = HibernateUtil.getSessionFactory().openSession()) {

			return session
					.createQuery(
							"select count(c.id) " + "from InwardCheque c " + "where c.sendTo = :sendTo "
									+ "and c.batch.createdAt between :from and :to "
									+ "and c.batch.batchStatus = :batchStatus " + "and c.chequeStatus = :chequeStatus "
									+ "and c.cbsValidation = :cbsValidation " + "and c.decision = :decision",
							Long.class)
					.setParameter("sendTo", SendTo.TV_1).setParameter("from", from).setParameter("to", to)
					.setParameter("batchStatus", BatchStatus.PendingAtChecker)
					.setParameter("chequeStatus", ChequeStatus.Processed)
					.setParameter("cbsValidation", CbsValidation.Valid).setParameter("decision", DecisionStatus.PENDING)
					.uniqueResult();
		}
	}

	// ── Per-batch: ALL cheques in the batch ───────────────────────────────
	@Override
	public Long getTotalChequesForBatchTV1(Long batchId) {
	    try (Session session = HibernateUtil.getSessionFactory().openSession()) {
	        return session.createQuery(
	                "select count(c.id) from InwardCheque c " +
	                "where c.batch.id = :batchId " +
	                "and c.cbsValidation = :cbsValidation",
	                Long.class)
	            .setParameter("batchId", batchId)
	            .setParameter("cbsValidation", CbsValidation.Valid)
	            .uniqueResult();
	    }
	}

	// ── Per-batch: cheques where sendTo = queue ───────────────────────────
	@Override
	public Long getAssignedChequesForBatchTV1(Long batchId) {

	    try (Session session = HibernateUtil.getSessionFactory().openSession()) {

	        return session.createQuery(
	                "select count(c.id) " +
	                "from InwardCheque c " +
	                "where c.batch.id = :batchId " +
	                "and c.amount <= :maxAmount " +
	                "and c.batch.batchStatus = :batchStatus " +
	                "and c.cbsValidation = :cbsValidation",
	                Long.class)
	            .setParameter("batchId", batchId)
	            .setParameter("maxAmount", 100000.00)
	            .setParameter("batchStatus", BatchStatus.PendingAtChecker)
	            .setParameter("cbsValidation", CbsValidation.Valid)
	            .uniqueResult();
	    }
	}

	// ── Per-batch: pending cheques ────────────────────────────────────────
	// Pending = sendTo = queue AND (decision IS NULL OR decision = PENDING)
	@Override
	public Long getPendingChequesForBatchTV1(Long batchId) {

	    try (Session session = HibernateUtil.getSessionFactory().openSession()) {

	        return session.createQuery(
	                "select count(c.id) " +
	                "from InwardCheque c " +
	                "where c.batch.id = :batchId " +
	                "and c.amount <= :limit " +
	                "and c.cbsValidation = :cbsValidation " +
	                "and c.chequeStatus = :chequeStatus",
	                Long.class)
	            .setParameter("batchId", batchId)
	            .setParameter("limit", new BigDecimal("100000"))
	            .setParameter("cbsValidation", CbsValidation.Valid)
	            .setParameter("chequeStatus", ChequeStatus.Processed)
	            .uniqueResult();
	    }
	}
	
	
	// ── Per-batch: cleared cheques ─────────────────────────────────────────
	// Cleared = sendTo = TV_1 or TV_2 AND decision IN (APPROVED, ACCEPTED)
	@Override
	public Long getClearedChequesForBatchTV1(Long batchId) {
	    try (Session session = HibernateUtil.getSessionFactory().openSession()) {

	        return session.createQuery(
	                "select count(c.id) from InwardCheque c " +
	                "where c.batch.id = :batchId " +
	                "and c.chequeStatus <> :status " +
	                "and c.amount <= :maxAmount " +
	                "and c.cbsValidation = :cbsValidation",
	                Long.class)
	            .setParameter("batchId", batchId)
	            .setParameter("status", ChequeStatus.Processed)
	            .setParameter("maxAmount", 100000)
	            .setParameter("cbsValidation", CbsValidation.Valid)
	            .uniqueResult();
	    }
	}

	/**
	 * Submitted Batches Count
	 *
	 * Counts distinct batches assigned to TV_1 for the selected date where: - Batch
	 * Status = Submitted - Cheque Status = Normal - CBS Validation = Valid -
	 * Decision Status = ACCEPTED - Send To = TV_1
	 */
//    @Override
//    public Long getSubmittedBatchCount(Date date) {
//
//        LocalDateTime from = startOfDay(date);
//        LocalDateTime to = endOfDay(date);
//
//        try (Session session = HibernateUtil.getSessionFactory().openSession()) {
//
//            return session.createQuery(
//                    "select count(distinct c.batch.id) " +
//                    "from InwardCheque c " +
//                    "where c.sendTo = :sendTo " +
//                    "and c.batch.createdAt between :from and :to " +
//                    "and c.batch.batchStatus = :batchStatus " +
//                    "and c.chequeStatus = :chequeStatus " +
//                    "and c.cbsValidation = :cbsValidation " +
//                    "and c.decision = :decision",
//                    Long.class)
//                    .setParameter("sendTo", SendTo.TV_1)
//                    .setParameter("from", from)
//                    .setParameter("to", to)
//                    .setParameter("batchStatus", BatchStatus.ClearedAtChecker)
//                    .setParameter("chequeStatus", ChequeStatus.Processed)
//                    .setParameter("cbsValidation", CbsValidation.Valid)
//                    .setParameter("decision", DecisionStatus.ACCEPTED)
//                    .uniqueResult();
//        }
//    }

	/**
	 * Submitted Cheques Count For Batch
	 *
	 * Counts cheques in a specific batch where: - Batch Status = Submitted - Cheque
	 * Status = Normal - CBS Validation = Valid - Decision Status = ACCEPTED - Send
	 * To = TV_1
	 */
	@Override
	public Long getSubmittedBatchesTV1(Long batchId) {

		try (Session session = HibernateUtil.getSessionFactory().openSession()) {

			return session
					.createQuery("select count(c.id) " + "from InwardCheque c " + "where c.batch.id = :batchId "
							+ "and c.sendTo = :sendTo " + "and c.batch.batchStatus = :batchStatus "
							+ "and c.chequeStatus = :chequeStatus " + "and c.cbsValidation = :cbsValidation "
							+ "and c.decision = :decision", Long.class)
					.setParameter("batchId", batchId).setParameter("sendTo", SendTo.TV_1)
					.setParameter("batchStatus", BatchStatus.ClearedAtChecker)
					.setParameter("chequeStatus", ChequeStatus.Ready).setParameter("cbsValidation", CbsValidation.Valid)
					.setParameter("decision", DecisionStatus.ACCEPTED).uniqueResult();
		}
	}

	// ── Helpers ───────────────────────────────────────────────────────────
	private LocalDateTime startOfDay(Date date) {
		return date.toInstant().atZone(ZoneId.systemDefault()).toLocalDate().atStartOfDay();
	}

	private LocalDateTime endOfDay(Date date) {
		return date.toInstant().atZone(ZoneId.systemDefault()).toLocalDate().atTime(23, 59, 59);
	}

	// ── Batch list for a queue on a specific day ───────────────────────────
	@Override
	public List<InwardBatch> getBatchesByQueueAndDate(SendTo queue, Date date) {

	    LocalDateTime from = startOfDay(date);
	    LocalDateTime to   = endOfDay(date);

	    try (Session session = HibernateUtil.getSessionFactory().openSession()) {

	        return session.createQuery(
	                "select distinct c.batch " +
	                "from InwardCheque c " +
	                "where c.batch.createdAt between :from and :to " +
	                "and c.batch.batchStatus in (:pending, :cleared) " +
	                "order by c.batch.createdAt desc",
	                InwardBatch.class)
	                .setParameter("from", from)
	                .setParameter("to", to)
	                .setParameter("pending", BatchStatus.PendingAtChecker)
	                .setParameter("cleared", BatchStatus.ClearedAtChecker)
	                .list();
	    }
	}

	// ── KPI: total assigned batches for the day ────────────────────────────
	@Override
	public Long getAssignedBatchCountTV2(SendTo queue, Date date) {
		LocalDateTime from = startOfDay(date);
		LocalDateTime to = endOfDay(date);

		try (Session session = HibernateUtil.getSessionFactory().openSession()) {
			return session
					.createQuery("select count(distinct c.batch.id) " + "from InwardCheque c "
							+ "where c.sendTo = :queue " + "and c.batch.createdAt between :from and :to", Long.class)
					.setParameter("queue", queue).setParameter("from", from).setParameter("to", to).uniqueResult();
		}
	}

	// ── KPI: pending batches (not yet Cleared) ─────────────────────────────
	@Override
	public Long getPendingBatchCountTV2(SendTo queue, Date date) {

	    LocalDateTime from = startOfDay(date);
	    LocalDateTime to   = endOfDay(date);

	    try (Session session = HibernateUtil.getSessionFactory().openSession()) {

	        return session.createQuery(
	                "select count(distinct c.batch.id) " +
	                "from InwardCheque c " +
	                "where c.sendTo = :queue " +
	                "and c.batch.createdAt between :from and :to " +
	                "and c.batch.batchStatus = :batchStatus " +
	                "and c.chequeStatus = :chequeStatus " +
	                "and c.cbsValidation = :cbsValidation " +
	                "and c.decision = :decision",
	                Long.class)
	                .setParameter("queue", queue)
	                .setParameter("from", from)
	                .setParameter("to", to)
	                .setParameter("batchStatus", BatchStatus.PendingAtChecker)
	                .setParameter("chequeStatus", ChequeStatus.Processed)
	                .setParameter("cbsValidation", CbsValidation.Valid)
	                .setParameter("decision", DecisionStatus.PENDING)
	                .uniqueResult();
	    }
	}

	// ── KPI: cleared batches ───────────────────────────────────────────────
	@Override
	public Long getClearedBatchCountTV2(SendTo queue, Date date) {

	    LocalDateTime from = startOfDay(date);
	    LocalDateTime to = endOfDay(date);

	    try (Session session = HibernateUtil.getSessionFactory().openSession()) {

	        return session.createQuery(
	                "select count(distinct b.id) " +
	                "from InwardBatch b " +
	                "where b.createdAt between :from and :to " +
	                "and not exists (" +
	                "   select c.id " +
	                "   from InwardCheque c " +
	                "   where c.batch.id = b.id " +
	                "   and c.cbsValidation = :cbsValidation " +
	                "   and c.amount > :limit " +
	                "   and c.chequeStatus = :processed" +
	                ")",
	                Long.class)
	            .setParameter("from", from)
	            .setParameter("to", to)
	            .setParameter("limit", new BigDecimal("100000"))
	            .setParameter("cbsValidation", CbsValidation.Valid)
	            .setParameter("processed", ChequeStatus.Processed)
	            .uniqueResult();
	    }
	}

	// ── KPI: pending cheques ───────────────────────────────────────────────
	// Pending = decision IS NULL OR decision = PENDING
	@Override
	public Long getPendingChequeCountTV2(SendTo queue, Date date) {
		LocalDateTime from = startOfDay(date);
		LocalDateTime to = endOfDay(date);

		try (Session session = HibernateUtil.getSessionFactory().openSession()) {
			return session
					.createQuery("select count(c.id) " + "from InwardCheque c " + "where c.sendTo = :queue "
							+ "and c.batch.createdAt between :from and :to "
							+ "and (c.decision is null or c.decision <> :accepted)", Long.class)
					.setParameter("queue", queue).setParameter("from", from).setParameter("to", to)
					.setParameter("accepted", DecisionStatus.ACCEPTED).uniqueResult();
		}
	}

	// ── Per-batch: ALL cheques in the batch ───────────────────────────────
	@Override
	public Long getTotalChequesForBatchTV2(Long batchId) {
	    try (Session session = HibernateUtil.getSessionFactory().openSession()) {
	        return session.createQuery(
	                "select count(c.id) from InwardCheque c " +
	                "where c.batch.id = :batchId " +
	                "and c.cbsValidation = :cbsValidation",
	                Long.class)
	            .setParameter("batchId", batchId)
	            .setParameter("cbsValidation", CbsValidation.Valid)
	            .uniqueResult();
	    }
	}

	// ── Per-batch: cheques where sendTo = queue ───────────────────────────
	@Override
	public Long getAssignedChequesForBatchTV2(Long batchId, SendTo queue) {
	    try (Session session = HibernateUtil.getSessionFactory().openSession()) {
	        return session.createQuery(
	                "select count(c.id) from InwardCheque c " +
	                "where c.batch.id = :batchId " +
	                "and c.amount > :maxAmount " +
	                "and c.cbsValidation = :cbsValidation",
	                Long.class)
	            .setParameter("batchId", batchId)
	            .setParameter("maxAmount", 100000.00)
	            .setParameter("cbsValidation", CbsValidation.Valid)
	            .uniqueResult();
	    }
	}
	
	// ── Per-batch: pending cheques ────────────────────────────────────────
	// Pending = sendTo = queue AND (decision IS NULL OR decision = PENDING)
	@Override
	public Long getPendingChequesForBatchTV2(Long batchId, SendTo queue) {

	    try (Session session = HibernateUtil.getSessionFactory().openSession()) {

	        return session.createQuery(
	                "select count(c.id) " +
	                "from InwardCheque c " +
	                "where c.batch.id = :batchId " +
	                "and c.sendTo = :sendTo " +
	                "and c.batch.batchStatus = :batchStatus " +
	                "and c.chequeStatus = :chequeStatus " +
	                "and c.cbsValidation = :cbsValidation " +
	                "and c.decision = :decision",
	                Long.class)
	            .setParameter("batchId", batchId)
	            .setParameter("sendTo", queue)
	            .setParameter("batchStatus", BatchStatus.PendingAtChecker)
	            .setParameter("chequeStatus", ChequeStatus.Processed)
	            .setParameter("cbsValidation", CbsValidation.Valid)
	            .setParameter("decision", DecisionStatus.PENDING)
	            .uniqueResult();
	    }
	}

	// ── Per-batch: cleared cheques ─────────────────────────────────────────
	// Cleared = sendTo = queue AND decision IN (APPROVED, ACCEPTED)
	@Override
	public Long getClearedChequesForBatchTV2(Long batchId, SendTo queue) {
	    try (Session session = HibernateUtil.getSessionFactory().openSession()) {

	        return session.createQuery(
	                "select count(c.id) from InwardCheque c " +
	                "where c.batch.id = :batchId " +
	                "and c.chequeStatus <> :status " +
	                "and c.amount > :minAmount " +
	                "and c.cbsValidation = :cbsValidation",
	                Long.class)
	            .setParameter("batchId", batchId)
	            .setParameter("status", ChequeStatus.Processed)
	            .setParameter("minAmount", new BigDecimal("100000"))
	            .setParameter("cbsValidation", CbsValidation.Valid)
	            .uniqueResult();
	    }
	}

	@Override
	public void updateBatchStatusIfCompleted(Long batchId) {

	    try (Session session = HibernateUtil.getSessionFactory().openSession()) {

	        Transaction tx = session.beginTransaction();

	        Long pendingCount = session.createQuery(
	                "select count(c.id) " +
	                "from InwardCheque c " +
	                "where c.batch.id = :batchId " +
	                "and c.cbsValidation = :valid " +
	                "and c.chequeStatus in (:normal, :processed, :repair)",
	                Long.class)
	                .setParameter("batchId", batchId)
	                .setParameter("valid", CbsValidation.Valid)
	                .setParameter("normal", ChequeStatus.Normal)
	                .setParameter("processed", ChequeStatus.Processed)
	                .setParameter("repair", ChequeStatus.Repair)
	                .uniqueResult();

	        if (pendingCount != null && pendingCount == 0) {

	            session.createMutationQuery(
	                    "update InwardBatch b " +
	                    "set b.batchStatus = :status " +
	                    "where b.id = :batchId " +
	                    "and b.batchStatus <> :status")
	                    .setParameter("status", BatchStatus.ClearedAtChecker)
	                    .setParameter("batchId", batchId)
	                    .executeUpdate();
	        }

	        tx.commit();
	    }
	}
	
}