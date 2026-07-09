import { createSlice, createAsyncThunk, PayloadAction } from '@reduxjs/toolkit';
import axios from 'axios';
import { RootState } from '../store';
import { Account, CreateAccountRequest } from '@/types';

// Fetch from Query Service (Read Side)
export const fetchAccounts = createAsyncThunk(
  'accounts/fetchAccounts',
  async (_, { getState, rejectWithValue }) => {
    try {
      const { auth } = getState() as RootState;
      const response = await axios.get<Account[]>('http://localhost:8082/api/accounts', {
        headers: { Authorization: `Bearer ${auth.token}` },
      });
      return response.data;
    } catch (err: unknown) {
      const message = axios.isAxiosError(err)
        ? err.response?.data?.message || err.message
        : 'Failed to fetch accounts';
      return rejectWithValue(message);
    }
  }
);

// Post to Command Service (Write Side)
export const createAccount = createAsyncThunk(
  'accounts/createAccount',
  async (request: CreateAccountRequest, { getState, dispatch, rejectWithValue }) => {
    try {
      const { auth } = getState() as RootState;
      await axios.post('http://localhost:8081/api/accounts', request, {
        headers: { Authorization: `Bearer ${auth.token}` },
      });
      // Refresh the accounts list to get the newly projected account
      dispatch(fetchAccounts());
      return true;
    } catch (err: unknown) {
      const message = axios.isAxiosError(err)
        ? err.response?.data?.message || err.message
        : 'Failed to create account';
      return rejectWithValue(message);
    }
  }
);

interface AccountState {
  accounts: Account[];
  loading: boolean;
  error: string | null;
}

const initialState: AccountState = {
  accounts: [],
  loading: false,
  error: null,
};

const accountSlice = createSlice({
  name: 'accounts',
  initialState,
  reducers: {},
  extraReducers: (builder) => {
    builder
      .addCase(fetchAccounts.pending, (state) => {
        state.loading = true;
        state.error = null;
      })
      .addCase(fetchAccounts.fulfilled, (state, action: PayloadAction<Account[]>) => {
        state.loading = false;
        state.accounts = action.payload;
      })
      .addCase(fetchAccounts.rejected, (state, action) => {
        state.loading = false;
        state.error = action.payload as string;
      });
  },
});

export default accountSlice.reducer;