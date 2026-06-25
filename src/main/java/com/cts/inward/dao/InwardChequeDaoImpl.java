package com.cts.inward.dao;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import org.hibernate.Session;
import org.hibernate.Transaction;

import com.cts.inward.dto.ChequeReportDTO;
import com.cts.inward.dto.ReportChequeDetailDTO;
import com.cts.inward.entity.InwardBatch;
import com.cts.inward.entity.InwardCheque;
import com.cts.inward.enums.BatchStatus;
import com.cts.inward.enums.CbsValidation;
import com.cts.inward.enums.ChequeStatus;
import com.cts.inward.enums.DecisionStatus;
import com.cts.inward.enums.SendTo;
import com.cts.util.HibernateUtil;

public class InwardChequeDaoImpl implements InwardChequeDao {

	// ── Basic CRUD ─────────────────────────────────────────────────────────

	@Override
	public void save(InwardCheque cheque) {
		Transaction tx = null;
		try (Session session = HibernateUtil.getSessionFactory().openSession()) {
			tx = session.beginTransaction();

			// Re-fetch the real managed InwardBatch inside THIS session.
			// BatchProcessingServiceImpl puts a dummy InwardBatch (id=null)
			// on the cheque just to carry the batchId string — we replace it here.
			if (cheque.getBatch() != null && cheque.getBatch().getBatchId() != null) {
				InwardBatch managedBatch = (InwardBatch) session
						.createNativeQuery("SELECT * FROM inward_batch WHERE batch_id = :batchId", InwardBatch.class)
						.setParameter("batchId", cheque.getBatch().getBatchId()).uniqueResult();

				if (managedBatch == null) {
					throw new RuntimeException("InwardBatch not found for batchId: " + cheque.getBatch().getBatchId());
				}
				cheque.setBatch(managedBatch); // replace dummy with managed entity
			}

			session.persist(cheque);
			tx.commit();

		} catch (Exception e) {
			// tx may be null if openSession() itself threw
			if (tx != null && tx.isActive()) {
				try {
					tx.rollback();
				} catch (Exception rb) {
					/* ignore rollback error */ }
			}
			throw new RuntimeException("Failed to save cheque : " + e.getMessage(), e);
		}
	}

	@Override
	public void update(InwardCheque cheque) {
		Transaction tx = null;
		try (Session session = HibernateUtil.getSessionFactory().openSession()) {
			tx = session.beginTransaction();
			session.merge(cheque);
			tx.commit();
		} catch (Exception e) {
			if (tx != null && tx.isActive()) {
				try {
					tx.rollback();
				} catch (Exception rb) {
					/* ignore */ }
			}
			throw new RuntimeException("Failed to update cheque : " + e.getMessage(), e);
		}
	}

	// ── Maker correction ───────────────────────────────────────────────────

	/**
	 * saveCorrections() — saves only the maker-corrected fields. Already uses
	 * native SQL — no change needed here.
	 */
	@Override
	public void saveCorrections(InwardCheque cheque) {
		Transaction tx = null;
		try (Session session = HibernateUtil.getSessionFactory().openSession()) {
			tx = session.beginTransaction();

			String sql = "UPDATE inward_cheque SET " + "  account_no               = :accountNo,              "
					+ "  amount                   = :amount,                 "
					+ "  amount_in_words          = :amountInWords,          "
					+ "  cheque_no                = :chequeNo,               "
					+ "  micr_code                = :micrCode,               "
					+ "  ifsc_code                = :ifscCode,               "
					+ "  payee_name               = :payeeName,              "
					+ "  drawer_name              = :drawerName,             "
					+ "  presenting_bank          = :presentingBank,         "
					+ "  branch_name              = :branchName,             "
					+ "  transaction_code         = :transactionCode,        "
					+ "  cheque_date              = :chequeDate,             "
					+ "  maker_correction_remarks = :makerCorrectionRemark,  "
					+ "  is_edited_by_maker       = :isEditedByMaker,        "
					+ "  edited_fields            = :editedFields,           "
					+ "  cbs_validation           = cast(:cbsValidation AS cbs_validation) " + "WHERE id = :id";

			session.createNativeQuery(sql).setParameter("accountNo", cheque.getAccountNo())
					.setParameter("amount", cheque.getAmount()).setParameter("amountInWords", cheque.getAmountInWords())
					.setParameter("chequeNo", cheque.getChequeNo()).setParameter("micrCode", cheque.getMicrCode())
					.setParameter("ifscCode", cheque.getIfscCode()).setParameter("payeeName", cheque.getPayeeName())
					.setParameter("drawerName", cheque.getDrawerName())
					.setParameter("presentingBank", cheque.getPresentingBank())
					.setParameter("branchName", cheque.getBranchName())
					.setParameter("transactionCode", cheque.getTransactionCode())
					.setParameter("chequeDate", cheque.getChequeDate())
					.setParameter("makerCorrectionRemark", cheque.getMakerCorrectionRemark())
					.setParameter("isEditedByMaker", cheque.isEditedByMaker())
					.setParameter("editedFields", cheque.getEditedFields())
					.setParameter("cbsValidation", getEnumName(cheque.getCbsValidation()))
					.setParameter("id", cheque.getId()).executeUpdate();

			tx.commit();

		} catch (Exception e) {
			if (tx != null)
				tx.rollback();
			throw new RuntimeException(
					"Error saving corrections for cheque id=" + cheque.getId() + ": " + e.getMessage(), e);
		}
	}

