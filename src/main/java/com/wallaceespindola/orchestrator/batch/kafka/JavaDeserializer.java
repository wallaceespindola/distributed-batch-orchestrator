package com.wallaceespindola.orchestrator.batch.kafka;

import java.io.ByteArrayInputStream;
import java.io.ObjectInputStream;
import org.apache.kafka.common.errors.SerializationException;
import org.apache.kafka.common.serialization.Deserializer;

/** Counterpart of {@link JavaSerializer}. */
public class JavaDeserializer implements Deserializer<Object> {

    @Override
    public Object deserialize(String topic, byte[] data) {
        if (data == null) {
            return null;
        }
        try (ObjectInputStream in = new ObjectInputStream(new ByteArrayInputStream(data))) {
            return in.readObject();
        } catch (Exception e) {
            throw new SerializationException("Failed to deserialize message", e);
        }
    }
}
