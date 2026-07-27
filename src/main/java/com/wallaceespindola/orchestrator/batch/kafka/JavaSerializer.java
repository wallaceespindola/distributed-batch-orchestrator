package com.wallaceespindola.orchestrator.batch.kafka;

import java.io.ByteArrayOutputStream;
import java.io.ObjectOutputStream;
import org.apache.kafka.common.errors.SerializationException;
import org.apache.kafka.common.serialization.Serializer;

/**
 * Java-serialization Kafka serializer for Spring Batch {@code StepExecutionRequest}
 * messages (they are {@link java.io.Serializable} but not Jackson-friendly).
 */
public class JavaSerializer implements Serializer<Object> {

    @Override
    public byte[] serialize(String topic, Object data) {
        if (data == null) {
            return null;
        }
        try (ByteArrayOutputStream bytes = new ByteArrayOutputStream();
             ObjectOutputStream out = new ObjectOutputStream(bytes)) {
            out.writeObject(data);
            out.flush();
            return bytes.toByteArray();
        } catch (Exception e) {
            throw new SerializationException("Failed to serialize " + data.getClass(), e);
        }
    }
}