	@Override
	public List<InwardCheque> findByBatchId(Long batchId) {

		try (Session session = HibernateUtil.getSessionFactory().openSession()) {

			InwardBatch batch = session.get(InwardBatch.class, batchId);
			BatchStatus batchStatus = batch.getBatchStatus();

			// PendingAtChecker -> only CBS Valid cheques
			if (BatchStatus.PendingAtChecker.equals(batchStatus)
					|| BatchStatus.ClearedAtChecker.equals(batchStatus)) {

				return session.createNativeQuery(
						"SELECT * FROM inward_cheque "
						+ "WHERE batch_id = :batchId "
						+ "AND cbs_validation = CAST('Valid' AS cbs_validation) "
						+ "ORDER BY id ASC", InwardCheque.class)
						.setParameter("batchId", batchId)
						.list();

			}
			// Cleared & ClearedAtChecker -> only Normal cheques
			else if (BatchStatus.Cleared.equals(batchStatus) || BatchStatus.ClearedAtChecker.equals(batchStatus)) {

				return session.createNativeQuery(
						"SELECT * FROM inward_cheque "
						+ "WHERE batch_id = :batchId "
						+ "AND cheque_status = CAST('Normal' AS cheque_status) "
						+ "ORDER BY id ASC", InwardCheque.class)
						.setParameter("batchId", batchId)
						.list();

			}
			// Draft & Pending -> all cheques
			else {

				return session.createNativeQuery(
						"SELECT * FROM inward_cheque "
						+ "WHERE batch_id = :batchId "
						+ "ORDER BY id ASC", InwardCheque.class)
						.setParameter("batchId", batchId)
						.list();
			}
		}
	}

	@Override
	public List<InwardCheque> TV1_ChequesList(Long batchId) {
	    try (Session session = HibernateUtil.getSessionFactory().openSession()) {

	        return session.createNativeQuery(
	            "SELECT * FROM inward_cheque "
	            + "WHERE batch_id = :batchId "
	            + "AND cbs_validation = CAST('Valid' AS cbs_validation) "
	            + "AND amount <= :maxAmount "
	            + "ORDER BY id ASC", InwardCheque.class)
	            .setParameter("batchId",    batchId)
	            .setParameter("maxAmount",  new BigDecimal("100000"))
	            .list();
	    }
	}

	@Override
	public List<InwardCheque> TV2_ChequesList(Long batchId) {
	    try (Session session = HibernateUtil.getSessionFactory().openSession()) {

	        return session.createNativeQuery(
	            "SELECT * FROM inward_cheque "
	            + "WHERE batch_id = :batchId "
	            + "AND cbs_validation = CAST('Valid' AS cbs_validation) "
	            + "AND amount > :minAmount "
	            + "ORDER BY id ASC", InwardCheque.class)
	            .setParameter("batchId",   batchId)
	            .setParameter("minAmount", new BigDecimal("100000"))
	            .list();
	    }
	}

	@Override
	public void updateMICR(InwardCheque inwardCheque) {
		Transaction tx = null;
		try (Session session = HibernateUtil.getSessionFactory().openSession()) {
			tx = session.beginTransaction();
			session.merge(inwardCheque);
			tx.commit();
		} catch (Exception e) {
			if (tx != null)
				tx.rollback();
			throw new RuntimeException("Error updating InwardCheque", e);
		}
	}

	@Override
	public long countByBatchId(Long batchId, String role) {

	    String sql = "SELECT COUNT(*) FROM inward_cheque WHERE batch_id = :batchId";

	    // TV1 and TV2 should see only CBS Valid cheques
	    if ("TV1".equalsIgnoreCase(role) || "TV2".equalsIgnoreCase(role)) {
	        sql += " AND cbs_validation = CAST('Valid' AS cbs_validation)";
	    }

	    try (Session session = HibernateUtil.getSessionFactory().openSession()) {

	        Number result = (Number) session.createNativeQuery(sql)
	                .setParameter("batchId", batchId)
	                .getSingleResult();

	        return result != null ? result.longValue() : 0L;
	    }
	}

	@Override
	public long countMicrErrorsByBatchId(Long batchId) {
		try (Session session = HibernateUtil.getSessionFactory().openSession()) {
			Number result = (Number) session.createNativeQuery(
					"SELECT COUNT(*) FROM inward_cheque "
					+ "WHERE batch_id = :batchId "
					+ "AND cheque_status = CAST('Repair' AS cheque_status)")
					.setParameter("batchId", batchId)
					.getSingleResult();
			return result != null ? result.longValue() : 0L;
		}
	}

	/**
	 * Updates decision = 'REJECTED' for all INVALID cheques in this batch using a
	 * native SQL query.
	 *
	 * WHY native SQL instead of Hibernate merge(): The decision column uses a
	 * PostgreSQL custom ENUM type (decision_status).
	 * Hibernate's @Enumerated(EnumType.STRING) sends the value as a plain varchar,
	 * but PostgreSQL requires an explicit cast to the custom type. Native SQL lets
	 * us write the cast directly: CAST('REJECTED' AS decision_status) which works
	 * reliably regardless of Hibernate type mapping.
	 */
	@Override
	public void updateDecisionToRejectedForBatch(Long batchId) {
		Transaction tx = null;
		try (Session session = HibernateUtil.getSessionFactory().openSession()) {
			tx = session.beginTransaction();
			session.createNativeQuery("UPDATE inward_cheque " + "SET decision = CAST('REJECTED' AS decision_status), "
					+ "    cheque_status = CAST('Reject' AS cheque_status), " + "    send_to = 'MAKER' "
					+ "WHERE batch_id = :batchId " + "AND cbs_validation = CAST('Invalid' AS cbs_validation)")
					.setParameter("batchId", batchId).executeUpdate();
			tx.commit();
			System.out.println("DaoImpl: REJECTED update committed for batchId=" + batchId);
		} catch (Exception e) {
			if (tx != null)
				tx.rollback();
			throw new RuntimeException("Error updating decision to REJECTED", e);
		}
	}

	/**
	 * Updates cbs_validation and error_reason for a single cheque by ID, using
	 * native SQL (with explicit enum cast) to avoid the session.merge() issues that
	 * occur with detached entities holding lazy @ManyToOne associations (e.g.
	 * InwardCheque.batch).
	 *
	 * This is called once per cheque from CbsChequeListComposer's timer tick, so it
	 * must be lightweight and reliable — a full merge() of the detached entity is
	 * not needed, only these two columns change.
	 */
	@Override
	public void updateCbsValidationResult(Long chequeId, CbsValidation cbsValidation, String errorReason) {
		Transaction tx = null;
		try (Session session = HibernateUtil.getSessionFactory().openSession()) {
			tx = session.beginTransaction();
			session.createNativeQuery(
					"UPDATE inward_cheque " + "SET cbs_validation = CAST(:cbsValidation AS cbs_validation), "
							+ "    cbs_reject_reason = :errorReason " + "WHERE id = :chequeId")
					.setParameter("cbsValidation", cbsValidation != null ? cbsValidation.name() : null)
					.setParameter("errorReason", errorReason).setParameter("chequeId", chequeId).executeUpdate();
			tx.commit();
		} catch (Exception e) {
			if (tx != null)
				tx.rollback();
			throw new RuntimeException("Error updating CBS validation result for chequeId=" + chequeId, e);
		}
	}

