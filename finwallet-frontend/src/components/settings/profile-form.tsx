import { zodResolver } from "@hookform/resolvers/zod";
import { Loader2 } from "lucide-react";
import { useForm } from "react-hook-form";
import { toast } from "sonner";
import { FormField } from "@/components/common/form-field";
import { Avatar, AvatarFallback } from "@/components/ui/avatar";
import { Button } from "@/components/ui/button";
import { Input } from "@/components/ui/input";
import { useUpdateProfile } from "@/hooks/use-settings";
import { getErrorMessage } from "@/services";
import type { User } from "@/types";
import { formatDate } from "@/utils/date";
import { initialsOf } from "@/utils/labels";
import { profileSchema, type ProfileForm as ProfileFormValues } from "@/utils/validation";

export function ProfileForm({ user }: { user: User }) {
  const update = useUpdateProfile();
  const form = useForm<ProfileFormValues>({
    resolver: zodResolver(profileSchema),
    defaultValues: { fullName: user.fullName, phone: user.phone ?? "" },
  });
  const { errors, isDirty } = form.formState;

  const onSubmit = form.handleSubmit((values) =>
    update.mutate(
      { fullName: values.fullName, phone: values.phone || null },
      {
        onSuccess: (saved) => {
          form.reset({ fullName: saved.fullName, phone: saved.phone ?? "" });
          toast.success("Profile updated");
        },
        onError: (error) => toast.error(getErrorMessage(error)),
      },
    ),
  );

  return (
    <div className="space-y-6">
      <div className="flex items-center gap-4">
        <Avatar className="size-16">
          <AvatarFallback className="bg-primary text-lg font-bold text-primary-foreground">{initialsOf(user.fullName)}</AvatarFallback>
        </Avatar>
        <div>
          <p className="text-lg font-bold">{user.fullName}</p>
          <p className="font-mono text-xs text-muted-foreground">{user.userCode}</p>
          <p className="text-xs text-muted-foreground">Member since {formatDate(user.createdAt)}</p>
        </div>
      </div>

      <form onSubmit={onSubmit} className="grid gap-5 sm:grid-cols-2" noValidate>
        <FormField id="fullName" label="Full name" error={errors.fullName?.message}>
          <Input id="fullName" aria-invalid={!!errors.fullName} {...form.register("fullName")} />
        </FormField>
        <FormField id="phone" label="Phone number" error={errors.phone?.message}>
          <Input id="phone" placeholder="+60 12-345 6789" aria-invalid={!!errors.phone} {...form.register("phone")} />
        </FormField>
        <div className="sm:col-span-2">
          <FormField id="email" label="Email" hint="Changing your email needs verification and isn't available in this prototype yet.">
            <Input id="email" value={user.email} disabled readOnly />
          </FormField>
        </div>
        <div className="sm:col-span-2">
          <Button type="submit" size="lg" disabled={!isDirty || update.isPending}>
            {update.isPending && <Loader2 className="animate-spin" />}
            Save changes
          </Button>
        </div>
      </form>
    </div>
  );
}
