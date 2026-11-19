import { createServerFn } from "@tanstack/react-start";
import { readMyWallet } from "./wallet.server";

export const getCurrentWallet = createServerFn({ method: "GET" }).handler(() => readMyWallet());