	/**
	 * Forward to Tv1 and Tv2.
	 *
	 * For every VALID cheque in this batch (cbs_validation = 'Valid'): - amount <=
	 * threshold → send_to = 'TV_1' - amount > threshold → send_to = 'TV_2' -
	 * decision = 'PENDING' (CAST to decision_status) - cheque_status = 'Pending'
	 * (CAST to cheque_status)
	 *
	 * Invalid/REJECTED cheques are untouched here — those are handled by
	 * updateDecisionToRejectedForBatch(), which sets send_to = 'MAKER' and
	 * cheque_status = 'Reject'.error_reason
	 *
	 * send_to has no @JdbcTypeCode(NAMED_ENUM)/columnDefinition on the entity, so
	 * it is mapped as a plain varchar column — no cast needed for it.
	 */
	@Override
	public void forwardToTvQueuesByThreshold(Long batchId, java.math.BigDecimal threshold) {
		Transaction tx = null;
		try (Session session = HibernateUtil.getSessionFactory().openSession()) {
			tx = session.beginTransaction();

			// TV_1: amount <= threshold
			session.createNativeQuery("UPDATE inward_cheque " + "SET send_to = 'TV_1', "
					+ "    decision = CAST('PENDING' AS decision_status), "
					+ "    cheque_status = CAST('Processed' AS cheque_status) " + "WHERE batch_id = :batchId "
					+ "AND cbs_validation = CAST('Valid' AS cbs_validation) " + "AND amount <= :threshold")
					.setParameter("batchId", batchId).setParameter("threshold", threshold).executeUpdate();

			// TV_2: amount > threshold
			session.createNativeQuery("UPDATE inward_cheque " + "SET send_to = 'TV_2', "
					+ "    decision = CAST('PENDING' AS decision_status), "
					+ "    cheque_status = CAST('Processed' AS cheque_status) " + "WHERE batch_id = :batchId "
					+ "AND cbs_validation = CAST('Valid' AS cbs_validation) " + "AND amount > :threshold")
					.setParameter("batchId", batchId).setParameter("threshold", threshold).executeUpdate();

			tx.commit();
			System.out.println("DaoImpl: forwardToTvQueuesByThreshold committed for batchId=" + batchId + " threshold="
					+ threshold);
		} catch (Exception e) {
			if (tx != null)
				tx.rollback();
			throw new RuntimeException("Error forwarding cheques to TV1/TV2 for batchId=" + batchId, e);
		}
	}

	/**
	 * Returns all cheques in this batch where cbsValidation = Invalid. Used by
	 * CbsValidationComposer to mark them as REJECTED (Return to RRF).
	 */
	@Override
	public List<InwardCheque> findInvalidByBatchId(Long batchId) {
		try (Session session = HibernateUtil.getSessionFactory().openSession()) {
			return session.createNativeQuery(
					"SELECT * FROM inward_cheque "
					+ "WHERE batch_id = :batchId "
					+ "AND cbs_validation = CAST('Invalid' AS cbs_validation)", InwardCheque.class)
					.setParameter("batchId", batchId)
					.list();
		}
	}

	// Fetches TV1 or TV2 data according to the login
	@Override
	public long getChequeCountByRole(Long batchId, String role) {

	    String sql;

	    if ("TV1".equalsIgnoreCase(role)) {

	        sql = "SELECT COUNT(*) FROM inward_cheque "
	            + "WHERE batch_id = :batchId "
	            + "AND amount <= :limit "
	            + "AND cbs_validation = CAST('Valid' AS cbs_validation)";

	    } else if ("TV2".equalsIgnoreCase(role)) {

	        sql = "SELECT COUNT(*) FROM inward_cheque "
	            + "WHERE batch_id = :batchId "
	            + "AND amount > :limit "
	            + "AND cbs_validation = CAST('Valid' AS cbs_validation)";

	    } else {
	        return 0L;
	    }

	    try (Session session = HibernateUtil.getSessionFactory().openSession()) {

	        Number count = (Number) session.createNativeQuery(sql)
	                .setParameter("batchId", batchId)
	                .setParameter("limit", new BigDecimal("100000"))
	                .getSingleResult();

	        return count == null ? 0L : count.longValue();
	    }
	}

	// cbs button enable and disable is dependent on this method in maker
	// if all cheques status is normal then it will enable the cbs validation button
	@Override
	public long getNonNormalChequeCount(Long batchId) {

		try (Session session = HibernateUtil.getSessionFactory().openSession()) {

			Number count = (Number) session.createNativeQuery(
					"SELECT COUNT(*) FROM inward_cheque "
					+ "WHERE batch_id = :batchId "
					+ "AND cheque_status <> CAST('Normal' AS cheque_status)")
					.setParameter("batchId", batchId)
					.getSingleResult();

			return count == null ? 0L : count.longValue();
		}
	}

