import type { PaymentMethod } from "@/types";

export interface PaymentMethodOption {
  id: PaymentMethod;
  label: string;
  description: string;
  processingTime: string;
}

/** Display metadata for the simulated deposit methods. */
export const PAYMENT_METHODS: PaymentMethodOption[] = [
  {
    id: "ONLINE_BANKING",
    label: "Online Banking",
    description: "Simulated FPX-style redirect",
    processingTime: "Instant",
  },
  {
    id: "BANK_TRANSFER",
    label: "Bank Transfer",
    description: "Simulated manual transfer",
    processingTime: "1–2 business days",
  },
  {
    id: "DEBIT_CARD",
    label: "Debit Card",
    description: "Simulated card, no card details collected",
    processingTime: "Instant",
  },
];
