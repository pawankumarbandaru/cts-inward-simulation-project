package com.cts.inward.dto;

import com.cts.inward.entity.InwardCheque;

/**
 * DTO : V2LoadResult
 *
 * Result wrapper returned by RA_V2_Verifier2ChequeService.getChequeForV2Review().
 * Mirrors CheckerLoadResult — same structure, same Status enum values.
 *
 * The composer checks isOk() first. If false, it closes the popup and shows getMessage().
 */
public class V2LoadResult {

    /**
     * Describes why a cheque could not be loaded for Verifier 2 review.
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

    private V2LoadResult(Status status, InwardCheque cheque, String message) {
        this.status  = status;
        this.cheque  = cheque;
        this.message = message;
    }

    // ── Factory methods ────────────────────────────────────────────────────

    public static V2LoadResult ok(InwardCheque cheque) {
        return new V2LoadResult(Status.OK, cheque, null);
    }

    public static V2LoadResult notFound(String chequeNo) {
        return new V2LoadResult(Status.NOT_FOUND, null,
            "Cheque " + chequeNo + " was not found in the system.");
    }

    public static V2LoadResult alreadyAccepted(String chequeNo) {
        return new V2LoadResult(Status.ALREADY_ACCEPTED, null,
            "Cheque " + chequeNo + " is already approved. No further action needed.");
    }

    public static V2LoadResult alreadyRejected(String chequeNo) {
        return new V2LoadResult(Status.ALREADY_REJECTED, null,
            "Cheque " + chequeNo + " is already rejected.");
    }

    public static V2LoadResult returnedToMaker(String chequeNo) {
        return new V2LoadResult(Status.RETURNED_TO_MAKER, null,
            "Cheque " + chequeNo + " was returned to maker for correction. Waiting for resubmission.");
    }

    public static V2LoadResult wrongRoute(String chequeNo) {
        return new V2LoadResult(Status.WRONG_ROUTE, null,
            "Cheque " + chequeNo + " is routed to Verifier 1 (Checker), not Verifier 2 (Branch Manager).");
    }

    public static V2LoadResult wrongStatus(String chequeNo, String actualStatus) {
        return new V2LoadResult(Status.WRONG_STATUS, null,
            "Cheque " + chequeNo + " cannot be reviewed. Current status: " + actualStatus + ".");
    }

    // ── Getters ────────────────────────────────────────────────────────────

    public Status       getStatus()  { return status; }
    public InwardCheque getCheque()  { return cheque; }
    public String       getMessage() { return message; }
    public boolean      isOk()       { return status == Status.OK; }
}
