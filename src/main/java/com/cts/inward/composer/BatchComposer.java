package com.cts.inward.composer;

import java.io.File;
import java.io.FileOutputStream;
import java.io.FileWriter;
import java.io.InputStream;
import java.util.HashMap;
import java.util.Map;

import org.zkoss.util.media.Media;
import org.zkoss.zk.ui.Component;
import org.zkoss.zk.ui.Executions;
import org.zkoss.zk.ui.event.UploadEvent;
import org.zkoss.zk.ui.select.SelectorComposer;
import org.zkoss.zk.ui.select.annotation.Listen;
import org.zkoss.zk.ui.select.annotation.Wire;
import org.zkoss.zul.Div;
import org.zkoss.zul.Label;
import org.zkoss.zul.Listbox;
import org.zkoss.zul.Messagebox;
import org.zkoss.zul.Window;

import com.cts.composer.DashboardComposer;
import com.cts.inward.entity.InwardBatch;
import com.cts.inward.service.BatchProcessingService;
import com.cts.inward.service.BatchProcessingServiceImpl;
import com.cts.inward.service.InwardChequeService;
import com.cts.inward.service.InwardChequeServiceImpl;

public class BatchComposer extends SelectorComposer<Component> {

	private static final long serialVersionUID = 1L;

	// ==========================
	// WIRED COMPONENTS
	// ==========================

	@Wire
	private Label xmlFileName;

	@Wire
	private Label zipFileName;

	@Wire
	private Label xmlStatusLabel;

	@Wire
	private Label zipStatusLabel;

	@Wire
	private Div xmlStatusDot;

	@Wire
	private Div zipStatusDot;
	
	// ==========================
	// FIELDS
	// ==========================

	private File xmlFile;
	private File zipFile;

	private final BatchProcessingService batchService = new BatchProcessingServiceImpl();
	
	// Used to look up the DB primary key (id) from the batchId string
	// after processing, so we can pass it to MakerBatchDetailComposer.
	

	// ==========================
	// XML UPLOAD
	// ==========================

	@Listen("onUpload = #xmlUpload")
	public void uploadXml(UploadEvent event) throws Exception {

		Media media = event.getMedia();
		xmlFile = saveXmlToTempFile(media);

		xmlFileName.setValue("✔ " + media.getName());
		xmlStatusDot.setStyle(greenDotStyle());
		xmlStatusLabel.setValue("BPXF file selected : " + media.getName());
	}

	// ==========================
	// ZIP UPLOAD
	// ==========================

	@Listen("onUpload = #zipUpload")
	public void uploadZip(UploadEvent event) throws Exception {

		Media media = event.getMedia();
		zipFile = saveZipToTempFile(media);

		zipFileName.setValue("✔ " + media.getName());
		zipStatusDot.setStyle(greenDotStyle());
		zipStatusLabel.setValue("BIGF file selected : " + media.getName());
	}

	// ==========================
	// PROCESS BATCH
	// ==========================

