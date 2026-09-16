package io.github.lealoureiro.greekgods;

import java.io.IOException;
import java.util.Arrays;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.web.client.ResourceAccessException;

import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * {@link GreekGodReconciliationService} against mocked collaborators: trimming
 * and blank-skipping (task 2.3, design D4), per-name isolation on failure
 * (task 3.3, design D8), and containment of an upstream failure (design:
 * "leave the catalogue intact").
 *
 * <p>A context-free unit test: {@link GreekGodSourceClient} and
 * {@link GreekGodCatalogueWriter} are Mockito mocks, so no Spring context, no
 * database, and no real network call is involved.
 */
@ExtendWith(MockitoExtension.class)
class GreekGodReconciliationServiceTest {

    @Mock
    private GreekGodSourceClient sourceClient;

    @Mock
    private GreekGodCatalogueWriter catalogueWriter;

    private GreekGodReconciliationService reconciliationService() {
        return new GreekGodReconciliationService(sourceClient, catalogueWriter);
    }

    @Test
    void takesUpEachOfferedNameOnceTrimmed() {
        when(sourceClient.fetchNames()).thenReturn(List.of(" Zeus ", "Hera"));
        when(catalogueWriter.takeUpIfUnseen("Zeus")).thenReturn(true);
        when(catalogueWriter.takeUpIfUnseen("Hera")).thenReturn(true);

        reconciliationService().reconcile();

        verify(catalogueWriter).takeUpIfUnseen("Zeus");
        verify(catalogueWriter).takeUpIfUnseen("Hera");
    }

    @Test
    void skipsAnEmptyOrWhitespaceOnlyNameWithoutEndingTheRun() {
        when(sourceClient.fetchNames()).thenReturn(Arrays.asList("", "   ", "Hera"));
        when(catalogueWriter.takeUpIfUnseen("Hera")).thenReturn(true);

        reconciliationService().reconcile();

        verify(catalogueWriter).takeUpIfUnseen(eq("Hera"));
        verify(catalogueWriter, never()).takeUpIfUnseen(eq(""));
        verify(catalogueWriter, never()).takeUpIfUnseen(eq("   "));
    }

    @Test
    void leavesAHeldNameAloneWhenTheWriterReportsItWasAlreadyPresent() {
        when(sourceClient.fetchNames()).thenReturn(List.of("Zeus"));
        when(catalogueWriter.takeUpIfUnseen("Zeus")).thenReturn(false);

        // No exception, and the single collaborator call is the only assertion
        // available at this layer — the writer itself owns "did this change
        // anything", verified in GreekGodCatalogueWriterIT.
        reconciliationService().reconcile();

        verify(catalogueWriter).takeUpIfUnseen("Zeus");
    }

    @Test
    void continuesWithTheRemainingNamesAfterOneNameFailsToBeTakenUp() {
        when(sourceClient.fetchNames()).thenReturn(List.of("Zeus", "Hera", "Poseidon"));
        when(catalogueWriter.takeUpIfUnseen("Zeus")).thenReturn(true);
        when(catalogueWriter.takeUpIfUnseen("Hera"))
            .thenThrow(new DataIntegrityViolationException("simulated failure taking up Hera"));
        when(catalogueWriter.takeUpIfUnseen("Poseidon")).thenReturn(true);

        reconciliationService().reconcile();

        InOrder order = inOrder(catalogueWriter);
        order.verify(catalogueWriter).takeUpIfUnseen("Zeus");
        order.verify(catalogueWriter).takeUpIfUnseen("Hera");
        order.verify(catalogueWriter).takeUpIfUnseen("Poseidon");
    }

    @Test
    void leavesTheCatalogueUntouchedWhenTheSourceIsUnavailable() {
        when(sourceClient.fetchNames())
            .thenThrow(new ResourceAccessException("simulated source unavailable",
                new IOException("connection refused")));

        reconciliationService().reconcile();

        verifyNoInteractions(catalogueWriter);
    }
}
