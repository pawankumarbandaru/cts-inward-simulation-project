package com.cts.composer;

import org.zkoss.zk.ui.Component;
import org.zkoss.zk.ui.HtmlBasedComponent;
import org.zkoss.zk.ui.event.Event;
import org.zkoss.zk.ui.select.SelectorComposer;
import org.zkoss.zk.ui.select.annotation.Listen;
import org.zkoss.zk.ui.select.annotation.Wire;
import org.zkoss.zk.ui.util.Clients;
import org.zkoss.zul.Div;
import org.zkoss.zul.Label;

/**
 * SidebarComposer — Inward ClearPay CTS
 *
 * Upgraded to match Outward project:
 *   - Collapse toggle button (☰) to shrink sidebar to icon-only mode
 *   - Accordion expand/collapse for Inward Clearing and Inward Reports sections
 *   - Active state tracking for clicked menu items
 *   - Page navigation via DashboardComposer (existing inward pattern)
 *
 * NOTE: page navigation uses pagePath attribute + DashboardComposer.getInstance().loadPage()
 *       which is the existing inward project pattern — NOT changed.
 */
public class SidebarComposer extends SelectorComposer<Component> {

    private static final long serialVersionUID = 1L;

    // ── TOGGLE BUTTON ─────────────────────────────────────────────
    @Wire("#sidebarToggle")
    private Div sidebarToggle;

    // ── INWARD CLEARING accordion ──────────────────────────────────
    @Wire("#inwardHeader")
    private Div inwardHeader;

    @Wire("#inwardMenu")
    private Div inwardMenu;

    @Wire("#inwardArrow")
    private Label inwardArrow;

    // ── INWARD REPORTS accordion ───────────────────────────────────
    @Wire("#inwardReportsHeader")
    private Div inwardReportsHeader;

    @Wire("#inwardReportsMenu")
    private Div inwardReportsMenu;

    @Wire("#inwardReportsArrow")
    private Label inwardReportsArrow;
    
    @Wire("#makerReturnBadge")
    private Label makerReturnBadge;

    // ── STATE ──────────────────────────────────────────────────────
    private boolean isCollapsed = false;

    // ══════════════════════════════════════════════════════════════
    // INIT
    // ══════════════════════════════════════════════════════════════

    @Override
    public void doAfterCompose(Component comp) throws Exception {
        super.doAfterCompose(comp);


     // Accordion removed — both submenus stay permanently open (no toggle).
        if (inwardArrow != null) {
            inwardArrow.setValue("▼");
        }
        if (inwardMenu != null) {
            inwardMenu.setVisible(true);
        }
        if (inwardReportsArrow != null) {
            inwardReportsArrow.setValue("▼");
        }
        if (inwardReportsMenu != null) {
            inwardReportsMenu.setVisible(true);
        }
        refreshReturnBadge();
        
        org.zkoss.zk.ui.event.EventQueues
        .lookup("chequeStatusUpdated", org.zkoss.zk.ui.event.EventQueues.DESKTOP, true)
        .subscribe(event -> refreshReturnBadge());
    }
    
    
    private void refreshReturnBadge() {
        try {
            long count = new com.cts.inward.service.InwardCountService()
                    .getMakerPendingCount();
            if (makerReturnBadge != null) {
                if (count > 0) {
                    makerReturnBadge.setValue(String.valueOf(count));
                    makerReturnBadge.setVisible(true);
                } else {
                    makerReturnBadge.setValue("");
                    makerReturnBadge.setVisible(false);   // ← hides on zero
                }
            }
        } catch (Exception e) {
            System.err.println("SidebarComposer: badge refresh failed - " + e.getMessage());
        }
    }
    // ══════════════════════════════════════════════════════════════
    // COLLAPSE TOGGLE
    // ══════════════════════════════════════════════════════════════

    @Listen("onClick = #sidebarToggle")
    public void onToggleSidebar() {
        if (isCollapsed) {
            expandSidebar();
        } else {
            collapseSidebar();
        }
    }

    private void collapseSidebar() {
        // Close all open submenus before collapsing
        closeAllSubmenus(getSelf());

        Component root = getSelf();
        String currentSclass = getSclass(root);
        setSclass(root, (currentSclass + " collapsed").trim());

        // Shrink sidebar width via JavaScript
        updateSidebarWidth("70px");
        isCollapsed = true;
    }

    private void expandSidebar() {
        Component root = getSelf();
        String currentSclass = getSclass(root);
        setSclass(root, currentSclass.replace(" collapsed", "").trim());

        // Restore full sidebar width via JavaScript
        updateSidebarWidth("240px");
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

    /**
     * Wires a header div to toggle its submenu open/closed on click.
     * If sidebar is collapsed, clicking first expands it then opens the submenu.
     */
    private void wireAccordion(Div header, Div menu, Label arrow) {
        if (header == null || menu == null || arrow == null) {
            return;
        }

        header.addEventListener("onClick", event -> {
            // If sidebar is collapsed, expand it first
            if (isCollapsed) {
                expandSidebar();
            }

            boolean isOpen = menu.isVisible();
            menu.setVisible(!isOpen);
            arrow.setValue(isOpen ? "▶" : "▼");
        });
    }

    // ══════════════════════════════════════════════════════════════
    // NAVIGATION — same pattern as existing inward project
    // ══════════════════════════════════════════════════════════════

    @Listen("onClick=.cts-menu-item")
    public void onMenuItemClick(Event event) {
        Component target = event.getTarget();

        // Walk up the component tree to find the node that has the pagePath attribute
        while (target != null && target.getAttribute("pagePath") == null) {
            target = target.getParent();
        }

        if (target == null) {
            return;
        }

        String pagePath = target.getAttribute("pagePath").toString();

        // Update active state styling
        clearActiveState(getSelf());
        setSclass(target, (getSclass(target) + " active").trim());

        // Navigate using the existing inward pattern
        DashboardComposer.getInstance().loadPage(pagePath);
    }

    // ══════════════════════════════════════════════════════════════
    // HELPER UTILITIES
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
            ? hbc.getSclass()
            : "";
    }

    private void setSclass(Component comp, String sclass) {
        if (comp instanceof HtmlBasedComponent hbc) {
            hbc.setSclass(sclass);
        }
    }
}
