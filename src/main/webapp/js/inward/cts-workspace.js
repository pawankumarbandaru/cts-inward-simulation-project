/**
 * File     : cts-workspace.js
 * Location : src/main/webapp/js/inward/cts-workspace.js
 *
 * JavaScript for the Checker Review Workspace, V2 Review Workspace,
 * and Maker Repair Workspace.
 * Loaded via <script src="/js/inward/cts-workspace.js" defer="true"/>
 * in checkerReviewWorkspace.zul and verifier2ReviewWorkspace.zul.
 *
 * Contains:
 *   CTS_CHECKER_MODAL            — highlights edited fields and images
 *   initCheckerRemarksDropdown() — show/hide checker "Other" textarea
 *   initMakerRemarksDropdown()   — show/hide maker "Other" textarea
 *   initV2RemarksDropdown()      — show/hide V2 (Branch Manager) "Other" textarea
 */

// ── CTS_CHECKER_MODAL ──────────────────────────────────────────────────────
// Called by CheckerReviewWorkspaceComposer and V2ReviewWorkspaceComposer
// via Clients.evalJavaScript() to highlight edited fields and images.

var CTS_CHECKER_MODAL = (function () {
    return {

        /**
         * Adds highlight glow to the cheque image wrapper.
         * Called by composer when isEditedByMaker == true.
         */
        highlightEditedImage: function () {
            var realWrapper = document.getElementById('chq-real-wrapper');
            if (realWrapper) realWrapper.classList.add('chq-image-edited-highlight');
            var placeholder = document.getElementById('chq-img-placeholder');
            if (placeholder) placeholder.classList.add('chq-image-edited-highlight');
            var banner = document.getElementById('chk-edited-fields-banner');
            if (banner) banner.style.display = 'flex';
        },

        /**
         * Highlights each field that the maker edited.
         * Called by composer: CTS_CHECKER_MODAL.highlightEditedFields('cheque_no,amount')
         * Maps DB column names to ZUL widget IDs and adds yellow border + badge.
         *
         * Works for both chk- and v2- workspace pages — both use rcb-f-* field IDs
         * from the shared macro components.
         *
         * @param {string} fieldsCsv  comma-separated DB column names
         */
        highlightEditedFields: function (fieldsCsv) {
            // Map: DB column name → ZUL widget ID (or array of IDs)
            var fieldMap = {
                'cheque_no'        : 'rcb-f-chqno',
                'amount'           : 'rcb-f-amount',
                'micr_code'        : ['rcb-f-city', 'rcb-f-bank', 'rcb-f-branch'],
                'micr'             : ['rcb-f-city', 'rcb-f-bank', 'rcb-f-branch'],
                'transaction_code' : 'rcb-f-tc',
                'account_no'       : 'rcb-f-acc',
                'payee_name'       : 'rcb-f-payee',
                'cheque_date'      : 'rcb-f-date'
            };

            // Friendly display names for the banner chips
            var labelMap = {
                'cheque_no'        : 'Cheque No.',
                'amount'           : 'Amount',
                'micr_code'        : 'MICR Code (City / Bank / Branch)',
                'micr'             : 'MICR Code (City / Bank / Branch)',
                'transaction_code' : 'Transaction Code',
                'account_no'       : 'Account No.',
                'payee_name'       : 'Payee Name',
                'cheque_date'      : 'Cheque Date'
            };

            if (!fieldsCsv || fieldsCsv.trim() === '') return;

            var fields = fieldsCsv.split(',');
            var displayNames = [];

            fields.forEach(function (f) {
                f = f.trim();
                var ids = fieldMap[f];
                if (!ids) return;

                if (!Array.isArray(ids)) ids = [ids];

                ids.forEach(function (id) {
                    var wrapper = document.getElementById(id);
                    if (!wrapper) return;
                    wrapper.classList.add('chk-field-edited');

                    if (!wrapper.querySelector('.chk-edited-badge')) {
                        var badge = document.createElement('span');
                        badge.className = 'chk-edited-badge';
                        badge.textContent = '✏ Edited';
                        wrapper.insertBefore(badge, wrapper.firstChild);
                    }
                });

                if (labelMap[f]) displayNames.push(labelMap[f]);
            });

            // Show banner and populate chip list
            var banner = document.getElementById('chk-edited-fields-banner')
                      || document.getElementById('v2-edited-fields-banner');
            if (banner) banner.style.display = 'flex';

            var listLabel = document.getElementById('chk-edited-fields-list')
                         || document.getElementById('v2-edited-fields-list');
            if (listLabel && displayNames.length > 0) {
                listLabel.innerHTML = displayNames.map(function (n) {
                    return '<span class="chk-edited-chip">' + n + '</span>';
                }).join(' ');
            }

            var realWrapper = document.getElementById('chq-real-wrapper');
            if (realWrapper) realWrapper.classList.add('chq-image-edited-highlight');
            var placeholder = document.getElementById('chq-img-placeholder');
            if (placeholder) placeholder.classList.add('chq-image-edited-highlight');
        }

    };
}());

// ── Checker (Verifier 1) remarks dropdown ──────────────────────────────────
// Shows/hides the "Other" free-text box when checker selects "Other".
// Uses element IDs: chk-action-remarks, chk-remarks-other-wrap

