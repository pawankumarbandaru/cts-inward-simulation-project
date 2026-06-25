package com.cts.composer;

import org.zkoss.zk.ui.Component;
import org.zkoss.zk.ui.event.Event;
import org.zkoss.zk.ui.select.SelectorComposer;
import org.zkoss.zk.ui.select.annotation.Listen;

public class CheckerSidebarComposer extends SelectorComposer<Component> {

    private static final long serialVersionUID = 1L;

    @Override
    public void doAfterCompose(Component comp) throws Exception {
        super.doAfterCompose(comp);
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
