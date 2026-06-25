package com.cts.inward.composer;

import java.text.SimpleDateFormat;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import org.zkoss.zk.ui.Component;
import org.zkoss.zk.ui.Executions;
import org.zkoss.zk.ui.event.Events;
import org.zkoss.zk.ui.select.SelectorComposer;
import org.zkoss.zk.ui.select.annotation.Listen;
import org.zkoss.zk.ui.select.annotation.Wire;
import org.zkoss.zk.ui.util.Clients;
import org.zkoss.zul.Button;
import org.zkoss.zul.Combobox;
import org.zkoss.zul.Datebox;
import org.zkoss.zul.Label;
import org.zkoss.zul.Listbox;
import org.zkoss.zul.Listcell;
import org.zkoss.zul.Listitem;
import org.zkoss.zul.Textbox;

import com.cts.composer.CheckerDashboardComposer;
import com.cts.inward.dto.CheckerDashboardStatsDTO;
import com.cts.inward.dto.InwardBatchDTO;
import com.cts.inward.service.CheckerDashboardService;
import com.cts.inward.service.CheckerDashboardServiceImpl;

public class TV1_InwardDashboardComposer extends SelectorComposer<Component> {

	// ── KPI Labels ────────────────────────────────────────────────────────
	@Wire
	private Label lblPendingBatches;
	@Wire
	private Label lblClearedBatches;
	@Wire
	private Label lblPendingCheques;

	@Wire
	private Label lblPageMeta;

	// ── Filters ───────────────────────────────────────────────────────────
	@Wire
	private Textbox txtBatchId;
	@Wire
	private Combobox cmbStatus;
	@Wire
	private Datebox filterDate;

	// ── Table ─────────────────────────────────────────────────────────────
	@Wire
	private Listbox batchListbox;

	// ── Service ───────────────────────────────────────────────────────────
	private final CheckerDashboardService service = new CheckerDashboardServiceImpl();

//    private final SimpleDateFormat sdfDisplay = new SimpleDateFormat("dd-MMM-yyyy HH:mm");

	private static final DateTimeFormatter sdfDisplay = DateTimeFormatter.ofPattern("dd-MMM-yyyy HH:mm");
	private final SimpleDateFormat sdfMeta = new SimpleDateFormat("dd MMM yyyy");

	// In-memory cache for the currently loaded date's batches
	private List<InwardBatchDTO> currentDateBatches = new ArrayList<>();

	// ── Lifecycle ─────────────────────────────────────────────────────────
	@Override
	public void doAfterCompose(Component comp) throws Exception {
		super.doAfterCompose(comp);
		System.out.println("===== CHECKER DASHBOARD LOADED =====");
		filterDate.setValue(today());
		loadDashboard(today());
	}

	// ── Core loader ───────────────────────────────────────────────────────
	private void loadDashboard(Date date) {

		// KPI
		CheckerDashboardStatsDTO stats = service.getStatsForDate(date);
		System.out.println("KPI Date = " + date);
		lblPendingBatches.setValue(String.valueOf(stats.getPendingBatches()));
		lblClearedBatches.setValue(String.valueOf(stats.getClearedBatches()));
		lblPendingCheques.setValue(String.valueOf(stats.getPendingCheques()));

		lblPageMeta.setValue(sdfMeta.format(date) + " · TV1 Queue");

		// Batch list
		currentDateBatches = service.getTV1BatchesForDate(date);
		populateBatchTable(currentDateBatches);
	}

	// ── Table renderer ────────────────────────────────────────────────────
	private void populateBatchTable(List<InwardBatchDTO> batches) {

		batchListbox.getItems().clear();

		if (batches == null || batches.isEmpty())
			return;

		for (InwardBatchDTO batch : batches) {

			Listitem item = new Listitem();

			// 1. Batch ID
			item.appendChild(new Listcell(batch.getBatchId()));

			// 2. Upload Date
			String uploadDate = "-";
			if (batch.getUploadDate() != null) {
				uploadDate = sdfDisplay.format(batch.getUploadDate());
			}
			item.appendChild(new Listcell(uploadDate));

			// 3. Total Cheques (all cheques in the batch)
			item.appendChild(
					new Listcell(String.valueOf(batch.getTotalCheques() != null ? batch.getTotalCheques() : 0)));

			// 4. Assigned to TV1 Checker
			item.appendChild(
					new Listcell(String.valueOf(batch.getAssignedCheques() != null ? batch.getAssignedCheques() : 0)));

			// 5. Pending Cheques
			item.appendChild(
					new Listcell(String.valueOf(batch.getPendingCheques() != null ? batch.getPendingCheques() : 0)));

			// 6. Cleared Cheques
			item.appendChild(
					new Listcell(String.valueOf(batch.getClearedCheques() != null ? batch.getClearedCheques() : 0)));

			// 7. Status badge
			Listcell statusCell = new Listcell();
			Label statusLabel = new Label(batch.getStatus() != null ? batch.getStatus().name() : "");
			statusLabel.setStyle(badgeStyle(batch.getStatus() != null ? batch.getStatus().name() : ""));
			statusCell.appendChild(statusLabel);
			item.appendChild(statusCell);

			// 8. Action — View button
			Listcell actionCell = new Listcell();
			Button btnView = new Button("View");
			btnView.setSclass("btn-view-action");
			btnView.setStyle("background:#1a2a4a;color:white;border:none;" + "border-radius:5px;padding:4px 14px;"
					+ "font-size:12px;cursor:pointer;font-weight:600;");

			// Capture batchId for the click handler
			final String batchId = batch.getBatchId();
			final Long batchDbId = batch.getId();

			btnView.addEventListener(Events.ON_CLICK, e -> {
				redirectToChequeDetails(batchId);
			});

			actionCell.appendChild(btnView);
			item.appendChild(actionCell);

			batchListbox.appendChild(item);
		}
	}

