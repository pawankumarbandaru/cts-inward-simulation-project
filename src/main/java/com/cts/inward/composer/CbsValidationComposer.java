package com.cts.inward.composer;

import java.util.List;

import org.zkoss.zk.ui.Component;
import org.zkoss.zk.ui.Executions;
import org.zkoss.zk.ui.event.Event;
import org.zkoss.zk.ui.event.EventQueues;
import org.zkoss.zk.ui.select.SelectorComposer;
import org.zkoss.zk.ui.select.annotation.Listen;
import org.zkoss.zk.ui.select.annotation.Wire;
import org.zkoss.zul.Button;
import org.zkoss.zul.Label;
import org.zkoss.zul.Messagebox;

import com.cts.inward.entity.InwardCheque;
import com.cts.inward.enums.BatchStatus;
import com.cts.inward.service.InwardChequeMICRService;
import com.cts.inward.service.InwardChequeServiceMICRImpl;

public class CbsValidationComposer extends SelectorComposer<Component> {

	private static final long serialVersionUID = 1L;

	private final InwardChequeMICRService chequeService = new InwardChequeServiceMICRImpl();

	@Wire
	Label lblCbsPageSubtitle;
	@Wire
	Button btnReturnToRrf;
	@Wire
	Button btnForwardTv1Tv2;

	private Long currentBatchId = null;

	String role = (String) Executions.getCurrent().getDesktop().getAttribute("role");

	/**
	 * the page subtitle, hides both action buttons, publishes batchContext, and
	 * subscribes to cbsValidationComplete to enable buttons after validation
	 * finishes. Related to: CBS Validation page — page-level initialization.
	 */
	@Override
	public void doAfterCompose(Component comp) throws Exception {
		super.doAfterCompose(comp);

		Object batchDbIdArg = Executions.getCurrent().getDesktop().getAttribute("cbsBatchDbId");
		if (batchDbIdArg == null) {
			batchDbIdArg = Executions.getCurrent().getAttribute("cbsBatchDbId");
		}
		if (batchDbIdArg == null) {
			batchDbIdArg = Executions.getCurrent().getParameter("cbsBatchDbId");
		}

		if (batchDbIdArg instanceof Long) {
			currentBatchId = (Long) batchDbIdArg;
		} else if (batchDbIdArg instanceof String) {
			try {
				currentBatchId = Long.parseLong((String) batchDbIdArg);
			} catch (NumberFormatException e) {
				currentBatchId = null;
			}
		}

		if (currentBatchId == null || currentBatchId <= 0) {
			currentBatchId = chequeService.resolveLatestBatchId();
		}

		Executions.getCurrent().getDesktop().removeAttribute("cbsBatchDbId");

		System.out.println("CbsValidationComposer: batchDbId = " + currentBatchId);

		try {
			comp.getFellow("cbsBatchSummaryMacro").setAttribute("batchDbId", currentBatchId);
		} catch (Exception e) {
			System.err.println("CbsValidationComposer: cbsBatchSummaryMacro not found");
		}

		// Page subtitle
		try {
			long total = chequeService.getTotalChequeCount(currentBatchId, role);
			lblCbsPageSubtitle.setValue("CBS Validation " + currentBatchId + " · " + total + " cheques");
		} catch (Exception e) {
			lblCbsPageSubtitle.setValue("CBS Validation " + currentBatchId);
		}

		// Both buttons hidden until validation completes
		btnReturnToRrf.setVisible(false);
		btnForwardTv1Tv2.setVisible(false);

		// Publish batchDbId so macro composers start their work
		EventQueues.lookup("batchContext", EventQueues.DESKTOP, true)
				.publish(new Event("onBatchResolved", null, currentBatchId));

		// CbsChequeListComposer publishes this when all cheques are processed.
		EventQueues.lookup("cbsValidationComplete", EventQueues.DESKTOP, true).subscribe((Event event) -> {
			long[] summary = (long[]) event.getData();
			updateButtonState(summary[0], summary[1]);
		});
	}

	/**
	 * Shows and enables/disables "Return to RRF" and "Forward to TV1/TV2" buttons
	 * based on validCount and invalidCount received after all CBS validation
	 * completes. Related to: CBS Validation page — drives which action button is
	 * active after validation.
	 */
	private void updateButtonState(long validCount, long invalidCount) {
		System.out.println(
				"CbsValidationComposer: updateButtonState " + "valid=" + validCount + " invalid=" + invalidCount);

		if (invalidCount > 0) {
			btnReturnToRrf.setVisible(true);
			btnReturnToRrf.setDisabled(false);

			btnForwardTv1Tv2.setVisible(true);
			btnForwardTv1Tv2.setDisabled(true);

		} else {
			// All cheques are valid → only Forward button active
			btnReturnToRrf.setVisible(true);
			btnReturnToRrf.setDisabled(true);

			btnForwardTv1Tv2.setVisible(true);
			btnForwardTv1Tv2.setDisabled(false);
		}
	}

