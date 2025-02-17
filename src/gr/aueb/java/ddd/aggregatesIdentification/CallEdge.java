package gr.aueb.java.ddd.aggregatesIdentification;

public class CallEdge {
    private Object caller;
    private Object target;
    private boolean inUpdateTransaction;
    private boolean creationEvent;

    public CallEdge(Object caller, Object target, boolean inUpdateTransaction, boolean creationEvent) {
        this.caller = caller;
        this.target = target;
        this.inUpdateTransaction = inUpdateTransaction;
        this.creationEvent = creationEvent;
    }

    public Object getCaller() {
        return caller;
    }

    public Object getTarget() {
        return target;
    }

    public boolean isInUpdateTransaction() {
        return inUpdateTransaction;
    }

    public boolean isCreationEvent() {
        return creationEvent;
    }
}
