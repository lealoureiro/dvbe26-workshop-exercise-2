# Feature 1 — Greek Gods API

## What we're building

A small public REST API that returns a list of Greek gods. Each god in the
response carries just two things: an id and a name.

The read endpoint is deliberately self-contained — it reads from our own
database and nothing else. The data itself is kept fresh in the background by a
scheduled task that pulls from an external service, so a user request never
waits on, or fails because of, a third party.

## Main features

- **Read endpoint** — returns the list of Greek gods; each entry exposes an id
  and a name.
- **Public access** — no authentication required.
- **No runtime dependencies beyond the database** — serving a request must not
  call any external service; if the upstream provider is down or slow, the
  endpoint is unaffected.
- **Own copy of the data** — stored in a PostgreSQL table with an
  id and a unique name.
- **Asynchronous refresh via scheduled task** — a background job periodically
  fetches the god names from the external service described in
  `oas/provided/my-json-server-oas.yaml`, outside of any user request.
- **Upstream supplies names only** — the external service returns a plain list
  of names with no ids.
- **Name is the key on sync** — an unseen name is inserted with a fresh id; a
  name we already hold is left untouched.
- **Stable ids forever** — a sync must never change the id of an existing god,
  because consumers may have stored it. If Zeus is `1` today, Zeus is `1` after
  every future sync.
- **Failures surface as a server-side error** — problems on our side are
  reported as such rather than hidden or served as partial data.
