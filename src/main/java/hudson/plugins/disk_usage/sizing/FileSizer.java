/*
 * Copyright (c) 2015 Ergon Informatik AG
 * Kleinstrasse 15, 8008 Zuerich, Switzerland
 * All rights reserved.
 */

package hudson.plugins.disk_usage.sizing;

import java.io.File;
import java.util.List;

public interface FileSizer {
	
	boolean canRun();
	
	Long calculateFileSize(File f, List<File> exceedFiles);

	Long blockSize();
}