	@Listen("onClick = #processBtn")
	public void processBatch() {

		if (xmlFile == null) {
		    xmlStatusDot.setStyle(redDotStyle());
		    xmlStatusLabel.setValue("BPXF file not selected — please upload");
		    Messagebox.show("Please upload the BPXF (XML) file first");
		    return;
		}

	    if (zipFile == null) {
	    	zipStatusDot.setStyle(redDotStyle());
	        zipStatusLabel.setValue("BIGF file not selected — please upload");
	        Messagebox.show("Please upload the BIGF (ZIP) file first");
	        return;
	    }

	    // Show the centered progress dialog
	    Window progressWindow = buildProgressWindow();

	    // Get the current ZK desktop — needed for server push from background thread
	    org.zkoss.zk.ui.Desktop desktop = Executions.getCurrent().getDesktop();
	    desktop.enableServerPush(true);

	    // Keep references to the labels inside the window so we can update them
	    Label              progressLabel = (Label) progressWindow.getFellow("progressLabel");
	    org.zkoss.zul.Html svgHtml       = (org.zkoss.zul.Html) progressWindow.getFellow("spinnerHtml");
	    Div                progressFill  = (Div) progressWindow.getFellow("progressFill");
	    // Run batch processing in a background thread
	    // so the UI thread stays free for server-push updates
	    Thread worker = new Thread(() -> {

	        String[] resultHolder = { null };
	        Exception[] errorHolder = { null };

	        try {
	            String result = batchService.processBatch(xmlFile, zipFile,
	                    (current, total) -> {

	                        // This lambda runs on the background thread.
	                        // Use Executions.schedule() to push UI update to ZK.
	                    	Executions.schedule(desktop, evt -> {
	                    	    int pct = (total == 0) ? 0 : (current * 100 / total);

	                    	    svgHtml.setContent(buildSpinnerHtml(pct));          // updates % number
	                    	    progressFill.setStyle(progressBarStyle(pct));        // moves the bar
	                    	    progressLabel.setValue(current + " / " + total + " cheques processed");	

	                    	}, null);
	                    });

	            resultHolder[0] = result;

	        } catch (Exception e) {
	            errorHolder[0] = e;
	        }

	        // Processing done — update UI on ZK thread
	        final String finalResult  = resultHolder[0];
	        final Exception finalError = errorHolder[0];

	        Executions.schedule(desktop, evt -> {

	            progressWindow.detach(); // close progress dialog
	            desktop.enableServerPush(false);

	            if (finalError != null) {
	                finalError.printStackTrace();
	                Messagebox.show("Error : " + finalError.getMessage());
	                return;
	            }

	         // NEW
	            String[] parts    = finalResult.split("\\|");
	            String   batchId  = parts[0];
	            String   total    = parts[1];
	            String   inserted = parts[2];
	            String   skipped  = parts[3];
	            String   failed   = parts[4];

	            final Long batchDbId = batchService.resolveBatchDbId(batchId);
	            
	         // Delete only the current upload's temporary XML file
                if (xmlFile != null && xmlFile.exists()) {
                    System.out.println("Deleting XML Temp File : " + xmlFile.getAbsolutePath());

                    if (xmlFile.delete()) {
                        System.out.println("XML Temp File Deleted Successfully.");
                    } else {
                        System.out.println("Failed to Delete XML Temp File.");
                    }
                }

                // Delete only the current upload's temporary ZIP file
                if (zipFile != null && zipFile.exists()) {
                    System.out.println("Deleting ZIP Temp File : " + zipFile.getAbsolutePath());

                    if (zipFile.delete()) {
                        System.out.println("ZIP Temp File Deleted Successfully.");
                    } else {
                        System.out.println("Failed to Delete ZIP Temp File.");
                    }
                }

	            Messagebox.show(
	                    "Batch Processed Successfully!\n\n"
	                    + "Batch ID      :  " + batchId    + "\n"
	                    + "Total Cheques :  " + total      + "\n"
	                    + "Inserted Cheques   :  " + inserted   + "\n"
	                    + "Duplicate Cheques       :  " + skipped    + "\n"
	                    + "Failed Cheques     :  " + failed,
	                    "Processing Complete",
	                    Messagebox.OK,
	                    Messagebox.INFORMATION,
	                    event -> navigateToMicrService(batchDbId));

	        }, null);
	    });

	    worker.setDaemon(true);
	    worker.start();
	}

	// ==========================
	// BUILD PROGRESS WINDOW
	// ==========================

	/**
	 * Creates a centered modal-like Window with:
	 *   - A title
	 *   - A progress bar (div inside div)
	 *   - A "X / Y cheques inserted" label
	 *   - A percentage label
	 *
	 * All child components have IDs so the composer can wire them.
	 */
	private Window buildProgressWindow() {

	    Window win = new Window();
	    win.setTitle("Processing Batch...");
	    win.setBorder("normal");
	    win.setWidth("420px");
	    win.setClosable(false);

	    // ── Rotating spinner + percentage in center ────────────────────────
	    org.zkoss.zul.Html svgHtml = new org.zkoss.zul.Html();
	    svgHtml.setId("spinnerHtml");
	    svgHtml.setStyle("display:block; text-align:center; margin:20px auto 10px auto;");
	    svgHtml.setContent(buildSpinnerHtml(0));

	    // ── Horizontal progress bar track ──────────────────────────────────
	    Div track = new Div();
	    track.setStyle(
	            "width:90%; height:14px; background:#e5e7eb;"
	            + "border-radius:6px; overflow:hidden;"
	            + "margin:0 auto 14px auto;");

	    Div fill = new Div();
	    fill.setId("progressFill");
	    fill.setStyle(progressBarStyle(0));
	    track.appendChild(fill);

	    // ── Status label ───────────────────────────────────────────────────
	    Label progressLabel = new Label("0 / 0 cheques processed");
	    progressLabel.setId("progressLabel");
	    progressLabel.setStyle(
	            "display:block; text-align:center; font-size:13px;"
	            + "color:#555; margin-bottom:16px;");

	    win.appendChild(svgHtml);
	    win.appendChild(track);
	    win.appendChild(progressLabel);

	    win.setPage(getSelf().getPage());
	    win.setPosition("center,center");
	    win.doHighlighted();

	    return win;
	}

