package gr.aueb.java.archifactor.jpa.exceptions;

public class CompositeKeyException extends RuntimeException {
    public CompositeKeyException(String message) {
        super(message);
    }
}