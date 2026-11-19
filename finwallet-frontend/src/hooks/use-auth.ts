import { useMutation, useQueryClient, useSuspenseQuery } from "@tanstack/react-query";
import { toast } from "sonner";
import { useNavigate } from "@tanstack/react-router";
import { authService, getErrorMessage } from "@/services";
import type { ForgotPasswordRequest, LoginRequest, RegisterRequest } from "@/types";
import { currentUserQuery } from "./queries";

/** Signed-in user. Only use under the authenticated layout, which preloads it. */
export function useCurrentUser() {
  const user = useSuspenseQuery(currentUserQuery()).data;
  if (!user) throw new Error("Your session has ended. Sign in again.");
  return user;
}

export function useLogin() {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: (req: LoginRequest) => authService.login(req),
    onSuccess: () => queryClient.clear(),
  });
}

export function useRegister() {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: (req: RegisterRequest) => authService.register(req),
    onSuccess: () => queryClient.clear(),
  });
}

export function useForgotPassword() {
  return useMutation({
    mutationFn: (req: ForgotPasswordRequest) => authService.forgotPassword(req),
  });
}

export function useLogout() {
  const queryClient = useQueryClient();
  const navigate = useNavigate();
  return async () => {
    await queryClient.cancelQueries();
    try {
      await authService.logout();
    } catch (error) {
      toast.error(getErrorMessage(error));
      return;
    }
    queryClient.clear();
    await navigate({ to: "/login", replace: true });
  };
}
