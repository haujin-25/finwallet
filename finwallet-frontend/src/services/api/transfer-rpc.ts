import { createServerFn } from "@tanstack/react-start";
import type { SpringTransferRequest } from "@/types";
import { sendTransfer } from "./transfer.server";

export const postTransfer = createServerFn({ method: "POST" })
  .inputValidator((input: { accountEmail: string; request: SpringTransferRequest }) => input)
  .handler(({ data }) => sendTransfer(data));
