package com.cts.inward.service;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;

import com.cts.util.PropertyUtil;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * CBSService — Core Banking System Validation
 *
 * Connects to Firebase Firestore (CBS simulation) and validates cheque details.
 * All methods are static — call directly as CBSService.validateCheque(...)
 *
 * Main method to use: validateCheque(accountNumber, chequeNumber, amount)
 * Returns: "VALID" if all checks pass, or an error code string if any check
 * fails.
 */
public class CBSService {

	private static final String BASE_URL = PropertyUtil.getProperty("firebase.base.url");
			//"https://fir-6311f-default-rtdb.asia-southeast1.firebasedatabase.app";

	// Shared HTTP client and JSON mapper — created once, reused for all calls
	private static final HttpClient HTTP_CLIENT = HttpClient.newHttpClient();
	private static final ObjectMapper JSON_MAPPER = new ObjectMapper();

	// ── Complete CTS Validation ───────────────────────────────────────
	/**
	 * Runs all 5 CBS checks in order. Stops and returns an error code as soon as
	 * any check fails.
	 *
	 * Returns: "VALID" → all checks passed "ACCOUNT_NOT_FOUND" → account does not
	 * exist in CBS "ACCOUNT_INACTIVE" → account is frozen or closed
	 * "CHEQUE_NOT_FOUND" → cheque not registered in CBS "STOPPED_CHEQUE" → payment
	 * stopped by account holder "CHEQUE_ALREADY_CLEARED" → cheque already processed
	 * (duplicate) "INSUFFICIENT_FUNDS" → not enough balance for this amount
	 */
	 public static String validateInOneRequest(String accountNumber, String chequeNumber, double amount) {

	        // Step 1: Fetch the full account data in ONE request
	        JsonNode accountData = getAccountDetails(accountNumber);

	        // Check 1: Account must exist in Firebase
	        if (accountData == null || accountData.isNull()) {
	            System.out.println("CBSService.validateInOneRequest: account not found → " + accountNumber);
	            return "ACCOUNT_NOT_FOUND";
	        }

	        // Check 2: Account must be ACTIVE
	        String accountStatus = accountData.path("accountStatus").asText("");
	        if (!"ACTIVE".equalsIgnoreCase(accountStatus)) {
	            System.out.println("CBSService.validateInOneRequest: account inactive → " + accountNumber
	                + " | status=" + accountStatus);
	            return "ACCOUNT_INACTIVE";
	        }

	        // Check 3: Cheque must be registered under this account
	        JsonNode registeredCheques = accountData.path("registeredCheques");
	        JsonNode chequeNode = registeredCheques.path(chequeNumber);
	        if (chequeNode.isMissingNode() || chequeNode.isNull()) {
	            System.out.println("CBSService.validateInOneRequest: cheque not found → " + chequeNumber);
	            return "CHEQUE_NOT_FOUND";
	        }

	        // Check 4: Stop payment must NOT be marked on this account
	        boolean stopPayment = accountData.path("stopPayment").asBoolean(false);
	        if (stopPayment) {
	            System.out.println("CBSService.validateInOneRequest: stop payment marked → " + accountNumber);
	            return "STOP_PAYMENT";
	        }

	        // Check 5: Account must have sufficient balance
	        double balance = accountData.path("balance").asDouble(0);
	        if (balance < amount) {
	            System.out.println("CBSService.validateInOneRequest: insufficient funds → "
	                + "balance=" + balance + " | required=" + amount);
	            return "INSUFFICIENT_FUNDS";
	        }

	        // All 5 checks passed
	        System.out.println("CBSService.validateInOneRequest: VALID → account=" + accountNumber
	            + " | cheque=" + chequeNumber);
	        return "VALID";
	    }

	    // ── Get Full Account Details ──────────────────────────────────────────
	    // Returns all fields of the account as a JsonNode, or null if not found.
	    // This is the same single fetch that validateInOneRequest() uses internally.
	    public static JsonNode getAccountDetails(String accountNumber) {
	        try {
	            String url = BASE_URL + "/" + accountNumber + ".json";
	            HttpResponse<String> response = sendGetRequest(url);
	            if (response.statusCode() != 200) return null;
	            String body = response.body();
	            if ("null".equals(body) || body == null || body.isBlank()) return null;
	            return JSON_MAPPER.readTree(body);
	        } catch (Exception e) {
	            e.printStackTrace();
	            return null;
	        }
	    }

	   


	// ── Internal Helper — Sends a GET request ────────────────────────
	private static HttpResponse<String> sendGetRequest(String url) throws Exception {
		HttpRequest request = HttpRequest.newBuilder().uri(URI.create(url)).GET().build();
		return HTTP_CLIENT.send(request, HttpResponse.BodyHandlers.ofString());
	}
}