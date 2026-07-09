"use client";

import { useEffect, useState } from "react";
import { useDispatch, useSelector } from "react-redux";
import { AppDispatch, RootState } from "@/store/store";
import { fetchAccounts } from "@/store/slices/accountSlice";
import CreateAccountModal from "@/components/CreateAccountModal";
import { useRouter } from "next/navigation";

export default function DashboardPage() {
  const dispatch = useDispatch<AppDispatch>();
  const router = useRouter();
  const { accounts, loading, error } = useSelector(
    (state: RootState) => state.accounts
  );
  const [isModalOpen, setIsModalOpen] = useState(false);

  useEffect(() => {
    // Hydrate the dashboard on mount
    dispatch(fetchAccounts());
  }, [dispatch]);

  const formatCurrency = (amount: number, currency: string) => {
    return new Intl.NumberFormat("en-US", {
      style: "currency",
      currency: currency,
    }).format(amount);
  };

  return (
    <div className="min-h-screen bg-gray-50 p-8">
      <div className="max-w-7xl mx-auto">
        <div className="flex justify-between items-center mb-8">
          <h1 className="text-3xl font-bold text-gray-900">Your Accounts</h1>
          <button
            onClick={() => setIsModalOpen(true)}
            className="px-4 py-2 bg-blue-900 text-white font-semibold rounded-md hover:bg-blue-800 transition-colors"
          >
            + Open Account
          </button>
        </div>

        {error && (
          <div className="mb-6 p-4 bg-red-50 text-red-700 rounded-md">
            {error}
          </div>
        )}

        {loading && accounts.length === 0 ? (
          <div className="text-gray-500">Loading your ledger...</div>
        ) : (
          <div className="grid grid-cols-1 md:grid-cols-2 lg:grid-cols-3 gap-6">
            {accounts.map((account) => (
              <div
                key={account.id}
                onClick={() => router.push(`/dashboard/accounts/${account.id}`)}
                className="bg-white rounded-xl p-6 shadow-sm border border-gray-100 hover:shadow-md transition-shadow cursor-pointer"
              >
                <div className="flex justify-between items-start mb-4">
                  <div>
                    <h3 className="text-sm font-semibold text-gray-500 uppercase tracking-wider">
                      {account.accountType.replace("_", " ")}
                    </h3>
                    <p className="text-xs text-gray-400 mt-1">
                      {account.id.substring(0, 8)}...
                    </p>
                  </div>
                  <span
                    className={`px-2 py-1 text-xs font-semibold rounded-full ${
                      account.status === "ACTIVE"
                        ? "bg-green-100 text-green-800"
                        : "bg-red-100 text-red-800"
                    }`}
                  >
                    {account.status}
                  </span>
                </div>

                <div className="mt-6">
                  <p className="text-3xl font-bold text-gray-900">
                    {formatCurrency(account.balance, account.currency)}
                  </p>
                  <p className="text-sm text-gray-500 mt-2">
                    {account.transactionCount} total transactions
                  </p>
                </div>
              </div>
            ))}

            {accounts.length === 0 && !loading && (
              <div className="col-span-full text-center py-12 bg-white rounded-xl border border-dashed border-gray-300 text-gray-500">
                You don&apos;t have any accounts yet. Open one to get started.
              </div>
            )}
          </div>
        )}
      </div>

      <CreateAccountModal
        isOpen={isModalOpen}
        onClose={() => setIsModalOpen(false)}
      />
    </div>
  );
}
