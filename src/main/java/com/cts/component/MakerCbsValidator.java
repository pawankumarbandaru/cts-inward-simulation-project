package com.cts.component;

/**
 * File    : MakerCbsValidator.java
 * Package : com.cts.component
 * Purpose : Runs CBS (Core Banking System) validation for a cheque in the Maker Repair Workspace.
 *           Calls CBSService in a single request, displays a 5-check result popup,
 *           saves the result to DB via InwardChequeService, then triggers either
 *           submit-to-checker (pass) or RRF generation (fail) via a callback.
 * Author  : Ramana
 * Date    : 24-06-2025
 */

import org.zkoss.zk.ui.event.Events;
import org.zkoss.zul.Button;
import org.zkoss.zul.Div;
import org.zkoss.zul.Html;
import org.zkoss.zul.Window;

import com.cts.inward.entity.InwardCheque;
import com.cts.inward.service.CBSService;
import com.cts.inward.service.InwardChequeService;
import com.cts.inward.service.InwardChequeServiceImpl;
import com.cts.util.ChequeRoutingUtil;

public class MakerCbsValidator {

	private final Div pageRoot;
	private final String currentUserId;
	private final CbsResultCallback callback;

	// Used to save CBS result to DB — goes through service, not DAO directly
	private final InwardChequeService chequeService = new InwardChequeServiceImpl();

	// Kept as a field so the close (✕) button inside the popup can detach it
	private Window cbsPopup;

	/**
	 * @param pageRoot      Root Div of the page — used to attach the CBS popup
	 *                      window
	 * @param currentUserId Logged-in maker user ID
	 * @param callback      Implemented by M_RepairWorkspaceComposer to handle
	 *                      pass/fail
	 */
	public MakerCbsValidator(Div pageRoot, String currentUserId, CbsResultCallback callback) {
		this.pageRoot = pageRoot;
		this.currentUserId = currentUserId;
		this.callback = callback;
	}

	// ── Callback interface ────────────────────────────────────────────────────

	/** Implemented by the composer to act on CBS pass or fail. */
	public interface CbsResultCallback {
		void onCbsPassed(double amount, String destination);

		void onCbsFailed(String failureCode);
	}

	// ── Main entry point ──────────────────────────────────────────────────────

	/**
	 * Runs all 5 CBS checks in a single Firebase request, builds the result popup,
	 * saves the result to DB, and shows pass or fail UI in the popup.
	 *
	 * @param currentCheque the cheque being validated
	 * @param accountNo     account number from the form field
	 * @param chequeNo      cheque number from the form field
	 * @param amount        cheque amount
	 */
	public void run(InwardCheque currentCheque, String accountNo, String chequeNo, double amount) {

		cbsPopup = buildPopupWindow(chequeNo);

		Div checkRowsDiv = (Div) cbsPopup.getFellow("cbs-popup-check-rows");
		Div resultArea = (Div) cbsPopup.getFellow("cbs-popup-result-area");

		// Single Firebase request — returns "VALID" or a failure code
		String cbsResult = CBSService.validateInOneRequest(accountNo, chequeNo, amount);

		System.out.println(
				"MakerCbsValidator: CBS result = " + cbsResult + " | account=" + accountNo + " | cheque=" + chequeNo);

		// Determine which checks passed, based on where the result failed
		boolean accountExists = !cbsResult.equals("ACCOUNT_NOT_FOUND");
		boolean accountActive = accountExists && !cbsResult.equals("ACCOUNT_INACTIVE");
		boolean chequeRegistered = accountActive && !cbsResult.equals("CHEQUE_NOT_FOUND");
		boolean noStopPayment = chequeRegistered && !cbsResult.equals("STOP_PAYMENT");
		boolean sufficientFunds = noStopPayment && !cbsResult.equals("INSUFFICIENT_FUNDS");

		checkRowsDiv.appendChild(
				buildCheckRow(1, "Account Exists in CBS", "Account No: " + accountNo, accountExists, false));

		if (!accountExists) {
			appendSkippedRows(checkRowsDiv, 2, 1);
		} else {
			checkRowsDiv.appendChild(buildCheckRow(2, "Account is Active", "Status check for account " + accountNo,
					accountActive, false));

			if (!accountActive) {
				appendSkippedRows(checkRowsDiv, 3, 2);
			} else {
				checkRowsDiv.appendChild(buildCheckRow(3, "Cheque Registered in CBS", "Cheque No: " + chequeNo,
						chequeRegistered, false));

				if (!chequeRegistered) {
					appendSkippedRows(checkRowsDiv, 4, 3);
				} else {
					checkRowsDiv.appendChild(buildCheckRow(4, "No Stop Payment on Account",
							"Stop payment flag check for account " + accountNo, noStopPayment, false));

					if (!noStopPayment) {
						appendSkippedRows(checkRowsDiv, 5, 4);
					} else {
						checkRowsDiv.appendChild(buildCheckRow(5, "Sufficient Balance",
								"Required: ₹" + String.format("%,.2f", amount), sufficientFunds, false));
					}
				}
			}
		}

		// Save CBS result to DB — if this fails, we still show the popup (don't block
		// the user)
		try {
			if (currentCheque != null && currentCheque.getId() != null) {
				boolean isValid = "VALID".equals(cbsResult);
				String failReason = isValid ? null : getFailureMessage(cbsResult);
				chequeService.saveCbsResult(currentCheque.getId(), isValid, failReason);
			}
		} catch (Exception e) {
			System.err.println("MakerCbsValidator: could not save CBS result — " + e.getMessage());
		}

		if ("VALID".equals(cbsResult)) {
			handlePass(currentCheque, amount, resultArea);
		} else {
			handleFail(cbsResult, resultArea);
		}
	}

