package com.cts.component;

import org.zkoss.zk.ui.HtmlMacroComponent;

/*
 * Component class for chequeList macro.
 * Registers the macro with its ZUL file.
 * Composer (ChequeListComposer) handles all UI logic
 * because apply is declared inside chequeList.zul.
 */
public class ChequeListComponent extends HtmlMacroComponent {

    private static final long serialVersionUID = 1L;

    @Override
    public void afterCompose() {
        super.afterCompose();
        // Composer declared in zul handles everything
    }
}