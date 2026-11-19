import { env } from "@/config/env";
import type { ChangePasswordRequest, NotificationPreferences, UpdateProfileRequest, User } from "@/types";
import { mockAuth } from "../mock/mock-auth";
import { apiClient } from "./client";

export const userService = {
  updateProfile(req: UpdateProfileRequest): Promise<User> {
    return env.useMockApi ? mockAuth.updateProfile(req) : apiClient.patch<User>("/users/me", req);
  },

  changePassword(req: ChangePasswordRequest): Promise<void> {
    return env.useMockApi ? mockAuth.changePassword(req) : apiClient.post<void>("/users/me/password", req);
  },

  getNotificationPreferences(): Promise<NotificationPreferences> {
    return env.useMockApi
      ? mockAuth.getPreferences()
      : apiClient.get<NotificationPreferences>("/users/me/notification-preferences");
  },

  updateNotificationPreferences(req: NotificationPreferences): Promise<NotificationPreferences> {
    return env.useMockApi
      ? mockAuth.updatePreferences(req)
      : apiClient.put<NotificationPreferences>("/users/me/notification-preferences", req);
  },
};
