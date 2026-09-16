package io.github.lealoureiro.greekgods;

import java.util.List;

import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

/**
 * Read-only access to the Greek gods catalogue.
 *
 * <p>This type publishes exactly one operation, and it is a query. There is no
 * insert, update, or delete here and none anywhere else in this capability, so no
 * consumer of this change can remove a record, change a name, or change an
 * identifier (design D2). Identifier permanence is held by that absence.
 *
 * <p>Nothing here holds a client for the third-party name source (design D3): the
 * catalogue is answered from local rows alone, so neither the latency nor the
 * availability of that service can affect a request.
 */
@Repository
@Transactional(readOnly = true)
class GreekGodRepository {

    private final JdbcClient jdbcClient;

    GreekGodRepository(JdbcClient jdbcClient) {
        this.jdbcClient = jdbcClient;
    }

    /**
     * Every record in the catalogue, in ascending identifier order.
     *
     * <p>The ordering is applied by the database (design D6). Sorting in Java, or
     * leaving the order to the query planner, would both make the published
     * ordering guarantee an accident rather than a promise.
     *
     * @return every catalogue record, ascending by identifier; empty when the
     *         catalogue holds nothing, which is a valid state and not a failure
     */
    List<GreekGod> findAll() {
        return jdbcClient.sql("SELECT id, name FROM greek_god ORDER BY id ASC")
            .query(GreekGod.class)
            .list();
    }
}
