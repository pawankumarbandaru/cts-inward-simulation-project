package com.cts.composer;

import org.zkoss.zk.ui.Component;
import org.zkoss.zk.ui.Executions;
import org.zkoss.zk.ui.Session;
import org.zkoss.zk.ui.Sessions;
import org.zkoss.zk.ui.select.SelectorComposer;
import org.zkoss.zk.ui.select.annotation.Listen;
import org.zkoss.zk.ui.select.annotation.Wire;
import org.zkoss.zul.Label;
import org.zkoss.zul.Textbox;

public class LoginComposer extends SelectorComposer<Component> {

    private static final long serialVersionUID = 1L;

    // ── Session attribute keys
    private static final String SESS_LOGGED_USER = "loggedUser";
    private static final String SESS_USER_ROLE   = "userRole";
    private static final String SESS_USER_BRANCH = "userBranch";
    private static final String SESS_USER_NAME   = "userName";

    // ── Hardcoded credentials → role mapping
    //    maker   / maker123   → MAKER
    //    checker / checker123 → CHECKER  (TV1)
    //    tv2     / tv2123     → TV2
    private static final java.util.Map<String, String[]> CREDENTIALS;
    static {
        CREDENTIALS = new java.util.HashMap<>();
        // key = username, value = [password, role, displayName]
        CREDENTIALS.put("maker",   new String[]{"maker123", "MAKER", "Maker User"});
        CREDENTIALS.put("tv1", new String[]{"TV1123", "TV1", "TV1 Checker User"});
        CREDENTIALS.put("tv2",     new String[]{"TV2123", "TV2", "TV2 Checker User"});
    }

    @Wire("#userId")
    private Textbox userId;

    @Wire("#password")
    private Textbox password;

    @Wire("#lblError")
    private Label lblError;

    @Override
    public void doAfterCompose(Component comp) throws Exception {
        super.doAfterCompose(comp);
    }

    @Listen("onClick = #btnLogin; onOK = #userId; onOK = #password")
    public void onLogin() {

        String uid  = (userId   != null) ? userId.getValue().trim()   : "";
        String pass = (password != null) ? password.getValue().trim() : "";

        if (uid.isEmpty()) {
            showError("Please enter your User ID.");
            return;
        }
        if (pass.isEmpty()) {
            showError("Please enter your Password.");
            return;
        }

        // ── Credential check ─────────────────────────────────────────
        String[] cred = CREDENTIALS.get(uid.toLowerCase());
        if (cred == null || !cred[0].equals(pass)) {
            showError("Invalid User ID or Password.");
            return;
        }

        String role        = cred[1];
        String displayName = cred[2];
        String branch      = "BLR01";

        // ── Store in ZK session ──────────────────────────────────────
        Session session = Sessions.getCurrent();
        session.setAttribute(SESS_LOGGED_USER, uid);
        session.setAttribute(SESS_USER_NAME,   displayName);
        session.setAttribute(SESS_USER_BRANCH, branch);

        // ── Role-based redirect ──────────────────────────────────────
	        switch (role) {
	
	        case "TV1":
	            Executions.sendRedirect(
	                "component/checkerDashboard.zul?role=TV1");
	            break;
	
	        case "TV2":
	            Executions.sendRedirect(
	                "component/tv2Dashboard.zul?role=TV2");
	            break;
	
	        default:
	            Executions.sendRedirect(
	                "zul/dashboard.zul?role=MAKER");
	            break;
	    }
    }

    private void showError(String msg) {
        if (lblError != null) {
            lblError.setValue(msg);
            lblError.setVisible(true);
        }
    }
}
