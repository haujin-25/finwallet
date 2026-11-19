import { z } from "zod";
import { DEMO_MODE } from "@/config/demo";
import { parseAmountToMinor, parseTransferAmount } from "./money";

export const emailSchema = z
  .string()
  .trim()
  .min(1, "Enter your email")
  .email("Enter a valid email")
  .max(255);

export const passwordSchema = z
  .string()
  .min(8, "Use at least 8 characters")
  .regex(/[A-Za-z]/, "Include at least one letter")
  .regex(/\d/, "Include at least one number");

/** Amount as typed by the user. Only checks format; limits are enforced by the API. */
export const amountInputSchema = z
  .string()
  .trim()
  .min(1, "Enter an amount")
  .refine((value) => parseAmountToMinor(value) !== null, "Enter a valid amount, e.g. 150.00");

export const loginSchema = z.object({
  email: emailSchema,
  password: z.string().min(1, "Enter your password"),
});

export const registerSchema = z
  .object({
    fullName: z.string().trim().min(1, "Enter your full name").max(100),
    email: emailSchema,
    password: DEMO_MODE ? passwordSchema : z.string().min(8, "Use at least 8 characters").max(72),
    confirmPassword: z.string().min(1, "Confirm your password"),
  })
  .refine((data) => data.password === data.confirmPassword, {
    path: ["confirmPassword"],
    message: "Passwords don't match",
  });

export const forgotPasswordSchema = z.object({ email: emailSchema });

export const transferFormSchema = z.object({
  recipient: z.string().trim().min(3, "Enter the recipient's email or user ID"),
  amount: amountInputSchema,
  description: z.string().trim().max(80, "Keep it under 80 characters").optional(),
});

export const liveTransferFormSchema = transferFormSchema.extend({
  recipient: emailSchema,
  amount: z
    .string()
    .refine(
      (value) => parseTransferAmount(value) !== null,
      "Enter a positive MYR amount with up to two decimal places.",
    ),
  description: z
    .string()
    .trim()
    .refine(
      (value) => !value.includes("\0") && [...value].length <= 500,
      "Use up to 500 characters without a NUL character.",
    )
    .optional(),
});

export const depositFormSchema = z.object({
  amount: amountInputSchema,
  method: z.enum(["ONLINE_BANKING", "BANK_TRANSFER", "DEBIT_CARD"]),
});

export const profileSchema = z.object({
  fullName: z.string().trim().min(2, "Enter your full name").max(80),
  phone: z
    .string()
    .trim()
    .regex(/^(\+?6?0)?[0-9\s-]{8,13}$/, "Enter a valid Malaysian phone number")
    .or(z.literal("")),
});

export const changePasswordSchema = z
  .object({
    currentPassword: z.string().min(1, "Enter your current password"),
    newPassword: passwordSchema,
    confirmPassword: z.string().min(1, "Confirm your new password"),
  })
  .refine((data) => data.newPassword === data.confirmPassword, {
    path: ["confirmPassword"],
    message: "Passwords don't match",
  });

export type LoginForm = z.infer<typeof loginSchema>;
export type RegisterForm = z.infer<typeof registerSchema>;
export type TransferForm = z.infer<typeof transferFormSchema>;
export type DepositForm = z.infer<typeof depositFormSchema>;
export type ProfileForm = z.infer<typeof profileSchema>;
export type ChangePasswordForm = z.infer<typeof changePasswordSchema>;
