package com.wallaceespindola.orchestrator.batch.kafka;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.apache.kafka.common.errors.SerializationException;
import org.junit.jupiter.api.Test;
import org.springframework.batch.integration.partition.StepExecutionRequest;

class JavaSerdeTest {

    private final JavaSerializer serializer = new JavaSerializer();
    private final JavaDeserializer deserializer = new JavaDeserializer();

    @Test
    void roundTripsStepExecutionRequest() {
        StepExecutionRequest request = new StepExecutionRequest("workerStep", 7L, 42L);

        Object result = deserializer.deserialize("topic", serializer.serialize("topic", request));

        assertThat(result).isInstanceOf(StepExecutionRequest.class);
        StepExecutionRequest deserialized = (StepExecutionRequest) result;
        assertThat(deserialized.getStepName()).isEqualTo("workerStep");
        assertThat(deserialized.getJobExecutionId()).isEqualTo(7L);
        assertThat(deserialized.getStepExecutionId()).isEqualTo(42L);
    }

    @Test
    void rejectsClassesOutsideAllowlist() {
        byte[] payload = serializer.serialize("topic", new java.io.File("/tmp/gadget"));

        assertThatThrownBy(() -> deserializer.deserialize("topic", payload))
                .isInstanceOf(SerializationException.class);
    }

    @Test
    void nullPassesThrough() {
        assertThat(serializer.serialize("topic", null)).isNull();
        assertThat(deserializer.deserialize("topic", null)).isNull();
    }
}
