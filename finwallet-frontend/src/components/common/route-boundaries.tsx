import { Link, useRouter, type ErrorComponentProps } from "@tanstack/react-router";
import { AlertTriangle, SearchX } from "lucide-react";
import { Button } from "@/components/ui/button";
import { getErrorMessage } from "@/services";

export function RouteError({ error, reset }: ErrorComponentProps) {
  const router = useRouter();
  return (
    <div className="mx-auto flex max-w-md flex-col items-center py-20 text-center" role="alert">
      <span className="mb-4 inline-flex size-12 items-center justify-center rounded-2xl bg-destructive-soft text-destructive">
        <AlertTriangle className="size-5" />
      </span>
      <h2 className="text-lg font-bold">We couldn't load this page</h2>
      <p className="mt-1 text-sm text-muted-foreground">{getErrorMessage(error)}</p>
      <Button
        className="mt-6"
        onClick={() => {
          router.invalidate();
          reset();
        }}
      >
        Try again
      </Button>
    </div>
  );
}

export function RouteNotFound({ title = "Not found", description }: { title?: string; description?: string }) {
  return (
    <div className="mx-auto flex max-w-md flex-col items-center py-20 text-center">
      <span className="mb-4 inline-flex size-12 items-center justify-center rounded-2xl bg-muted text-muted-foreground">
        <SearchX className="size-5" />
      </span>
      <h2 className="text-lg font-bold">{title}</h2>
      {description && <p className="mt-1 text-sm text-muted-foreground">{description}</p>}
      <Button asChild variant="outline" className="mt-6">
        <Link to="/overview">Back to overview</Link>
      </Button>
    </div>
  );
}