	// ── Popup window builder ──────────────────────────────────────────────────

	/**
	 * Builds and attaches the CBS result popup window to the page. Returns the
	 * window so the caller can get its child Divs by fellow ID.
	 */
	private Window buildPopupWindow(String chequeNo) {

		Window win = new Window();
		win.setSclass("cbs-popup-overlay");
		win.setStyle("position:fixed; inset:0; background:rgba(10,25,50,.55);"
				+ "z-index:9000; display:flex; align-items:center; justify-content:center;");
		win.setBorder("none");
		win.setClosable(false);

		Div card = new Div();
		card.setSclass("cbs-popup-card");

		// Header row
		Div hdr = new Div();
		hdr.setSclass("cbs-popup-hdr");

		Div hdrText = new Div();
		hdrText.setStyle("flex:1;");
		Html hdrHtml = new Html();
		hdrHtml.setContent("<div class='cbs-popup-hdr-title'>🏦 CBS Validation — Live Results</div>"
				+ "<div class='cbs-popup-hdr-sub'>Cheque No: " + escapeHtml(chequeNo)
				+ " &nbsp;|&nbsp; CBS Validation Result</div>");
		hdrText.appendChild(hdrHtml);
		hdr.appendChild(hdrText);

		// Close button
		Button btnHeaderClose = new Button("✕");
		btnHeaderClose.setStyle("width:28px;height:28px;padding:0;border-radius:50%;font-size:14px;"
				+ "font-weight:700;border:1px solid rgba(255,255,255,.35);"
				+ "background:rgba(0,0,0,.25);color:#fff;cursor:pointer;"
				+ "display:flex;align-items:center;justify-content:center;flex-shrink:0;");
		btnHeaderClose.addEventListener(Events.ON_CLICK, event -> {
			if (cbsPopup != null) {
				cbsPopup.detach();
				cbsPopup = null;
			}
		});
		hdr.appendChild(btnHeaderClose);
		card.appendChild(hdr);

		// Body — contains check rows and result area
		Div body = new Div();
		body.setSclass("cbs-popup-body");

		Div checkRowsDiv = new Div();
		checkRowsDiv.setId("cbs-popup-check-rows");
		checkRowsDiv.setSclass("cbs-check-rows");
		body.appendChild(checkRowsDiv);

		Div resultArea = new Div();
		resultArea.setId("cbs-popup-result-area");
		body.appendChild(resultArea);

		card.appendChild(body);
		win.appendChild(card);
		win.setPage(pageRoot.getPage());
		win.doEmbedded();

		return win;
	}

