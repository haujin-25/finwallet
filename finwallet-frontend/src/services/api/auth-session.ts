import { createServerFn } from "@tanstack/react-start";
import { z } from "zod";
import type { LoginRequest, RegisterRequest } from "@/types";
import { endSession, readSession, registerAccount, startSession } from "./auth-session.server";

const credentials = z.object({
  email: z.string().trim().email().max(255),
  password: z.string().refine((password) => password.trim().length > 0),
});

const registration = credentials.extend({
  fullName: z.string().trim().min(1).max(100),
  password: z
    .string()
    .min(8)
    .max(72)
    .refine((password) => password.trim().length > 0),
});

const invalidInput = { ok: false, status: 400, message: "Check the details you entered." } as const;

export const loginSession = createServerFn({ method: "POST" })
  .inputValidator((input: LoginRequest) => {
    const result = credentials.safeParse(input);
    return result.success ? result.data : null;
  })
  .handler(({ data }) => (data ? startSession(data) : invalidInput));

export const registerSession = createServerFn({ method: "POST" })
  .inputValidator((input: RegisterRequest) => {
    const result = registration.safeParse(input);
    return result.success ? result.data : null;
  })
  .handler(({ data }) => (data ? registerAccount(data) : invalidInput));

export const getSession = createServerFn().handler(() => readSession());

export const logoutSession = createServerFn({ method: "POST" }).handler(() => endSession());
