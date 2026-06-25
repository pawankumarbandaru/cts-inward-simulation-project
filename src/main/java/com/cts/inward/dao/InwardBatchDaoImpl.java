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

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.Date;
import java.util.List;

public class InwardBatchDaoImpl implements InwardBatchDao {

	/**
	 * Fetches dashboard statistics for a given date range.
	 *
	 * Input: fromDate - Start date/time for filtering batches.
	 *        toDate   - End date/time for filtering batches.
	 *
	 * Count total batches created within the date range.
	 * Count batches with status CLEARED.
	 * Count batches with status PENDING.
	 * Count batches with status DRAFT.
	 *
	 * Populate DashboardStatsDTO with these counts.
	 * Returns: DashboardStatsDTO containing:
	 *          - Total batches
	 *          - Cleared batches
	 *          - Pending batches
	 *          - Draft batches
	 */
	// MakerDashboard
	@Override
	public DashboardStatsDTO getDashboardStats(Date fromDate, Date toDate) {

		// Convert java.util.Date to LocalDateTime for SQL timestamp comparison
		LocalDateTime fromLdt = fromDate != null
				? fromDate.toInstant().atZone(ZoneId.systemDefault()).toLocalDateTime()
				: null;

		LocalDateTime toLdt = toDate != null
				? toDate.toInstant().atZone(ZoneId.systemDefault()).toLocalDateTime()
				: null;

		try (Session session = HibernateUtil.getSessionFactory().openSession()) {

			// Count all batches created within the date range
			Long totalBatches = ((Number) session
					.createNativeQuery("SELECT COUNT(id) FROM inward_batch " +
							"WHERE created_at BETWEEN :fromDate AND :toDate")
					.setParameter("fromDate", fromLdt)
					.setParameter("toDate", toLdt)
					.getSingleResult()).longValue();

			// Count only Cleared status batches within the date range
			Long clearedBatches = ((Number) session
					.createNativeQuery("SELECT COUNT(id) FROM inward_batch " +
							"WHERE batch_status = 'Cleared' " +
							"AND created_at BETWEEN :fromDate AND :toDate")
					.setParameter("fromDate", fromLdt)
					.setParameter("toDate", toLdt)
					.getSingleResult()).longValue();

			// Count only Pending status batches within the date range
			Long pendingBatches = ((Number) session
					.createNativeQuery("SELECT COUNT(id) FROM inward_batch " +
							"WHERE batch_status = 'Pending' " +
							"AND created_at BETWEEN :fromDate AND :toDate")
					.setParameter("fromDate", fromLdt)
					.setParameter("toDate", toLdt)
					.getSingleResult()).longValue();

			// Count only Draft status batches within the date range
			Long draftBatches = ((Number) session
					.createNativeQuery("SELECT COUNT(id) FROM inward_batch " +
							"WHERE batch_status = 'Draft' " +
							"AND created_at BETWEEN :fromDate AND :toDate")
					.setParameter("fromDate", fromLdt)
					.setParameter("toDate", toLdt)
					.getSingleResult()).longValue();

			return new DashboardStatsDTO(
					totalBatches,
					clearedBatches,
					pendingBatches,
					draftBatches
			);
		}
	}

