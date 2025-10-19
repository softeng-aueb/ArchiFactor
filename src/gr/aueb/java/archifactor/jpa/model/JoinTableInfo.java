package gr.aueb.java.archifactor.jpa.model;

public class JoinTableInfo {
    private String tableName;
    private String joinColumns;
    private String inverseJoinColumns;

    public JoinTableInfo(String tableName, String joinColumns, String inverseJoinColumns) {
        this.tableName = tableName;
        this.joinColumns = joinColumns;
        this.inverseJoinColumns = inverseJoinColumns;
    }

    public String getTableName() {
        return tableName;
    }

    public String getJoinColumns() {
        return joinColumns;
    }

    public String getInverseJoinColumns() {
        return inverseJoinColumns;
    }
}
