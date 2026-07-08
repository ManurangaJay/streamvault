"use client";

import { useState } from "react";
import { useRouter } from "next/navigation";
import { useDispatch } from "react-redux";
import axios from "axios";
import { loginSuccess } from "../../store/slices/authSlice";

export default function RegisterPage() {
  const router = useRouter();
  const dispatch = useDispatch();

  const [fullName, setFullName] = useState("");
  const [email, setEmail] = useState("");
  const [password, setPassword] = useState("");
  const [error, setError] = useState("");
  const [loading, setLoading] = useState(false);

  const handleRegister = async (e: React.FormEvent) => {
    e.preventDefault();
    setError("");
    setLoading(true);

    try {
      const response = await axios.post(
        "http://localhost:8081/api/auth/register",
        {
          fullName,
          email,
          password,
        }
      );

      const token = response.data.token;

      document.cookie = `streamvault_jwt=${token}; path=/; max-age=3600; SameSite=Strict`;
      dispatch(loginSuccess(token));
      router.push("/dashboard");
    } catch (err: unknown) {
      const errorMessage =
        axios.isAxiosError(err) && err.response?.data?.message
          ? String(err.response.data.message)
          : "Registration failed. Please try again.";

      setError(errorMessage);
    } finally {
      setLoading(false);
    }
  };

  return (
    <div className="min-h-screen flex items-center justify-center bg-gray-50">
      <div className="max-w-md w-full p-8 bg-white rounded-xl shadow-lg border border-gray-100">
        <h2 className="text-3xl font-bold text-center text-gray-900 mb-8">
          Create Account
        </h2>

        {error && (
          <div className="mb-4 p-3 bg-red-50 text-red-700 rounded-md text-sm">
            {error}
          </div>
        )}

        <form onSubmit={handleRegister} className="space-y-6">
          <div>
            <label className="block text-sm font-medium text-gray-700">
              Full Name
            </label>
            <input
              type="text"
              required
              className="mt-1 block w-full px-4 py-2 border border-gray-300 rounded-md focus:ring-blue-500 focus:border-blue-500"
              value={fullName}
              onChange={(e) => setFullName(e.target.value)}
            />
          </div>

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
              minLength={8}
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
            {loading ? "Creating Account..." : "Register"}
          </button>
        </form>
      </div>
    </div>
  );
}
