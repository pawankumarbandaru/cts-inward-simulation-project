package com.cts.inward.composer;

import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.Map;

import org.zkoss.zk.ui.Component;
import org.zkoss.zk.ui.Executions;
import org.zkoss.zk.ui.event.Event;
import org.zkoss.zk.ui.event.EventQueues;
import org.zkoss.zk.ui.select.SelectorComposer;
import org.zkoss.zk.ui.select.annotation.Listen;
import org.zkoss.zk.ui.select.annotation.Wire;
import org.zkoss.zul.Button;
import org.zkoss.zul.Div;
import org.zkoss.zul.Label;

import com.cts.composer.DashboardComposer;
import com.cts.inward.entity.InwardBatch;
import com.cts.inward.enums.BatchStatus;
import com.cts.inward.service.InwardChequeMICRService;
import com.cts.inward.service.InwardChequeServiceMICRImpl;

public class BatchSummaryComposer extends SelectorComposer<Component> {

	private static final long serialVersionUID = 1L;

	private static final String ROLE_MAKER = "MAKER";
	private static final String ROLE_TV1 = "TV1";
	private static final String ROLE_TV2 = "TV2";

	private final InwardChequeMICRService inwardChequeService = new InwardChequeServiceMICRImpl();

	@Wire
	Label lblBatchId;
	@Wire
	Label lblUploadTime;
	@Wire
	Label lblTotalCount;
	@Wire
	Label lblSecondaryLabel;
	@Wire
	Label lblSecondaryCount;
	@Wire
	Div divCbsAction;
	@Wire
	Button btnSendToCbs;

	private Long currentBatchId = null;
	private String currentRole = ROLE_MAKER;

	// Used to display the batch's createdAt timestamp as the Upload Time
	private static final DateTimeFormatter UPLOAD_TIME_FMT = DateTimeFormatter.ofPattern("dd-MM-yyyy hh:mm a");

	/**
	 * Reads batchDbId and role from macro attributes, applies role-based layout,
	 * loads summary data, and subscribes to batchContext and chequeStatusUpdated
	 * queues. Related to: MICR page and CBS Validation page — the summary card
	 * shown at the top.
	 */
	@Override
	public void doAfterCompose(Component comp) throws Exception {
		super.doAfterCompose(comp);

		Object batchAttr = comp.getSpaceOwner().getAttribute("batchDbId");

		if (batchAttr instanceof Long) {
			currentBatchId = (Long) batchAttr;
		} else if (batchAttr instanceof String) {
			try {
				currentBatchId = Long.parseLong((String) batchAttr);
			} catch (NumberFormatException e) {
				currentBatchId = null;
			}
		}

		Object roleAttr = Executions.getCurrent().getDesktop().getAttribute("userRole");
		System.out.println("BatchSummaryComposer Desktop Role = " + comp.getDesktop().getAttribute("userRole"));
		if (roleAttr instanceof String && !((String) roleAttr).isEmpty()) {
			currentRole = ((String) roleAttr).toUpperCase().trim();
		}

		System.out.println("BatchSummaryComposer: batchDbId=" + currentBatchId + "  role=" + currentRole);

		applyRoleLayout();

		if (currentBatchId != null && currentBatchId > 0) {
			loadBatchSummary();
		}

		// ── Listen: batch selection change from dashboard list
		EventQueues.lookup("batchContext", EventQueues.DESKTOP, true).subscribe((Event event) -> {
			if (event.getData() instanceof Long) {
				Long incoming = (Long) event.getData();
				if (incoming != null && incoming > 0) {
					currentBatchId = incoming;
					loadBatchSummary();
				}
			}
		});

		// ── Listen: cheque status updated — refresh counts
		EventQueues.lookup("chequeStatusUpdated", EventQueues.DESKTOP, true)
				.subscribe((Event event) -> loadBatchSummary());
	}

