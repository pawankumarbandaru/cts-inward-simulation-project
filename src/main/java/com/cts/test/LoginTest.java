package com.cts.test;

import com.cts.uam.model.AuthResult;
import com.cts.uam.service.UserService;
import com.cts.uam.service.UserServiceImpl;

/**
 * TEMPORARY test for Step 1. Run as Java Application in Eclipse.
 * Confirms DB lookup + BCrypt verify work before we touch the UI.
 * DELETE this class after testing.
 */
public class LoginTest {

    public static void main(String[] args) {

        UserService userService = new UserServiceImpl();

        // 1) Correct password → expect SUCCESS
        AuthResult ok = userService.authenticate("maker_in", "Maker@2026#");
        System.out.println("Correct password  -> " + ok.reason);

        // 2) Wrong password → expect WRONG_PASSWORD
        AuthResult bad = userService.authenticate("maker_in", "wrongpass");
        System.out.println("Wrong password    -> " + bad.reason);

        // 3) Unknown user → expect USER_NOT_FOUND
        AuthResult missing = userService.authenticate("ghost", "whatever");
        System.out.println("Unknown user      -> " + missing.reason);
    }
}