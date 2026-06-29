package com.cts.composer;

import org.zkoss.zk.ui.Component;
import org.zkoss.zk.ui.HtmlBasedComponent;
import org.zkoss.zk.ui.event.Event;
import org.zkoss.zk.ui.select.SelectorComposer;
import org.zkoss.zk.ui.select.annotation.Listen;
import org.zkoss.zk.ui.select.annotation.Wire;
import org.zkoss.zul.Div;
import org.zkoss.zul.Label;
import org.zkoss.zk.ui.util.Clients;

/**
 * CheckerSidebarComposer — TV1 Sidebar
 *
 * UPGRADED to match outward project sidebar:
 *   - Collapse toggle button (☰) to shrink/expand sidebar
 *   - Accordion expand/collapse for Inward Clearing section
 *   - Active state tracking when menu item is clicked
 *   - Page navigation via CheckerDashboardComposer (existing pattern unchanged)
 */
public class CheckerSidebarComposer extends SelectorComposer<Component> {

    private static final long serialVersionUID = 1L;

    // ── TOGGLE BUTTON ─────────────────────────────────────────────
    @Wire("#checkerSidebarToggle")
    private Div checkerSidebarToggle;

    // ── INWARD CLEARING accordion ──────────────────────────────────
    @Wire("#tv1InwardHeader")
    private Div tv1InwardHeader;

    @Wire("#tv1InwardMenu")
    private Div tv1InwardMenu;

    @Wire("#tv1InwardArrow")
    private Label tv1InwardArrow;
    
    @Wire("#tv1ResubmittedBadge")
    private Label tv1ResubmittedBadge;

    // ── STATE ──────────────────────────────────────────────────────
    private boolean isCollapsed = false;

    // ══════════════════════════════════════════════════════════════
    // INIT
    // ══════════════════════════════════════════════════════════════

    @Override
    public void doAfterCompose(Component comp) throws Exception {
        super.doAfterCompose(comp);

     // Keep the Inward Clearing submenu always open so the count
        // badges are always visible. No accordion toggle needed.
        if (tv1InwardArrow != null) {
            tv1InwardArrow.setValue("▼");
        }
        if (tv1InwardMenu != null) {
            tv1InwardMenu.setVisible(true);
        }


        // Show the badge count immediately on page load.
        refreshResubmittedBadge();

     // Re-run the badge count whenever any cheque status changes.
        org.zkoss.zk.ui.event.EventQueues
            .lookup("chequeStatusUpdated", org.zkoss.zk.ui.event.EventQueues.DESKTOP, true)
            .subscribe(event -> refreshResubmittedBadge());
    }
    
    
    
    
    /** Loads the TV1 resubmitted count and shows it as a badge. */
    private void refreshResubmittedBadge() {
        try {
            long count = new com.cts.inward.service.InwardCountService()
                    .getTv1ResubmittedCount();
            if (tv1ResubmittedBadge != null) {
                if (count > 0) {
                    tv1ResubmittedBadge.setValue(String.valueOf(count));
                    tv1ResubmittedBadge.setVisible(true);
                }  else {
                    tv1ResubmittedBadge.setValue("");
                    tv1ResubmittedBadge.setVisible(false);
                }
            }
        } catch(Exception e) {
            System.err.println("CheckerSidebarComposer: badge refresh failed - " + e.getMessage());
        }
    }

    // ══════════════════════════════════════════════════════════════
    // COLLAPSE TOGGLE
    // ══════════════════════════════════════════════════════════════

    @Listen("onClick = #checkerSidebarToggle")
    public void onToggleSidebar() {
        if (isCollapsed) {
            expandSidebar();
        } else {
            collapseSidebar();
        }
    }

    private void collapseSidebar() {
        // Close all submenus before collapsing
        closeAllSubmenus(getSelf());

        Component root = getSelf();
        String current = getSclass(root);
        setSclass(root, (current + " collapsed").trim());

        updateSidebarWidth("70px");
        isCollapsed = true;
    }

    private void expandSidebar() {
        Component root = getSelf();
        String current = getSclass(root);
        setSclass(root, current.replace(" collapsed", "").trim());

        updateSidebarWidth("260px");
        isCollapsed = false;
    }

    private void closeAllSubmenus(Component parent) {
        for (Component child : parent.getChildren()) {
            String sclass = getSclass(child);

            if (sclass.contains("cts-submenu")) {
                ((HtmlBasedComponent) child).setVisible(false);
            }

            if (child instanceof Label lbl && sclass.contains("cts-arrow")) {
                lbl.setValue("▶");
            }

            closeAllSubmenus(child);
        }
    }

    private void updateSidebarWidth(String width) {
        // Collapse disabled — width is fixed via CSS (.z-west / .cts-sidebar).
        // No JavaScript width changes, so the layout can never break.
    }

    // ══════════════════════════════════════════════════════════════
    // ACCORDION
    // ══════════════════════════════════════════════════════════════

    private void wireAccordion(Div header, Div menu, Label arrow) {
        if (header == null || menu == null || arrow == null) return;

        header.addEventListener("onClick", event -> {
            // Expand sidebar first if collapsed
            if (isCollapsed) {
                expandSidebar();
            }

            boolean isOpen = menu.isVisible();
            menu.setVisible(!isOpen);
            arrow.setValue(isOpen ? "▶" : "▼");
        });
    }

    // ══════════════════════════════════════════════════════════════
    // NAVIGATION — existing pattern unchanged
    // ══════════════════════════════════════════════════════════════

    @Listen("onClick=.cts-menu-item")
    public void navigate(Event event) {
        Component target = event.getTarget();

        // Walk up to find the component that has pagePath attribute
        while (target != null && target.getAttribute("pagePath") == null) {
            target = target.getParent();
        }
        if (target == null) return;

        String pagePath = target.getAttribute("pagePath").toString();
        if (pagePath == null || pagePath.trim().isEmpty()) return;

        // Update active styling
        clearActiveState(getSelf());
        setSclass(target, (getSclass(target) + " active").trim());

        // Navigate via CheckerDashboardComposer
        CheckerDashboardComposer.getInstance().loadPage(pagePath);
    }

    // ══════════════════════════════════════════════════════════════
    // HELPERS
    // ══════════════════════════════════════════════════════════════

    private void clearActiveState(Component parent) {
        String sclass = getSclass(parent);
        if (sclass.contains("cts-menu-item")) {
            setSclass(parent, sclass.replace("active", "").trim());
        }
        for (Component child : parent.getChildren()) {
            clearActiveState(child);
        }
    }

    private String getSclass(Component comp) {
        return (comp instanceof HtmlBasedComponent hbc && hbc.getSclass() != null)
            ? hbc.getSclass() : "";
    }

    private void setSclass(Component comp, String sclass) {
        if (comp instanceof HtmlBasedComponent hbc) {
            hbc.setSclass(sclass);
        }
    }
}
