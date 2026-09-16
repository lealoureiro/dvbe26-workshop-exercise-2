package io.github.lealoureiro.greekgods;

import java.net.URI;
import java.util.List;
import java.util.UUID;

import jakarta.servlet.http.HttpServletRequest;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataAccessException;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.transaction.TransactionException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * The Greek gods catalogue, published over HTTP.
 *
 * <p>This controller publishes one operation, and it is a read. It offers nothing
 * that removes a record, changes a name, or changes an identifier (design D2), and
 * it holds no client for the third-party name source (design D3) — the catalogue is
 * served from local rows alone, so a request succeeds whether or not that service
 * is reachable.
 */
@RestController
class GreekGodController {

    private static final Logger log = LoggerFactory.getLogger(GreekGodController.class);

    private final GreekGodRepository greekGodRepository;

    GreekGodController(GreekGodRepository greekGodRepository) {
        this.greekGodRepository = greekGodRepository;
    }

    /**
     * Every Greek god the catalogue currently holds, ascending by identifier.
     *
     * <p>An empty catalogue is a valid served state, not a failure: it answers
     * {@code 200} with an empty array, so a consumer can tell "nothing is recorded"
     * apart from "the request did not work".
     */
    @GetMapping(path = "/api/v1/gods/greek", produces = MediaType.APPLICATION_JSON_VALUE)
    List<GreekGod> greekGods() {
        return greekGodRepository.findAll();
    }

    /**
     * Reports a catalogue read failure as the service's own, distinct from an empty
     * result.
     *
     * <p>The handler is local to this controller rather than a global advice: this
     * capability publishes exactly one operation, so the mapping belongs next to the
     * read it protects, and nothing else is silently affected by it. Promote it to a
     * {@code @RestControllerAdvice} when a second controller needs the same mapping.
     *
     * <p>The detail sent to the consumer is deliberately generic. The cause — which
     * may carry SQL, schema names, or connection strings — is logged server-side
     * against a correlation id and never serialized into the response.
     *
     * <p>{@link TransactionException} is handled alongside {@link DataAccessException}
     * because the two arrive from the same failure. When the database becomes
     * unreachable mid-request, the read fails with a {@code DataAccessException}, and
     * the surrounding read-only transaction then fails to roll back on the dead
     * connection — at which point Spring discards the original and throws
     * {@code TransactionSystemException} instead. That type is not a
     * {@code DataAccessException}, so catching only the latter left the real
     * database-outage path falling through to the default error page: a generic 500
     * carrying none of the published contract. Both mean the same thing to a
     * consumer — the catalogue could not be read.
     */
    @ExceptionHandler({ DataAccessException.class, TransactionException.class })
    ResponseEntity<ProblemDetail> handleCatalogueReadFailure(Exception ex,
                                                            HttpServletRequest request) {
        String errorId = UUID.randomUUID().toString();
        log.error("Greek gods catalogue could not be read, errorId={}", errorId, ex);

        ProblemDetail problem = ProblemDetail.forStatusAndDetail(
            HttpStatus.INTERNAL_SERVER_ERROR,
            "The Greek gods catalogue could not be read.");
        problem.setType(URI.create("https://github.com/lealoureiro/dvbe26-workshop-exercise-2/problems/catalogue-unavailable"));
        problem.setTitle("Catalogue unavailable");
        problem.setInstance(URI.create(request.getRequestURI()));
        problem.setProperty("errorId", errorId);

        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(problem);
    }
}
