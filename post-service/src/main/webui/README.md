# Your Say News admin

The TypeScript/React administration UI is built and served by Quarkus Quinoa at `/admin`.

- `src/app` composes the application.
- `src/pages` owns route-level screens.
- `src/shared` contains domain-agnostic components and the editorial design tokens.
- Future admin capabilities should live in `src/features/<domain>` and export a public API from
  that feature's `index.ts`.

Use Bun for direct frontend commands:

```shell
bun install
bun run typecheck
bun run build
```
