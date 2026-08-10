import { createContext, useCallback, useEffect, useMemo, useRef, useState } from "react";
import { getCurrentAuth, logout as requestLogout } from "../api/authApi";

export const AuthContext = createContext(null);

export function AuthProvider({ children }) {
  const [auth, setAuth] = useState({ status: "loading", provider: null, error: null });
  const bootstrapped = useRef(false);

  const refreshAuth = useCallback(async ({ showLoading = false } = {}) => {
    if (showLoading) setAuth({ status: "loading", provider: null, error: null });
    try {
      const result = await getCurrentAuth();
      const next = result.authenticated
        ? { status: "authenticated", provider: result.provider, error: null }
        : { status: "anonymous", provider: null, error: null };
      setAuth(next);
      return next;
    } catch (error) {
      const next = { status: "error", provider: null, error };
      setAuth(next);
      return next;
    }
  }, []);

  useEffect(() => {
    if (bootstrapped.current) return;
    bootstrapped.current = true;
    void refreshAuth();
  }, [refreshAuth]);

  const logout = useCallback(async () => {
    await requestLogout();
    setAuth({ status: "anonymous", provider: null, error: null });
  }, []);

  const value = useMemo(
    () => ({ ...auth, refreshAuth, logout }),
    [auth, logout, refreshAuth],
  );

  return <AuthContext.Provider value={value}>{children}</AuthContext.Provider>;
}
