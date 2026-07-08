import { NextResponse } from "next/server";
import type { NextRequest } from "next/server";

export function middleware(request: NextRequest) {
  // Extract the token from the cookies
  const token = request.cookies.get("streamvault_jwt")?.value;
  const isAuthPage =
    request.nextUrl.pathname.startsWith("/login") ||
    request.nextUrl.pathname.startsWith("/register");

  // If trying to access a protected route without a token, redirect to login
  if (!token && !isAuthPage) {
    return NextResponse.redirect(new URL("/login", request.url));
  }

  // If already logged in and trying to access login/register, redirect to dashboard
  if (token && isAuthPage) {
    return NextResponse.redirect(new URL("/dashboard", request.url));
  }

  return NextResponse.next();
}

// Specify exactly which routes the middleware should protect
export const config = {
  matcher: ["/dashboard/:path*", "/login", "/register"],
};
