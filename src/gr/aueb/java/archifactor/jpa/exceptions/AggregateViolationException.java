package gr.aueb.java.archifactor.jpa.exceptions;

public class AggregateViolationException extends RuntimeException {
    public AggregateViolationException(String message) {
        super(message);
    }
}
