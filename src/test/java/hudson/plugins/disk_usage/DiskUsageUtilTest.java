package hudson.plugins.disk_usage;

import org.junit.Assert;
import org.junit.Test;

/**
 *
 * @author Lucie Votypkova
 */
public class DiskUsageUtilTest {  
    
    @Test
    public void testGetSizeInBytes(){
        String sizeInString = "57 B";
        Long size = 57l;
        Assert.assertEquals("Byte representation of size 57 B is wrong.", 57, DiskUsageUtil.getSizeInBytes(sizeInString), 0);
        sizeInString = "5 KB";
        size = 1024l*5;
        Assert.assertEquals("Byte representation of size 5 KB is wrong.", size, DiskUsageUtil.getSizeInBytes(sizeInString), 0);
        sizeInString = "9 MB";
        size = 1024l*1024*9;
        Assert.assertEquals("Byte representation of size 9 MB is wrong.", size, DiskUsageUtil.getSizeInBytes(sizeInString), 0);
        sizeInString = "1 GB";
        size = 1024l*1024*1024;
        Assert.assertEquals("Byte representation of size 1 GB is wrong.", size, DiskUsageUtil.getSizeInBytes(sizeInString), 0);
        sizeInString = "2 TB";
        size = 1024l*1024*1024*1024*2;
        Assert.assertEquals("Byte representation of size 2 TB is wrong.", size, DiskUsageUtil.getSizeInBytes(sizeInString), 0);
        sizeInString = "-";
        Assert.assertEquals("Byte representation of size - is wrong.", 0, DiskUsageUtil.getSizeInBytes(sizeInString), 0);       
    }
    
    @Test
    public void testGetSizeInString(){
       String sizeInString = "57 B";
        Long size = 57l;
        Assert.assertEquals("String representation of size 57 B is wrong.", sizeInString, DiskUsageUtil.getSizeString(size));
        sizeInString = "5 KB";
        size = 1024l*5;
        Assert.assertEquals("String representation of size 5 KB is wrong.", sizeInString, DiskUsageUtil.getSizeString(size));
        sizeInString = "9 MB";
        size = 1024l*1024*9;
        Assert.assertEquals("String representation of size 9 MB is wrong.", sizeInString, DiskUsageUtil.getSizeString(size));
        sizeInString = "1 GB";
        size = 1024l*1024*1024;
        Assert.assertEquals("String representation of size 1 GB is wrong.", sizeInString, DiskUsageUtil.getSizeString(size));
        sizeInString = "2 TB";
        size = 1024l*1024*1024*1024*2;
        Assert.assertEquals("String representation of size 2 TB is wrong.", sizeInString, DiskUsageUtil.getSizeString(size));
        sizeInString = "-";
        size=0l;
        Assert.assertEquals("String representation of size 0 B is wrong.", sizeInString, DiskUsageUtil.getSizeString(size));       
   
    }

   
}
