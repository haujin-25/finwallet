import { zodResolver } from "@hookform/resolvers/zod";
import { Link, createFileRoute } from "@tanstack/react-router";
import { ArrowLeft, Loader2, MailCheck } from "lucide-react";
import { useForm } from "react-hook-form";
import { AuthLayout } from "@/components/auth/auth-layout";
import { FormField } from "@/components/common/form-field";
import { Button } from "@/components/ui/button";
import { Input } from "@/components/ui/input";
import { useForgotPassword } from "@/hooks/use-auth";
import { getErrorMessage } from "@/services";
import { forgotPasswordSchema } from "@/utils/validation";

export const Route = createFileRoute("/forgot-password")({
  ssr: false,
  head: () => ({
    meta: [
      { title: "Reset your password — FinWallet" },
      { name: "description", content: "Request a password reset link for your FinWallet account." },
      { property: "og:title", content: "Reset your password — FinWallet" },
      { property: "og:description", content: "Request a password reset link for your FinWallet account." },
      { property: "og:type", content: "website" },
      { name: "twitter:card", content: "summary" },
    ],
  }),
  component: ForgotPasswordPage,
});

function ForgotPasswordPage() {
  const forgot = useForgotPassword();
  const form = useForm<{ email: string }>({ resolver: zodResolver(forgotPasswordSchema), defaultValues: { email: "" } });
  const { errors } = form.formState;

  if (forgot.isSuccess) {
    return (
      <AuthLayout title="Check your inbox" subtitle={forgot.data.message}>
        <div className="flex items-start gap-3 rounded-xl bg-success-soft p-4">
          <MailCheck className="mt-0.5 size-5 shrink-0 text-success" />
          <p className="text-sm text-foreground">
            We've sent instructions to <span className="font-semibold">{forgot.variables?.email}</span>. In this
            prototype no email is actually sent.
          </p>
        </div>
        <Button asChild variant="outline" size="lg" className="mt-6 w-full">
          <Link to="/login">
            <ArrowLeft /> Back to sign in
          </Link>
        </Button>
      </AuthLayout>
    );
  }

  return (
    <AuthLayout title="Forgot your password?" subtitle="Enter your email and we'll send you a link to reset it.">
      <form onSubmit={form.handleSubmit((v) => forgot.mutate(v))} className="space-y-5" noValidate>
        <FormField id="email" label="Email" error={errors.email?.message}>
          <Input id="email" type="email" autoComplete="email" placeholder="you@example.com" aria-invalid={!!errors.email} {...form.register("email")} />
        </FormField>
        {forgot.isError && (
          <p className="rounded-lg bg-destructive-soft px-3 py-2.5 text-sm font-medium text-destructive" role="alert">
            {getErrorMessage(forgot.error)}
          </p>
        )}
        <Button type="submit" size="xl" className="w-full" disabled={forgot.isPending}>
          {forgot.isPending && <Loader2 className="animate-spin" />}
          Send reset link
        </Button>
      </form>
      <Link to="/login" className="mt-8 inline-flex items-center gap-1.5 text-sm font-semibold text-primary hover:underline">
        <ArrowLeft className="size-4" /> Back to sign in
      </Link>
    </AuthLayout>
  );
}
