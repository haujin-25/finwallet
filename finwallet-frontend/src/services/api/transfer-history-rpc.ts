import { createServerFn } from "@tanstack/react-start";
import { readTransferHistory } from "./transfer-history.server";

export const getTransferHistory = createServerFn({ method: "GET" })
  .inputValidator((input: { page: number; size: number }) => input)
  .handler(({ data }) => readTransferHistory(data));
