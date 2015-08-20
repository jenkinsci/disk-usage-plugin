/*
 * Copyright (c) 2015 Ergon Informatik AG
 * Kleinstrasse 15, 8008 Zuerich, Switzerland
 * All rights reserved.
 */

package hudson.plugins.disk_usage.sizing;

import com.google.common.collect.ImmutableList;
import hudson.plugins.disk_usage.sizing.strategy.JavaIOFileSizer;
import hudson.plugins.disk_usage.sizing.strategy.UnixDiskUsageFileSizer;

public class FileSizerProvider {

	private static final ImmutableList<FileSizer> fileSizers = ImmutableList.of(new UnixDiskUsageFileSizer(), new JavaIOFileSizer());
	
	public FileSizer fileSizer() {
		for (FileSizer fileSizer : fileSizers) {
			if(fileSizer.canRun()) {
				return fileSizer;
			}
		}
		throw new IllegalStateException("Should never be reached, at least the " + JavaIOFileSizer.class + " should work.");
	} 
}