	/**
	 * resubmitToChecker() — already uses native SQL — no change needed.
	 */
	@Override
	public void resubmitToChecker(InwardCheque cheque) {
		Transaction tx = null;
		try (Session session = HibernateUtil.getSessionFactory().openSession()) {
			tx = session.beginTransaction();

			String sql = "UPDATE inward_cheque SET " + "  cheque_status  = cast(:chequeStatus  AS cheque_status),   "
					+ "  decision       = cast(:decision       AS decision_status), "
					+ "  send_to        = cast(:sendTo         AS send_to_enum),   "
					+ "  cbs_validation = cast(:cbsValidation  AS cbs_validation), "
					+ "  resubmitted_by = :resubmittedBy " + "WHERE id = :id";

			session.createNativeQuery(sql).setParameter("chequeStatus", getEnumName(cheque.getChequeStatus()))
					.setParameter("decision", getEnumName(cheque.getDecision()))
					.setParameter("sendTo", getEnumName(cheque.getSendTo()))
					.setParameter("cbsValidation", getEnumName(cheque.getCbsValidation()))
					.setParameter("resubmittedBy", cheque.getResubmittedBy()).setParameter("id", cheque.getId())
					.executeUpdate();

			tx.commit();

		} catch (Exception e) {
			if (tx != null)
				tx.rollback();
			throw new RuntimeException("Error resubmitting cheque id=" + cheque.getId() + ": " + e.getMessage(), e);
		}
	}

	// ── Query methods ──────────────────────────────────────────────────────

	@Override
	public Optional<InwardCheque> findByChequeNumber(String chequeNumber) {
		try (Session session = HibernateUtil.getSessionFactory().openSession()) {
			InwardCheque result = (InwardCheque) session.createNativeQuery(
					"SELECT * FROM inward_cheque WHERE cheque_no = :chequeNo", InwardCheque.class)
					.setParameter("chequeNo", chequeNumber)
					.uniqueResult();
			return Optional.ofNullable(result);
		} catch (Exception e) {
			throw new RuntimeException("Error finding cheque: " + e.getMessage(), e);
		}
	}

	@Override
	public InwardCheque findById(Long id) {
		try (Session session = HibernateUtil.getSessionFactory().openSession()) {
			return session.get(InwardCheque.class, id);
		}
	}

	/**
	 * findAll()
	 *
	 * CHANGED: was HQL "FROM InwardCheque" NOW: native SQL SELECT * FROM
	 * inward_cheque ORDER BY created_at DESC
	 *
	 * We add ORDER BY created_at DESC so newest cheques appear first — same
	 * ordering logic used in all other list queries in this project.
	 */
	@Override
	public List<InwardCheque> findAll() {
		try (Session session = HibernateUtil.getSessionFactory().openSession()) {

			String sql = "SELECT * FROM inward_cheque ORDER BY created_at DESC";

			return session.createNativeQuery(sql, InwardCheque.class).list();

		} catch (Exception e) {
			throw new RuntimeException("Error loading all cheques: " + e.getMessage(), e);
		}
	}

	/**
	 * findByBatchId()
	 *
	 * SQL explanation: ic = alias for inward_cheque JOIN inward_batch ib ON
	 * ic.batch_id = ib.id WHERE ib.batch_id = :batchId ← batchId is a String column
	 * in inward_batch
	 *
	 * NOTE: ic.batch_id is the FK column (Long), ib.batch_id is the String business
	 * key. We JOIN on ic.batch_id = ib.id (PK), then filter on ib.batch_id
	 * (String).
	 */
	@Override
	public List<InwardCheque> findByBatchId(String batchId) {
		try (Session session = HibernateUtil.getSessionFactory().openSession()) {

			String sql = "SELECT ic.* FROM inward_cheque ic " + "JOIN inward_batch ib ON ic.batch_id = ib.id "
					+ "WHERE ib.batch_id = :batchId " + "ORDER BY ic.created_at DESC";

			return session.createNativeQuery(sql, InwardCheque.class).setParameter("batchId", batchId).list();

		} catch (Exception e) {
			throw new RuntimeException("Error loading cheques for batch: " + e.getMessage(), e);
		}
	}

	/**
	 * existsByChequeNumber()
	 *
	 * CHANGED: was HQL "SELECT COUNT(c) FROM InwardCheque c WHERE c.chequeNo =
	 * :chequeNo" NOW: native SQL COUNT(*) on inward_cheque table directly.
	 *
	 * We cast getSingleResult() to Number (not Long) because PostgreSQL COUNT comes
	 * back as BigInteger via JDBC — using Number.longValue() handles both.
	 */
	@Override
	public boolean existsByChequeNumber(String chequeNumber) {
		try (Session session = HibernateUtil.getSessionFactory().openSession()) {

			String sql = "SELECT COUNT(*) FROM inward_cheque WHERE cheque_no = :chequeNo";

			Number count = (Number) session.createNativeQuery(sql).setParameter("chequeNo", chequeNumber)
					.getSingleResult();

			return count != null && count.longValue() > 0;

		} catch (Exception e) {
			throw new RuntimeException("Error checking cheque existence: " + e.getMessage(), e);
		}
	}

	/**
	 * findAllNeedingCorrection()
	 *
	 * CHANGED: was HQL with enum parameters directly. NOW: native SQL with CAST for
	 * all three PostgreSQL enum columns.
	 *
	 * Filters: cheque_status = 'Repair' (returned to maker for correction) decision
	 * = 'REFERRED' (not yet final decision) send_to = 'MAKER' (routed back to
	 * maker) Ordered: newest first by created_at.
	 */

	@Override
	public List<InwardCheque> findAllNeedingCorrection() {
		try (Session session = HibernateUtil.getSessionFactory().openSession()) {

			String sql = "SELECT * FROM inward_cheque " + "WHERE cheque_status = cast(:status   AS cheque_status)  "
					+ "AND   decision      = cast(:decision  AS decision_status) "
					+ "AND   send_to       = cast(:sendTo    AS send_to_enum)   " + "ORDER BY created_at DESC";

			return session.createNativeQuery(sql, InwardCheque.class).setParameter("status", ChequeStatus.Repair.name())
					.setParameter("decision", DecisionStatus.REFERRED.name())
					.setParameter("sendTo", SendTo.MAKER.name()).list();

		} catch (Exception e) {
			throw new RuntimeException("Error loading cheques needing correction: " + e.getMessage(), e);
		}
	}

