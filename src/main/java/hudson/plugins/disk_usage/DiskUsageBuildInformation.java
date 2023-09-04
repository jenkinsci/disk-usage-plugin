/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package hudson.plugins.disk_usage;

import java.io.Serializable;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Objects;

/**
 *
 * @author Lucie Votypkova
 */
public class DiskUsageBuildInformation implements Serializable, Comparable<DiskUsageBuildInformation> {

    private static final long serialVersionUID = 8936406908101796594L;

    private static final String PATTERN = "yyyy-MM-dd_HH-mm-ss";

    private String id;
    private long timestamp;
    private int number;

    private Long size;

    public DiskUsageBuildInformation(String id, long timestamp, int number, Long size) {
        this.id = id;
        this.timestamp = timestamp;
        this.number = number;
        this.size = size;
    }

    private Object readResolve() {
        if(timestamp == 0) {
            try {
                timestamp = new SimpleDateFormat(PATTERN).parse(id).getTime();
            } catch (ParseException x) {
                // never mind
            }
        }
        return this;
    }

    public String getId() {
        return id;
    }

    public long getTimestamp() {
        return timestamp;
    }

    public int getNumber() {
        return number;
    }

    public Long getSize() {
        if(size == null) {
            return 0L;
        }
        return size;
    }

    @Override
    public boolean equals(Object o) {
        if(o instanceof DiskUsageBuildInformation) {
            DiskUsageBuildInformation information = (DiskUsageBuildInformation) o;
            return information.getId().equals(id);
        }
        return false;
    }

    @Override
    public int hashCode() {
        return Objects.hashCode(id);
    }

    @Override
    public int compareTo(DiskUsageBuildInformation o) {
        if(o != null) {
            return id.compareTo(o.getId());
        }
        throw new IllegalArgumentException("Can not compare with different type");
    }

    public void setSize(Long size) {
        this.size = size;
    }

    public String toString() {
        return "Id " + id + " number " + number + " size " + size;
    }
}
