import { zodResolver } from "@hookform/resolvers/zod";
import { Link, createFileRoute, redirect, useNavigate } from "@tanstack/react-router";
import { Loader2 } from "lucide-react";
import { useForm } from "react-hook-form";
import { AuthLayout } from "@/components/auth/auth-layout";
import { FormField } from "@/components/common/form-field";
import { PasswordInput } from "@/components/common/password-input";
import { Button } from "@/components/ui/button";
import { Input } from "@/components/ui/input";
import { DEMO_MODE } from "@/config/demo";
import { useRegister } from "@/hooks/use-auth";
import { ApiError, authService, getErrorMessage } from "@/services";
import { registerSchema, type RegisterForm } from "@/utils/validation";

export const Route = createFileRoute("/register")({
  ssr: false,
  beforeLoad: async () => {
    if (await authService.getCurrentUser().catch(() => null)) throw redirect({ to: "/overview" });
  },
  head: () => ({
    meta: [
      { title: "Create your account — FinWallet" },
      { name: "description", content: "Open a FinWallet digital wallet in under a minute." },
      { property: "og:title", content: "Create your account — FinWallet" },
      { property: "og:description", content: "Open a FinWallet digital wallet in under a minute." },
      { property: "og:type", content: "website" },
      { name: "twitter:card", content: "summary" },
    ],
  }),
  component: RegisterPage,
});

function RegisterPage() {
  const navigate = useNavigate();
  const register = useRegister();
  const form = useForm<RegisterForm>({
    resolver: zodResolver(registerSchema),
    defaultValues: { fullName: "", email: "", password: "", confirmPassword: "" },
  });
  const { errors } = form.formState;

  const onSubmit = form.handleSubmit(({ fullName, email, password }) =>
    register.mutate(
      { fullName, email, password },
      {
        onSuccess: () =>
          DEMO_MODE
            ? navigate({ to: "/overview", replace: true })
            : navigate({ to: "/login", search: { registered: true }, replace: true }),
        onError: (error) => {
          if (error instanceof ApiError && error.status === 409) {
            form.setError("email", { message: error.message });
          }
        },
      },
    ),
  );

  const rootError = register.isError && !(register.error instanceof ApiError && register.error.status === 409);

  return (
    <AuthLayout title="Create your account" subtitle="It takes less than a minute. No card required.">
      <form onSubmit={onSubmit} className="space-y-5" noValidate>
        <FormField id="fullName" label="Full name" error={errors.fullName?.message}>
          <Input id="fullName" autoComplete="name" placeholder="As shown on your IC" aria-invalid={!!errors.fullName} {...form.register("fullName")} />
        </FormField>
        <FormField id="email" label="Email" error={errors.email?.message}>
          <Input id="email" type="email" autoComplete="email" placeholder="you@example.com" aria-invalid={!!errors.email} {...form.register("email")} />
        </FormField>
        <FormField id="password" label="Password" error={errors.password?.message} hint={DEMO_MODE ? "At least 8 characters, with a letter and a number." : "Use 8 to 72 characters."}>
          <PasswordInput id="password" autoComplete="new-password" aria-invalid={!!errors.password} {...form.register("password")} />
        </FormField>
        <FormField id="confirmPassword" label="Confirm password" error={errors.confirmPassword?.message}>
          <PasswordInput id="confirmPassword" autoComplete="new-password" aria-invalid={!!errors.confirmPassword} {...form.register("confirmPassword")} />
        </FormField>

        {rootError && (
          <p className="rounded-lg bg-destructive-soft px-3 py-2.5 text-sm font-medium text-destructive" role="alert">
            {getErrorMessage(register.error)}
          </p>
        )}

        <Button type="submit" size="xl" className="w-full" disabled={register.isPending}>
          {register.isPending && <Loader2 className="animate-spin" />}
          Create account
        </Button>
        <p className="text-center text-xs leading-relaxed text-muted-foreground">
          {DEMO_MODE
            ? "This is a prototype. Your account and wallet are simulated and hold no real money."
            : "FinWallet is a portfolio project. Wallets hold no real money."}
        </p>
      </form>

      <p className="mt-8 text-center text-sm text-muted-foreground">
        Already have an account?{" "}
        <Link to="/login" className="font-semibold text-primary hover:underline">
          Sign in
        </Link>
      </p>
    </AuthLayout>
  );
}