	@Override
	public long countNeedingCorrection() {
		try (Session session = HibernateUtil.getSessionFactory().openSession()) {
			Number count = (Number) session.createNativeQuery(
					"SELECT COUNT(*) FROM inward_cheque "
					+ "WHERE cheque_status = CAST('Repair' AS cheque_status) "
					+ "AND   decision      = CAST('REFERRED' AS decision_status) "
					+ "AND   send_to       = CAST('MAKER' AS send_to_enum)")
					.getSingleResult();
			return count != null ? count.longValue() : 0L;
		} catch (Exception e) {
			throw new RuntimeException("Error counting cheques: " + e.getMessage(), e);
		}
	}

	// ── Null-safe enum helper ──────────────────────────────────────────────

	private String getEnumName(Enum<?> enumValue) {
		return enumValue != null ? enumValue.name() : null;
	}

	/**
	 * Fetches accepted cheques for the given batch ID. Input: batchId Returns: List
	 * of ChequeReportDTO objects.
	 */
	@Override
	public List<ChequeReportDTO> findAcceptedCheques(String batchId) {

		try (Session session = HibernateUtil.getSessionFactory().openSession()) {

			String sql = "SELECT " + "    b.batch_id, " + "    c.cheque_no, " + "    c.cheque_date, "
					+ "    c.micr_code, " + "    c.payee_name, " + "    c.amount " + "FROM inward_cheque c "
					+ "JOIN inward_batch b ON c.batch_id = b.id " + "WHERE b.batch_id = :batchId "
					+ "AND c.cheque_status = CAST(:chequeStatus AS cheque_status) "
					+ "AND c.decision = CAST(:decision AS decision_status) " + "ORDER BY b.batch_id";

			List<Object[]> rows = session.createNativeQuery(sql).setParameter("batchId", batchId)
					.setParameter("chequeStatus", ChequeStatus.Ready.name())
					.setParameter("decision", DecisionStatus.ACCEPTED.name()).list();

			List<ChequeReportDTO> result = new ArrayList<>();

			for (Object[] row : rows) {
				result.add(new ChequeReportDTO((String) row[0], // batchId
						(String) row[1], // chequeNo
						row[2] != null ? ((java.sql.Timestamp) row[2]).toLocalDateTime() : null, // chequeDate
						(String) row[3], // micrCode
						(String) row[4], // payeeName
						(BigDecimal) row[5], // amount
						null // reason
				));
			}

			return result;
		}
	}

	/**
	 * DTO projection — RRF (returned cheques). Condition: chequeStatus = Reject AND
	 * decision = REJECTED Includes c.batch.batchId for JasperReports grouping.
	 */
	@Override
	public List<ChequeReportDTO> findReturnedCheques(String batchId) {
		try (Session session = HibernateUtil.getSessionFactory().openSession()) {

			String sql = "SELECT b.batch_id, c.cheque_no, c.cheque_date, c.micr_code, c.payee_name, "
					+ "       c.amount, c.cbs_reject_reason "
					+ "FROM inward_cheque c "
					+ "JOIN inward_batch b ON c.batch_id = b.id "
					+ "WHERE b.batch_id = :batchId "
					+ "AND c.cheque_status = CAST('Reject' AS cheque_status) "
					+ "AND c.decision      = CAST('REJECTED' AS decision_status) "
					+ "ORDER BY b.batch_id";

			List<Object[]> rows = session.createNativeQuery(sql)
					.setParameter("batchId", batchId).list();

			List<ChequeReportDTO> result = new ArrayList<>();
			for (Object[] row : rows) {
				result.add(new ChequeReportDTO(
						(String) row[0], // batchId
						(String) row[1], // chequeNo
						row[2] != null ? ((java.sql.Timestamp) row[2]).toLocalDateTime() : null, // chequeDate
						(String) row[3], // micrCode
						(String) row[4], // payeeName
						(BigDecimal) row[5], // amount
						(String) row[6]  // errorReason
				));
			}
			return result;
		}
	}

	/**
	 * NEW METHOD — added for TV2's Approve / Reject / Refer actions.
	 *
	 * Updates only decision / cheque_status / send_to via a scoped native SQL
	 * UPDATE with explicit Postgres enum casts (same pattern as
	 * {@code InwardChequeMICRDaoImpl.updateCbsValidationResult()}).
	 *
	 * Deliberately NOT routed through {@code InwardChequeMICRDaoImpl.update()} —
	 * that method does a full {@code session.merge()} of the whole entity and is
	 * shared with the MICR repair flow ({@code InwardChequeServiceMICRImpl}). Using
	 * this dedicated, narrowly-scoped method instead means TV2's decision actions
	 * can never accidentally affect MICR-repair columns or any other code path
	 * relying on that shared method.
	 */
	@Override
	public void updateTv2Decision(Long chequeId, DecisionStatus decision, ChequeStatus chequeStatus, SendTo sendTo) {
		Transaction tx = null;
		try (Session session = HibernateUtil.getSessionFactory().openSession()) {
			tx = session.beginTransaction();
			session.createNativeQuery("UPDATE inward_cheque " + "SET decision = CAST(:decision AS decision_status), "
					+ "    cheque_status = CAST(:chequeStatus AS cheque_status), "
					+ "    send_to = CAST(:sendTo AS send_to) " + "WHERE id = :chequeId")
					.setParameter("decision", decision != null ? decision.name() : null)
					.setParameter("chequeStatus", chequeStatus != null ? chequeStatus.name() : null)
					.setParameter("sendTo", sendTo != null ? sendTo.name() : null).setParameter("chequeId", chequeId)
					.executeUpdate();
			tx.commit();
		} catch (Exception e) {
			if (tx != null)
				tx.rollback();
			throw new RuntimeException("Error updating TV2 decision for chequeId=" + chequeId, e);
		}
	}

