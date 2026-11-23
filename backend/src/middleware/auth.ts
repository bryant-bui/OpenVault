import type { MiddlewareHandler } from "hono";
import type { AppEnv } from "../env.js";
import { verifyAccessToken, type AccessTokenClaims } from "../security/jwt.js";
import type { UserRow } from "../services/serializer.js";

export interface AuthVariables {
  user: UserRow;
  claims: AccessTokenClaims;
}

export const requireAuth: MiddlewareHandler<{
  Bindings: AppEnv;
  Variables: AuthVariables;
}> = async (c, next) => {
  const authHeader = c.req.header("Authorization");
  if (!authHeader || !authHeader.startsWith("Bearer ")) {
    return c.json({ error: "unauthorized", message: "Missing bearer token" }, 401);
  }

  const token = authHeader.slice(7).trim();
  if (!token) {
    return c.json({ error: "unauthorized", message: "Empty bearer token" }, 401);
  }

  const claims = await verifyAccessToken(token, c.env.JWT_SIGNING_SECRET);
  if (!claims) {
    return c.json({ error: "unauthorized", message: "Invalid or expired access token" }, 401);
  }

  const user = await c.env.DB.prepare("SELECT * FROM users WHERE id = ?")
    .bind(claims.sub)
    .first<UserRow>();

  if (!user || user.security_stamp !== claims.sstamp) {
    return c.json({ error: "unauthorized", message: "Session has been revoked" }, 401);
  }

  c.set("user", user);
  c.set("claims", claims);
  await next();
};