	private String buildSpinnerHtml(int pct) {

	    return "<style>"
	        + "@keyframes rotate { from { transform: rotate(0deg); } to { transform: rotate(360deg); } }"
	        + ".spinner-ring { animation: rotate 1.2s linear infinite; transform-origin: 50px 50px; }"
	        + "</style>"

	        + "<svg width='100' height='100' viewBox='0 0 100 100'>"

	        // Static grey background circle
	        + "<circle cx='50' cy='50' r='40'"
	        + " fill='none' stroke='#e5e7eb' stroke-width='6'/>"

	        // Rotating arc (always spinning — the dynamic feel)
	        + "<g class='spinner-ring'>"
	        + "<circle cx='50' cy='50' r='40'"
	        + " fill='none'"
	        + " stroke='url(#spinGrad)'"
	        + " stroke-width='6'"
	        + " stroke-linecap='round'"
	        + " stroke-dasharray='60 192'/>"   // short arc, rest is gap
	        + "</g>"

	        // Gradient for the spinning arc
	        + "<defs>"
	        + "<linearGradient id='spinGrad' x1='0%' y1='0%' x2='100%' y2='0%'>"
	        + "<stop offset='0%'  stop-color='#1a3a6e'/>"
	        + "<stop offset='100%' stop-color='#2d5aa0'/>"
	        + "</linearGradient>"
	        + "</defs>"

	        // Percentage text in center — this is the only part that updates
	        + "<text x='50' y='50'"
	        + " text-anchor='middle'"
	        + " dominant-baseline='central'"
	        + " font-size='18'"
	        + " font-weight='700'"
	        + " fill='#1a3a6e'>"
	        + pct + "%"
	        + "</text>"

	        + "</svg>";
	}	

	// ==========================
	// NAVIGATE TO MICR SERVICE
	// ==========================

	/**
	 * Called after the user dismisses the success dialog.
	 *
	 * Passes batchDbId as an execution attribute so that MakerBatchDetailComposer
	 * can read it with: Executions.getCurrent().getAttribute("batchDbId")
	 *
	 * @param batchDbId the Long primary key of the just-processed batch, or null if
	 *                  lookup failed (falls back to default 1L in target)
	 */
	private void navigateToMicrService(Long batchDbId) {
		Map<String, Object> args = new HashMap<>();
		if (batchDbId != null) {
			args.put("batchDbId", batchDbId);
		}
		DashboardComposer.getInstance().loadPage("/zul/inward/batchDetail.zul", args);
	}

	// ==========================
	// RESOLVE BATCH DB ID
	// ==========================

	/**
	 * Look up the Long primary key of the batch by its batchId string. Returns null
	 * if the batch cannot be found (unlikely right after processing).
	 */


	// ==========================
	// FILE SAVE HELPERS
	// ==========================

	private File saveXmlToTempFile(Media media) throws Exception {

        File file = File.createTempFile("BPXF_", ".xml");
        
        System.out.println("XML Temp File Path: " + file.getAbsolutePath());

        try (FileWriter writer = new FileWriter(file)) {
            writer.write(media.getStringData());
        }

        return file;
    }

    private File saveZipToTempFile(Media media) throws Exception {

        File file = File.createTempFile("BIGF_", ".zip");
        
        System.out.println("ZIP Temp File Path: " + file.getAbsolutePath());

        try (InputStream in = media.getStreamData();
             FileOutputStream out = new FileOutputStream(file)) {

            byte[] buffer = new byte[4096];
            int len;

            while ((len = in.read(buffer)) > 0) {
                out.write(buffer, 0, len);
            }
        }

        return file;
    }
    
    
	// ==========================
	// STYLE HELPERS
	// ==========================

	private String greenDotStyle() {
	    return "width:12px; height:12px; border-radius:50%;"
	            + "background:#22c55e; display:inline-block; flex-shrink:0;";
	}

	private String redDotStyle() {
	    return "width:12px; height:12px; border-radius:50%;"
	            + "background:#ef4444; display:inline-block; flex-shrink:0;";
	}

	private String progressBarStyle(int pct) {
	    return "height:100%; border-radius:6px;"
	            + "background:linear-gradient(90deg,#1a3a6e,#2d5aa0);"
	            + "width:" + pct + "%; transition:width 0.3s ease;";
	}
}