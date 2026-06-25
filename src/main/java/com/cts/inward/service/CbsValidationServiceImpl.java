package com.cts.inward.service;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import com.cts.inward.dto.CbsAccountData;
import com.cts.inward.dto.CbsValidationResult;
import com.cts.inward.entity.InwardCheque;
import com.cts.util.PropertyUtil;

/**
 * CbsServiceImpl
 * ──────────────
 * CBS validation using the Firebase Realtime Database REST API.
 *
 * FIX 1: Firebase URL is now read from application.properties (firebase.base.url)
 *        instead of a hardcoded placeholder. Hardcoded placeholder caused every
 *        fetch to fail → null cbsData → "Account Not Found" for every cheque.
 *
 * FIX 2: Removed validation Rule 9 (payeeName vs accountName comparison).
 *        In banking, the cheque payee (who the cheque is written to) and the
 *        account holder (who owns the drawee account) are different entities.
 *        Comparing them is incorrect business logic and caused all valid cheques
 *        to be marked INVALID with "Account Holder Name Mismatch".
 *
 * Validation rules now (ordered — first failure wins):
 *   1. Missing account number on cheque       → "Missing Account Number"
 *   2. CBS data null (account not in Firebase) → "Account Not Found"
 *   3. CBS accountNo blank (data integrity)   → "Missing CBS Data"
 *   4. accountStatus != "ACTIVE"              → "Account Inactive"
 *   5. Cheque not in registeredCheques        → "Cheque Not Found"
 *   6. stopPayment == true                    → "Stop Payment Marked"
 *   7. balance < cheque amount                → "Insufficient Funds"
 *   8. accountName blank in CBS               → "Missing Account Holder Information"
 *   9. All pass                               → VALID: "Validation Successful"
 */
public class CbsValidationServiceImpl implements CbsValidationService {

    // ── Configuration — read from application.properties ─────────────────
    // firebase.base.url=https://your-project-default-rtdb.firebaseio.com
    private static final String FIREBASE_BASE_URL =
            PropertyUtil.getProperty("firebase.base.url");

    private static final int CONNECT_TIMEOUT_MS = 5_000;
    private static final int READ_TIMEOUT_MS    = 8_000;

    // ── Method 1: fetchAccountData ────────────────────────────────────────

    @Override
    public CbsAccountData fetchAccountData(String accountNo) {
        if (accountNo == null || accountNo.isBlank()) {
            System.out.println("CbsServiceImpl.fetchAccountData: accountNo is blank — returning null");
            return null;
        }

        // Accounts are top-level keys in Firebase: /{accountNo}.json
        String urlStr = FIREBASE_BASE_URL + "/" + accountNo.trim() + ".json";
        System.out.println("CbsServiceImpl: fetching CBS data from → " + urlStr);

        try {
            URL url = new URL(urlStr);
            HttpURLConnection conn = (HttpURLConnection) url.openConnection();
            conn.setRequestMethod("GET");
            conn.setConnectTimeout(CONNECT_TIMEOUT_MS);
            conn.setReadTimeout(READ_TIMEOUT_MS);
            conn.setRequestProperty("Accept", "application/json");

            int responseCode = conn.getResponseCode();
            System.out.println("CbsServiceImpl: Firebase HTTP status = " + responseCode);

            if (responseCode != 200) {
                System.out.println("CbsServiceImpl: non-200 response for account " + accountNo);
                return null;
            }

            StringBuilder sb = new StringBuilder();
            try (BufferedReader reader = new BufferedReader(
                    new InputStreamReader(conn.getInputStream(), StandardCharsets.UTF_8))) {
                String line;
                while ((line = reader.readLine()) != null) sb.append(line.trim());
            }

            String body = sb.toString();
            System.out.println("CbsServiceImpl: Firebase response body length = " + body.length());

            // Firebase returns literal "null" when the node does not exist
            if (body.equals("null") || body.isEmpty()) {
                System.out.println("CbsServiceImpl: account not found in Firebase: " + accountNo);
                return null;
            }

            return parseAccountData(body);

        } catch (Exception e) {
            System.err.println("CbsServiceImpl.fetchAccountData ERROR: " + e.getMessage());
            e.printStackTrace();
            return null;
        }
    }