function initCheckerRemarksDropdown() {
    var sel   = document.getElementById('chk-action-remarks');
    var other = document.getElementById('chk-remarks-other-wrap');
    if (!sel || !other) {
        setTimeout(initCheckerRemarksDropdown, 200);
        return;
    }
    var inp = sel.querySelector('input') || sel;
    function check() {
        var v = inp.value || '';
        other.style.display = (v.trim() === 'Other') ? 'block' : 'none';
    }
    inp.addEventListener('input',  check);
    inp.addEventListener('change', check);
    sel.addEventListener('click',  function () { setTimeout(check, 50); });
}

// ── Maker remarks dropdown ─────────────────────────────────────────────────
// Shows/hides the "Other" free-text box when maker selects "Other".
// Uses element IDs: rcb-f-remarks-select, rcb-remarks-other-wrap

function initMakerRemarksDropdown() {
    var sel      = document.getElementById('rcb-f-remarks-select');
    var otherBox = document.getElementById('rcb-remarks-other-wrap');
    if (!sel || !otherBox) {
        setTimeout(initMakerRemarksDropdown, 200);
        return;
    }
    sel.addEventListener('change', function () {
        var val = sel.value;
        otherBox.style.display = (val === 'OTHER') ? 'block' : 'none';
    });
}

// ── V2 (Branch Manager) remarks dropdown ───────────────────────────────────
// Shows/hides the "Other" free-text box when V2 reviewer selects "Other".
// Uses element IDs: v2-action-remarks, v2-remarks-other-wrap

function initV2RemarksDropdown() {
    var sel   = document.getElementById('v2-action-remarks');
    var other = document.getElementById('v2-remarks-other-wrap');
    if (!sel || !other) {
        setTimeout(initV2RemarksDropdown, 200);
        return;
    }
    var inp = sel.querySelector('input') || sel;
    function check() {
        var v = inp.value || '';
        other.style.display = (v.trim() === 'Other') ? 'block' : 'none';
    }
    inp.addEventListener('input',  check);
    inp.addEventListener('change', check);
    sel.addEventListener('click',  function () { setTimeout(check, 50); });
}


// ── Init on page load ──────────────────────────────────────────────────────
// Each function checks for its target element — returns safely if not found.
// So all functions can run on every workspace page without errors.

window.addEventListener('load', function () {
    initCheckerRemarksDropdown();
    initMakerRemarksDropdown();
    initV2RemarksDropdown();
    initDateboxPopupFix();
});

// ── Datebox calendar popup fix (inside modal repair/review workspaces) ────
//
// PROBLEM: Clicking a date field inside the modal popup (Maker Repair
// Workspace, Checker Review, Verifier 2 Review) opens ZK's calendar
// dropdown, which gets appended directly to <body> rather than nested
// inside the modal's own DOM. Without any width constraint it rendered
// wide enough to visually cover the whole modal/page — see Ramana's
// screenshot, 17 Jun 2026.
//
// Why a CSS-only fix wasn't reliable: ZK's internal class name for this
// popup wrapper differs across versions/molds (z-calendar-popup in some
// builds, plain z-calendar in others, sometimes just an unlabeled
// position:fixed div) and could not be confirmed against this exact ZK
// 10.1 build without inspecting the live rendered DOM. Rather than guess
// a class name again, this uses a MutationObserver to catch whatever
// element ZK actually appends to <body> right after a datebox button is
// clicked, and clamps it — independent of class naming.
//
// Detection logic: only treats a newly-appended <body> child as "the
// calendar popup" if (a) it appeared within ~400ms of a .z-datebox-btn
// click, and (b) it's wider than a calendar plausibly needs (300px) —
// avoiding any chance of mis-targeting an unrelated ZK popup (e.g. a
// combobox dropdown) that might also get appended to body around the
// same time.
function initDateboxPopupFix() {
    if (window.__ctsDateboxPopupFixInstalled) return;
    window.__ctsDateboxPopupFixInstalled = true;

    var lastDateboxBtnClickAt = 0;
    var lastClickedBtn = null;

    document.addEventListener('click', function (ev) {
        var btn = ev.target.closest && ev.target.closest('.z-datebox-btn');
        if (btn) {
            lastDateboxBtnClickAt = Date.now();
            lastClickedBtn = btn;
        }
    }, true);

    var observer = new MutationObserver(function (mutations) {
        if (Date.now() - lastDateboxBtnClickAt > 500) return;

        mutations.forEach(function (m) {
            m.addedNodes && m.addedNodes.forEach(function (node) {
                if (node.nodeType !== 1) return;       // only element nodes
                if (node.parentNode !== document.body) return;

                var rect = node.getBoundingClientRect();
                if (rect.width < 300) return;           // too narrow to be the offending popup

                // Clamp width/position so it sits as a normal dropdown
                // below the date field that opened it, instead of
                // stretching across the page.
                node.style.maxWidth   = '300px';
                node.style.width      = 'auto';
                node.style.boxShadow  = '0 8px 24px rgba(0,0,0,.25)';
                node.style.borderRadius = '6px';

                if (lastClickedBtn) {
                    var btnRect = lastClickedBtn.getBoundingClientRect();
                    node.style.position = 'fixed';
                    node.style.top  = (btnRect.bottom + 4) + 'px';
                    node.style.left = btnRect.left + 'px';
                }
            });
        });
    });

    observer.observe(document.body, { childList: true });
}