package com.wallaceespindola.orchestrator.batch.kafka;

import java.io.ByteArrayInputStream;
import java.io.ObjectInputFilter;
import java.io.ObjectInputStream;
import org.apache.kafka.common.errors.SerializationException;
import org.apache.kafka.common.serialization.Deserializer;

/**
 * Counterpart of {@link JavaSerializer}. A strict {@link ObjectInputFilter} allowlist
 * limits deserialization to the Spring Batch {@code StepExecutionRequest} shape,
 * blocking gadget-chain attacks from anything else published to the topic. In
 * production the Kafka listener should additionally be locked down with SASL + TLS so
 * untrusted producers cannot reach the partition-requests topic at all.
 */
public class JavaDeserializer implements Deserializer<Object> {

    private static final ObjectInputFilter ALLOWLIST = ObjectInputFilter.Config.createFilter(
            "org.springframework.batch.integration.partition.StepExecutionRequest;"
                    + "java.lang.*;java.util.*;maxdepth=5;maxrefs=64;maxbytes=65536;!*");

    @Override
    public Object deserialize(String topic, byte[] data) {
        if (data == null) {
            return null;
        }
        try (ObjectInputStream in = new ObjectInputStream(new ByteArrayInputStream(data))) {
            in.setObjectInputFilter(ALLOWLIST);
            return in.readObject();
        } catch (Exception e) {
            throw new SerializationException("Failed to deserialize message", e);
        }
    }
}
