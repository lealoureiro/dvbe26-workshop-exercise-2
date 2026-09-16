package io.github.lealoureiro.greekgods;

import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.assertj.MockMvcTester;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

/**
 * Response mapping for {@code GET /api/v1/gods/greek}.
 *
 * <p>A context-free unit test: the repository is a Mockito mock and the controller
 * is constructed directly, so no Spring context, no database, and no container is
 * involved and this runs in the fast Surefire phase. {@code MockMvcTester} still
 * exercises the real request mapping, the real Jackson serialization, and the
 * controller's own exception handler, which is what "response mapping" means here.
 */
@ExtendWith(MockitoExtension.class)
class GreekGodControllerTest {

    private static final String ENDPOINT = "/api/v1/gods/greek";

    @Mock
    private GreekGodRepository greekGodRepository;

    private MockMvcTester mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcTester.of(new GreekGodController(greekGodRepository));
    }

    @Test
    void mapsCatalogueRecordsToIdAndNameAndNothingElse() {
        when(greekGodRepository.findAll())
            .thenReturn(List.of(new GreekGod(1L, "Zeus"), new GreekGod(2L, "Hera")));

        assertThat(mockMvc.get().uri(ENDPOINT))
            .hasStatusOk()
            .hasContentTypeCompatibleWith(MediaType.APPLICATION_JSON)
            .bodyJson()
            // Strict: an extra field, a missing field, or a different order all fail.
            .isStrictlyEqualTo("""
                [{"id":1,"name":"Zeus"},{"id":2,"name":"Hera"}]
                """);
    }

    @Test
    void treatsAnEmptyCatalogueAsASuccessfulEmptyResult() {
        when(greekGodRepository.findAll()).thenReturn(List.of());

        assertThat(mockMvc.get().uri(ENDPOINT))
            .hasStatusOk()
            .hasContentTypeCompatibleWith(MediaType.APPLICATION_JSON)
            .bodyJson()
            .isStrictlyEqualTo("[]");
    }

    @Test
    void reportsACatalogueReadFailureAsTheServicesOwn() {
        when(greekGodRepository.findAll()).thenThrow(catalogueUnreadable());

        assertThat(mockMvc.get().uri(ENDPOINT))
            .hasStatus(HttpStatus.INTERNAL_SERVER_ERROR)
            .bodyJson()
            .extractingPath("$.title")
            .isEqualTo("Catalogue unavailable");
    }

    @Test
    void carriesACorrelationIdOnAFailureSoTheCauseStaysServerSide() {
        when(greekGodRepository.findAll()).thenThrow(catalogueUnreadable());

        assertThat(mockMvc.get().uri(ENDPOINT))
            .hasStatus(HttpStatus.INTERNAL_SERVER_ERROR)
            .bodyJson()
            .extractingPath("$.errorId")
            .asString()
            .isNotEmpty();
    }

    @Test
    void neitherLeaksInternalDetailNorPresentsAFailureAsAnEmptyCatalogue() {
        when(greekGodRepository.findAll()).thenThrow(catalogueUnreadable());

        assertThat(mockMvc.get().uri(ENDPOINT))
            .hasStatus(HttpStatus.INTERNAL_SERVER_ERROR)
            .bodyText()
            .as("a read failure must never be mistakable for an empty catalogue")
            .isNotEqualTo("[]")
            .as("the cause is logged server-side, never serialized to the consumer")
            .doesNotContain("greek_god")
            .doesNotContain("db-host")
            .doesNotContain("connection refused")
            .doesNotContain("DataAccessResourceFailureException");
    }

    private static DataAccessResourceFailureException catalogueUnreadable() {
        return new DataAccessResourceFailureException(
            "connection refused reading greek_god at db-host:5432");
    }
}
