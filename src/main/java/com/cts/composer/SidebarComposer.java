package com.cts.composer;

import org.zkoss.zk.ui.Component;
import org.zkoss.zk.ui.event.Event;
import org.zkoss.zk.ui.select.SelectorComposer;
import org.zkoss.zk.ui.select.annotation.Listen;
import org.zkoss.zk.ui.select.annotation.Wire;
import org.zkoss.zul.Div;
import org.zkoss.zul.Label;

public class SidebarComposer extends SelectorComposer<Component> {

	// inwardMenu has no toggle — always visible, no @Wire needed

	@Wire
	private Div inwardReportsMenu;
	@Wire
	private Div userMenu;

	@Wire
	private Label inwardReportsArrow;
	@Wire
	private Label userArrow;

	@Override
	public void doAfterCompose(Component comp) throws Exception {
		super.doAfterCompose(comp);
		inwardReportsArrow.setValue("▶");
		userArrow.setValue("▶");
	}

	// ── INWARD REPORTS ──
	@Listen("onClick=#inwardReportsHeader")
	public void toggleInwardReportsMenu() {
		boolean open = !inwardReportsMenu.isVisible();
		inwardReportsMenu.setVisible(open);
		inwardReportsArrow.setValue(open ? "▼" : "▶");
	}

	// ── USER MANAGEMENT ──
	@Listen("onClick=#userHeader")
	public void toggleUserMenu() {
		boolean open = !userMenu.isVisible();
		userMenu.setVisible(open);
		userArrow.setValue(open ? "▼" : "▶");
	}

	// ── PAGE NAVIGATION ──
	@Listen("onClick=.cts-menu-item")
	public void navigate(Event event) {
		Component target = event.getTarget();
		while (target != null && target.getAttribute("pagePath") == null) {
			target = target.getParent();
		}
		if (target == null) {
			return;
		}
		String pagePath = target.getAttribute("pagePath").toString();
		DashboardComposer.getInstance().loadPage(pagePath);
	}
}