    // ── Method 2: validateCheque ──────────────────────────────────────────

    /**
     * Validates a cheque against CBS account data.
     *
     * Rules applied in order — first failure wins.
     *
     * NOTE: Rule 9 (payeeName vs accountName comparison) has been REMOVED.
     * Reason: payeeName = who the cheque is written TO (payee/beneficiary).
     *         accountName = who OWNS the drawee account (drawer's account holder).
     * These are different people in banking. Comparing them is wrong business logic.
     */
    @Override
    public CbsValidationResult validateCheque(InwardCheque cheque, CbsAccountData cbsData) {
        if (cheque == null) {
            return CbsValidationResult.failure("Validation Failed: cheque is null");
        }

        // Rule 1 — Account number must be present on the cheque
        String accountNo = cheque.getAccountNo();
        if (accountNo == null || accountNo.isBlank()) {
            return CbsValidationResult.failure("Missing Account Number");
        }

        // Rule 2 — CBS must have a record for this account
        if (cbsData == null) {
            return CbsValidationResult.failure("Account Not Found");
        }

        // Rule 3 — CBS data integrity check
        if (cbsData.getAccountNo() == null || cbsData.getAccountNo().isBlank()) {
            return CbsValidationResult.failure("Missing CBS Data");
        }

        // Rule 4 — Account must be ACTIVE
        if (!cbsData.isActive()) {
            return CbsValidationResult.failure("Account Inactive");
        }

        // Rule 5 — Cheque must be registered in CBS under this account
        String chequeNo = cheque.getChequeNo();
        CbsAccountData.CbsRegisteredCheque registeredCheque = cbsData.getCheque(chequeNo);
        if (registeredCheque == null) {
            return CbsValidationResult.failure("Cheque Not Found");
        }

        // Rule 6 — No stop payment instruction on this account
        if (cbsData.isStopPayment()) {
            return CbsValidationResult.failure("Stop Payment Marked");
        }

        // Rule 7 — Sufficient balance
        if (cheque.getAmount() != null) {
            double chequeAmount = cheque.getAmount().doubleValue();
            if (cbsData.getBalance() < chequeAmount) {
                return CbsValidationResult.failure("Insufficient Funds");
            }
        }

        // Rule 8 — Account holder name must be present in CBS
        String cbsAccountName = cbsData.getAccountName();
        if (cbsAccountName == null || cbsAccountName.isBlank()) {
            return CbsValidationResult.failure("Missing Account Holder Information");
        }

        // Rule 9 (REMOVED) — payeeName vs accountName comparison is wrong business logic.
        // payeeName is who the cheque is written TO; accountName is who owns the account.
        // These are intentionally different entities in banking transactions.

        // All rules passed
        return CbsValidationResult.success();
    }

    @Override
    public long[] countValidAndInvalid(List<CbsValidationResult> results) {
        long validCount = 0;
        long invalidCount = 0;

        if (results != null) {
            for (CbsValidationResult result : results) {
                if (result == null) continue;
                if (result.isValid()) {
                    validCount++;
                } else {
                    invalidCount++;
                }
            }
        }

        return new long[] { validCount, invalidCount };
    }
    // ── Private Helper: parseAccountData ─────────────────────────────────

    private CbsAccountData parseAccountData(String json) {
        CbsAccountData data = new CbsAccountData();

        data.setAccountNo(    extractString(json,  "accountNo"));
        data.setAccountName(  extractString(json,  "accountName"));
        data.setAccountStatus(extractString(json,  "accountStatus"));
        data.setBalance(      extractDouble(json,  "balance"));
        data.setStopPayment(  extractBoolean(json, "stopPayment"));
        data.setBankName(     extractString(json,  "bankName"));
        data.setBranchName(   extractString(json,  "branchName"));

        Map<String, CbsAccountData.CbsRegisteredCheque> cheques = parseRegisteredCheques(json);
        data.setRegisteredCheques(cheques);

        System.out.println("CbsServiceImpl: parsed account → "
                + data.getAccountNo()
                + " | name="    + data.getAccountName()
                + " | status="  + data.getAccountStatus()
                + " | balance=" + data.getBalance()
                + " | cheques=" + cheques.size());

        return data;
    }

