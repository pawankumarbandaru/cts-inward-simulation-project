package com.cts.composer;

import java.util.Map;

import org.zkoss.zk.ui.Component;
import org.zkoss.zk.ui.Executions;
import org.zkoss.zk.ui.select.SelectorComposer;
import org.zkoss.zk.ui.select.annotation.Wire;
import org.zkoss.zul.Div;

import com.cts.security.SecurityUtil;
import com.cts.uam.model.User;

public class CheckerDashboardComposer extends SelectorComposer<Component> {

    private static final long serialVersionUID = 1L;

    @Wire
    private Div checkerContentArea;

    private static CheckerDashboardComposer instance;

    @Override
    public void doAfterCompose(Component comp) throws Exception {
        super.doAfterCompose(comp);

        // Trusted role from the logged-in User (not the URL).
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
        loadPage("/zul/inward/checkerDashboard_inward.zul");
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

    public static CheckerDashboardComposer getInstance() {
        return instance;
    }

    /** Load page with no arguments. */
    public void loadPage(String pagePath) {
        checkerContentArea.getChildren().clear();
        Executions.createComponents(pagePath, checkerContentArea, null);
    }

    /** Load page passing a map of arguments (accessible via Executions.getCurrent().getArg()). */
    public void loadPage(String pagePath, Map<String, Object> args) {
        checkerContentArea.getChildren().clear();
        Executions.createComponents(pagePath, checkerContentArea, args);
    }
}