package gr.aueb.java.ddd.aggregatesIdentification;

public class CallEdge {
    private Object caller;
    private Object target;
    private boolean inUpdateTransaction;
    private boolean creationEvent;
    private boolean inReadTransaction;

    public CallEdge(Object caller, Object target, boolean inUpdateTransaction, boolean creationEvent, boolean readEvent) {
        this.caller = caller;
        this.target = target;
        this.inUpdateTransaction = inUpdateTransaction;
        this.creationEvent = creationEvent;
        this.inReadTransaction = readEvent;
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
    
    public boolean isInReadTransaction() {
        return inReadTransaction;
    }
}
