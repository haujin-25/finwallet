import { zodResolver } from "@hookform/resolvers/zod";
import { Loader2 } from "lucide-react";
import { useForm } from "react-hook-form";
import { toast } from "sonner";
import { FormField } from "@/components/common/form-field";
import { PasswordInput } from "@/components/common/password-input";
import { Button } from "@/components/ui/button";
import { useChangePassword } from "@/hooks/use-settings";
import { ApiError, getErrorMessage } from "@/services";
import { changePasswordSchema, type ChangePasswordForm } from "@/utils/validation";

export function PasswordForm() {
  const change = useChangePassword();
  const form = useForm<ChangePasswordForm>({
    resolver: zodResolver(changePasswordSchema),
    defaultValues: { currentPassword: "", newPassword: "", confirmPassword: "" },
  });
  const { errors } = form.formState;

  const onSubmit = form.handleSubmit(({ currentPassword, newPassword }) =>
    change.mutate(
      { currentPassword, newPassword },
      {
        onSuccess: () => {
          form.reset();
          toast.success("Password updated");
        },
        onError: (error) => {
          if (error instanceof ApiError && error.code === "INVALID_PASSWORD") {
            form.setError("currentPassword", { message: error.message });
          } else {
            toast.error(getErrorMessage(error));
          }
        },
      },
    ),
  );

  return (
    <form onSubmit={onSubmit} className="max-w-md space-y-5" noValidate>
      <FormField id="currentPassword" label="Current password" error={errors.currentPassword?.message}>
        <PasswordInput id="currentPassword" autoComplete="current-password" aria-invalid={!!errors.currentPassword} {...form.register("currentPassword")} />
      </FormField>
      <FormField id="newPassword" label="New password" error={errors.newPassword?.message} hint="At least 8 characters, with a letter and a number.">
        <PasswordInput id="newPassword" autoComplete="new-password" aria-invalid={!!errors.newPassword} {...form.register("newPassword")} />
      </FormField>
      <FormField id="confirmPassword" label="Confirm new password" error={errors.confirmPassword?.message}>
        <PasswordInput id="confirmPassword" autoComplete="new-password" aria-invalid={!!errors.confirmPassword} {...form.register("confirmPassword")} />
      </FormField>
      <Button type="submit" size="lg" disabled={change.isPending}>
        {change.isPending && <Loader2 className="animate-spin" />}
        Update password
      </Button>
    </form>
  );
}
