import { createSlice, PayloadAction } from "@reduxjs/toolkit";
import Cookies from "js-cookie";

interface AuthState {
  token: string | null;
  isAuthenticated: boolean;
}

// Check for the cookie as soon as the file is evaluated
const savedToken = Cookies.get("streamvault_jwt") || null;

// Initialize Redux with the token if it exists
const initialState: AuthState = {
  token: savedToken,
  isAuthenticated: !!savedToken, // true if savedToken is a string, false if null
};

const authSlice = createSlice({
  name: "auth",
  initialState,
  reducers: {
    loginSuccess: (state, action: PayloadAction<string>) => {
      state.token = action.payload;
      state.isAuthenticated = true;
    },
    logout: (state) => {
      state.token = null;
      state.isAuthenticated = false;
      // Clear the cookie on logout
      Cookies.remove("streamvault_jwt");
    },
  },
});

export const { loginSuccess, logout } = authSlice.actions;
export default authSlice.reducer;
