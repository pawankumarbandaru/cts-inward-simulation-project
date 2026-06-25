package com.cts.composer;

import java.util.Map;

import org.zkoss.zk.ui.Component;
import org.zkoss.zk.ui.Executions;
import org.zkoss.zk.ui.select.SelectorComposer;
import org.zkoss.zk.ui.select.annotation.Wire;
import org.zkoss.zul.Div;

public class TV2DashboardComposer extends SelectorComposer<Component> {

    private static final long serialVersionUID = 1L;

    @Wire
    private Div tv2ContentArea;

    private static TV2DashboardComposer instance;

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
        loadPage("/zul/inward/tv2Dashboard_inward.zul");
    }

    public static TV2DashboardComposer getInstance() {
        return instance;
    }

    /** Load page with no arguments. */
    public void loadPage(String pagePath) {
        tv2ContentArea.getChildren().clear();
        Executions.createComponents(pagePath, tv2ContentArea, null);
    }

    /** Load page passing a map of arguments (accessible via Executions.getCurrent().getArg()). */
    public void loadPage(String pagePath, Map<String, Object> args) {
        tv2ContentArea.getChildren().clear();
        Executions.createComponents(pagePath, tv2ContentArea, args);
    }
}