	/**
	 * Shows a confirmation dialog before calling processReturnToRrf(); proceeds
	 * only if the user confirms YES. Related to: CBS Validation page — "Return to
	 * RRF" button click handler.
	 */
	@Listen("onClick = #btnReturnToRrf")
	public void onReturnToRrf() {
		Messagebox.show("Mark all INVALID cheques as REJECTED and remove them from this list?", "Confirm Return to RRF",
				Messagebox.YES | Messagebox.NO, Messagebox.QUESTION, (Event e) -> {
					if (Messagebox.ON_YES.equals(e.getName())) {
						processReturnToRrf();
					}
				});
	}

	/**
	 * Loads INVALID cheques for the batch, bulk-marks them REJECTED via native SQL
	 * Related to: CBS Validation page — core "Return to RRF" business logic.
	 */
	private void processReturnToRrf() {
		if (currentBatchId == null)
			return;

		try {
			List<InwardCheque> invalidCheques = chequeService.getInvalidChequesByBatchId(currentBatchId);

			if (invalidCheques == null || invalidCheques.isEmpty()) {
				Messagebox.show("No invalid cheques found.", "Return to RRF", Messagebox.OK, Messagebox.INFORMATION);
				return;
			}

			int count = invalidCheques.size();

			chequeService.markInvalidChequesAsRejected(currentBatchId);

			// Notify BatchSummaryComposer to refresh its summary counts
			EventQueues.lookup("chequeStatusUpdated", EventQueues.DESKTOP, true)
					.publish(new Event("onChequeStatusUpdated", null, null));

			System.out.println(
					"CbsValidationComposer: marked " + count + " cheques as REJECTED for batchId=" + currentBatchId);

			// Tell CbsChequeListComposer to remove INVALID rows from its list
			EventQueues.lookup("removeInvalidCheques", EventQueues.DESKTOP, true)
					.publish(new Event("onRemoveInvalidCheques", null, null));

			// All invalid removed → Forward button becomes active
			updateButtonState(1L, 0L);

			Messagebox.show(count + " cheque(s) marked as REJECTED and removed from list.", "Return to RRF",
					Messagebox.OK, Messagebox.INFORMATION);

		} catch (Exception e) {
			System.err.println("CbsValidationComposer: Return to RRF failed: " + e.getMessage());
			e.printStackTrace();
			Messagebox.show("Error processing Return to RRF: " + e.getMessage(), "Error", Messagebox.OK,
					Messagebox.ERROR);
		}
	}

	/**
	 * Threshold amount used to route cheques between TV1 and TV2. amount <=
	 * THRESHOLD_AMOUNT → TV_1 ; amount > THRESHOLD_AMOUNT → TV_2.
	 */
	private static final java.math.BigDecimal THRESHOLD_AMOUNT = new java.math.BigDecimal("100000");

	/**
	 * Updates batch status to PendingAtChecker, routes all valid cheques to TV1 or
	 * TV2 based on the ₹1,00,000 threshold, disables both buttons, and shows a
	 * summary message. Related to: CBS Validation page — "Forward to TV1 and TV2"
	 * button click handler.
	 */
	@Listen("onClick = #btnForwardTv1Tv2")
	public void onForwardTv1Tv2() {
		if (currentBatchId == null)
			return;

		try {
			chequeService.updateBatchStatus(currentBatchId, BatchStatus.PendingAtChecker);

			long[] counts = chequeService.forwardToTvQueuesByThreshold(currentBatchId, THRESHOLD_AMOUNT);
			long tv1Count = counts[0];
			long tv2Count = counts[1];

			System.out.println(
					"CbsValidationComposer: forwarded batchId=" + currentBatchId + " to TV1/TV2 with threshold="
							+ THRESHOLD_AMOUNT + " (TV1=" + tv1Count + ", TV2=" + tv2Count + ")");

			btnReturnToRrf.setDisabled(true);
			btnForwardTv1Tv2.setDisabled(true);

			Messagebox.show(tv1Count + " cheque(s) sent to TV1 and " + tv2Count + " cheque(s) sent to TV2.",
					"Forward to Tv1 and Tv2", Messagebox.OK, Messagebox.INFORMATION);

		} catch (Exception e) {
			System.err.println("CbsValidationComposer: Forward to Tv1/Tv2 failed: " + e.getMessage());
			e.printStackTrace();
			Messagebox.show("Error forwarding cheques: " + e.getMessage(), "Error", Messagebox.OK, Messagebox.ERROR);
		}
	}
}