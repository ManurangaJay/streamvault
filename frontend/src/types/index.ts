export interface Account {
  id: string;
  ownerId: string;
  balance: number;
  currency: string;
  accountType: "SAVINGS" | "CURRENT" | "FIXED_DEPOSIT";
  status: "ACTIVE" | "SUSPENDED" | "CLOSED";
  transactionCount: number;
  lastUpdatedAt: string;
}

export interface CreateAccountRequest {
  accountType: string;
  currency: string;
}
