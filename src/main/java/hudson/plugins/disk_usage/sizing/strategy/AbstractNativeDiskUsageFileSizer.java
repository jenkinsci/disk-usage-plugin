/*
 * Copyright (c) 2015 Ergon Informatik AG
 * Kleinstrasse 15, 8008 Zuerich, Switzerland
 * All rights reserved.
 */

package hudson.plugins.disk_usage.sizing.strategy;

import static java.util.logging.Level.FINE;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.util.Arrays;
import java.util.List;
import hudson.Proc;
import hudson.plugins.disk_usage.sizing.FileSizer;

public abstract class AbstractNativeDiskUsageFileSizer implements FileSizer {

	protected FileSizer fallbackFileSizer = new JavaIOFileSizer();
	
	public boolean canRun() {
		return isTargetPlatform() && canExecuteBinary();
	}

	protected abstract boolean isTargetPlatform();

	protected abstract boolean canExecuteBinary();

	protected String executeCommand(File basedir, String command, String... params) throws NativeDiskUsageFileSizingException {
		final String[] fullCommand = new String[params.length + 1];
		fullCommand[0] = command;
		System.arraycopy(params, 0, fullCommand, 1, params.length);
		ByteArrayOutputStream out = new ByteArrayOutputStream();
		try {
			new Proc.LocalProc(fullCommand, new String[0], out, basedir).join();
		} catch (InterruptedException e) {
			throw new NativeDiskUsageFileSizingException("Interrupted while executing command " + Arrays.toString(fullCommand), e);
		} catch (IOException e) {
			throw new NativeDiskUsageFileSizingException("IOException while executing command " + Arrays.toString(fullCommand), e);
		}
		return out.toString();
	}

	protected Long fallbackCalculateFileSize(File f, List<File> exceedFiles) {
		UnixDiskUsageFileSizer.LOGGER.log(FINE, "Falling back to sizeCalculation via Java (slower).");
		return fallbackFileSizer.calculateFileSize(f, exceedFiles);
	}
}