	/**
	 * Configures secondary label text and CBS button visibility based on role;
	 * MAKER sees "MICR Errors" + CBS button, TV1/TV2 see role cheque count + no CBS
	 * button. Related to: MICR page — controls what the summary card shows per
	 * role.
	 */
	private void applyRoleLayout() {

		if (ROLE_TV1.equals(currentRole) || ROLE_TV2.equals(currentRole)) {

			lblSecondaryLabel.setValue(ROLE_TV1.equals(currentRole) ? "TV1 Cheques" : "TV2 Cheques");

			divCbsAction.setVisible(false);

		} else {

			lblSecondaryLabel.setValue("MICR Errors");
			divCbsAction.setVisible(true);
		}
	}

	/**
	 * Fetches total cheque count, secondary count, and batch metadata from DB and
	 * updates all summary labels; for MAKER also re-evaluates the CBS button state.
	 * Related to: MICR page and CBS Validation page — refreshes the summary card
	 * data.
	 */
	private void loadBatchSummary() {
		if (currentBatchId == null || currentBatchId <= 0)
			return;

		long totalCount = inwardChequeService.getTotalChequeCount(currentBatchId, currentRole);
		long secondaryCount = fetchSecondaryCount();

		InwardBatch batch = inwardChequeService.getBatchById(currentBatchId);

		lblBatchId.setValue("Batch-" + currentBatchId);
		lblUploadTime
				.setValue(batch != null && batch.getCreatedAt() != null ? batch.getCreatedAt().format(UPLOAD_TIME_FMT)
						: "--:-- --");
		lblTotalCount.setValue(String.valueOf(totalCount));
		lblSecondaryCount.setValue(String.valueOf(secondaryCount));

		if (ROLE_MAKER.equals(currentRole)) {
			evaluateCbsButton();
		}
	}

	/**
	 * Returns MICR error count for MAKER role, or cheque count by role (TV1/TV2)
	 * for checker roles; used to populate the secondary count label in the summary
	 * card.
	 */
	private long fetchSecondaryCount() {

		if (ROLE_TV1.equals(currentRole) || ROLE_TV2.equals(currentRole)) {

			return inwardChequeService.getChequeCountByRole(currentBatchId, currentRole);
		}

		return inwardChequeService.getMicrErrorCount(currentBatchId);
	}

	/**
	 * Checks batch status and non-normal cheque count to enable/disable/relabel the
	 * "Send to CBS Validation" button; prevents re-sending an already forwarded
	 * batch. Related to: MICR page — controls CBS button state for MAKER only.
	 */
	private void evaluateCbsButton() {

		BatchStatus status = inwardChequeService.getBatchStatus(currentBatchId);

		long nonNormalCount = inwardChequeService.getNonNormalChequeCount(currentBatchId);

		boolean allNormal = (nonNormalCount == 0);

		if (BatchStatus.PendingAtChecker.equals(status) || BatchStatus.Cleared.equals(status)) {

			btnSendToCbs.setDisabled(true);
			btnSendToCbs.setSclass("btn-cbs-action btn-cbs-disabled");

			btnSendToCbs.setLabel(
					BatchStatus.PendingAtChecker.equals(status) ? "Forwarded to Checker" : "Already Sent to CBS");

			return;
		}

		btnSendToCbs.setDisabled(!allNormal);

		btnSendToCbs.setSclass(allNormal ? "btn-cbs-action btn-cbs-enabled" : "btn-cbs-action btn-cbs-disabled");

		btnSendToCbs.setLabel("Send to CBS Validation");
	}

	/**
	 * Handles "Send to CBS Validation" button click: updates batch status to
	 * Cleared, re-evaluates the button, and navigates to cbsValidation.zul with the
	 * current batchDbId. Related to: MICR page → CBS Validation page transition
	 * (MAKER role only).
	 */
	@Listen("onClick = #btnSendToCbs")
	public void onSendToCbs() {
		if (currentBatchId == null || currentBatchId <= 0)
			return;

		inwardChequeService.updateBatchStatus(currentBatchId, BatchStatus.Cleared);

		evaluateCbsButton();

		Map<String, Object> args = new HashMap<>();
		args.put("cbsBatchDbId", currentBatchId);

		DashboardComposer.getInstance().loadPage("/zul/inward/cbsValidation.zul", args);
	}
}