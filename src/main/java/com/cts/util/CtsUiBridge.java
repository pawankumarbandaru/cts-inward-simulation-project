package com.cts.util;

import org.zkoss.zk.ui.util.Clients;

/**
 * Utility  : CtsUiBridge
 * Package  : com.cts.util
 *
 * Wraps all browser-side JavaScript calls made from ZK composers.
 * Composers call these methods instead of building raw JS strings inline.
 * JavaScript function names and DOM IDs are managed here — not scattered
 * across multiple composers.
 */
public final class CtsUiBridge {

    /**
     * Injects a semi-transparent backdrop into the browser DOM.
     * Used to dim the queue page behind the review popup window.
     *
     * @param backdropId HTML id to assign to the backdrop element
     */
    public static void showBackdrop(String backdropId) {
        Clients.evalJavaScript(
            "var bd = document.createElement('div');" +
            "bd.id = '" + backdropId + "';" +
            "bd.style.cssText = 'position:fixed;top:0;left:0;width:100%;height:100%;" +
                "background:rgba(0,0,0,0.55);backdrop-filter:blur(2px);z-index:999;';" +
            "document.body.appendChild(bd);"
        );
    }

    /**
     * Removes the backdrop element from the browser DOM.
     *
     * @param backdropId HTML id of the backdrop element to remove
     */
    public static void removeBackdrop(String backdropId) {
        Clients.evalJavaScript(
            "var bd = document.getElementById('" + backdropId + "');" +
            "if (bd) bd.parentNode.removeChild(bd);"
        );
    }

    /**
     * Sets the innerHTML of a native HTML element by its DOM id.
     *
     * WHY THIS EXISTS:
     *   ZK's <html> component (Html class) uses a client-side "stub" to mount.
     *   Calling Html.setContent() in the same server response as heavy ZK tree
     *   operations (clearing/rebuilding a Listbox or Rows) causes the stub to be
     *   orphaned -> "Failed to mount: Unknown stub" error.
     *
     *   The fix: use a plain <n:div id="..."> in the ZUL (no ZK stub),
     *   and set its text here via a simple JavaScript innerHTML call.
     *   No stub involved — no conflict.
     *
     * @param nativeDivId  the HTML id of the <n:div> in the ZUL (e.g., "v2-success-text")
     * @param htmlContent  the HTML string to set as the div's innerHTML
     */
    public static void setBannerText(String nativeDivId, String htmlContent) {
        if (nativeDivId == null || htmlContent == null) return;
        // Escape single quotes in the content to avoid breaking the JS string
        String safe = htmlContent.replace("'", "\\'");
        Clients.evalJavaScript(
            "var el = document.getElementById('" + nativeDivId + "');" +
            "if (el) el.innerHTML = '" + safe + "';"
        );
    }

    /**
     * Highlights the fields that the maker edited, so the checker can
     * immediately see what changed. Calls CTS_CHECKER_MODAL.highlightEditedFields()
     * defined in cts-workspace.js.
     *
     * @param fieldsCsv comma-separated DB column names e.g. "cheque_no,amount"
     */
    public static void highlightCheckerEditedFields(String fieldsCsv) {
        if (fieldsCsv == null || fieldsCsv.isBlank()) return;
        // RACE CONDITION FIX:
        // This call can run before cts-workspace.js (an external <script src>)
        // has finished loading, since both the script tag and this eval call
        // are injected in the same AU response when the popup opens. Calling
        // CTS_CHECKER_MODAL directly here would silently fail with a
        // "CTS_CHECKER_MODAL is not defined" error if the script isn't ready yet.
        // Fix: poll briefly until the object exists, then call it.
        Clients.evalJavaScript(
            "(function waitForCtsModal(tries) {" +
            "  if (window.CTS_CHECKER_MODAL && typeof window.CTS_CHECKER_MODAL.highlightEditedFields === 'function') {" +
            "    window.CTS_CHECKER_MODAL.highlightEditedFields('" + fieldsCsv + "');" +
            "  } else if (tries > 0) {" +
            "    setTimeout(function () { waitForCtsModal(tries - 1); }, 50);" +
            "  }" +
            "})(20);"
        );
    }