	// ── Pass / Fail result builders ───────────────────────────────────────────

	/**
	 * Shows the "All 5 Checks Passed" result box and a submit button. Routes the
	 * cheque to TV_1 or TV_2 based on amount using ChequeRoutingUtil.
	 */
	private void handlePass(InwardCheque currentCheque, double amount, Div resultArea) {

		String destination = ChequeRoutingUtil.routeForAsString(amount);
		boolean isHighValue = "TV_2".equals(destination);

		String routingNote = isHighValue
				? "₹" + String.format("%,.0f", amount) + " > ₹1,00,000 → routed to TV_2 (Verifier 2)"
				: "₹" + String.format("%,.0f", amount) + " ≤ ₹1,00,000 → routed to TV_1 (Verifier 1)";

		Html passBox = new Html();
		passBox.setContent("<div class='cbs-pass-box'>"
				+ "<div class='cbs-pass-title'>✅ All 5 CBS Checks Passed Successfully</div>"
				+ "<div class='cbs-pass-note'>" + escapeHtml(routingNote) + "</div>"
				+ "<div style='font-size:10px;font-weight:700;color:#6B7280;"
				+ "text-transform:uppercase;letter-spacing:.4px;'>Cheque will be sent to:</div>"
				+ "<div class='cbs-pass-dest'>" + escapeHtml(destination) + "</div>" + "</div>");
		resultArea.appendChild(passBox);

		final double finalAmount = amount;
		final String finalDestination = destination;

		Div footerDiv = new Div();
		footerDiv.setSclass("cbs-popup-footer");

		Button submitBtn = new Button("📤 Transfer to " + destination + " & Back to Queue");
		submitBtn.setSclass("btn-submit-checker");
		submitBtn.setStyle("flex:1;");
		submitBtn.addEventListener(Events.ON_CLICK, event -> {
			if (cbsPopup != null) {
				cbsPopup.detach();
				cbsPopup = null;
			}
			callback.onCbsPassed(finalAmount, finalDestination);
		});
		footerDiv.appendChild(submitBtn);
		resultArea.appendChild(footerDiv);
	}

	/**
	 * Shows the CBS failure reason and a Generate RRF button.
	 */
	private void handleFail(String failureCode, Div resultArea) {

		String failureMessage = getFailureMessage(failureCode);

		Html failBox = new Html();
		failBox.setContent("<div class='cbs-fail-box'>" + "<div class='cbs-fail-title'>❌ CBS Validation Failed</div>"
				+ "<div class='cbs-fail-note'>" + escapeHtml(failureMessage) + "</div>"
				+ "<div style='font-size:10px;color:#9B1C1C;margin-top:4px;font-style:italic;'>"
				+ "Cheque cannot be forwarded. Generate RRF to return to NPCI.</div>" + "</div>");
		resultArea.appendChild(failBox);

		final String finalFailureCode = failureCode;

		Div footerDiv = new Div();
		footerDiv.setSclass("cbs-popup-footer");

		Button btnRRF = new Button("📋 Generate RRF (Return to NPCI)");
		btnRRF.setSclass("btn-rrf");
		btnRRF.setStyle("flex:1;");
		btnRRF.addEventListener(Events.ON_CLICK, event -> {
			if (cbsPopup != null) {
				cbsPopup.detach();
				cbsPopup = null;
			}
			callback.onCbsFailed(finalFailureCode);
		});
		footerDiv.appendChild(btnRRF);
		resultArea.appendChild(footerDiv);
	}

	// ── Check row builder ─────────────────────────────────────────────────────

