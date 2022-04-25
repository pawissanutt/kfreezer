package pawis.kfreezer.model;

public class KFRunStatus {
    public static final String FAILED = "FAILED";
    public static final String PENDING = "PENDING";
    public static final String COMPLETED = "COMPLETED";

    String state = PENDING;
    String error;

    public String getState() {
        return state;
    }

    public void setState(String state) {
        this.state = state;
    }

    public String getError() {
        return error;
    }

    public void setError(String error) {
        this.error = error;
    }
}