	private void redirectToChequeDetails(String batchIdValue) {

		CheckerDashboardComposer shell = CheckerDashboardComposer.getInstance();

		if (shell == null) {
			Clients.showNotification("Navigation error: shell not found.", "error", null, "top_center", 3000);
			return;
		}

		// ✅ Store batchId on Desktop so CheckerBatchDetailComposer can read it
		Executions.getCurrent().getDesktop().setAttribute("batchId", batchIdValue);

		Map<String, Object> args = new HashMap<>();
		args.put("batchId", batchIdValue);

		shell.loadPage("/zul/inward/batchDetail.zul", args);
	}

//	// ── Navigation: View cheque details for a batch ───────────────────────
//    private void navigateToChequeDetail(String batchId, Long batchDbId) {
//
//        CheckerDashboardComposer shell = CheckerDashboardComposer.getInstance();
//
//        if (shell == null) {
//            Clients.showNotification(
//                "Navigation error: shell not found.",
//                "error", null, "top_center", 3000);
//            return;
//        }
//
//        Map<String, Object> args = new HashMap<>();
//        args.put("batchId",   batchId);
//        args.put("batchDbId", batchDbId);
//
//        shell.loadPage("/zul/inward/TV1ChequeDetail.zul", args);
//    }

	// ── Button listeners ──────────────────────────────────────────────────
	@Listen("onClick=#btnSearch")
	public void searchBatches() {

		String batchIdFilter = txtBatchId.getValue() == null ? "" : txtBatchId.getValue().trim().toLowerCase();

		String statusFilter = cmbStatus.getValue() == null ? "ALL" : cmbStatus.getValue().trim().toUpperCase();

		List<InwardBatchDTO> filtered = currentDateBatches.stream()

				// Filter by Batch ID
				.filter(batch -> batchIdFilter.isEmpty() || batch.getBatchId().toLowerCase().contains(batchIdFilter))

				// Filter by Status
				.filter(batch -> {
					if ("ALL".equals(statusFilter)) {
						return true;
					}

					return batch.getStatus() != null && batch.getStatus() != null
							&& batch.getStatus().name().equalsIgnoreCase(statusFilter);
				})

				.collect(Collectors.toList());

		populateBatchTable(filtered);

		Clients.showNotification(filtered.size() + " batch(es) found.", "info", null, "top_center", 2000);
	}

	@Listen("onChange=#filterDate")
	public void onDateChange() {
		Date selected = filterDate.getValue();
		if (selected == null)
			selected = today();
		txtBatchId.setValue("");
		cmbStatus.setValue("All");
		loadDashboard(selected);
	}

	@Listen("onClick=#btnClear")
	public void clearFilters() {
		txtBatchId.setValue("");
		cmbStatus.setValue("All");
		populateBatchTable(currentDateBatches);
		Clients.showNotification("Filters cleared.", "info", null, "top_center", 2000);
	}

	// ── Helpers ───────────────────────────────────────────────────────────
	private String badgeStyle(String status) {
		if (status == null)
			return defaultBadge();
		switch (status.toUpperCase()) {
		case "CLEARED":
			return "background:#198754;color:white;" + "padding:3px 10px;border-radius:12px;"
					+ "font-size:11px;font-weight:700;";
		case "AT_CHECKER_QUEUE":
			return "background:#0d6efd;color:white;" + "padding:3px 10px;border-radius:12px;"
					+ "font-size:11px;font-weight:700;";
		case "PENDING":
		case "AT_MICR_SERVICE":
			return "background:#ffc107;color:black;" + "padding:3px 10px;border-radius:12px;"
					+ "font-size:11px;font-weight:700;";
		default:
			return defaultBadge();
		}
	}

	private String defaultBadge() {
		return "background:#e5e7eb;color:#374151;" + "padding:3px 10px;border-radius:12px;"
				+ "font-size:11px;font-weight:700;";
	}

	private Date today() {
		Calendar cal = Calendar.getInstance();
		cal.set(Calendar.HOUR_OF_DAY, 0);
		cal.set(Calendar.MINUTE, 0);
		cal.set(Calendar.SECOND, 0);
		cal.set(Calendar.MILLISECOND, 0);
		return cal.getTime();
	}
}