	/**
	 * Builds a single CBS check row (PASSED / FAILED / SKIPPED).
	 *
	 * @param checkNum  check number (1–5)
	 * @param checkName human-readable name of the check
	 * @param detail    supporting detail shown below the check name
	 * @param passed    true if this check passed
	 * @param skipped   true if this check was skipped due to an earlier failure
	 */
	private Html buildCheckRow(int checkNum, String checkName, String detail, boolean passed, boolean skipped) {

		Html row = new Html();
		String icon, statusText, rowStyle, statusStyle;

		if (skipped) {
			icon = "⊘";
			statusText = "SKIPPED";
			rowStyle = "cbs-check-row cbs-check-skipped";
			statusStyle = "cbs-check-status-skipped";
		} else if (passed) {
			icon = "✅";
			statusText = "PASSED";
			rowStyle = "cbs-check-row cbs-check-pass";
			statusStyle = "cbs-check-status-pass";
		} else {
			icon = "❌";
			statusText = "FAILED";
			rowStyle = "cbs-check-row cbs-check-fail";
			statusStyle = "cbs-check-status-fail";
		}

		row.setContent("<div class='" + rowStyle + "'>" + "<span class='cbs-check-icon'>" + icon + "</span>"
				+ "<div class='cbs-check-info'>" + "<span class='cbs-check-name'>Check " + checkNum + ": "
				+ escapeHtml(checkName) + "</span>" + "<span class='cbs-check-detail'>" + escapeHtml(detail) + "</span>"
				+ "</div>" + "<span class='" + statusStyle + "'>" + statusText + "</span>" + "</div>");
		return row;
	}

	/**
	 * Appends a single combined SKIPPED row for all remaining checks after a
	 * failure.
	 *
	 * @param container     the Div to append to
	 * @param fromCheckNum  first check number to mark as skipped
	 * @param failedAtCheck the check number that actually failed
	 */
	private void appendSkippedRows(Div container, int fromCheckNum, int failedAtCheck) {
		String[] checkNames = { "", "Account Exists in CBS", "Account is Active", "Cheque Registered in CBS",
				"No Stop Payment on Account", "Sufficient Balance" };

		if (fromCheckNum > 5)
			return;

		String rangeLabel = (fromCheckNum == 5) ? "Check 5" : "Checks " + fromCheckNum + "–5";

		StringBuilder names = new StringBuilder();
		for (int i = fromCheckNum; i <= 5; i++) {
			if (names.length() > 0)
				names.append(", ");
			names.append(checkNames[i]);
		}

		Html row = new Html();
		row.setContent("<div class='cbs-check-row cbs-check-skipped'>" + "<span class='cbs-check-icon'>⊘</span>"
				+ "<div class='cbs-check-info'>" + "<span class='cbs-check-name'>" + rangeLabel + ": Not Run</span>"
				+ "<span class='cbs-check-detail'>" + escapeHtml(names.toString()) + " — stopped after Check "
				+ failedAtCheck + "</span>" + "</div>" + "<span class='cbs-check-status-skipped'>SKIPPED</span>"
				+ "</div>");
		container.appendChild(row);
	}

	// ── Failure message lookup ────────────────────────────────────────────────

	/**
	 * Returns a human-readable failure message for a given CBS result code.
	 */
	public static String getFailureMessage(String resultCode) {
		if (resultCode == null)
			return "CBS validation failed.";
		switch (resultCode) {
		case "ACCOUNT_NOT_FOUND":
			return "Account number not found in CBS records.";
		case "ACCOUNT_INACTIVE":
			return "Account is inactive (frozen or closed).";
		case "CHEQUE_NOT_FOUND":
			return "Cheque number not registered in CBS.";
		case "STOP_PAYMENT":
		case "STOPPED_CHEQUE":
			return "Payment stopped by account holder.";
		case "CHEQUE_ALREADY_CLEARED":
			return "Cheque has already been cleared (duplicate).";
		case "INSUFFICIENT_FUNDS":
			return "Insufficient balance in account.";
		default:
			return "CBS validation failed. Code: " + resultCode;
		}
	}

	// ── HTML escape ───────────────────────────────────────────────────────────

	private String escapeHtml(String input) {
		if (input == null)
			return "";
		return input.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;").replace("\"", "&quot;");
	}
}