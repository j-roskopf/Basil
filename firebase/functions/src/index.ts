import { initializeApp, getApps } from "firebase-admin/app";

if (getApps().length === 0) {
  initializeApp();
}

export { extractRecipe } from "./extract/extractRecipe";
export { proxyImage } from "./imageProxy";
export { requestEmailOtp, verifyEmailOtp } from "./otp/emailOtp";
