import { expect, it, vi } from "vitest";
import type { SpringTransferResult } from "@/types";
import { postTransfer } from "./transfer-rpc";
import { transferService } from "./transfer.service";

vi.mock("./transfer-rpc", () => ({ postTransfer: vi.fn() }));

it("shares an in-flight request and refuses to send a different payload under that key", async () => {
  let finish!: (result: SpringTransferResult) => void;
  vi.mocked(postTransfer).mockReturnValue(
    new Promise((resolve) => {
      finish = resolve;
    }),
  );
  const account = "alice@example.com";
  const request = {
    recipientEmail: "bob@example.com",
    amount: 1050,
    description: null,
    idempotencyKey: "key",
  };

  const first = transferService.send(account, request);
  const duplicate = transferService.send(account, { ...request });
  expect(await transferService.send(account, { ...request, amount: 1 })).toMatchObject({
    outcome: "unknown",
    status: 409,
  });
  expect(postTransfer).toHaveBeenCalledTimes(1);
  const outcome: SpringTransferResult = {
    outcome: "unknown",
    status: 0,
    message: "Connection lost",
  };
  finish(outcome);
  expect(await Promise.all([first, duplicate])).toEqual([outcome, outcome]);

  vi.mocked(postTransfer).mockResolvedValueOnce(outcome);
  await transferService.send(account, request);
  expect(postTransfer).toHaveBeenNthCalledWith(2, { data: { accountEmail: account, request } });
});