	/**
	 * Fetches all cheques for the given batchId. Status is resolved using the same
	 * dual condition used everywhere else:
	 *
	 * chequeStatus = Ready AND decision = ACCEPTED → "ACCEPTED" chequeStatus =
	 * Reject AND decision = REJECTED → "REJECTED" anything else → "PENDING"
	 *
	 * Ordering: Accepted first, Rejected second, Pending last, then chequeNo asc.
	 */
	@Override
	public List<ReportChequeDetailDTO> findAllChequesForReport(String batchId) {

		String sql = "SELECT " + " c.cheque_no, " + " c.account_no, " + " c.payee_name, " + " c.amount, " + " CASE "
				+ "   WHEN c.cheque_status = 'Ready' AND c.decision = 'ACCEPTED' THEN 'ACCEPTED' "
				+ "   WHEN c.cheque_status = 'Reject' AND c.decision = 'REJECTED' THEN 'REJECTED' "
				+ "   ELSE 'PENDING' " + " END AS status, " + " c.cbs_reject_reason, " + " c.micr_code, "
				+ " c.cheque_date " + "FROM inward_cheque c " + "JOIN inward_batch b ON c.batch_id = b.id "
				+ "WHERE b.batch_id = :batchId " + "ORDER BY " + " CASE "
				+ "   WHEN c.cheque_status = 'Ready' AND c.decision = 'ACCEPTED' THEN 0 "
				+ "   WHEN c.cheque_status = 'Reject' AND c.decision = 'REJECTED' THEN 1 " + "   ELSE 2 "
				+ " END, c.cheque_no";

		try (Session session = HibernateUtil.getSessionFactory().openSession()) {

			List<Object[]> rows = session.createNativeQuery(sql).setParameter("batchId", batchId).getResultList();

			List<ReportChequeDetailDTO> result = new ArrayList<>();

			for (Object[] row : rows) {

				LocalDateTime chequeDate = null;
				if (row[7] != null) {
					chequeDate = ((java.sql.Timestamp) row[7]).toLocalDateTime();
				}

				result.add(new ReportChequeDetailDTO((String) row[0], // chequeNo
						(String) row[1], // accountNo
						(String) row[2], // payeeName
						(BigDecimal) row[3], // amount
						(String) row[4], // status
						(String) row[5], // reason
						(String) row[6], // micrCode
						chequeDate // LocalDateTime
				));
			}

			return result;
		}
	}

	@Override
	public List<InwardCheque> findResubmittedForChecker() {
		try (Session session = HibernateUtil.getSessionFactory().openSession()) {
			return session.createNativeQuery(
					"SELECT * FROM inward_cheque "
					+ "WHERE cheque_status = CAST('Resubmitted' AS cheque_status) "
					+ "AND   send_to       = CAST('TV_1' AS send_to_enum) "
					+ "AND   decision      = CAST('REFERRED' AS decision_status) "
					+ "ORDER BY created_at DESC", InwardCheque.class)
					.list();
		} catch (Exception e) {
			throw new RuntimeException("Error loading resubmitted cheques for checker: " + e.getMessage(), e);
		}
	}

	@Override
	public long countResubmittedForChecker() {
		try (Session session = HibernateUtil.getSessionFactory().openSession()) {
			Number count = (Number) session.createNativeQuery(
					"SELECT COUNT(*) FROM inward_cheque "
					+ "WHERE cheque_status = CAST('Resubmitted' AS cheque_status) "
					+ "AND   send_to       = CAST('TV_1' AS send_to_enum) "
					+ "AND   decision      = CAST('REFERRED' AS decision_status)")
					.getSingleResult();
			return count != null ? count.longValue() : 0L;
		} catch (Exception e) {
			throw new RuntimeException("Error counting resubmitted cheques: " + e.getMessage(), e);
		}
	}

	/**
	 * findResubmittedByChequeNo()
	 *
	 * CHANGED: was HQL with 4 conditions including enum parameters. NOW: native SQL
	 * with CAST on enum columns + cheque_no string match.
	 *
	 * Used by the service before approve/reject/return — must confirm the cheque is
	 * still actionable (Resubmitted + TV_1 + REFERRED). Returns Optional — empty if
	 * cheque already processed or not found.
	 */

	@Override
	public Optional<InwardCheque> findResubmittedByChequeNoTV1(String chequeNo) {
		try (Session session = HibernateUtil.getSessionFactory().openSession()) {

			String sql = "SELECT * FROM inward_cheque " + "WHERE cheque_no     = :chequeNo                           "
					+ "AND   cheque_status = cast(:status   AS cheque_status)    "
					+ "AND   send_to       = cast(:sendTo   AS send_to_enum)     "
					+ "AND   decision      = cast(:decision  AS decision_status)";

			InwardCheque result = (InwardCheque) session.createNativeQuery(sql, InwardCheque.class)
					.setParameter("chequeNo", chequeNo).setParameter("status", ChequeStatus.Resubmitted.name())
					.setParameter("sendTo", SendTo.TV_1.name()).setParameter("decision", DecisionStatus.REFERRED.name())
					.uniqueResult();

			return Optional.ofNullable(result);

		} catch (Exception e) {
			throw new RuntimeException("Error finding resubmitted cheque for checker: " + e.getMessage(), e);
		}
	}

	@Override
	public Optional<InwardCheque> findByChequeNoTV1(String chequeNo) {
		try (Session session = HibernateUtil.getSessionFactory().openSession()) {
			InwardCheque result = (InwardCheque) session.createNativeQuery(
					"SELECT * FROM inward_cheque WHERE cheque_no = :chequeNo", InwardCheque.class)
					.setParameter("chequeNo", chequeNo)
					.uniqueResult();
			return Optional.ofNullable(result);
		} catch (Exception e) {
			throw new RuntimeException("Error fetching cheque by number: " + e.getMessage(), e);
		}
	}

