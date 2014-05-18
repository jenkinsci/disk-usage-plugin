/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package hudson.plugins.disk_usage;

import java.io.Serializable;

/**
 *
 * @author Lucie Votypkova
 */
public class DiskUsageBuildInformation implements Serializable{
    
    private String id;
    
    private int number;
    
    private Long size;
    
    public DiskUsageBuildInformation(String id, int number, Long size){
        this.id = id;
        this.number = number;
        this.size = size;
    }
    
    public String getId(){
        return id;
    }
    
    public int getNumber(){
        return number;
    }
    
    public Long getSize(){
        return size;
    }
    
    @Override
    public boolean equals(Object o){
        if(o instanceof DiskUsageBuildInformation){
            DiskUsageBuildInformation information = (DiskUsageBuildInformation) o;
            return information.getId().equals(id);
        }
        return false;
    }
    
    public void setSize(Long size){
        this.size = size;
    }
    
    public String toString(){
        return "Id " + id + " number " + number + " size " + size;
    }
}
