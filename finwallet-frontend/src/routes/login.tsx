import { zodResolver } from "@hookform/resolvers/zod";
import { Link, createFileRoute, redirect, useNavigate } from "@tanstack/react-router";
import { Loader2, Sparkles } from "lucide-react";
import { useForm } from "react-hook-form";
import { AuthLayout } from "@/components/auth/auth-layout";
import { FormField } from "@/components/common/form-field";
import { PasswordInput } from "@/components/common/password-input";
import { Button } from "@/components/ui/button";
import { Input } from "@/components/ui/input";
import { DEMO_ACCOUNT, DEMO_MODE } from "@/config/demo";
import { useLogin } from "@/hooks/use-auth";
import { authService, getErrorMessage } from "@/services";
import { loginSchema, type LoginForm } from "@/utils/validation";

export const Route = createFileRoute("/login")({
  ssr: false,
  validateSearch: (search: Record<string, unknown>): {
    registered?: true | undefined;
    redirect?: string | undefined;
  } => ({
    registered: search["registered"] === true ? true : undefined,
    redirect:
      typeof search["redirect"] === "string" && search["redirect"].startsWith("/") && !search["redirect"].startsWith("//") && !/[\\\s]/.test(search["redirect"])
        ? search["redirect"]
        : undefined,
  }),
  beforeLoad: async () => {
    if (await authService.getCurrentUser().catch(() => null)) throw redirect({ to: "/overview" });
  },
  head: () => ({
    meta: [
      { title: "Sign in — FinWallet" },
      { name: "description", content: "Sign in to your FinWallet digital wallet." },
      { property: "og:title", content: "Sign in — FinWallet" },
      { property: "og:description", content: "Sign in to your FinWallet digital wallet." },
      { property: "og:type", content: "website" },
      { name: "twitter:card", content: "summary" },
    ],
  }),
  component: LoginPage,
});

function LoginPage() {
  const search = Route.useSearch();
  const navigate = useNavigate();
  const login = useLogin();
  const form = useForm<LoginForm>({ resolver: zodResolver(loginSchema), defaultValues: { email: "", password: "" } });
  const { errors } = form.formState;

  const onSubmit = form.handleSubmit((values) =>
    login.mutate(values, {
      onSuccess: () => navigate({ href: search.redirect ?? "/overview", replace: true }),
    }),
  );

  return (
    <AuthLayout title="Welcome back" subtitle="Sign in to see your balance and recent activity.">
      {search.registered && (
        <p className="mb-4 rounded-lg bg-success-soft px-3 py-2.5 text-sm" role="status">
          Account created. Sign in to continue.
        </p>
      )}
      <form onSubmit={onSubmit} className="space-y-5" noValidate>
        <FormField id="email" label="Email" error={errors.email?.message}>
          <Input
            id="email"
            type="email"
            autoComplete="email"
            placeholder="you@example.com"
            aria-invalid={!!errors.email}
            {...form.register("email")}
          />
        </FormField>
        <FormField
          id="password"
          label="Password"
          error={errors.password?.message}
          labelAction={
            DEMO_MODE ? <Link to="/forgot-password" className="text-xs font-semibold text-primary hover:underline">
              Forgot password?
            </Link> : undefined
          }
        >
          <PasswordInput
            id="password"
            autoComplete="current-password"
            placeholder="••••••••"
            aria-invalid={!!errors.password}
            {...form.register("password")}
          />
        </FormField>

        {login.isError && (
          <p className="rounded-lg bg-destructive-soft px-3 py-2.5 text-sm font-medium text-destructive" role="alert">
            {getErrorMessage(login.error)}
          </p>
        )}

        <Button type="submit" size="xl" className="w-full" disabled={login.isPending}>
          {login.isPending && <Loader2 className="animate-spin" />}
          Sign in
        </Button>
      </form>

      {DEMO_MODE && (
        <button
          type="button"
          onClick={() => {
            form.setValue("email", DEMO_ACCOUNT.email, { shouldValidate: true });
            form.setValue("password", DEMO_ACCOUNT.password, { shouldValidate: true });
          }}
          className="mt-4 flex w-full items-center gap-3 rounded-xl border border-dashed bg-muted/60 px-4 py-3 text-left transition-colors hover:bg-muted"
        >
          <Sparkles className="size-4 shrink-0 text-success" />
          <span className="text-xs leading-relaxed text-muted-foreground">
            <span className="block font-semibold text-foreground">Use the demo account</span>
            {DEMO_ACCOUNT.email} · {DEMO_ACCOUNT.password}
          </span>
        </button>
      )}

      <p className="mt-8 text-center text-sm text-muted-foreground">
        New to FinWallet?{" "}
        <Link to="/register" className="font-semibold text-primary hover:underline">
          Create an account
        </Link>
      </p>
    </AuthLayout>
  );
}
