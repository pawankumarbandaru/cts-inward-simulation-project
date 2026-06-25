package com.cts.component;

import org.zkoss.zk.ui.HtmlMacroComponent;

/**
 * Component class for cbsChequeList macro.
 *
 * Follows the same pattern as ChequeListComponent.
 * The macro ZUL is at /component/cbsChequeList.zul.
 * No composer is declared inside cbsChequeList.zul — the parent page
 * composer (CbsValidationComposer) populates the Listbox directly via
 * the wired lbCbsChequeList component.
 *
 * setMacroURI() is called before super.afterCompose() (following the fix
 * documented in BatchSummaryComponent) to ensure ZK resolves the correct path.
 */
public class CbsChequeListComponent extends HtmlMacroComponent {

    private static final long serialVersionUID = 1L;

    @Override
    public void afterCompose() {
        setMacroURI("/component/cbsChequeList.zul");
        super.afterCompose();
    }
}