	@Override
	public void updateCheckerDecision(InwardCheque cheque) {
		Transaction tx = null;
		try (Session session = HibernateUtil.getSessionFactory().openSession()) {
			tx = session.beginTransaction();

			String sql = "UPDATE inward_cheque SET "
					+ "  cheque_status         = cast(:chequeStatus AS cheque_status),  "
					+ "  decision              = cast(:decision      AS decision_status), "
					+ "  send_to               = cast(:sendTo        AS send_to_enum),   "
					+ "  checker_sendback_reason  = :sendbackReason,  "
					+ "  checker_reject_reason = :checkerRejectReason, "
					+ "  is_edited_by_maker    = :isEditedByMaker,     "
					+ "  edited_fields         = :editedFields         " + "WHERE id = :id";

			session.createNativeQuery(sql).setParameter("chequeStatus", getEnumName(cheque.getChequeStatus()))
					.setParameter("decision", getEnumName(cheque.getDecision()))
					.setParameter("sendTo", getEnumName(cheque.getSendTo()))
					.setParameter("sendbackReason", cheque.getSendbackReason())
					.setParameter("checkerRejectReason", cheque.getRejectReason())
					.setParameter("isEditedByMaker", cheque.isEditedByMaker())
					.setParameter("editedFields", cheque.getEditedFields()).setParameter("id", cheque.getId())
					.executeUpdate();

			tx.commit();

		} catch (Exception e) {
			if (tx != null)
				tx.rollback();
			throw new RuntimeException(
					"Error saving checker decision for cheque " + cheque.getChequeNo() + ": " + e.getMessage(), e);
		}
	}

	/**
	 * findResubmittedForVerifier2()
	 *
	 * CHANGED: was HQL with enum constants passed directly as parameters. NOW:
	 * native SQL SELECT * with CAST on all three enum columns.
	 *
	 * Filters: cheque_status = 'Resubmitted' (maker fixed and sent back) send_to =
	 * 'TV_2' (routed to Branch Manager) decision = 'REFERRED' (no final decision
	 * yet) Ordered: newest first by created_at DESC.
	 */
	@Override
	public List<InwardCheque> findResubmittedForVerifier2() {
		try (Session session = HibernateUtil.getSessionFactory().openSession()) {

			String sql = "SELECT * FROM inward_cheque " + "WHERE cheque_status = cast(:status   AS cheque_status)   "
					+ "AND   send_to       = cast(:sendTo   AS send_to_enum)    "
					+ "AND   decision      = cast(:decision  AS decision_status) " + "ORDER BY created_at DESC";

			return session.createNativeQuery(sql, InwardCheque.class)
					.setParameter("status", ChequeStatus.Resubmitted.name()).setParameter("sendTo", SendTo.TV_2.name())
					.setParameter("decision", DecisionStatus.REFERRED.name()).list();

		} catch (Exception e) {
			throw new RuntimeException("Error loading resubmitted cheques for Verifier 2: " + e.getMessage(), e);
		}
	}

	/**
	 * countResubmittedForVerifier2()
	 *
	 * CHANGED: was HQL COUNT query with enum parameters. NOW: native SQL COUNT(*)
	 * with CAST for PostgreSQL enum columns.
	 *
	 * Used for the sidebar pending badge number on the TV2 dashboard.
	 */
	@Override
	public long countResubmittedForVerifier2() {
		try (Session session = HibernateUtil.getSessionFactory().openSession()) {

			String sql = "SELECT COUNT(*) FROM inward_cheque "
					+ "WHERE cheque_status = cast(:status   AS cheque_status)   "
					+ "AND   send_to       = cast(:sendTo   AS send_to_enum)    "
					+ "AND   decision      = cast(:decision  AS decision_status)";

			Number count = (Number) session.createNativeQuery(sql)
					.setParameter("status", ChequeStatus.Resubmitted.name()).setParameter("sendTo", SendTo.TV_2.name())
					.setParameter("decision", DecisionStatus.REFERRED.name()).getSingleResult();

			return count != null ? count.longValue() : 0L;

		} catch (Exception e) {
			throw new RuntimeException("Error counting resubmitted cheques for Verifier 2: " + e.getMessage(), e);
		}
	}

	/**
	 * findResubmittedByChequeNo()
	 *
	 * CHANGED: was HQL with 4 filter conditions. NOW: native SQL with CAST on all
	 * three enum columns.
	 *
	 * Used before approve/reject/return — verifies cheque is still actionable.
	 * Returns Optional — empty if already processed or not found.
	 */
	@Override
	public Optional<InwardCheque> findResubmittedByChequeNoTV2(String chequeNo) {

		try (Session session = HibernateUtil.getSessionFactory().openSession()) {

			String sql = "SELECT * FROM inward_cheque " + "WHERE cheque_no     = :chequeNo                           "
					+ "AND   cheque_status = cast(:status   AS cheque_status)    "
					+ "AND   send_to       = cast(:sendTo   AS send_to_enum)     "
					+ "AND   decision      = cast(:decision  AS decision_status)";

			InwardCheque result = (InwardCheque) session.createNativeQuery(sql, InwardCheque.class)
					.setParameter("chequeNo", chequeNo).setParameter("status", ChequeStatus.Resubmitted.name())
					.setParameter("sendTo", SendTo.TV_2.name()).setParameter("decision", DecisionStatus.REFERRED.name())
					.uniqueResult();

			return Optional.ofNullable(result);

		} catch (Exception e) {
			throw new RuntimeException("Error finding resubmitted cheque for Verifier 2: " + e.getMessage(), e);
		}
	}

	/**
	 * findByChequeNo()
	 *
	 * CHANGED: was HQL "FROM InwardCheque c WHERE c.chequeNo = :chequeNo" NOW:
	 * native SQL SELECT * FROM inward_cheque WHERE cheque_no = ?
	 *
	 * NO status filter — used by getChequeForV2Review() to load the cheque in any
	 * state and check whether it is currently actionable.
	 */

	@Override
	public Optional<InwardCheque> findByChequeNoTV2(String chequeNo) {
		try (Session session = HibernateUtil.getSessionFactory().openSession()) {

			String sql = "SELECT * FROM inward_cheque WHERE cheque_no = :chequeNo";

			InwardCheque result = (InwardCheque) session.createNativeQuery(sql, InwardCheque.class)
					.setParameter("chequeNo", chequeNo).uniqueResult();

			return Optional.ofNullable(result);

		} catch (Exception e) {
			throw new RuntimeException("Error fetching cheque by number (TV2): " + e.getMessage(), e);
		}
	}

