package com.cts.composer;

import java.text.SimpleDateFormat;

import java.util.Date;

import org.zkoss.zk.ui.Component;
import org.zkoss.zk.ui.Executions;
import org.zkoss.zk.ui.Sessions;
import org.zkoss.zk.ui.event.Event;
import org.zkoss.zk.ui.select.SelectorComposer;
import org.zkoss.zk.ui.select.annotation.Listen;
import org.zkoss.zk.ui.select.annotation.Wire;
import org.zkoss.zul.Div;
import org.zkoss.zul.Label;
import org.zkoss.zul.Messagebox;
import org.zkoss.zul.Textbox;
import org.zkoss.zul.Timer;

import com.cts.security.SessionKeys;
import com.cts.uam.model.User;
import com.cts.uam.service.UserService;
import com.cts.uam.service.UserServiceImpl;

/**
 * HeaderComposer — Inward ClearPay CTS
 *
 * Features:
 *   - Profile popup (avatar, name, email, role badge)
 *   - Clock toggle (ON/OFF pill)
 *   - Change Password modal with validation (updates cts_users via BCrypt)
 *   - Logout with confirmation dialog
 *   - Idle auto-logout with countdown warning
 *   - Live session timer in popup footer
 */
public class HeaderComposer extends SelectorComposer<Component> {

    private static final long serialVersionUID = 1L;
    private final UserService userService = new UserServiceImpl();

    // Session key to persist clock ON/OFF across header re-renders
    private static final String SESS_CLOCK_VISIBLE = "header_clock_visible";

    // ── HEADER WIRES ───────────────────────────────────────────────
    @Wire private Timer  hdrTimer;
    @Wire private Label  lblHdrTime;
    @Wire private Label  lblHdrDate;
    @Wire private Label  lblHdrAvatar;
    @Wire private Div    hdrCentre;

    // ── AVATAR / POPUP WIRES ───────────────────────────────────────
    @Wire private Div    avatarBtn;
    @Wire private Div    profilePopup;
    @Wire private Div    ppOverlay;

    // ── POPUP CONTENT WIRES ────────────────────────────────────────
    @Wire private Label  lblPopupAvatar;
    @Wire private Label  lblPopupName;
    @Wire private Label  lblPopupEmail;
    @Wire private Label  lblPopupRole;
    @Wire private Label  lblSessionTime;

    // ── CLOCK TOGGLE WIRES ─────────────────────────────────────────
    @Wire private Div    clockTogglePill;
    @Wire private Label  lblClockToggle;

    // ── CHANGE PASSWORD MODAL WIRES ────────────────────────────────
    @Wire private Div     chpwModalOverlay;
    @Wire private Textbox txtCurrentPw;
    @Wire private Textbox txtNewPw;
    @Wire private Textbox txtConfirmPw;
    @Wire private Label   lblChpwError;

    @Wire private Timer  idleCountdownTimer;
    @Wire private org.zkoss.zul.Window idleWarnDialog;

    // ── STATE ──────────────────────────────────────────────────────
    private long    sessionStartMillis;
    private boolean popupOpen    = false;
    private boolean clockVisible = true;

    private final SimpleDateFormat timeFormat = new SimpleDateFormat("hh:mm a");
    private final SimpleDateFormat dateFormat = new SimpleDateFormat("dd MMM yyyy");

    // ══════════════════════════════════════════════════════════════
    // INIT
    // ══════════════════════════════════════════════════════════════

    @Override
    public void doAfterCompose(Component comp) throws Exception {
        super.doAfterCompose(comp);

        // Persist session start time in HTTP session so it doesn't reset on header re-render
        jakarta.servlet.http.HttpServletRequest httpReq =
            (jakarta.servlet.http.HttpServletRequest) Executions.getCurrent().getNativeRequest();
        jakarta.servlet.http.HttpSession httpSession = httpReq.getSession(false);

        if (httpSession != null) {
            Long stored = (Long) httpSession.getAttribute("SESSION_START_MILLIS");
            if (stored == null) {
                stored = System.currentTimeMillis();
                httpSession.setAttribute("SESSION_START_MILLIS", stored);
            }
            sessionStartMillis = stored;
        } else {
            sessionStartMillis = System.currentTimeMillis();
        }

        // Restore clock toggle preference from ZK session
        Object saved = Sessions.getCurrent().getAttribute(SESS_CLOCK_VISIBLE);
        clockVisible = (saved == null) ? true : Boolean.TRUE.equals(saved);
        applyClockState();

        // ── LOAD USER FROM SESSION ─────────────────────────────────
        User currentUser = currentUser();

        String username;
        String role;
        String email;

        if (currentUser != null) {
            username = (currentUser.getFullName() != null && !currentUser.getFullName().isBlank())
                    ? currentUser.getFullName()
                    : currentUser.getUsername();
            role  = (currentUser.getRoleLabel() != null) ? currentUser.getRoleLabel() : "OPERATOR";
            email = (currentUser.getEmail() != null && !currentUser.getEmail().isBlank())
                    ? currentUser.getEmail()
                    : currentUser.getUsername().toLowerCase() + "@navbharatbank.in";
        } else {
            username = "User";
            role     = "OPERATOR";
            email    = "user@navbharatbank.in";
        }

        String avatarLetter = String.valueOf(username.charAt(0)).toUpperCase();

        // Populate header avatar
        lblHdrAvatar.setValue(avatarLetter);

        // Populate profile popup labels
        lblPopupAvatar.setValue(avatarLetter);
        lblPopupName.setValue(username);
        lblPopupEmail.setValue(email);
        lblPopupRole.setValue(role.toUpperCase());

        // Set clock immediately so header doesn't show defaults for first second
        updateClock();
    }

