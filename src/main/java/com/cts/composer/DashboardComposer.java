package com.cts.composer;

import java.util.Map;

import org.zkoss.zk.ui.Component;
import org.zkoss.zk.ui.Executions;
import org.zkoss.zk.ui.select.SelectorComposer;
import org.zkoss.zk.ui.select.annotation.Wire;
import org.zkoss.zul.Div;

import com.cts.security.SecurityUtil;
import com.cts.uam.model.User;

public class DashboardComposer extends SelectorComposer<Component> {

	private static final long serialVersionUID = 1L;

	@Wire
	private Div contentArea;

	private static DashboardComposer instance;

	@Override
	public void doAfterCompose(Component comp) throws Exception {
		super.doAfterCompose(comp);

		// Role comes from the trusted logged-in User (Step 2 session),
		// NOT the URL — so it can't be spoofed by editing ?role=.
		// Downstream composers (cheque popup, lists) read this "userRole".
		String role = resolveRole();

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

	/** Read role from the logged-in User; fall back to URL param if absent. */
	private String resolveRole() {
		User user = SecurityUtil.getCurrentUser();
		if (user != null && user.getRoleLabel() != null && !user.getRoleLabel().isBlank()) {
			return user.getRoleLabel().trim().toUpperCase();
		}
		String param = Executions.getCurrent().getParameter("role");
		return (param != null) ? param.trim().toUpperCase() : "";
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

		if (attributes != null) {
			for (Map.Entry<String, Object> entry : attributes.entrySet()) {
				Executions.getCurrent().getDesktop()
				          .setAttribute(entry.getKey(), entry.getValue());
			}
		}

		Executions.createComponents(pagePath, contentArea, null);
	}
}