package com.cts.component;

import org.zkoss.zk.ui.HtmlMacroComponent;

/*
 * Component class for batchSummary macro.
 * CRITICAL FIX: setMacroURI() must be called before super.afterCompose()
 * so that HtmlMacroComponent resolves the ZUL from the correct path.
 * Without this it defaults to /components/<class-name>.zul which does not exist.
 */
public class BatchSummaryComponent extends HtmlMacroComponent {

    private static final long serialVersionUID = 1L;

    @Override
    public void afterCompose() {
        // FIX: Register the correct macro URI before composing.
        // Path must match macroURI declared in makerBatchDetail.zul
        setMacroURI("/component/batchSummary.zul");
        super.afterCompose();
    }
}
