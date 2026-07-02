package com.privateflow.modules.api.auth;

public final class AuthContext {

  private static final ThreadLocal<AuthUser> CURRENT = new ThreadLocal<>();

  private AuthContext() {
  }

  public static void set(AuthUser user) {
    CURRENT.set(user);
  }

  public static AuthUser current() {
    return CURRENT.get();
  }

  public static String username() {
    AuthUser user = CURRENT.get();
    return user == null ? "SYSTEM" : user.username();
  }

  public static void clear() {
    CURRENT.remove();
  }
}
