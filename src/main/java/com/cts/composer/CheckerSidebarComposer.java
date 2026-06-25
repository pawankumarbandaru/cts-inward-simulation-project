package com.cts.composer;

import org.zkoss.zk.ui.Component;
import org.zkoss.zk.ui.event.Event;
import org.zkoss.zk.ui.select.SelectorComposer;
import org.zkoss.zk.ui.select.annotation.Listen;
import org.zkoss.zk.ui.select.annotation.Wire;
import org.zkoss.zul.Div;
import org.zkoss.zul.Label;

public class CheckerSidebarComposer extends SelectorComposer<Component> {

    private static final long serialVersionUID = 1L;

    // Reports sub-menu toggle
    @Wire
    private Div checkerReportsMenu;

    @Wire
    private Label checkerReportsArrow;

    @Override
    public void doAfterCompose(Component comp) throws Exception {
        super.doAfterCompose(comp);
        checkerReportsArrow.setValue("▶");
    }

    // ── REPORTS toggle ────────────────────────────────────────
    @Listen("onClick=#checkerReportsHeader")
    public void toggleReportsMenu() {
        boolean open = !checkerReportsMenu.isVisible();
        checkerReportsMenu.setVisible(open);
        checkerReportsArrow.setValue(open ? "▼" : "▶");
    }

    // ── PAGE NAVIGATION ──────────────────────────────────────
    // Same pattern as SidebarComposer — reads pagePath custom-attribute,
    // routes through CheckerDashboardComposer.loadPage()
    @Listen("onClick=.cts-menu-item")
    public void navigate(Event event) {
        Component target = event.getTarget();
        while (target != null && target.getAttribute("pagePath") == null) {
            target = target.getParent();
        }
        if (target == null) return;

        String pagePath = target.getAttribute("pagePath").toString();
        if (pagePath == null || pagePath.trim().isEmpty()) return;

        CheckerDashboardComposer.getInstance().loadPage(pagePath);
    }
}