	// MakerDashboardService
	@Override
	public List<InwardBatch> getAllBatches() {

		Session session = null;
		try {
			session = HibernateUtil.getSessionFactory().openSession();

			System.out.println("SESSION OPENED");

			// Sanity check: verify total row count in the table via native SQL
			Object count = session
					.createNativeQuery("SELECT COUNT(*) FROM inward_batch")
					.getSingleResult();
			System.out.println("DB COUNT = " + count);

			// Fetch only Draft status batches — Maker can still edit these
			// addEntity() maps each result row to an InwardBatch object
			List<InwardBatch> list = session
					.createNativeQuery("SELECT * FROM inward_batch WHERE batch_status = 'Draft'")
					.addEntity(InwardBatch.class)
					.list();
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

		// Convert java.util.Date to LocalDateTime for SQL timestamp comparison
		LocalDateTime fromLdt = fromDate != null
				? fromDate.toInstant().atZone(ZoneId.systemDefault()).toLocalDateTime()
				: null;

		LocalDateTime toLdt = toDate != null
				? toDate.toInstant().atZone(ZoneId.systemDefault()).toLocalDateTime()
				: null;

		Session session = null;

		try {
			session = HibernateUtil.getSessionFactory().openSession();

			// Start with base SQL — WHERE 1=1 allows safe appending of optional AND filters
			StringBuilder sql = new StringBuilder("SELECT * FROM inward_batch WHERE 1=1 ");

			// Append batch_id filter only if provided — LOWER() for case-insensitive partial match
			if (batchId != null && !batchId.trim().isEmpty()) {
				sql.append("AND LOWER(batch_id) LIKE :batchId ");
			}

			// Append batch_status filter only if a status was selected
			if (status != null) {
				sql.append("AND batch_status = :status ");
			}

			// Append from-date filter only if provided
			if (fromLdt != null) {
				sql.append("AND created_at >= :fromDate ");
			}

			// Append to-date filter only if provided
			if (toLdt != null) {
				sql.append("AND created_at <= :toDate ");
			}

			// Always sort newest batches first
			sql.append("ORDER BY created_at DESC");

			// addEntity() maps each SQL result row to an InwardBatch object
			org.hibernate.query.NativeQuery<InwardBatch> query = session
					.createNativeQuery(sql.toString())
					.addEntity(InwardBatch.class);

			// Bind parameter values only for the filters that were actually appended
			if (batchId != null && !batchId.trim().isEmpty()) {
				// Wrap with % for LIKE wildcard — lowercase match
				query.setParameter("batchId", "%" + batchId.toLowerCase() + "%");
			}

			if (status != null) {
				// Enum is passed as its String name since SQL does not know Java enums
				query.setParameter("status", status.name());
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

			// SUM of all cheque amounts for the batch; COALESCE returns 0 if no cheques exist
			// inward_cheque.batch_id is the FK column linking to inward_batch.id
			Object result = session
					.createNativeQuery("SELECT COALESCE(SUM(amount), 0) FROM inward_cheque " +
							"WHERE batch_id = :batchId")
					.setParameter("batchId", batchId)
					.getSingleResult();

			return result != null ? new BigDecimal(result.toString()) : BigDecimal.ZERO;
		}
	}

	// makerDashboardService
	@Override
	public Long getAcceptedCountByBatchId(Long batchId) {
		try (Session session = HibernateUtil.getSessionFactory().openSession()) {

			// Count cheques accepted by the checker — decision = ACCEPTED AND cheque_status = Ready
			Long result = ((Number) session
					.createNativeQuery("SELECT COUNT(*) FROM inward_cheque " +
							"WHERE batch_id = :batchId " +
							"AND decision = 'ACCEPTED' " +
							"AND cheque_status = 'Ready'")
					.setParameter("batchId", batchId)
					.getSingleResult()).longValue();

			return result != null ? result : 0L;
		}
	}

	// MakerDashboardService
	@Override
	public Long getRejectedCountByBatchId(Long batchId) {
		try (Session session = HibernateUtil.getSessionFactory().openSession()) {

			// Count cheques permanently rejected by the checker — decision = REJECTED
			Long result = ((Number) session
					.createNativeQuery("SELECT COUNT(*) FROM inward_cheque " +
							"WHERE batch_id = :batchId " +
							"AND decision = 'REJECTED'")
					.setParameter("batchId", batchId)
					.getSingleResult()).longValue();

			return result != null ? result : 0L;
		}
	}

	// MakerDashboardService
	@Override
	public Long getInvalidCountByBatchId(Long batchId) {
		try (Session session = HibernateUtil.getSessionFactory().openSession()) {

			// Count cheques that need attention — either MICR repair or CBS invalid
			// OR condition covers both failure types in one query
			return ((Number) session
					.createNativeQuery("SELECT COUNT(*) FROM inward_cheque " +
							"WHERE batch_id = :batchId " +
							"AND (cheque_status = 'Repair' OR cbs_validation = 'Invalid')")
					.setParameter("batchId", batchId)
					.getSingleResult()).longValue();
		}
	}

	@Override
	public void save(InwardBatch batch) {

		// save() still uses session.persist() — no SQL query needed here
		// Hibernate generates the INSERT statement automatically from the entity fields
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

	@Override
	public void update(InwardBatch batch) {

		Transaction tx = null;
		try (Session session = HibernateUtil.getSessionFactory().openSession()) {

			tx = session.beginTransaction();

			// Re-fetch the managed entity inside this session using the business key (batch_id)
			// addEntity() maps the result row back to an InwardBatch object
			InwardBatch managed = (InwardBatch) session
					.createNativeQuery("SELECT * FROM inward_batch WHERE batch_id = :batchId")
					.addEntity(InwardBatch.class)
					.setParameter("batchId", batch.getBatchId())
					.uniqueResult();

			if (managed != null) {
				// Copy only the fields that need updating — avoid touching other columns
				managed.setSuccessCount(batch.getSuccessCount());
				managed.setTotalCheques(batch.getTotalCheques());
				managed.setBatchStatus(batch.getBatchStatus());
				managed.setMicrRepairCount(batch.getMicrRepairCount());

				// merge() is safe here because managed is a live entity inside this open session
				session.merge(managed);
			}

			tx.commit();

		} catch (Exception e) {
			if (tx != null)
				tx.rollback();
			throw new RuntimeException("Failed to update batch : " + e.getMessage(), e);
		}
	}

	// for MICR
	@Override
	public InwardBatch findByBatchId(String batchId) {
		try (Session session = HibernateUtil.getSessionFactory().openSession()) {

			// Search by the string business key (batch_id column), not the numeric PK (id)
			// addEntity() maps the result row to an InwardBatch object
			return (InwardBatch) session
					.createNativeQuery("SELECT * FROM inward_batch WHERE batch_id = :batchId")
					.addEntity(InwardBatch.class)
					.setParameter("batchId", batchId)
					.uniqueResult();
		}
	}

	@Override
	public void updateBatchStatus(Long id, BatchStatus status) {

		try (Session session = HibernateUtil.getSessionFactory().openSession()) {

			session.beginTransaction();

			// Direct UPDATE on the batch_status column by numeric PK — no entity load needed
			// Enum is passed as its String name since SQL does not know Java enums
			session.createNativeQuery(
			        "UPDATE inward_batch SET batch_status = CAST(:status AS batch_status) WHERE id = :id")
			    .setParameter("status", status.name())
			    .setParameter("id", id)
			    .executeUpdate();

			session.getTransaction().commit();
		}
	}

	@Override
	public List<InwardBatch> findAll() {
		try (Session session = HibernateUtil.getSessionFactory().openSession()) {

			// Fetch all rows from inward_batch, newest first by created_at
			// addEntity() maps each result row to an InwardBatch object
			return session
					.createNativeQuery("SELECT * FROM inward_batch ORDER BY created_at DESC")
					.addEntity(InwardBatch.class)
					.list();
		}
	}

	@Override
	public InwardBatch findLatest() {
		try (Session session = HibernateUtil.getSessionFactory().openSession()) {

			// ORDER BY id DESC gives the newest batch first
			// LIMIT 1 fetches only that one row
			return (InwardBatch) session
					.createNativeQuery("SELECT * FROM inward_batch ORDER BY id DESC LIMIT 1")
					.addEntity(InwardBatch.class)
					.uniqueResult();
		}
	}

	@Override
	public InwardBatch findById(Long id) {
		try (Session session = HibernateUtil.getSessionFactory().openSession()) {

			// Direct PK lookup on inward_batch.id — fastest way to fetch a single batch
			return (InwardBatch) session
					.createNativeQuery("SELECT * FROM inward_batch WHERE id = :id")
					.addEntity(InwardBatch.class)
					.setParameter("id", id)
					.uniqueResult();
		}
	}

	// ── Batch list for a queue on a specific day ───────────────────────────
	@Override
	public List<InwardBatch> getBatchesByDate(Date date) {
		LocalDateTime from = startOfDay(date);
		LocalDateTime to = endOfDay(date);

		try (Session session = HibernateUtil.getSessionFactory().openSession()) {

			// JOIN inward_cheque → inward_batch to find batches that have TV_1 cheques
			// DISTINCT avoids duplicate batch rows when multiple cheques belong to the same batch
			return session
					.createNativeQuery("SELECT DISTINCT ib.* FROM inward_batch ib " +
							"JOIN inward_cheque ic ON ic.batch_id = ib.id " +
							"WHERE ic.send_to = 'TV_1' " +
							"AND ib.created_at BETWEEN :from AND :to " +
							"ORDER BY ib.created_at DESC")
					.addEntity(InwardBatch.class)
					.setParameter("from", from)
					.setParameter("to", to)
					.list();
		}
	}

	// ── KPI: total assigned batches for the day ────────────────────────────
	@Override
	public Long getAssignedBatchCountForTV1(Date date) {

		LocalDateTime from = startOfDay(date);
		LocalDateTime to = endOfDay(date);

		try (Session session = HibernateUtil.getSessionFactory().openSession()) {

			// COUNT DISTINCT batch IDs — one batch can have many cheques but we count it only once
			// All five conditions must match for a cheque to be counted
			return ((Number) session
					.createNativeQuery("SELECT COUNT(DISTINCT ic.batch_id) FROM inward_cheque ic " +
							"JOIN inward_batch ib ON ic.batch_id = ib.id " +
							"WHERE ic.send_to = 'TV_1' " +
							"AND ib.created_at BETWEEN :from AND :to " +
							"AND ib.batch_status = 'PendingAtChecker' " +
							"AND ic.cheque_status = 'Processed' " +
							"AND ic.cbs_validation = 'Valid' " +
							"AND ic.decision = 'PENDING'")
					.setParameter("from", from)
					.setParameter("to", to)
					.getSingleResult()).longValue();
		}
	}

	// ── KPI: pending batches (not yet Cleared) ─────────────────────────────
	@Override
	public Long getPendingBatchCountTV1(Date date) {

		LocalDateTime from = startOfDay(date);
		LocalDateTime to = endOfDay(date);

		try (Session session = HibernateUtil.getSessionFactory().openSession()) {

			// Same multi-condition filter as assigned — pending means no decision taken yet by TV1
			return ((Number) session
					.createNativeQuery("SELECT COUNT(DISTINCT ic.batch_id) FROM inward_cheque ic " +
							"JOIN inward_batch ib ON ic.batch_id = ib.id " +
							"WHERE ic.send_to = 'TV_1' " +
							"AND ib.created_at BETWEEN :from AND :to " +
							"AND ib.batch_status = 'PendingAtChecker' " +
							"AND ic.cheque_status = 'Processed' " +
							"AND ic.cbs_validation = 'Valid' " +
							"AND ic.decision = 'PENDING'")
					.setParameter("from", from)
					.setParameter("to", to)
					.getSingleResult()).longValue();
		}
	}

	// ── KPI: cleared batches ───────────────────────────────────────────────
	@Override
	public Long getClearedBatchCountTV1(Date date) {

		LocalDateTime from = startOfDay(date);
		LocalDateTime to = endOfDay(date);

		try (Session session = HibernateUtil.getSessionFactory().openSession()) {

			// NOT EXISTS subquery — count batches that have NO remaining Processed cheques
			// A batch is cleared when all TV1-eligible cheques (amount <= 100000, Valid) are decided
			return ((Number) session
					.createNativeQuery("SELECT COUNT(DISTINCT ib.id) FROM inward_batch ib " +
							"WHERE ib.created_at BETWEEN :from AND :to " +
							"AND NOT EXISTS (" +
							"   SELECT 1 FROM inward_cheque ic " +
							"   WHERE ic.batch_id = ib.id " +
							"   AND ic.cbs_validation = 'Valid' " +
							"   AND ic.amount <= :limit " +
							"   AND ic.cheque_status = 'Processed'" +
							")")
					.setParameter("from", from)
					.setParameter("to", to)
					.setParameter("limit", new BigDecimal("100000"))
					.getSingleResult()).longValue();
		}
	}

	// ── KPI: pending cheques ───────────────────────────────────────────────
	@Override
	public Long getPendingChequeCountTV1(Date date) {

		LocalDateTime from = startOfDay(date);
		LocalDateTime to = endOfDay(date);

		try (Session session = HibernateUtil.getSessionFactory().openSession()) {

			// Count individual cheque rows (not distinct batches) pending TV1 action
			return ((Number) session
					.createNativeQuery("SELECT COUNT(ic.id) FROM inward_cheque ic " +
							"JOIN inward_batch ib ON ic.batch_id = ib.id " +
							"WHERE ic.send_to = 'TV_1' " +
							"AND ib.created_at BETWEEN :from AND :to " +
							"AND ib.batch_status = 'PendingAtChecker' " +
							"AND ic.cheque_status = 'Processed' " +
							"AND ic.cbs_validation = 'Valid' " +
							"AND ic.decision = 'PENDING'")
					.setParameter("from", from)
					.setParameter("to", to)
					.getSingleResult()).longValue();
		}
	}

	// ── Per-batch: ALL cheques in the batch ───────────────────────────────
	@Override
	public Long getTotalChequesForBatchTV1(Long batchId) {
		try (Session session = HibernateUtil.getSessionFactory().openSession()) {

			// Count all cheques in the batch that passed CBS validation
			return ((Number) session
					.createNativeQuery("SELECT COUNT(id) FROM inward_cheque " +
							"WHERE batch_id = :batchId " +
							"AND cbs_validation = 'Valid'")
					.setParameter("batchId", batchId)
					.getSingleResult()).longValue();
		}
	}

	// ── Per-batch: cheques where sendTo = queue ───────────────────────────
	@Override
	public Long getAssignedChequesForBatchTV1(Long batchId) {

		try (Session session = HibernateUtil.getSessionFactory().openSession()) {

			// Amount <= 100000 (₹1 lakh threshold) — these go to TV1
			// JOIN inward_batch to check batch_status on the parent batch
			return ((Number) session
					.createNativeQuery("SELECT COUNT(ic.id) FROM inward_cheque ic " +
							"JOIN inward_batch ib ON ic.batch_id = ib.id " +
							"WHERE ic.batch_id = :batchId " +
							"AND ic.amount <= :maxAmount " +
							"AND ib.batch_status = 'PendingAtChecker' " +
							"AND ic.cbs_validation = 'Valid'")
					.setParameter("batchId", batchId)
					.setParameter("maxAmount", 100000.00)
					.getSingleResult()).longValue();
		}
	}

	// ── Per-batch: pending cheques ────────────────────────────────────────
	@Override
	public Long getPendingChequesForBatchTV1(Long batchId) {

		try (Session session = HibernateUtil.getSessionFactory().openSession()) {

			// Pending TV1 cheques — amount <= 100000, CBS Valid, still in Processed status
			return ((Number) session
					.createNativeQuery("SELECT COUNT(id) FROM inward_cheque " +
							"WHERE batch_id = :batchId " +
							"AND amount <= :limit " +
							"AND cbs_validation = 'Valid' " +
							"AND cheque_status = 'Processed'")
					.setParameter("batchId", batchId)
					.setParameter("limit", new BigDecimal("100000"))
					.getSingleResult()).longValue();
		}
	}

	// ── Per-batch: cleared cheques ─────────────────────────────────────────
	@Override
	public Long getClearedChequesForBatchTV1(Long batchId) {
		try (Session session = HibernateUtil.getSessionFactory().openSession()) {

			// Cleared TV1 cheques — amount <= 100000, CBS Valid, cheque_status is NOT Processed
			// (meaning a decision has already been taken on them)
			return ((Number) session
					.createNativeQuery("SELECT COUNT(id) FROM inward_cheque " +
							"WHERE batch_id = :batchId " +
							"AND cheque_status <> 'Processed' " +
							"AND amount <= :maxAmount " +
							"AND cbs_validation = 'Valid'")
					.setParameter("batchId", batchId)
					.setParameter("maxAmount", 100000)
					.getSingleResult()).longValue();
		}
	}

	// Submitted Cheques Count For Batch
	@Override
	public Long getSubmittedBatchesTV1(Long batchId) {

		try (Session session = HibernateUtil.getSessionFactory().openSession()) {

			// Count cheques submitted for TV1 — all five conditions must match
			// JOIN inward_batch to check batch_status on the parent batch
			return ((Number) session
					.createNativeQuery("SELECT COUNT(ic.id) FROM inward_cheque ic " +
							"JOIN inward_batch ib ON ic.batch_id = ib.id " +
							"WHERE ic.batch_id = :batchId " +
							"AND ic.send_to = 'TV_1' " +
							"AND ib.batch_status = 'ClearedAtChecker' " +
							"AND ic.cheque_status = 'Ready' " +
							"AND ic.cbs_validation = 'Valid' " +
							"AND ic.decision = 'ACCEPTED'")
					.setParameter("batchId", batchId)
					.getSingleResult()).longValue();
		}
	}

	// ── Batch list for a queue on a specific day ───────────────────────────
	@Override
	public List<InwardBatch> getBatchesByQueueAndDate(SendTo queue, Date date) {

		LocalDateTime from = startOfDay(date);
		LocalDateTime to = endOfDay(date);

		try (Session session = HibernateUtil.getSessionFactory().openSession()) {

			// DISTINCT avoids duplicate batch rows when multiple cheques belong to the same batch
			// IN clause checks two allowed batch statuses: PendingAtChecker or ClearedAtChecker
			return session
					.createNativeQuery("SELECT DISTINCT ib.* FROM inward_batch ib " +
							"JOIN inward_cheque ic ON ic.batch_id = ib.id " +
							"WHERE ib.created_at BETWEEN :from AND :to " +
							"AND ib.batch_status IN ('PendingAtChecker', 'ClearedAtChecker') " +
							"ORDER BY ib.created_at DESC")
					.addEntity(InwardBatch.class)
					.setParameter("from", from)
					.setParameter("to", to)
					.list();
		}
	}

	@Override
	public Long getAssignedBatchCountTV2(SendTo queue, Date date) {
	    LocalDateTime from = startOfDay(date);
	    LocalDateTime to = endOfDay(date);

	    try (Session session = HibernateUtil.getSessionFactory().openSession()) {

	        return ((Number) session
	                .createNativeQuery("SELECT COUNT(DISTINCT ic.batch_id) FROM inward_cheque ic " +
	                        "JOIN inward_batch ib ON ic.batch_id = ib.id " +
	                        "WHERE ic.send_to = CAST(:queue AS send_to_enum) " +
	                        "AND ib.created_at BETWEEN :from AND :to")
	                .setParameter("queue", queue.name())
	                .setParameter("from", from)
	                .setParameter("to", to)
	                .getSingleResult()).longValue();
	    }
	}

	// ── KPI: pending batches (not yet Cleared) ─────────────────────────────
	@Override
	public Long getPendingBatchCountTV2(SendTo queue, Date date) {

		LocalDateTime from = startOfDay(date);
		LocalDateTime to = endOfDay(date);

		try (Session session = HibernateUtil.getSessionFactory().openSession()) {

			// Same multi-condition filter as TV1 pending — send_to uses the queue parameter for TV2
			return ((Number) session
					.createNativeQuery("SELECT COUNT(DISTINCT ic.batch_id) FROM inward_cheque ic " +
							"JOIN inward_batch ib ON ic.batch_id = ib.id " +
							"WHERE ic.send_to = CAST(:queue AS send_to_enum) " +
							"AND ib.created_at BETWEEN :from AND :to " +
							"AND ib.batch_status = 'PendingAtChecker' " +
							"AND ic.cheque_status = 'Processed' " +
							"AND ic.cbs_validation = 'Valid' " +
							"AND ic.decision = 'PENDING'")
					.setParameter("queue", queue.name())
					.setParameter("from", from)
					.setParameter("to", to)
					.getSingleResult()).longValue();
		}
	}

	// ── KPI: cleared batches ───────────────────────────────────────────────
	@Override
	public Long getClearedBatchCountTV2(SendTo queue, Date date) {

		LocalDateTime from = startOfDay(date);
		LocalDateTime to = endOfDay(date);

		try (Session session = HibernateUtil.getSessionFactory().openSession()) {

			// NOT EXISTS subquery — count batches that have NO remaining Processed cheques
			// TV2 handles high-value cheques (amount > 100000)
			return ((Number) session
					.createNativeQuery("SELECT COUNT(DISTINCT ib.id) FROM inward_batch ib " +
							"WHERE ib.created_at BETWEEN :from AND :to " +
							"AND NOT EXISTS (" +
							"   SELECT 1 FROM inward_cheque ic " +
							"   WHERE ic.batch_id = ib.id " +
							"   AND ic.cbs_validation = 'Valid' " +
							"   AND ic.amount > :limit " +
							"   AND ic.cheque_status = 'Processed'" +
							")")
					.setParameter("from", from)
					.setParameter("to", to)
					.setParameter("limit", new BigDecimal("100000"))
					.getSingleResult()).longValue();
		}
	}

	// ── KPI: pending cheques ───────────────────────────────────────────────
	@Override
	public Long getPendingChequeCountTV2(SendTo queue, Date date) {
		LocalDateTime from = startOfDay(date);
		LocalDateTime to = endOfDay(date);

		try (Session session = HibernateUtil.getSessionFactory().openSession()) {

			// Count cheques where decision is NULL or decision is not ACCEPTED
			// IS NULL handles cases where no decision has been set yet
			return ((Number) session
					.createNativeQuery("SELECT COUNT(ic.id) FROM inward_cheque ic " +
							"JOIN inward_batch ib ON ic.batch_id = ib.id " +
							"WHERE ic.send_to = CAST(:queue AS send_to_enum) " +
							"AND ib.created_at BETWEEN :from AND :to " +
							"AND (ic.decision IS NULL OR ic.decision <> 'ACCEPTED')")
					.setParameter("queue", queue.name())
					.setParameter("from", from)
					.setParameter("to", to)
					.getSingleResult()).longValue();
		}
	}

	// ── Per-batch: ALL cheques in the batch ───────────────────────────────
	@Override
	public Long getTotalChequesForBatchTV2(Long batchId) {
		try (Session session = HibernateUtil.getSessionFactory().openSession()) {

			// Count all cheques in the batch that passed CBS validation
			return ((Number) session
					.createNativeQuery("SELECT COUNT(id) FROM inward_cheque " +
							"WHERE batch_id = :batchId " +
							"AND cbs_validation = 'Valid'")
					.setParameter("batchId", batchId)
					.getSingleResult()).longValue();
		}
	}

	// ── Per-batch: cheques where sendTo = queue ───────────────────────────
	@Override
	public Long getAssignedChequesForBatchTV2(Long batchId, SendTo queue) {
		try (Session session = HibernateUtil.getSessionFactory().openSession()) {

			// TV2 handles high-value cheques — amount > 100000 (above ₹1 lakh threshold)
			return ((Number) session
					.createNativeQuery("SELECT COUNT(id) FROM inward_cheque " +
							"WHERE batch_id = :batchId " +
							"AND amount > :maxAmount " +
							"AND cbs_validation = 'Valid'")
					.setParameter("batchId", batchId)
					.setParameter("maxAmount", 100000.00)
					.getSingleResult()).longValue();
		}
	}

	// ── Per-batch: pending cheques ────────────────────────────────────────
	@Override
	public Long getPendingChequesForBatchTV2(Long batchId, SendTo queue) {

		try (Session session = HibernateUtil.getSessionFactory().openSession()) {

			// Pending TV2 cheques — send_to matches queue, all five conditions must match
			// Enum is passed as its String name since SQL does not know Java enums
			return ((Number) session
					.createNativeQuery("SELECT COUNT(ic.id) FROM inward_cheque ic " +
							"JOIN inward_batch ib ON ic.batch_id = ib.id " +
							"WHERE ic.batch_id = :batchId " +
							"AND ic.send_to = CAST(:sendTo AS send_to_enum) " +
							"AND ib.batch_status = 'PendingAtChecker' " +
							"AND ic.cheque_status = 'Processed' " +
							"AND ic.cbs_validation = 'Valid' " +
							"AND ic.decision = 'PENDING'")
					.setParameter("batchId", batchId)
					.setParameter("sendTo", queue.name())
					.getSingleResult()).longValue();
		}
	}

	// ── Per-batch: cleared cheques ─────────────────────────────────────────
	@Override
	public Long getClearedChequesForBatchTV2(Long batchId, SendTo queue) {
		try (Session session = HibernateUtil.getSessionFactory().openSession()) {

			// Cleared TV2 cheques — amount > 100000, CBS Valid, cheque_status is NOT Processed
			// (meaning a decision has already been taken on them)
			return ((Number) session
					.createNativeQuery("SELECT COUNT(id) FROM inward_cheque " +
							"WHERE batch_id = :batchId " +
							"AND cheque_status <> 'Processed' " +
							"AND amount > :minAmount " +
							"AND cbs_validation = 'Valid'")
					.setParameter("batchId", batchId)
					.setParameter("minAmount", new BigDecimal("100000"))
					.getSingleResult()).longValue();
		}
	}

	@Override
	public void updateBatchStatusIfCompleted(Long batchId) {

		try (Session session = HibernateUtil.getSessionFactory().openSession()) {

			Transaction tx = session.beginTransaction();

			// Count cheques still in undecided statuses (Normal, Processed, Repair) and CBS Valid
			// IN clause covers all three undecided cheque statuses in one query
			Long pendingCount = ((Number) session
					.createNativeQuery("SELECT COUNT(id) FROM inward_cheque " +
							"WHERE batch_id = :batchId " +
							"AND cbs_validation = 'Valid' " +
							"AND cheque_status IN ('Normal', 'Processed', 'Repair')")
					.setParameter("batchId", batchId)
					.getSingleResult()).longValue();

			if (pendingCount != null && pendingCount == 0) {

				// No pending cheques remain — update batch_status to ClearedAtChecker
				// Extra safety check: only update if batch is NOT already ClearedAtChecker
				session.createNativeQuery(
								"UPDATE inward_batch SET batch_status = 'ClearedAtChecker' " +
								"WHERE id = :batchId " +
								"AND batch_status <> 'ClearedAtChecker'")
						.setParameter("batchId", batchId)
						.executeUpdate();
			}

			tx.commit();
		}
	}

	// ── Helpers ───────────────────────────────────────────────────────────
	private LocalDateTime startOfDay(Date date) {
		return date.toInstant().atZone(ZoneId.systemDefault()).toLocalDate().atStartOfDay();
	}

	private LocalDateTime endOfDay(Date date) {
		return date.toInstant().atZone(ZoneId.systemDefault()).toLocalDate().atTime(23, 59, 59);
	}

}