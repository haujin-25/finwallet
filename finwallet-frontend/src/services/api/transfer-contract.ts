import { z } from "zod";
import type { SpringTransferRequest } from "@/types";

export const springTransferRequest: z.ZodType<SpringTransferRequest> = z.object({
  recipientEmail: z.string().email().max(255),
  amount: z.number().int().safe().positive(),
  description: z
    .string()
    .refine((value) => !value.includes("\0") && [...value].length <= 500)
    .nullable(),
  idempotencyKey: z
    .string()
    .min(1)
    .max(255)
    .regex(/^[A-Za-z0-9._:-]+$/),
});
