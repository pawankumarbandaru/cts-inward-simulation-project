package com.cts.inward.dto;

import com.cts.inward.entity.InwardCheque;

/**
 * DTO : CheckerLoadResult
 *
 * Result wrapper returned by RA_C_CheckerChequeService.getChequeForCheckerReview().
 * Carries the loaded cheque (when found and actionable) plus a plain-text status
 * message the composer can display in the UI banner.
 *
 * The composer checks isOk() first. If false, it closes the popup and shows getMessage().
 */
public class CheckerLoadResult {

    /**
     * Describes why a cheque could not be loaded for review.
     */
    public enum Status {
        OK,
        NOT_FOUND,
        ALREADY_ACCEPTED,
        ALREADY_REJECTED,
        RETURNED_TO_MAKER,
        WRONG_ROUTE,
        WRONG_STATUS
    }

    private final Status       status;
    private final InwardCheque cheque;   // non-null only when status == OK
    private final String       message;  // plain text, shown in UI banner

    private CheckerLoadResult(Status status, InwardCheque cheque, String message) {
        this.status  = status;
        this.cheque  = cheque;
        this.message = message;
    }

    // ── Factory methods ────────────────────────────────────────────────────

    public static CheckerLoadResult ok(InwardCheque cheque) {
        return new CheckerLoadResult(Status.OK, cheque, null);
    }

    public static CheckerLoadResult notFound(String chequeNo) {
        return new CheckerLoadResult(Status.NOT_FOUND, null,
            "Cheque " + chequeNo + " was not found in the system.");
    }

    public static CheckerLoadResult alreadyAccepted(String chequeNo) {
        return new CheckerLoadResult(Status.ALREADY_ACCEPTED, null,
            "Cheque " + chequeNo + " is already approved. No further action needed.");
    }

    public static CheckerLoadResult alreadyRejected(String chequeNo) {
        return new CheckerLoadResult(Status.ALREADY_REJECTED, null,
            "Cheque " + chequeNo + " is already rejected.");
    }

    public static CheckerLoadResult returnedToMaker(String chequeNo) {
        return new CheckerLoadResult(Status.RETURNED_TO_MAKER, null,
            "Cheque " + chequeNo + " was returned to maker for correction. Waiting for resubmission.");
    }

    public static CheckerLoadResult wrongRoute(String chequeNo) {
        return new CheckerLoadResult(Status.WRONG_ROUTE, null,
            "Cheque " + chequeNo + " is routed to a different checker role (Branch Manager).");
    }

    public static CheckerLoadResult wrongStatus(String chequeNo, String actualStatus) {
        return new CheckerLoadResult(Status.WRONG_STATUS, null,
            "Cheque " + chequeNo + " cannot be reviewed. Current status: " + actualStatus + ".");
    }

    // ── Getters ────────────────────────────────────────────────────────────

    public Status       getStatus()  { return status; }
    public InwardCheque getCheque()  { return cheque; }
    public String       getMessage() { return message; }
    public boolean      isOk()       { return status == Status.OK; }
}