    /**
     * Server-side replacement for highlightCheckerEditedFields().
     *
     * Highlights maker-edited fields directly on the ZK components by adding
     * the "chk-field-edited" CSS class via setSclass() — no client JavaScript,
     * no getElementById() lookups, no script-load timing, no risk of duplicate
     * DOM ids picking the wrong element. The component reference IS the field,
     * so there's nothing to "find".
     *
     * The "✏ Edited" badge is rendered purely via a CSS ::after pseudo-element
     * on .chk-field-edited (see checkerStyles.css) — also no JS needed.
     *
     * @param fieldsCsv   comma-separated DB column names, e.g. "amount,micr_code"
     *                    (also accepts the shorter "micr" alias for MICR fields)
     * @param chequeNo    wired #rcb-f-chqno component (nullable)
     * @param city        wired #rcb-f-city component (nullable)
     * @param bank        wired #rcb-f-bank component (nullable)
     * @param branch      wired #rcb-f-branch component (nullable)
     * @param tc          wired #rcb-f-tc component (nullable)
     * @param amount      wired #rcb-f-amount component (nullable)
     * @param date        wired #rcb-f-date component (nullable)
     * @param acc         wired #rcb-f-acc component (nullable)
     * @param payee       wired #rcb-f-payee component (nullable)
     */
    public static void applyEditedFieldHighlight(
            String fieldsCsv,
            org.zkoss.zk.ui.HtmlBasedComponent chequeNo,
            org.zkoss.zk.ui.HtmlBasedComponent city,
            org.zkoss.zk.ui.HtmlBasedComponent bank,
            org.zkoss.zk.ui.HtmlBasedComponent branch,
            org.zkoss.zk.ui.HtmlBasedComponent tc,
            org.zkoss.zk.ui.HtmlBasedComponent amount,
            org.zkoss.zk.ui.HtmlBasedComponent date,
            org.zkoss.zk.ui.HtmlBasedComponent acc,
            org.zkoss.zk.ui.HtmlBasedComponent payee) {

        if (fieldsCsv == null || fieldsCsv.isBlank()) return;

        for (String raw : fieldsCsv.split(",")) {
            String field = raw.trim();
            switch (field) {
                case "cheque_no":
                    addEditedClass(chequeNo);
                    break;
                case "amount":
                    addEditedClass(amount);
                    break;
                case "micr_code":
                case "micr":
                    addEditedClass(city);
                    addEditedClass(bank);
                    addEditedClass(branch);
                    break;
                case "transaction_code":
                    addEditedClass(tc);
                    break;
                case "account_no":
                    addEditedClass(acc);
                    break;
                case "payee_name":
                    addEditedClass(payee);
                    break;
                case "cheque_date":
                    addEditedClass(date);
                    break;
                default:
                    // Unknown key — ignore rather than fail the whole page.
                    break;
            }
        }
    }

    /**
     * Adds "chk-field-edited" to a component's sclass (for the ✏ badge) AND
     * applies the highlight colors directly via setStyle() so they are
     * guaranteed to render regardless of CSS load order or the field's
     * disabled state (checker/V2 fields are always disabled, and disabled
     * inputs can pick up theme-level background/border styling that beats
     * a plain CSS class on the wrapper).
     */
    private static void addEditedClass(org.zkoss.zk.ui.HtmlBasedComponent field) {
        if (field == null) return;

        String existing = field.getSclass();
        if (existing == null || !existing.contains("chk-field-edited")) {
            field.setSclass(existing == null || existing.isBlank()
                    ? "chk-field-edited"
                    : existing + " chk-field-edited");
        }

        // Direct inline style on the wrapper — wins over any disabled-state
        // or theme CSS without depending on selector specificity.
        String existingStyle = field.getStyle();
        String highlightStyle =
                "border:1px solid #F59E0B !important;"
              + "background-color:#FFFBEB !important;"
              + "box-shadow:0 0 0 2px rgba(245,158,11,0.25) !important;";
        field.setStyle(existingStyle == null || existingStyle.isBlank()
                ? highlightStyle
                : existingStyle + ";" + highlightStyle);
    }

    /**
     * Scrolls a CSS-class-selected element to the top.
     * Used to bring validation errors into view after a failed save.
     *
     * @param cssClass the CSS class of the scroll container
     */
    public static void scrollToTop(String cssClass) {
        Clients.evalJavaScript(
            "var el = document.querySelector('." + cssClass + "');" +
            "if (el) el.scrollTop = 0;"
        );
    }

    /** Utility class — no instances. */
    private CtsUiBridge() {}
}