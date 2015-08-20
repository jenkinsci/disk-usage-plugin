/*
 * Copyright (c) 2015 Ergon Informatik AG
 * Kleinstrasse 15, 8008 Zuerich, Switzerland
 * All rights reserved.
 */

package hudson.plugins.disk_usage.sizing.strategy;

import java.io.File;
import java.io.IOException;
import java.util.List;
import java.util.logging.Logger;
import hudson.Util;
import hudson.plugins.disk_usage.sizing.FileSizer;

public class JavaIOFileSizer implements FileSizer {

	public static final Logger LOGGER = Logger.getLogger(JavaIOFileSizer.class.getName());

	public boolean canRun() {
		return true; //the java file sizing is always possible
	}

	public Long calculateFileSize(File f, List<File> exceedFiles) {
		long size = 0;
		if(!f.exists())
			return size;
		if (f.isDirectory() && !isSymlink(f)) {
			File[] fileList = f.listFiles();
			if (fileList != null) for (File child : fileList) {
				if(exceedFiles.contains(child))
					continue; //do not count exceeded files
				if (!isSymlink(child)) size += calculateFileSize(child, exceedFiles);
			}
			else {
				LOGGER.info("Failed to list files in " + f.getPath() + " - ignoring");
			}
		}
		return size + (f.isFile() ? f.length() : 0L);
	}
	
	public static boolean isSymlink(File f) {
		try {
			return Util.isSymlink(f);
		} catch (IOException e) {
			return false;
		}
	}

	@Override
	public Long blockSize() {
		return 1L;
	}
}
