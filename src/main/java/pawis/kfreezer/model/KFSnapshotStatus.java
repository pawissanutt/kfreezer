package pawis.kfreezer.model;

import java.time.Instant;

public class KFSnapshotStatus {

    public static final String FAILED = "FAILED";
    public static final String PENDING = "PENDING";
    public static final String COMPLETED = "COMPLETED";

    String state = PENDING;
    String jobSpecs;

    String snapshotTime;
    String error;

    public String getState() {
        return state;
    }

    public void setState(String state) {
        this.state = state;
    }

    public String getJobSpecs() {
        return jobSpecs;
    }

    public void setJobSpecs(String jobSpecs) {
        this.jobSpecs = jobSpecs;
    }

    public String getError() {
        return error;
    }

    public void setError(String error) {
        this.error = error;
    }

    public String getSnapshotTime() {
        return snapshotTime;
    }

    public void setSnapshotTime(String snapshotTime) {
        this.snapshotTime = snapshotTime;
    }
}
