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
 * TV2SidebarComposer — TV2 Sidebar
 *
 * UPGRADED to match outward project sidebar:
 *   - Collapse toggle button (☰) to shrink/expand sidebar
 *   - Accordion expand/collapse for Inward Clearing section
 *   - Active state tracking when menu item is clicked
 *   - Page navigation via TV2DashboardComposer (existing pattern unchanged)
 */
public class TV2SidebarComposer extends SelectorComposer<Component> {

    private static final long serialVersionUID = 1L;

    // ── TOGGLE BUTTON ─────────────────────────────────────────────
    @Wire("#tv2SidebarToggle")
    private Div tv2SidebarToggle;

    // ── INWARD CLEARING accordion ──────────────────────────────────
    @Wire("#tv2InwardHeader")
    private Div tv2InwardHeader;

    @Wire("#tv2InwardMenu")
    private Div tv2InwardMenu;

    @Wire("#tv2InwardArrow")
    private Label tv2InwardArrow;
    
    @Wire("#tv2ResubmittedBadge")
    private Label tv2ResubmittedBadge;
    
    @Wire("#tv2ReferredBadge")
    private Label tv2ReferredBadge;

    // ── STATE ──────────────────────────────────────────────────────
    private boolean isCollapsed = false;

    // ══════════════════════════════════════════════════════════════
    // INIT
    // ══════════════════════════════════════════════════════════════

    @Override
    public void doAfterCompose(Component comp) throws Exception {
        super.doAfterCompose(comp);

        if (tv2InwardArrow != null) {
            tv2InwardArrow.setValue("▼");
        }
        if (tv2InwardMenu != null) {
            tv2InwardMenu.setVisible(true);
        }

        refreshResubmittedBadge();
        
        org.zkoss.zk.ui.event.EventQueues
        .lookup("chequeStatusUpdated", org.zkoss.zk.ui.event.EventQueues.DESKTOP, true)
        .subscribe(event -> refreshResubmittedBadge());
        
     // Show live count badges on load
        refreshBadges();

        // Refresh badges whenever any cheque status changes
        org.zkoss.zk.ui.event.EventQueues
            .lookup("chequeStatusUpdated", org.zkoss.zk.ui.event.EventQueues.DESKTOP, true)
            .subscribe(event -> refreshBadges());
    }
    
    private void refreshResubmittedBadge() {
        try {
            long count = new com.cts.inward.service.InwardCountService()
                    .getTv2ResubmittedCount();
            if (tv2ResubmittedBadge != null) {
                if (count > 0) {
                    tv2ResubmittedBadge.setValue(String.valueOf(count));
                    tv2ResubmittedBadge.setVisible(true);
                } else {
                    tv2ResubmittedBadge.setVisible(false);
                }
            }
        } catch (Exception e) {
            System.err.println("TV2SidebarComposer: badge refresh failed - " + e.getMessage());
        }
    }
    
    /** Loads TV2 resubmitted + referred counts and shows them as red badges. */
    private void refreshBadges() {
        try {
            com.cts.inward.service.InwardCountService countService =
                    new com.cts.inward.service.InwardCountService();

            setBadge(tv2ResubmittedBadge, countService.getTv2ResubmittedCount());
            setBadge(tv2ReferredBadge,    countService.getTv2ReferredCount());
        } catch (Exception e) {
            System.err.println("TV2SidebarComposer: badge refresh failed - " + e.getMessage());
        }
    }

    /** Shows the badge with the count, or HIDES it completely when zero. */
    private void setBadge(Label badge, long count) {
        if (badge == null) return;
        if (count > 0) {
            badge.setValue(String.valueOf(count));
            badge.setVisible(true);
        } else {
            badge.setValue("");          // clear any old value
            badge.setVisible(false);     // hide completely — no empty red dot
        }
    }

    // ══════════════════════════════════════════════════════════════
    // COLLAPSE TOGGLE
    // ══════════════════════════════════════════════════════════════

    @Listen("onClick = #tv2SidebarToggle")
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

        // Navigate via TV2DashboardComposer
        TV2DashboardComposer.getInstance().loadPage(pagePath);
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
