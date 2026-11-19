<!-- LOVABLE:BEGIN -->
> [!IMPORTANT]
> This project is connected to [Lovable](https://lovable.dev). Avoid rewriting
> published git history — force pushing, or rebasing/amending/squashing commits
> that are already pushed — as it rewrites history on Lovable's side and the
> user will likely lose their project history.
>
> Commits you push to the connected branch sync back to Lovable and show up in
> the editor, so keep the branch in a working state.

## What Lovable manages

Rules outside this block are the project's own. Change the pieces below in
Lovable, not by hand. With the [Lovable MCP](https://mcp.lovable.dev)
connected, `send_message` asks Lovable to do it; otherwise ask the user to
use the editor.

- **Database.** Do not add or edit `supabase/migrations/` or `drizzle/`.
  Ask Lovable to change the schema.
- **Generated files.** Do not edit a file whose header says it is automatically
  generated.
- **AI gateway.** Keep model calls on Lovable's gateway, and keep
  `LOVABLE_API_KEY` out of browser code.
- **Connectors.** Lovable provides native connectors. `list_connectors` shows
  which are enabled; `add_connector` only returns the dashboard link. When a
  connector is enabled, calls go through the connector gateway and need
  `LOVABLE_API_KEY`. When Lovable does not provide one, users can bring their
  own key and store it in Secrets.
- **Secrets.** Lovable manages them. Do not put secret values in the repo.
<!-- LOVABLE:END -->

## Architecture

- All data access goes through `src/services/api/*.service.ts`; each switches on `env.useMockApi` between `src/services/mock` and `apiClient`. Why: the NestJS backend replaces the mock without touching UI.
- UI never imports from `src/services/mock`; demo-only hints live in `src/config/demo.ts`. Why: mock removal stays a one-folder change.
- Money is integer minor units (sen) end to end; parse with `parseAmountToMinor`, display with `formatMoney`. Why: no floating-point money.
- Balances, fees and transaction outcomes are decided server-side (mock rules in `src/services/mock/mock-rules.ts`); components only display results. Why: no financial logic in the frontend.
- Signed-in pages live under `src/routes/_authenticated/` with `ssr: false`; the session is read from browser storage there. Why: tokens are client-held.
- Route loaders prime TanStack Query via `src/hooks/queries.ts`; components read with `useSuspenseQuery`. Mutations invalidate `["wallet"]` and `["transactions"]`.
- Page modules are route files in `src/routes/` (TanStack Router replaces React Router). Why: the template's router is fixed.
