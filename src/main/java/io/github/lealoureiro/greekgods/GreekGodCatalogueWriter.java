package io.github.lealoureiro.greekgods;

import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * The catalogue's only writer (design D1). {@link GreekGodRepository} stays
 * read-only; this is the seam design D1's context calls "the only writer that
 * catalogue will have" — kept in its own type rather than added to the
 * repository, so the read path's "exactly one operation, and it is a query"
 * guarantee stays literally true.
 *
 * <p>There is exactly one write operation here, and it is an insert. Nothing in
 * this class, and nothing anywhere else in this component, ever issues an
 * {@code UPDATE} or a {@code DELETE} against {@code greek_god} — that absence
 * is what design D1's "insert-only" guarantee rests on (verified for task 3.4
 * by inspection: this is the only class in the component that writes to the
 * table, and its only SQL statement is the {@code INSERT} below).
 */
@Component
class GreekGodCatalogueWriter {

    private final JdbcClient jdbcClient;

    GreekGodCatalogueWriter(JdbcClient jdbcClient) {
        this.jdbcClient = jdbcClient;
    }

    /**
     * Takes up {@code name} if the catalogue does not already hold it,
     * matching case-insensitively against the {@code uq_greek_god_name_lower}
     * expression index that {@code greek-gods-catalogue-read} already created
     * (design D2, D3).
     *
     * <p>This does not read, compare, and then decide in application code —
     * that would reopen the concurrency window a conditional insert closes.
     * Instead the statement itself does nothing when the name is already
     * present, so the database's own unique constraint is the single arbiter
     * of "new" (design D2), and two concurrent callers offering the same name
     * can never conflict, only one of them ever inserting.
     *
     * <p>Committed independently of any other name in the same reconciliation
     * run (design D8): this method carries its own {@code @Transactional}
     * boundary, distinct from the read-only default on
     * {@link GreekGodRepository}, so a failure taking up one name can never
     * undo a name already taken up earlier in the same run.
     *
     * @param name the candidate name, already trimmed and confirmed non-blank
     *             by the caller (design D4)
     * @return {@code true} when a new record was inserted; {@code false} when
     *         the name (or a case variant of it) was already held and nothing
     *         changed
     * @throws DataAccessException when the insert itself fails for a reason
     *                              other than the name already being held —
     *                              for example a database outage, or a name
     *                              too long for the {@code name} column. The
     *                              caller isolates this per name (design D8).
     */
    @Transactional
    boolean takeUpIfUnseen(String name) {
        int inserted = jdbcClient.sql("""
                INSERT INTO greek_god (name)
                VALUES (?)
                ON CONFLICT (lower(name)) DO NOTHING
                """)
            .param(name)
            .update();
        return inserted > 0;
    }
}
