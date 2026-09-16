package io.github.lealoureiro.greekgods;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.core.env.Environment;
import org.springframework.mock.env.MockEnvironment;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

/**
 * The kill switch's single typed decision (design D9, task 4.3/4.4): default
 * on, explicit off honoured, and an unreadable or unparseable setting treated
 * as off — never a start-up failure.
 */
@ExtendWith(MockitoExtension.class)
class ReconciliationKillSwitchTest {

    @Test
    void defaultsToEnabledWhenTheSettingIsAbsent() {
        var killSwitch = new ReconciliationKillSwitch(new MockEnvironment());

        assertThat(killSwitch.reconciliationEnabled()).isTrue();
    }

    @Test
    void honoursAnExplicitTrue() {
        var environment = new MockEnvironment()
            .withProperty(ReconciliationKillSwitch.PROPERTY, "true");

        assertThat(new ReconciliationKillSwitch(environment).reconciliationEnabled()).isTrue();
    }

    @Test
    void honoursAnExplicitFalse() {
        var environment = new MockEnvironment()
            .withProperty(ReconciliationKillSwitch.PROPERTY, "false");

        assertThat(new ReconciliationKillSwitch(environment).reconciliationEnabled()).isFalse();
    }

    @Test
    void isCaseInsensitiveAndToleratesSurroundingWhitespace() {
        var environment = new MockEnvironment()
            .withProperty(ReconciliationKillSwitch.PROPERTY, "  FALSE  ");

        assertThat(new ReconciliationKillSwitch(environment).reconciliationEnabled()).isFalse();
    }

    @Test
    void treatsAnUnparseableValueAsDisabled() {
        var environment = new MockEnvironment()
            .withProperty(ReconciliationKillSwitch.PROPERTY, "maybe");

        assertThat(new ReconciliationKillSwitch(environment).reconciliationEnabled())
            .as("an unreadable setting must never cause an unintended call to the third party")
            .isFalse();
    }

    @Test
    void treatsAnEnvironmentThatThrowsAsDisabled(@Mock Environment environment) {
        when(environment.getProperty(ReconciliationKillSwitch.PROPERTY))
            .thenThrow(new IllegalStateException("placeholder could not be resolved"));

        assertThat(new ReconciliationKillSwitch(environment).reconciliationEnabled())
            .as("a control that cannot be read at all must also be treated as off")
            .isFalse();
    }
}
