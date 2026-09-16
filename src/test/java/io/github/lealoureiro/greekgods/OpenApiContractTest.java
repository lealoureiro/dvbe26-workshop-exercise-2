package io.github.lealoureiro.greekgods;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;

import com.fasterxml.jackson.databind.ObjectMapper;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.test.web.servlet.assertj.MockMvcTester;
import org.yaml.snakeyaml.Yaml;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.fail;
import static org.mockito.Mockito.when;

/**
 * Holds {@code oas/greekController-oas.yaml} and the running code to each other
 * (design D4).
 *
 * <p>The contract is hand-authored and the response type is hand-written, which
 * duplicates the record shape in two places. design.md accepts that duplication
 * knowingly, on the condition that it is guarded by a failing build rather than by
 * inspection. This test is that guard, so it must fail on drift in <em>either</em>
 * direction: a field added to the code but not the contract, and a field declared
 * in the contract but not served, both turn the build red.
 *
 * <p>Every expectation below is read out of the YAML at test time. Nothing about the
 * contract is restated as a Java literal — a test that hardcoded the contract's
 * contents would agree with the code by construction and could never detect drift.
 *
 * <p>Scope, stated honestly: this validates the JSON Schema subset the contract
 * actually uses ({@code type}, {@code required}, {@code properties}, {@code items},
 * {@code additionalProperties}, and the string/number bounds). It is not a general
 * OpenAPI validator. Should the contract ever need {@code allOf}, {@code oneOf},
 * {@code pattern} or similar, this must be replaced with a real validator rather
 * than extended ad hoc.
 */
@ExtendWith(MockitoExtension.class)
class OpenApiContractTest {

    private static final String CONTRACT_PATH = "oas/greekController-oas.yaml";
    private static final String ENDPOINT = "/api/v1/gods/greek";

    private static final Set<String> MUTATING_METHODS = Set.of("post", "put", "patch", "delete");

    private static Map<String, Object> contract;

    @Mock
    private GreekGodRepository greekGodRepository;

    private MockMvcTester mockMvc;

