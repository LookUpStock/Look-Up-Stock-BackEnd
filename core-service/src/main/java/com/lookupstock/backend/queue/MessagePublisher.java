package com.lookupstock.backend.queue;

public interface MessagePublisher {

    void publish(String topic, Object payload);
}