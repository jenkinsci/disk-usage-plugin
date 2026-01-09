package hudson.plugins.disk_usage;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

/**
 *
 * @author Lucie Votypkova
 */
public class DiskUsageUtilTest {

    @Test
    void testGetSizeInBytes() {
        String sizeInString = "57 B";
        long size = 57L;
        Assertions.assertEquals(size, DiskUsageUtil.getSizeInBytes(sizeInString), 0, "Byte representation of size 57 B is wrong.");
        sizeInString = "5 KB";
        size = 1024L * 5;
        Assertions.assertEquals(size, DiskUsageUtil.getSizeInBytes(sizeInString), 0, "Byte representation of size 5 KB is wrong.");
        sizeInString = "9 MB";
        size = 1024L * 1024 * 9;
        Assertions.assertEquals(size, DiskUsageUtil.getSizeInBytes(sizeInString), 0, "Byte representation of size 9 MB is wrong.");
        sizeInString = "1 GB";
        size = 1024L * 1024 * 1024;
        Assertions.assertEquals(size, DiskUsageUtil.getSizeInBytes(sizeInString), 0, "Byte representation of size 1 GB is wrong.");
        sizeInString = "2 TB";
        size = 1024L * 1024 * 1024 * 1024 * 2;
        Assertions.assertEquals(size, DiskUsageUtil.getSizeInBytes(sizeInString), 0, "Byte representation of size 2 TB is wrong.");
        sizeInString = "-";
        Assertions.assertEquals(0, DiskUsageUtil.getSizeInBytes(sizeInString), 0, "Byte representation of size - is wrong.");
    }

    @Test
    void testGetSizeInString() {
        String sizeInString = "57 B";
        long size = 57L;
        Assertions.assertEquals(sizeInString, DiskUsageUtil.getSizeString(size), "String representation of size 57 B is wrong.");
        sizeInString = "5 KB";
        size = 1024L * 5;
        Assertions.assertEquals(sizeInString, DiskUsageUtil.getSizeString(size), "String representation of size 5 KB is wrong.");
        sizeInString = "9 MB";
        size = 1024L * 1024 * 9;
        Assertions.assertEquals(sizeInString, DiskUsageUtil.getSizeString(size), "String representation of size 9 MB is wrong.");
        sizeInString = "1 GB";
        size = 1024L * 1024 * 1024;
        Assertions.assertEquals(sizeInString, DiskUsageUtil.getSizeString(size), "String representation of size 1 GB is wrong.");
        sizeInString = "2 TB";
        size = 1024L * 1024 * 1024 * 1024 * 2;
        Assertions.assertEquals(sizeInString, DiskUsageUtil.getSizeString(size), "String representation of size 2 TB is wrong.");
        sizeInString = "-";
        size = 0L;
        Assertions.assertEquals(sizeInString, DiskUsageUtil.getSizeString(size), "String representation of size 0 B is wrong.");

    }


}
