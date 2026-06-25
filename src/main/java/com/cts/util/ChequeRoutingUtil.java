package com.cts.util;

import com.cts.inward.enums.SendTo;

/**
 * Util Class : ChequeRoutingUtil
 * Package    : com.cts.util
 *
 * SINGLE SOURCE OF TRUTH for the TV_1 / TV_2 routing rule.
 *
 * WHY THIS CLASS EXISTS:
 * ─────────────────────────────────────────────────────────────────
 * The ₹1,00,000 threshold used to be hardcoded in TWO separate places:
 *   1. InwardChequeServiceImpl.determineCheckerRoute() — the REAL routing
 *      decision that gets saved to the DB.
 *   2. MakerCbsValidator.handlePass() — a SECOND, independent copy of the
 *      same threshold, only used to show the destination in the CBS popup.
 *
 * Because these were two separate copies, a future change to the rule
 * (like this one) could easily be applied to only one of them — the popup
 * would then show one destination while the DB saved a different one.
 *
 * Both places now call routeFor(amount) here instead. There is exactly
 * ONE place to change the rule.
 *
 * CURRENT RULE:
 *   amount <= ₹1,00,000        → TV_1 (Checker / Verifier 1)
 *   amount >  ₹1,00,000        → TV_2 (Branch Manager / Verifier 2)
 *
 * (Previously: amount >= ₹1,00,000 went to TV_2. A cheque for exactly
 *  ₹1,00,000 now goes to TV_1 instead.)
 */
public final class ChequeRoutingUtil {

    /** Cheques at or below this amount go to TV_1. Above it, TV_2. */
    public static final double HIGH_VALUE_THRESHOLD = 100000.0; // ₹1,00,000

    private ChequeRoutingUtil() {
        // Utility class — never instantiated
    }

    /**
     * Decides which verifier a cheque should be routed to, based on amount.
     *
     * @param amount the cheque amount (must not be null)
     * @return SendTo.TV_1 if amount <= ₹1,00,000, otherwise SendTo.TV_2
     */
    public static SendTo routeFor(double amount) {
        return (amount > HIGH_VALUE_THRESHOLD) ? SendTo.TV_2 : SendTo.TV_1;
    }

    /**
     * Same decision, returned as the plain destination code used in
     * popup/UI text ("TV_1" / "TV_2") instead of the enum itself —
     * convenient for places that only need the string, not the enum.
     */
    public static String routeForAsString(double amount) {
        return routeFor(amount).name();
    }
}