    /** Reads the logged-in User object from the ZK session. */
    private User currentUser() {
        Object obj = Sessions.getCurrent().getAttribute(SessionKeys.CURRENT_USER);
        return (obj instanceof User user) ? user : null;
    }

    // ══════════════════════════════════════════════════════════════
    // TIMER — fires every 1 second
    // ══════════════════════════════════════════════════════════════

    @Listen("onTimer = #hdrTimer")
    public void onTick(Event event) {
        updateClock();
        updateSessionTime();
    }

    private void updateClock() {
        Date now = new Date();
        lblHdrTime.setValue(timeFormat.format(now).toUpperCase());
        lblHdrDate.setValue(dateFormat.format(now).toUpperCase());
    }

    private void updateSessionTime() {
        long elapsed = System.currentTimeMillis() - sessionStartMillis;
        long seconds = (elapsed / 1000) % 60;
        long minutes = (elapsed / (1000 * 60)) % 60;
        long hours   = (elapsed / (1000 * 60 * 60)) % 24;
        lblSessionTime.setValue(
            String.format("Active session · %02d:%02d:%02d", hours, minutes, seconds));
    }

    // ══════════════════════════════════════════════════════════════
    // AVATAR / POPUP
    // ══════════════════════════════════════════════════════════════

    @Listen("onClick = #avatarBtn")
    public void onAvatarClick() {
        if (popupOpen) closePopup();
        else           openPopup();
    }

    private void openPopup() {
        profilePopup.setSclass("profile-popup pp-visible");
        ppOverlay.setVisible(true);
        popupOpen = true;
    }

    private void closePopup() {
        profilePopup.setSclass("profile-popup");
        ppOverlay.setVisible(false);
        popupOpen = false;
    }

    // Click-outside overlay closes the popup
    @Listen("onClick = #ppOverlay")
    public void onOverlayClick() {
        closePopup();
    }

    // ══════════════════════════════════════════════════════════════
    // CLOCK TOGGLE
    // ══════════════════════════════════════════════════════════════

    @Listen("onClick = #btnToggleClock")
    public void onToggleClock() {
        clockVisible = !clockVisible;
        Sessions.getCurrent().setAttribute(SESS_CLOCK_VISIBLE, clockVisible);
        applyClockState();
    }

    private void applyClockState() {
        hdrCentre.setVisible(clockVisible);
        if (clockVisible) {
            clockTogglePill.setSclass("pp-toggle pp-toggle-on");
            lblClockToggle.setValue("ON");
        } else {
            clockTogglePill.setSclass("pp-toggle pp-toggle-off");
            lblClockToggle.setValue("OFF");
        }
    }

    // ══════════════════════════════════════════════════════════════
    // CHANGE PASSWORD MODAL
    // ══════════════════════════════════════════════════════════════

    @Listen("onClick = #btnChangePassword")
    public void onChangePassword() {
        closePopup();
        clearChpwForm();
        chpwModalOverlay.setVisible(true);
    }

    @Listen("onClick = #btnChpwClose; onClick = #btnChpwCancel")
    public void onChpwClose() {
        chpwModalOverlay.setVisible(false);
        clearChpwForm();
    }

