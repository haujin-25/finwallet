import { useMutation, useQueryClient } from "@tanstack/react-query";
import { userService } from "@/services";
import type { ChangePasswordRequest, NotificationPreferences, UpdateProfileRequest } from "@/types";
import { queryKeys } from "./queries";

export function useUpdateProfile() {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: (req: UpdateProfileRequest) => userService.updateProfile(req),
    onSuccess: (user) => queryClient.setQueryData(queryKeys.currentUser, user),
  });
}

export function useChangePassword() {
  return useMutation({ mutationFn: (req: ChangePasswordRequest) => userService.changePassword(req) });
}

export function useUpdateNotificationPreferences() {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: (req: NotificationPreferences) => userService.updateNotificationPreferences(req),
    onSuccess: (prefs) => queryClient.setQueryData(queryKeys.notificationPreferences, prefs),
  });
}
