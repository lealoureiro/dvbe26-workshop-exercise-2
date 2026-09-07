# Feature 1 — Greek Gods API

## What we're building

A tiny REST API that hands back a list of Greek gods.

You call:

```
GET /api/v1/gods/greek
```

And you get back JSON like this:

```json
[
  { "id": 1, "name": "Zeus" },
  { "id": 2, "name": "Hera" }
]
```

That's it. Two fields per god: a number `id` and a `name` string.

## Where the data comes from

We don't call anybody else's server when a user hits our endpoint. That would be
slow and it would break every time the other guy is down.

Instead, we keep our own copy in a PostgreSQL table (`greek_god`, with `id` and a
unique `name`). Something in the background goes and fetches the names from:

```
https://my-json-server.typicode.com/jabrena/latency-problems/greek
```

That external service just returns a plain list of names, like
`["Zeus", "Hera", "Poseidon"]` — no IDs, nothing else. We're the ones who assign
the IDs when we save them.

## The one rule that matters on sync

The **name is the key**. When we sync:

- Name we've never seen? Insert it, give it a fresh ID.
- Name we already have? Leave it alone. **Do not touch its ID.**

IDs have to stay stable forever, because people out there might be storing them.
If Zeus is `1` today, Zeus is `1` after the next sync too.

## What we return

- Everything fine → `200 OK`, JSON array.
- Something blew up on our side → `500 Internal Server Error`.

No auth needed, it's a public endpoint.

## Done when

- The endpoint works and returns the right shape.
- The background sync inserts new names and keeps old IDs.
- The OpenAPI spec matches what the code actually does.

