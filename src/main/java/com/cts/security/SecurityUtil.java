package com.cts.security;

import java.util.Collections;
import java.util.Set;

import org.zkoss.zk.ui.Sessions;

import com.cts.inward.enums.Permission;

public final class SecurityUtil {

	private SecurityUtil() {
	}

	@SuppressWarnings("unchecked")
	public static Set<Permission> getPermissions() {

		Object obj = Sessions.getCurrent().getAttribute("permissions");

		if (obj == null) {

			return Collections.emptySet();
		}

		return (Set<Permission>) obj;
	}

	public static boolean hasPermission(String permissionName) {

		try {

			Permission permission = Permission.valueOf(permissionName);

			return getPermissions().contains(permission);

		} catch (Exception e) {

			return false;
		}
	}
}