    @Listen("onClick = #btnSavePassword")
    public void onSavePassword() {
        lblChpwError.setValue("");

        String currentPw = txtCurrentPw.getValue().trim();
        String newPw     = txtNewPw.getValue().trim();
        String confirmPw = txtConfirmPw.getValue().trim();

        if (currentPw.isEmpty() || newPw.isEmpty() || confirmPw.isEmpty()) {
            lblChpwError.setValue("All fields are required.");
            return;
        }
        if (!newPw.equals(confirmPw)) {
            lblChpwError.setValue("New password and confirm password do not match.");
            return;
        }
        if (newPw.equals(currentPw)) {
            lblChpwError.setValue("New password must be different from current password.");
            return;
        }

        try {
            // Get the logged-in user from the session (same source as doAfterCompose)
            User currentUser = currentUser();
            if (currentUser == null) {
                lblChpwError.setValue("Session expired. Please log in again.");
                return;
            }
            String username = currentUser.getUsername();

            // Real change — verifies current pw, enforces strength, hashes new pw, updates DB
            userService.changeOwnPassword(username, currentPw, newPw);

            chpwModalOverlay.setVisible(false);
            clearChpwForm();

            Messagebox.show(
                "Password changed successfully. Please use your new password next time you log in.",
                "Done", Messagebox.OK, Messagebox.INFORMATION);

        } catch (IllegalArgumentException ex) {
            // Expected, user-friendly errors (wrong current pw, weak password, etc.)
            lblChpwError.setValue(ex.getMessage());
        } catch (Exception ex) {
            lblChpwError.setValue("Failed to change password. Please try again.");
        }
    }

    private void clearChpwForm() {
        txtCurrentPw.setValue("");
        txtNewPw.setValue("");
        txtConfirmPw.setValue("");
        lblChpwError.setValue("");
    }

    // ══════════════════════════════════════════════════════════════
    // IDLE AUTO-LOGOUT
    // ══════════════════════════════════════════════════════════════

    private int idleSecondsLeft = 30;

    /** Helper — safely get the countdown label from inside the dialog. */
    private Label getIdleMsgLabel() {
        if (idleWarnDialog == null) return null;
        Component c = idleWarnDialog.getFellowIfAny("idleWarnMsg");
        return (c instanceof Label) ? (Label) c : null;
    }

    @Listen("onIdleWarn = #idleWarnDialog")
    public void onIdleWarn() {
        idleSecondsLeft = 30;
        Label msg = getIdleMsgLabel();
        if (msg != null) {
            msg.setValue("You will be logged out in " + idleSecondsLeft + " seconds.");
        }
        idleWarnDialog.setVisible(true);
        idleWarnDialog.doHighlighted();   // centered, timer keeps running
        idleCountdownTimer.setRunning(true);
    }

    @Listen("onTimer = #idleCountdownTimer")
    public void onIdleCountdownTick() {
        idleSecondsLeft--;
        if (idleSecondsLeft <= 0) {
            doLogout();
            return;
        }
        Label msg = getIdleMsgLabel();
        if (msg != null) {
            msg.setValue("You will be logged out in " + idleSecondsLeft + " seconds.");
        }
    }

    /** User moved/clicked during the warning — cancel logout, hide dialog. */
    @Listen("onIdleReset = #idleWarnDialog")
    public void onIdleReset() {
        idleCountdownTimer.setRunning(false);
        idleWarnDialog.setVisible(false);
    }

    /** Client hit 0 seconds with no activity — log out. */
    @Listen("onIdleLogout = #idleWarnDialog")
    public void onIdleLogout() {
        doLogout();
    }

    /** "Stay Logged In" button — cancel the countdown. */
    @Listen("onClick = #idleStayBtn")
    public void onStayLoggedIn() {
        idleCountdownTimer.setRunning(false);
        idleWarnDialog.setVisible(false);
    }

    /** "Logout Now" button — immediate logout. */
    @Listen("onClick = #idleLogoutNowBtn")
    public void onLogoutNow() {
        doLogout();
    }

    // ══════════════════════════════════════════════════════════════
    // LOGOUT
    // ══════════════════════════════════════════════════════════════

    @Listen("onClick = #btnLogout")
    public void onLogout(Event event) {
        closePopup();
        Messagebox.show(
            "Are you sure you want to logout?\nYour current session will be ended.",
            "Confirm Logout",
            new Messagebox.Button[] { Messagebox.Button.YES, Messagebox.Button.NO },
            new String[]            { "Yes, Logout", "Cancel" },
            Messagebox.QUESTION,
            Messagebox.Button.NO,
            clickEvent -> {
                if (Messagebox.Button.YES.equals(clickEvent.getButton())) {
                    doLogout();
                }
            }
        );
    }

    private void doLogout() {
        Sessions.getCurrent().invalidate();
        Executions.sendRedirect("/zul/index.zul");
    }
}