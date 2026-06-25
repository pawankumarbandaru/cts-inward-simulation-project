	package com.cts.inward.composer;
	
	import java.text.SimpleDateFormat;
	import java.time.format.DateTimeFormatter;
	import java.util.ArrayList;
	import java.util.Calendar;
	import java.util.Date;
	import java.util.HashMap;
	import java.util.List;
	import java.util.Map;
	
	import org.zkoss.zk.ui.Component;
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
	import org.zkoss.zul.Messagebox;
	import org.zkoss.zul.Textbox;
	
	import com.cts.inward.dto.DashboardStatsDTO;
	import com.cts.inward.dto.InwardBatchDTO;
	import com.cts.inward.enums.BatchStatus;
	import com.cts.inward.service.InwardDashboardService;
	import com.cts.inward.service.InwardDashboardServiceImpl;
	
	public class Maker_InwardDashboardComposer
	        extends SelectorComposer<Component> {
	
	    // ── Stat Labels ──────────────────────────────────────────
	    @Wire private Label    lblTotalBatches;
	    @Wire private Label    lblClearedBatches;
	    @Wire private Label    lblDraftBatches;
	    @Wire private Label    lblPageMeta;
	
	    // ── Filter Controls ──────────────────────────────────────
	    @Wire private Textbox  txtBatchId;
	    @Wire private Combobox cmbStatus;
	    @Wire private Datebox  filterDate;
	
	    // ── Table ─────────────────────────────────────────────────
	    @Wire private Listbox  batchListbox;
	
	    // ── Service ───────────────────────────────────────────────
	    private final InwardDashboardService service =
	            new InwardDashboardServiceImpl();
	
	//    private final SimpleDateFormat sdfDisplay =
	//            new SimpleDateFormat("dd-MMM-yyyy HH:mm");
	    
	    private static final DateTimeFormatter sdfDisplay =
	            DateTimeFormatter.ofPattern("dd-MMM-yyyy HH:mm");
	
	    private final SimpleDateFormat sdfMeta =
	            new SimpleDateFormat("dd MMM yyyy");
	
	    // ── In-memory list for the currently loaded date ──────────
	    private List<InwardBatchDTO> currentDateBatches = new ArrayList<>();
	
	    // ─────────────────────────────────────────────────────────
	    @Override
	    public void doAfterCompose(Component comp) throws Exception {
	
	        super.doAfterCompose(comp);
	        System.out.println("===== DASHBOARD COMPOSER LOADED =====");
	
	     // NEW — check today first, fall back to yesterday if no batches today
	        Date defaultDate = resolveDashboardDate();
	        filterDate.setValue(defaultDate);
	        loadForDate(defaultDate);
	    }
	
	 // ── Smart Date Default ────────────────────────────────────
	 // Returns today if today has at least 1 batch, otherwise yesterday.
	 private Date resolveDashboardDate() {
	     Date todayDate = today();
	     Date startOfDay = toStartOfDay(todayDate);
	     Date endOfDay   = toEndOfDay(todayDate);

	     List<InwardBatchDTO> todayBatches = service.searchBatches("", "", startOfDay, endOfDay);

	     if (todayBatches != null && !todayBatches.isEmpty()) {
	         System.out.println("Dashboard default: today has "
	                 + todayBatches.size() + " batch(es), loading today.");
	         return todayDate;
	     }

	     System.out.println("Dashboard default: no batches today, falling back to yesterday.");
	     return yesterday();
	 }

		// ── Load batches for a specific date ─────────────────────
	    private void loadForDate(Date date) {
	
	        // Stat cards always reflect today's overall counts
	        loadDashboardStats(date);
	
	        // Update page meta label
	        lblPageMeta.setValue(sdfMeta.format(date) + " · Inward Clearing");
	
	        // Date range: start of day → end of day
	        Date startOfDay = toStartOfDay(date);
	        Date endOfDay   = toEndOfDay(date);
	
	        // Load from DB — no batchId/status filter, date range applied
	        currentDateBatches = service.searchBatches("", "", startOfDay, endOfDay);
	
	        System.out.println("Loaded " + currentDateBatches.size()
	                + " batches for date: " + sdfMeta.format(date));
	
	        // Show all loaded batches
	        populateBatchTable(currentDateBatches);
	    }
	
	    // ── Stat Cards ────────────────────────────────────────────
	    private void loadDashboardStats(Date date) {
	
	        Date startOfDay = toStartOfDay(date);
	        Date endOfDay   = toEndOfDay(date);
	
	        DashboardStatsDTO stats =
	                service.getDashboardStats(
	                        startOfDay,
	                        endOfDay);
	
	        lblTotalBatches.setValue(
	                String.valueOf(stats.getTotalBatches()));
	
	        lblClearedBatches.setValue(
	                String.valueOf(stats.getClearedBatches()));
	
	        lblDraftBatches.setValue(
	                String.valueOf(stats.getDraftBatches()));
	    }
	
	    // ── Populate Listbox Rows ─────────────────────────────────
	    private void populateBatchTable(List<InwardBatchDTO> batches) {
	
	        batchListbox.getItems().clear();
	
	        if (batches == null || batches.isEmpty()) return;
	
	        for (InwardBatchDTO batch : batches) {
	
	            Listitem item = new Listitem();
	
	            // Batch ID
	            item.appendChild(new Listcell(batch.getBatchId()));
	
	            // Upload Date
	            String uploadDate = "-";
	            if (batch.getUploadDate() != null) {
	                uploadDate = sdfDisplay.format(batch.getUploadDate());
	            }
	            item.appendChild(new Listcell(uploadDate));
	
	            // Total Cheques
	            item.appendChild(new Listcell(
	                    String.valueOf(batch.getTotalCheques())));
	
	            // Cleared Cheques
	//            item.appendChild(new Listcell(
	//                    String.valueOf(batch.getClearedCheques())));
	//
	//            // Pending Cheques
	//            item.appendChild(new Listcell(
	//                    String.valueOf(batch.getPendingCheques())));
	            
	            item.appendChild(new Listcell(
	                    String.valueOf(batch.getValidCheques() != null ? batch.getValidCheques() : 0)));
	
	            // Invalid Cheques (chequeStatus = Repair)
	            item.appendChild(new Listcell(
	                    String.valueOf(batch.getInvalidCheques() != null ? batch.getInvalidCheques() : 0)));
	
	            // Status Badge
	            Listcell statusCell = new Listcell();
	            Label statusLabel = new Label(
	            	    batch.getStatus() != null
	            	        ? batch.getStatus().name()
	            	        : ""
	            	);
	            statusLabel.setStyle(
	            	    badgeStyle(
	            	        batch.getStatus() != null
	            	            ? batch.getStatus().name()
	            	            : ""
	            	    )
	            	);
	            statusCell.appendChild(statusLabel);
	            item.appendChild(statusCell);
	
	            // View Button
	            Listcell actionCell = new Listcell();
	            Button viewBtn = new Button("View");
	            viewBtn.setStyle(
	                    "background:#1a3a6e;color:white;" +
	                    "border:none;border-radius:5px;padding:4px 12px;font-size:12px;");
	            viewBtn.addEventListener("onClick", e -> openBatchDetails(batch));
	            actionCell.appendChild(viewBtn);
	            item.appendChild(actionCell);
	
	            batchListbox.appendChild(item);
	        }
	    }
	
	    // ── SEARCH — filters on currentDateBatches only ───────────
	    @Listen("onClick=#btnSearch")
	    public void searchBatches() {
	
	        // 1. Which date is selected? Default to yesterday if empty.
	        Date selectedDate = filterDate.getValue();
	        if (selectedDate == null) {
	            selectedDate = yesterday();
	            filterDate.setValue(selectedDate);
	        }
	
	        // 2. Reload from DB for the selected date
	        Date startOfDay = toStartOfDay(selectedDate);
	        Date endOfDay   = toEndOfDay(selectedDate);
	
	        currentDateBatches = service.searchBatches("", "", startOfDay, endOfDay);
	        lblPageMeta.setValue(sdfMeta.format(selectedDate) + " · Inward Clearing");
	
	        // 3. Apply in-memory filters
	        String batchIdFilter = txtBatchId.getValue() == null
	                ? "" : txtBatchId.getValue().trim().toLowerCase();
	
	        String rawStatus = cmbStatus.getValue() == null
	                ? "" : cmbStatus.getValue().trim();
	        String statusFilter = (rawStatus.isEmpty() || "ALL".equalsIgnoreCase(rawStatus))
	                ? "" : rawStatus.toUpperCase();
	
	        List<InwardBatchDTO> filtered = new ArrayList<>();
	
	        for (InwardBatchDTO b : currentDateBatches) {
	
	            if (!batchIdFilter.isEmpty()) {
	                if (b.getBatchId() == null ||
	                        !b.getBatchId().toLowerCase().contains(batchIdFilter)) {
	                    continue;
	                }
	            }
	
	            if (!statusFilter.isEmpty()) {
	                if (b.getStatus() == null ||
	                		!b.getStatus().name().equalsIgnoreCase(statusFilter)) {
	                    continue;
	                }
	            }
	
	            filtered.add(b);
	        }
	        
	        //Reaload KPI's
	        loadDashboardStats(selectedDate);
	
	        System.out.println("SEARCH → date=[" + sdfMeta.format(selectedDate)
	                + "] batchId=[" + batchIdFilter
	                + "] status=[" + statusFilter
	                + "] result=" + filtered.size() + "/" + currentDateBatches.size());
	
	        populateBatchTable(filtered);
	
	        if (filtered.isEmpty()) {
	            Clients.showNotification(
	                    "No batches found for the selected filters.",
	                    "warning", null, "top_center", 3000);
	        } else {
	            Clients.showNotification(
	                    filtered.size() + " batch(es) found.",
	                    "info", null, "top_center", 2000);
	        }
	    }
	
	    // ── DATE CHANGE — reload from DB when date picker changes ─
	//    @Listen("onChange=#filterDate")
	//    public void onDateChange() {
	//
	//        Date selectedDate = filterDate.getValue();
	//
	//        // Reset filters when date changes
	//        txtBatchId.setValue("");
	//        cmbStatus.setValue("All");
	//
	//        if (selectedDate == null) {
	//            selectedDate = yesterday();
	//            filterDate.setValue(selectedDate);
	//        }
	//
	//        loadForDate(selectedDate);
	//    }
	
	    // ── CLEAR FILTERS ─────────────────────────────────────────
	    @Listen("onClick=#btnClear")
	    public void clearFilters() {
	
	        txtBatchId.setValue("");
	        cmbStatus.setValue("All");
	        filterDate.setValue(yesterday());
	
	        // Reload yesterday's batches
	        loadForDate(yesterday());
	
	        Clients.showNotification(
	                "Filters cleared. Showing yesterday's batches.",
	                "info", null, "top_center", 2000);
	    }
	
	    // ── REFRESH ───────────────────────────────────────────────
	    @Listen("onClick=#btnRefresh")
	    public void refreshDashboard() {
	
	        Date selectedDate = filterDate.getValue();
	        if (selectedDate == null) selectedDate = yesterday();
	
	        loadForDate(selectedDate);
	
	        Clients.showNotification(
	                "Dashboard refreshed.", "info",
	                null, "top_center", 2000);
	    }
	
	    @Listen("onClick=#btnRefreshList")
	    public void refreshList() {
	
	        Date selectedDate = filterDate.getValue();
	        if (selectedDate == null) selectedDate = yesterday();
	
	        // Reset in-memory filters too
	        txtBatchId.setValue("");
	        cmbStatus.setValue("All");
	
	        loadForDate(selectedDate);
	
	        Clients.showNotification(
	                "Batch list refreshed.", "info",
	                null, "top_center", 2000);
	    }
	
	    // ── Open Batch Detail ─────────────────────────────────────
	    private void openBatchDetails(InwardBatchDTO batch) {
	
	        try {
	        	boolean alreadyClearedToCbs =
	        	        BatchStatus.Cleared.equals(batch.getStatus());
	
	            if (alreadyClearedToCbs) {
	                Map<String, Object> cbsArgs = new HashMap<>();
	                cbsArgs.put("cbsBatchDbId", batch.getId());
	
	                com.cts.composer.DashboardComposer.getInstance()
	                        .loadPage("/zul/inward/cbsValidation.zul", cbsArgs);
	            } else {
	                Map<String, Object> args = new HashMap<>();
	                args.put("batchDbId", batch.getId());
	
	                com.cts.composer.DashboardComposer.getInstance()
	                        .loadPage("/zul/inward/batchDetail.zul", args);
	            }
	
	        } catch (Exception e) {
	            e.printStackTrace();
	            Messagebox.show("Unable to open batch details.");
	        }
	    }
	
	
	    // ── Badge Style by Status ─────────────────────────────────
	    private String badgeStyle(String status) {
	
	        if (status == null) return
	                "background:#e5e7eb;color:#374151;" +
	                "padding:3px 12px;border-radius:12px;font-size:11px;font-weight:700;";
	
	        switch (status.toUpperCase()) {
	            case "CLEARED":
	                return "background:#198754;color:white;" +
	                       "padding:3px 12px;border-radius:12px;font-size:11px;font-weight:700;";
	            case "PENDING":
	                return "background:#ffc107;color:#1a1a1a;" +
	                       "padding:3px 12px;border-radius:12px;font-size:11px;font-weight:700;";
	            case "RECEIVED":
	                return "background:#f59e0b;color:#1a1a1a;" +
	                       "padding:3px 12px;border-radius:12px;font-size:11px;font-weight:700;";
	            default:
	                return "background:#e5e7eb;color:#374151;" +
	                       "padding:3px 12px;border-radius:12px;font-size:11px;font-weight:700;";
	        }
	    }
	
	    // ── Date Helpers ──────────────────────────────────────────
	
	    // Yesterday: today minus 1 day, at midnight
	    private Date yesterday() {
	        Calendar cal = Calendar.getInstance();
	        cal.add(Calendar.DATE, -1);
	        cal.set(Calendar.HOUR_OF_DAY, 0);
	        cal.set(Calendar.MINUTE,      0);
	        cal.set(Calendar.SECOND,      0);
	        cal.set(Calendar.MILLISECOND, 0);
	        return cal.getTime();
	    }
	
	    private Date today() {
	        Calendar cal = Calendar.getInstance();
	        cal.set(Calendar.HOUR_OF_DAY, 0);
	        cal.set(Calendar.MINUTE,      0);
	        cal.set(Calendar.SECOND,      0);
	        cal.set(Calendar.MILLISECOND, 0);
	        return cal.getTime();
	    }
	
	    private Date toStartOfDay(Date date) {
	        Calendar cal = Calendar.getInstance();
	        cal.setTime(date);
	        cal.set(Calendar.HOUR_OF_DAY, 0);
	        cal.set(Calendar.MINUTE,      0);
	        cal.set(Calendar.SECOND,      0);
	        cal.set(Calendar.MILLISECOND, 0);
	        return cal.getTime();
	    }
	
	    private Date toEndOfDay(Date date) {
	        Calendar cal = Calendar.getInstance();
	        cal.setTime(date);
	        cal.set(Calendar.HOUR_OF_DAY, 23);
	        cal.set(Calendar.MINUTE,      59);
	        cal.set(Calendar.SECOND,      59);
	        cal.set(Calendar.MILLISECOND, 999);
	        return cal.getTime();
	    }
	}
