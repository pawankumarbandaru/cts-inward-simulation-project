package com.cts.composer;

import java.util.Map;

import org.zkoss.zk.ui.Component;
import org.zkoss.zk.ui.Executions;
import org.zkoss.zk.ui.select.SelectorComposer;
import org.zkoss.zk.ui.select.annotation.Wire;
import org.zkoss.zul.Div;

public class CheckerDashboardComposer extends SelectorComposer<Component> {

    private static final long serialVersionUID = 1L;

    @Wire
    private Div checkerContentArea;

    private static CheckerDashboardComposer instance;

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
        loadPage("/zul/inward/checkerDashboard_inward.zul");
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
