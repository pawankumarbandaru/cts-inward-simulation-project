package com.cts.composer;

import java.util.HashMap;
import java.util.Map;

import org.zkoss.zk.ui.Component;
import org.zkoss.zk.ui.Executions;
import org.zkoss.zk.ui.select.SelectorComposer;
import org.zkoss.zk.ui.select.annotation.Wire;
import org.zkoss.zul.Div;

public class DashboardComposer extends SelectorComposer<Component> {

	private static final long serialVersionUID = 1L;

	@Wire
	private Div contentArea;

	private static DashboardComposer instance;

	@Override
	public void doAfterCompose(Component comp) throws Exception {
		super.doAfterCompose(comp);
		
		String role =
		        Executions.getCurrent()
		                  .getParameter("role");

		Executions.getCurrent()
		          .getDesktop()
		          .setAttribute("userRole", role);

		System.out.println(
		        "Desktop="
		        + Executions.getCurrent().getDesktop().getId()
		        + " Role="
		        + role);
		
		instance = this;

		loadPage("/zul/inward/inwardDashboard.zul");
	}

	public static DashboardComposer getInstance() {
		return instance;
	}

	public void loadPage(String pagePath) {

		contentArea.getChildren().clear();

		Executions.createComponents(pagePath, contentArea, null);
	}
	
	
	/**
     * Load a page and pass data to the target composer via Desktop attributes.
     *
     * WHY Desktop scope (not Execution scope):
     * When navigateToMicrService() is called inside a Messagebox callback,
     * Executions.getCurrent() is a NEW execution — different from the one
     * that created the page. Attributes set on it are invisible to the target
     * composer. Desktop attributes persist across all executions in the same
     * browser session, so they are always readable by the target composer.
     */
    public void loadPage(String pagePath, Map<String, Object> attributes) {
        contentArea.getChildren().clear();

        // Store in Desktop scope — survives across async Messagebox callbacks
        if (attributes != null) {
            for (Map.Entry<String, Object> entry : attributes.entrySet()) {
                Executions.getCurrent().getDesktop()
                          .setAttribute(entry.getKey(), entry.getValue());
            }
        }

        Executions.createComponents(pagePath, contentArea, null);
    }
}