/*
 * Copyright (c) 2015 Ergon Informatik AG
 * Kleinstrasse 15, 8008 Zuerich, Switzerland
 * All rights reserved.
 */

package hudson.plugins.disk_usage.sizing.strategy;

import static java.util.logging.Level.*;

import java.io.File;
import java.util.Collections;
import java.util.logging.Level;
import java.util.logging.Logger;

import com.google.common.base.Splitter;
import com.google.common.collect.Iterables;
import org.apache.tools.ant.filters.StringInputStream;
import com.google.common.base.CharMatcher;
import com.google.common.base.Strings;
import hudson.Platform;

public class UnixDiskUsageFileSizer extends AbstractNativeDiskUsageFileSizer {

	public static final Logger LOGGER = Logger.getLogger(UnixDiskUsageFileSizer.class.getName());

	@Override
	protected boolean isTargetPlatform() {
		return Platform.current().equals(Platform.UNIX);
	}

	@Override
	protected boolean canExecuteBinary() {
		boolean canExecute = false;
		try {
			canExecute = !executeCommand(new File("."), "du", "-H", "-s", "-k").trim().isEmpty();
		} catch (NativeDiskUsageFileSizingException e) {
			LOGGER.log(INFO, "Cannot execute "+getClass()+ " on this machine.", e);
		}
		return canExecute;
	}

	protected long calculateFolderSize2(File folder) throws NativeDiskUsageFileSizingException {
		//find . -type f -print0 | xargs -0 stat -f%z | awk '{b+=$1} END {print b}'
		//String output = executeCommand(f, "find", ".", "-type f", "-print0" , "|", "xargs", "-0", "stat", "-f%z", "|", "awk", "'{b+=$1} END {print b}'");
		//String output = executeCommand(f, "find . -type f -print0 | xargs -0 stat -f'%z' | awk '{b+=$1} END {print b}'");

		final String findOutput = executeCommand(folder, "find", ".", "-type", "f", "-print0");
		//final String uniqueFindOutput = executeCommand(folder, new StringInputStream(findOutput), "uniq");
		LOGGER.log(Level.INFO, "sizing the following files: {0}", findOutput);
		final String xArgsOutput = executeCommand(folder, new StringInputStream(findOutput), "xargs", "-0", "stat", "-f%z");
		// on linux 			final String xArgsOutput = executeCommand(f, new StringInputStream(findOutput), "xargs", "-0", "stat", "-c %s");

		final String awkOutput = executeCommand(folder, new StringInputStream(xArgsOutput), "awk", "{b+=$1} END {print b}");
		return fileSizeFromOutput(awkOutput);
	}

	//du -H -s -k


	@Override
	protected long calculateFolderSize(File folder) throws NativeDiskUsageFileSizingException {
		final String duOutput = executeCommand(folder, "du", "-H", "-s", "-k", ".");
		return bytesFromDuOutput(duOutput);
	}

	private long bytesFromDuOutput(String duOutput) {
		Long kiloBytes = fileSizeFromOutput(duOutput);
		return kiloBytes * 1024;
	}

	private Long fileSizeFromOutput(String output) {
		final Iterable<String> values = Splitter.on(CharMatcher.BREAKING_WHITESPACE).trimResults().omitEmptyStrings().limit(2).split(output);
		final String fileSizeInKilobytes = Iterables.getFirst(values, "0");
		long resultingBytes = 0L;
		if (!Strings.isNullOrEmpty(fileSizeInKilobytes)) {
			resultingBytes = Long.parseLong(fileSizeInKilobytes);
		}
		LOGGER.log(FINER, "interpreting output of shell result '{0}' as {1} bytes.", new Object[]{output, resultingBytes});
		return resultingBytes;
	}

	@Override
	protected long calculateFileSize(File file) throws NativeDiskUsageFileSizingException {
		final String duOutput = executeCommand(file.getParentFile(), "du", "-H", "-s", "-k", file.getName());
		return bytesFromDuOutput(duOutput);
	}

	public static void main(String[] args) {
		UnixDiskUsageFileSizer sizer = new UnixDiskUsageFileSizer();
		System.out.println("can run: "+ sizer.canRun());
		final Long sizeInBytes = sizer.calculateFileSize(new File(System.getProperty("user.home")), Collections.<File>emptyList());
		System.out.println("Calculated FileSize: " + sizeInBytes);
	}
}
