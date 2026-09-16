package io.github.lealoureiro.greekgods;

/**
 * A Greek god as this service publishes it: an identifier minted here, and a name.
 *
 * <p>The identifier is a plain whole number rather than a wrapper type (design D8).
 * There is exactly one identifier in this domain and nothing to confuse it with, so
 * a {@code GodId} would add a type and a mapping step to guard against a collision
 * that cannot occur. Revisit if a second identifier ever enters the model.
 *
 * <p>This is also the published response shape: {@code {id, name}} and nothing else.
 * A separate response DTO would decouple the API from persistence, but there is no
 * internal field to hide here — the record already carries exactly what a consumer
 * receives — so the extra type would pay for nothing (design D7).
 */
record GreekGod(long id, String name) {
}
