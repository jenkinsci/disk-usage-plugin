/*
 * Copyright (c) 2015 Ergon Informatik AG
 * Kleinstrasse 15, 8008 Zuerich, Switzerland
 * All rights reserved.
 */

package hudson.plugins.disk_usage.sizing.strategy;

import static java.util.logging.Level.*;

import java.io.File;
import java.util.Collections;
import java.util.List;
import java.util.logging.Logger;
import com.google.common.base.CharMatcher;
import hudson.Platform;

public class UnixDiskUsageFileSizer extends AbstractNativeDiskUsageFileSizer {

	public static final Logger LOGGER = Logger.getLogger(JavaIOFileSizer.class.getName());

	@Override
	protected boolean isTargetPlatform() {
		return Platform.current().equals(Platform.UNIX);
	}

	@Override
	protected boolean canExecuteBinary() {
		boolean canExecute = false;
		try {
			canExecute = executeCommand(new File("."), "du", "-sk", ".").contains(".");
		} catch (NativeDiskUsageFileSizingException e) {
			LOGGER.log(FINEST, "Cannot execute "+getClass()+ " on this machine.", e);
		}
		return canExecute;
	}

	public Long calculateFileSize(File f, List<File> exceedFiles) {
		try {
			String output = executeCommand(f, "du", "-Hsk", ".");
			return bytesFromOutput(output);
		} catch (NativeDiskUsageFileSizingException e) {
			LOGGER.log(WARNING, "Problem while calculating disk-usage.", e);
		}
		return fallbackCalculateFileSize(f, exceedFiles);
	}

	private Long bytesFromOutput(String output) {
		// example output: 
		// 2284632	.
		final String bytesAsString = CharMatcher.inRange('0', '9').retainFrom(output);
		final long resultingKiloBytes = Long.parseLong(bytesAsString);
		final long resultingBytes = resultingKiloBytes * 1024L;
		LOGGER.log(FINER, "interpreting output of du '{0}' as {1} bytes.", new Object[]{output, resultingBytes});
		return resultingBytes;
	}

	public static void main(String[] args) {
		UnixDiskUsageFileSizer sizer = new UnixDiskUsageFileSizer();
		System.out.println("can run: "+ sizer.canRun());
		final Long sizeInBytes = sizer.calculateFileSize(new File(System.getProperty("user.home")), Collections.<File>emptyList());
		System.out.println("Calculated FileSize: " + sizeInBytes);
	}
}