	@Override
	public void updateVerifier2Decision(InwardCheque cheque) {

		Transaction tx = null; // track transaction so we can rollback on error

		// Open Hibernate session — auto-closed by try-with-resources
		try (Session session = HibernateUtil.getSessionFactory().openSession()) {

			tx = session.beginTransaction(); // start DB transaction

			// Native SQL UPDATE — using CAST for PostgreSQL enum types
			// :paramName → replaced by setParameter() below
			String sql = "UPDATE inward_cheque SET " +

			// cheque_status is a PostgreSQL ENUM — must CAST to the enum type
					"  cheque_status         = cast(:chequeStatus AS cheque_status),  " +

					// decision is a PostgreSQL ENUM — must CAST to the enum type
					"  decision              = cast(:decision      AS decision_status), " +

					// send_to is a PostgreSQL ENUM — must CAST to the enum type
					"  send_to               = cast(:sendTo        AS send_to_enum),   " +

					// checker_refer_reason — reason when returning to maker or approval note
					// Nullable: null when permanently rejecting
					"  checker_sendback_reason  = :sendbackReason,   " +

					// checker_reject_reason — permanent rejection reason (new column)
					// Nullable: null when approving or returning to maker
					"  checker_reject_reason = :checkerRejectReason,  " +

					// is_edited_by_maker — reset to false when returning to maker for new round
					// Stays as-is when approving or rejecting
					"  is_edited_by_maker    = :isEditedByMaker,      " +

					// edited_fields — reset to null when returning to maker for new round
					// Stays as-is when approving or rejecting
					"  edited_fields         = :editedFields          " +

					// WHERE clause — update only the specific cheque row by primary key
					"WHERE id = :id";

			session.createNativeQuery(sql)
					// getEnumName() converts enum to its String name (e.g., ChequeStatus.Ready →
					// "Ready")
					// PostgreSQL CAST then converts that string into the DB enum type
					.setParameter("chequeStatus", getEnumName(cheque.getChequeStatus()))
					.setParameter("decision", getEnumName(cheque.getDecision()))
					.setParameter("sendTo", getEnumName(cheque.getSendTo()))

					// May be null (null when rejecting, has value when returning or approving)
					.setParameter("sendbackReason", cheque.getSendbackReason())

					// May be null (null when approving/returning, has value when rejecting)
					.setParameter("checkerRejectReason", cheque.getRejectReason())

					// boolean — true if maker edited fields, false after returning to maker
					.setParameter("isEditedByMaker", cheque.isEditedByMaker())

					// May be null — comma-separated list of field names maker changed
					.setParameter("editedFields", cheque.getEditedFields())

					// Primary key — which row to update in the inward_cheque table
					.setParameter("id", cheque.getId())

					.executeUpdate(); // run the UPDATE query

			tx.commit(); // commit the transaction — makes the change permanent in DB

		} catch (Exception e) {
			// If anything went wrong, rollback to undo any partial changes
			if (tx != null)
				tx.rollback();
			throw new RuntimeException(
					"Error saving Verifier 2 decision for cheque " + cheque.getChequeNo() + ": " + e.getMessage(), e);
		}
	}

	/**
	 * generateRRF() — already uses native SQL — no change needed.
	 */
	@Override
	public void generateRRF(InwardCheque cheque) {
		Transaction tx = null;
		try (Session session = HibernateUtil.getSessionFactory().openSession()) {
			tx = session.beginTransaction();

			String sql = "UPDATE inward_cheque SET " + "  decision       = cast(:decision      AS decision_status), "
					+ "  cbs_validation = cast(:cbsValidation AS cbs_validation),  "
					+ "  resubmitted_by = :resubmittedBy " + "WHERE id = :id";

			session.createNativeQuery(sql).setParameter("decision", getEnumName(cheque.getDecision()))
					.setParameter("cbsValidation", getEnumName(cheque.getCbsValidation()))
					.setParameter("resubmittedBy", cheque.getResubmittedBy()).setParameter("id", cheque.getId())
					.executeUpdate();

			tx.commit();

		} catch (Exception e) {
			if (tx != null)
				tx.rollback();
			throw new RuntimeException("Error generating RRF for cheque id=" + cheque.getId() + ": " + e.getMessage(),
					e);
		}
	}

	@Override
	public void saveCbsResult(Long chequeId, boolean isValid, String failureReason) {

		// Figure out the value to store in the cbs_validation enum column
		String cbsValidationValue = isValid ? "Valid" : "Invalid";

		// If valid, we store null for cbs_reject_reason
		// If invalid, we store the failure reason text
		String reasonToSave = isValid ? null : failureReason;

		// Native SQL — uses PostgreSQL ENUM cast (::cbs_validation)
		String sql = "UPDATE inward_cheque " + "SET cbs_validation = :cbsVal::cbs_validation, "
				+ "    cbs_reject_reason = :reason " + "WHERE id = :chequeId";

		Transaction tx = null;

		try (Session session = HibernateUtil.getSessionFactory().openSession()) {

			tx = session.beginTransaction();

			session.createNativeQuery(sql).setParameter("cbsVal", cbsValidationValue)
					.setParameter("reason", reasonToSave).setParameter("chequeId", chequeId).executeUpdate();

			tx.commit();

			System.out.println("CbsValidationLogDaoImpl: saved CBS result for chequeId=" + chequeId + " | result="
					+ cbsValidationValue + " | reason=" + reasonToSave);

		} catch (Exception e) {
			if (tx != null)
				tx.rollback();
			System.err.println("CbsValidationLogDaoImpl: failed to save CBS result for chequeId=" + chequeId + " | "
					+ e.getMessage());
			e.printStackTrace();
			throw new RuntimeException("Failed to save CBS validation result: " + e.getMessage(), e);
		}
	}

	@Override
    public List<InwardCheque> listOfReferredCheques() {
        try (Session session = HibernateUtil.getSessionFactory().openSession()) {
            return session.createNativeQuery(
                    "SELECT * FROM inward_cheque "
                    + "WHERE cheque_status = CAST('Repair' AS cheque_status) "
                    + "AND   send_to       = CAST('TV_2' AS send_to_enum) "
                    + "AND   decision      = CAST('REFERRED' AS decision_status)",
                    InwardCheque.class)
                    .list();
        }
    }

}