    @BeforeAll
    static void loadContract() throws IOException {
        Path path = Path.of(System.getProperty("user.dir")).resolve(CONTRACT_PATH);
        assertThat(path).as("the published contract must exist").exists();
        try (InputStream in = Files.newInputStream(path)) {
            contract = new Yaml().load(in);
        }
        assertThat(contract).as("the published contract must not be empty").isNotNull();
    }

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcTester.of(new GreekGodController(greekGodRepository));
    }

    // ---------------------------------------------------------------- the document

    @Test
    void isOpenApi303() {
        assertThat(contract.get("openapi")).isEqualTo("3.0.3");
    }

    @Test
    void everyReferenceInTheContractResolves() {
        collectRefs(contract).forEach(ref -> assertThat(resolveRef(ref))
            .as("dangling reference %s", ref)
            .isNotNull());
    }

    @Test
    void publishesTheReadOperationAndNoOperationThatAltersARecord() {
        Map<String, Object> paths = section(contract, "paths");

        assertThat(paths).containsOnlyKeys(ENDPOINT);

        paths.forEach((path, node) -> {
            Set<String> methods = new TreeSet<>(asMap(node).keySet());
            assertThat(methods)
                .as("%s must publish no operation that removes a record, "
                    + "changes a name, or changes an identifier", path)
                .doesNotContainAnyElementsOf(MUTATING_METHODS);
            assertThat(methods).containsExactly("get");
        });
    }

    @Test
    void statesTheAscendingIdentifierOrderingGuarantee() {
        String description = readOperation().get("description").toString().toLowerCase(Locale.ROOT);

        assertThat(description)
            .as("design D6 requires the ordering guarantee to be published, not just implemented")
            .contains("ascending")
            .contains("identifier");
    }

    @Test
    void requiresNoCredential() {
        assertThat(section(contract, "components")).doesNotContainKey("securitySchemes");
        assertThat(contract).doesNotContainKey("security");
        assertThat(readOperation().get("security"))
            .as("the endpoint is public, and the contract must say so explicitly")
            .isEqualTo(List.of());
    }

    // ------------------------------------------------- served responses vs contract

    @Test
    void servedCatalogueConformsToTheContract() {
        when(greekGodRepository.findAll())
            .thenReturn(List.of(new GreekGod(1L, "Zeus"), new GreekGod(2L, "Hera")));

        assertServedResponseConforms(200, "application/json");
    }

    @Test
    void servedEmptyCatalogueConformsToTheContract() {
        when(greekGodRepository.findAll()).thenReturn(List.of());

        Object body = assertServedResponseConforms(200, "application/json");

        assertThat(body)
            .as("an empty catalogue is a success carrying an empty array, not an error")
            .isEqualTo(List.of());
    }

    @Test
    void servedFailureConformsToTheContract() {
        when(greekGodRepository.findAll())
            .thenThrow(new DataAccessResourceFailureException("connection refused"));

        assertServedResponseConforms(500, "application/problem+json");
    }

    /**
     * Serves a real response through the controller and validates it against the
     * schema the contract declares for that status and media type.
     *
     * @return the parsed response body, for any further assertion the caller needs
     */
    private Object assertServedResponseConforms(int status, String expectedMediaType) {
        var result = mockMvc.get().uri(ENDPOINT).exchange();

        assertThat(result.getResponse().getStatus()).isEqualTo(status);
        assertThat(result.getResponse().getContentType())
            .as("media type served for %d", status)
            .startsWith(contractMediaType(status, expectedMediaType));

        Object body = parseJson(responseBody(result));
        assertConforms(contractSchema(status, expectedMediaType), body, "$");
        return body;
    }

    private static String responseBody(
            org.springframework.test.web.servlet.assertj.MvcTestResult result) {
        try {
            return result.getResponse().getContentAsString();
        } catch (java.io.UnsupportedEncodingException ex) {
            return fail("served response could not be decoded", ex);
        }
    }

    // ------------------------------------------------------------ contract lookups

    private Map<String, Object> readOperation() {
        return asMap(section(contract, "paths").get(ENDPOINT)).get("get") instanceof Map<?, ?> op
            ? asMap(op)
            : fail("the contract publishes no GET for " + ENDPOINT);
    }

    /** The single media type the contract declares for this status. */
    private String contractMediaType(int status, String expected) {
        Set<String> declared = responseContent(status).keySet();
        assertThat(declared)
            .as("the contract must declare exactly one media type for %d", status)
            .containsExactly(expected);
        return expected;
    }

    private Map<String, Object> contractSchema(int status, String mediaType) {
        return asMap(asMap(responseContent(status).get(mediaType)).get("schema"));
    }

    /** Resolves {@code paths./api/v1/gods/greek.get.responses.<status>.content}. */
    private Map<String, Object> responseContent(int status) {
        Map<String, Object> response = asMap(
            asMap(readOperation().get("responses")).get(String.valueOf(status)));
        if (response.containsKey("$ref")) {
            response = asMap(resolveRef(response.get("$ref").toString()));
        }
        return asMap(response.get("content"));
    }

    // ------------------------------------------- a validator for the subset in use

    private void assertConforms(Map<String, Object> schema, Object value, String at) {
        Map<String, Object> resolved = schema.containsKey("$ref")
            ? asMap(resolveRef(schema.get("$ref").toString()))
            : schema;

        String type = String.valueOf(resolved.get("type"));
        switch (type) {
            case "array" -> {
                assertThat(value).as("%s must be an array", at).isInstanceOf(List.class);
                List<?> items = (List<?>) value;
                Map<String, Object> itemSchema = asMap(resolved.get("items"));
                for (int i = 0; i < items.size(); i++) {
                    assertConforms(itemSchema, items.get(i), at + "[" + i + "]");
                }
            }
            case "object" -> assertObjectConforms(resolved, value, at);
            case "string" -> {
                assertThat(value).as("%s must be a string", at).isInstanceOf(String.class);
                String text = (String) value;
                bound(resolved, "minLength").ifPresent(min ->
                    assertThat((long) text.length()).as("%s minLength", at).isGreaterThanOrEqualTo(min));
                bound(resolved, "maxLength").ifPresent(max ->
                    assertThat((long) text.length()).as("%s maxLength", at).isLessThanOrEqualTo(max));
            }
            case "integer" -> {
                assertThat(value).as("%s must be an integer", at)
                    .isInstanceOfAny(Integer.class, Long.class);
                long number = ((Number) value).longValue();
                bound(resolved, "minimum").ifPresent(min ->
                    assertThat(number).as("%s minimum", at).isGreaterThanOrEqualTo(min));
                bound(resolved, "maximum").ifPresent(max ->
                    assertThat(number).as("%s maximum", at).isLessThanOrEqualTo(max));
            }
            default -> fail("%s declares unsupported schema type '%s'; this validator "
                + "covers only the subset the contract uses".formatted(at, type));
        }
    }

    private void assertObjectConforms(Map<String, Object> schema, Object value, String at) {
        assertThat(value).as("%s must be an object", at).isInstanceOf(Map.class);
        Map<String, Object> object = asMap(value);
        Map<String, Object> properties = asMap(schema.get("properties"));

        Object required = schema.get("required");
        if (required instanceof List<?> names) {
            assertThat(object.keySet())
                .as("%s is missing a property the contract declares required", at)
                .containsAll(names.stream().map(Object::toString).toList());
        }

        if (Boolean.FALSE.equals(schema.get("additionalProperties"))) {
            assertThat(object.keySet())
                .as("%s carries a property the contract does not declare", at)
                .isSubsetOf(properties.keySet());
        }

        object.forEach((name, propertyValue) -> {
            if (properties.containsKey(name)) {
                assertConforms(asMap(properties.get(name)), propertyValue, at + "." + name);
            }
        });
    }

    // ------------------------------------------------------------------- utilities

    private Object resolveRef(String ref) {
        assertThat(ref).as("only local references are supported").startsWith("#/");
        Object node = contract;
        for (String segment : ref.substring(2).split("/")) {
            if (!(node instanceof Map<?, ?> map)) {
                return null;
            }
            node = map.get(segment);
        }
        return node;
    }

    private static Set<String> collectRefs(Object node) {
        Set<String> refs = new TreeSet<>();
        if (node instanceof Map<?, ?> map) {
            map.forEach((key, child) -> {
                if ("$ref".equals(key)) {
                    refs.add(child.toString());
                } else {
                    refs.addAll(collectRefs(child));
                }
            });
        } else if (node instanceof List<?> list) {
            list.forEach(child -> refs.addAll(collectRefs(child)));
        }
        return refs;
    }

    private static java.util.Optional<Long> bound(Map<String, Object> schema, String key) {
        return schema.get(key) instanceof Number number
            ? java.util.Optional.of(number.longValue())
            : java.util.Optional.empty();
    }

    private static Map<String, Object> section(Map<String, Object> node, String key) {
        return asMap(node.get(key));
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> asMap(Object node) {
        assertThat(node).isInstanceOf(Map.class);
        return (Map<String, Object>) node;
    }

    private static Object parseJson(String json) {
        try {
            return new ObjectMapper().readValue(json, Object.class);
        } catch (IOException ex) {
            return fail("served response was not valid JSON: " + json, ex);
        }
    }
}
