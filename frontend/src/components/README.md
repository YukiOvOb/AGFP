# Shared Frontend Components

Building blocks for every page (Issue #16, Proposal 7.4). Use these before writing page-specific markup or CSS, so all pages look and behave the same and the later visual pass changes one place.

Run the app in development and open `/dev/components` to see every component with sample data; the source is [ui/Gallery.tsx](ui/Gallery.tsx). That page is not included in production builds.

## What to Use

| Need                | Import                                                                                                        | Notes                                                                                |
| ------------------- | ------------------------------------------------------------------------------------------------------------- | ------------------------------------------------------------------------------------ |
| Page title          | `PageHeader` from `@/components/ui/page-header`                                                               | One per page: eyebrow, title, description, actions                                   |
| Panel               | `Card`, `CardHeader`, `CardTitle`, `CardDescription`, `CardContent`, `CardFooter` from `@/components/ui/card` |                                                                                      |
| Table               | `DataTable` from `@/components/ui/data-table`                                                                 | Sorting, paging, loading and empty states; pass `paging` when the API pages the data |
| Form field          | `Field` from `@/components/ui/field` with `Input`, `Select`, `Textarea`                                       | Binds label, hint and error to the control; works with `register()`                  |
| Button              | `Button` from `@/components/ui/button`                                                                        | `variant`: default, outline, ghost, destructive; `size="sm"`                         |
| Status              | `StatusBadge` from `@/components/ui/badge` with a map from `@/lib/status`                                     | `<StatusBadge status={reservationStatus[item.status]} />`                            |
| Plain label         | `Badge`                                                                                                       | `tone`: ok, pending, bad, muted, info                                                |
| Form in a dialog    | `Dialog…` from `@/components/ui/dialog`                                                                       | Always include a `DialogTitle`                                                       |
| Confirmation        | `ConfirmDialog` from `@/components/ui/confirm-dialog`                                                         | Before anything hard to undo; `destructive` for removal                              |
| Result of an action | `toast` from `@/components/ui/toast`                                                                          | `toast.success('Saved')`, `toast.error(message(error))`                              |
| Views of one page   | `Tabs…` from `@/components/ui/tabs`                                                                           |                                                                                      |
| Loading             | `Skeleton`, or `loading` on `DataTable`; `LoadingState` for a whole page                                      |                                                                                      |
| Nothing to show     | `EmptyState` from `@/components/Feedback`                                                                     | Pass `title`, `description`, optional `action`                                       |
| Request failed      | `ErrorState` from `@/components/Feedback`                                                                     | Pass `onRetry`                                                                       |

## Rules

- **Names come from one place.** Show roles with `roleLabel` / `roleLabels` from `@/lib/roles` (the stored `CUSTODIAN` and `MAINTAINER` read "Storekeeper" and "Technician") and statuses from the maps in `@/lib/status`. Never print an API code. Add a new status to the map, not to a page.
- **Colours come from tokens.** They are defined in [`src/styles.css`](../styles.css) and available as Tailwind utilities: `bg-card`, `text-muted-foreground`, `border-border`, `text-destructive`, `bg-secondary`. Do not write hex colours in a page.
- **Style with utilities.** Tailwind utilities on your markup override the older shared styles, because those now live in the `components` cascade layer. Add a rule to a stylesheet only when utilities cannot express it.
- **Validation messages stay next to the field** (`Field error={...}`); toasts are for the outcome of an action.
- **Menus are not security.** Hiding a link by role is for convenience; the backend decides access.
- **Time** is shown in Singapore time with the helpers in `@/lib/time`.

## Adding a Page to the Menu

Paths and labels for the pages still to be built are already reserved in [`src/app/navigation.ts`](../app/navigation.ts) (`/overdue`, `/manage/equipment`, `/reports`, `/admin/users`, `/admin/audit`). In the pull request that adds the page:

1. Add the `<Route>` in `src/app/App.tsx`, inside `RequireRole` when it is limited to a role.
2. Set `available: true` on its entry in `navigation.ts`.

The sidebar and the overview page pick it up for the listed roles. A new page that is not in the list gets a new entry there, not a link written into `Layout.tsx`.

## Tests

- Pages that call `toast` and assert on the message render `<Toaster />` from `@/components/ui/toast` in the test.
- On the overview page a menu label also appears on a card; in browser tests select menu links with `exact: true`.
- Signing in without a page to return to lands on `/` (the overview), not on notifications.
