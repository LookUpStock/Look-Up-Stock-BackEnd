package com.lookupstock.backend.model;

public class MessageAnswer {
    private String message;

    public MessageAnswer() {
    }

    public MessageAnswer(String message) {
        this.message = message;
    }

    public String getMessage() {
        return message;
    }

    public void setMessage(String message) {
        this.message = message;
    }
}
