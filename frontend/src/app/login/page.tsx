"use client";

import { useState } from "react";
import { useRouter } from "next/navigation";
import { useDispatch } from "react-redux";
import axios from "axios";
import { loginSuccess } from "../../store/slices/authSlice";

export default function LoginPage() {
  const router = useRouter();
  const dispatch = useDispatch();

  const [email, setEmail] = useState("");
  const [password, setPassword] = useState("");
  const [error, setError] = useState("");
  const [loading, setLoading] = useState(false);

  const handleLogin = async (e: React.FormEvent) => {
    e.preventDefault();
    setError("");
    setLoading(true);

    try {
      // Pointing to your Phase 1 Command Service REST API
      const response = await axios.post(
        "http://localhost:8081/api/auth/login",
        {
          email,
          password,
        }
      );

      const token = response.data.token; // Adjust based on your exact JSON response wrapper

      // 1. Sync to Next.js Middleware via Cookie (1 hour expiry matching backend)
      document.cookie = `streamvault_jwt=${token}; path=/; max-age=3600; SameSite=Strict`;

      // 2. Sync to Redux for reactive client components
      dispatch(loginSuccess(token));

      // 3. Reroute to protected zone
      router.push("/dashboard");
    } catch (error: unknown) {
      const fallback = "Invalid credentials. Please try again.";

      if (axios.isAxiosError(error)) {
        const data = error.response?.data;
        const message =
          typeof data === "object" &&
          data !== null &&
          "message" in data &&
          typeof (data as { message?: unknown }).message === "string"
            ? (data as { message: string }).message
            : error.message;

        setError(message || fallback);
      } else if (error instanceof Error) {
        setError(error.message || fallback);
      } else {
        setError(fallback);
      }
    } finally {
      setLoading(false);
    }
  };

  return (
    <div className="min-h-screen flex items-center justify-center bg-gray-50">
      <div className="max-w-md w-full p-8 bg-white rounded-xl shadow-lg border border-gray-100">
        <h2 className="text-3xl font-bold text-center text-gray-900 mb-8">
          StreamVault
        </h2>

        {error && (
          <div className="mb-4 p-3 bg-red-50 text-red-700 rounded-md text-sm">
            {error}
          </div>
        )}

        <form onSubmit={handleLogin} className="space-y-6">
          <div>
            <label className="block text-sm font-medium text-gray-700">
              Email
            </label>
            <input
              type="email"
              required
              className="mt-1 block w-full px-4 py-2 border border-gray-300 rounded-md focus:ring-blue-500 focus:border-blue-500"
              value={email}
              onChange={(e) => setEmail(e.target.value)}
            />
          </div>

          <div>
            <label className="block text-sm font-medium text-gray-700">
              Password
            </label>
            <input
              type="password"
              required
              className="mt-1 block w-full px-4 py-2 border border-gray-300 rounded-md focus:ring-blue-500 focus:border-blue-500"
              value={password}
              onChange={(e) => setPassword(e.target.value)}
            />
          </div>

          <button
            type="submit"
            disabled={loading}
            className="w-full py-2 px-4 bg-blue-900 text-white font-semibold rounded-md hover:bg-blue-800 disabled:opacity-50 transition-colors"
          >
            {loading ? "Authenticating..." : "Sign In"}
          </button>
        </form>
      </div>
    </div>
  );
}