    private Map<String, CbsAccountData.CbsRegisteredCheque> parseRegisteredCheques(String json) {
        Map<String, CbsAccountData.CbsRegisteredCheque> result = new HashMap<>();

        int blockStart = json.indexOf("\"registeredCheques\"");
        if (blockStart < 0) return result;

        int braceOpen = json.indexOf('{', blockStart + "\"registeredCheques\"".length());
        if (braceOpen < 0) return result;

        int braceClose = findMatchingBrace(json, braceOpen);
        if (braceClose < 0) return result;

        String block = json.substring(braceOpen + 1, braceClose);

        int pos = 0;
        while (pos < block.length()) {
            int entryOpen = block.indexOf('{', pos);
            if (entryOpen < 0) break;

            int entryClose = findMatchingBrace(block, entryOpen);
            if (entryClose < 0) break;

            String entryJson = block.substring(entryOpen, entryClose + 1);
            CbsAccountData.CbsRegisteredCheque cheque = parseSingleCheque(entryJson);
            if (cheque != null && cheque.getChequeNo() != null) {
                result.put(cheque.getChequeNo().trim(), cheque);
            }

            pos = entryClose + 1;
        }

        return result;
    }

    private CbsAccountData.CbsRegisteredCheque parseSingleCheque(String json) {
        CbsAccountData.CbsRegisteredCheque c = new CbsAccountData.CbsRegisteredCheque();
        c.setChequeNo(     extractString(json,  "chequeNo"));
        c.setAmount(       extractDouble(json,  "amount"));
        c.setAmountInWords(extractString(json,  "amountInWords"));
        c.setChequeDate(   extractString(json,  "chequeDate"));
        c.setStatus(       extractString(json,  "status"));
        c.setPresented(    extractBoolean(json, "presented"));
        c.setPaid(         extractBoolean(json, "paid"));
        return c;
    }

    private int findMatchingBrace(String s, int openPos) {
        int depth = 0;
        for (int i = openPos; i < s.length(); i++) {
            char ch = s.charAt(i);
            if (ch == '{') depth++;
            else if (ch == '}') {
                depth--;
                if (depth == 0) return i;
            }
        }
        return -1;
    }

    // ── JSON primitive extractors ─────────────────────────────────────────

    private String extractString(String json, String key) {
        String pattern = "\"" + key + "\"";
        int keyIdx = json.indexOf(pattern);
        if (keyIdx < 0) return null;

        int colonIdx = json.indexOf(':', keyIdx + pattern.length());
        if (colonIdx < 0) return null;

        int start = colonIdx + 1;
        while (start < json.length() && json.charAt(start) == ' ') start++;
        if (start >= json.length()) return null;

        if (json.charAt(start) == '"') {
            int end = json.indexOf('"', start + 1);
            return end > start ? json.substring(start + 1, end) : null;
        }
        return null;
    }

    private double extractDouble(String json, String key) {
        String pattern = "\"" + key + "\"";
        int keyIdx = json.indexOf(pattern);
        if (keyIdx < 0) return 0.0;

        int colonIdx = json.indexOf(':', keyIdx + pattern.length());
        if (colonIdx < 0) return 0.0;

        int start = colonIdx + 1;
        while (start < json.length() && json.charAt(start) == ' ') start++;
        if (start >= json.length()) return 0.0;

        int end = start;
        while (end < json.length()) {
            char ch = json.charAt(end);
            if (ch == ',' || ch == '}' || ch == '\n' || ch == ' ') break;
            end++;
        }
        String raw = json.substring(start, end).trim();
        try {
            return Double.parseDouble(raw);
        } catch (NumberFormatException e) {
            return 0.0;
        }
    }

    private boolean extractBoolean(String json, String key) {
        String pattern = "\"" + key + "\"";
        int keyIdx = json.indexOf(pattern);
        if (keyIdx < 0) return false;

        int colonIdx = json.indexOf(':', keyIdx + pattern.length());
        if (colonIdx < 0) return false;

        String tail = json.substring(colonIdx + 1).trim();
        return tail.startsWith("true");
    }
}