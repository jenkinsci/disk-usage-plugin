/*
 * Copyright (c) 2015 Ergon Informatik AG
 * Kleinstrasse 15, 8008 Zuerich, Switzerland
 * All rights reserved.
 */

package hudson.plugins.disk_usage.sizing.strategy;

import static java.util.logging.Level.FINE;
import static java.util.logging.Level.WARNING;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.util.Arrays;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;

import com.google.common.base.Charsets;
import com.google.common.base.Supplier;
import com.google.common.base.Suppliers;
import com.google.common.io.Files;
import org.apache.commons.io.input.NullInputStream;
import hudson.Proc;
import hudson.Util;
import hudson.plugins.disk_usage.sizing.FileSizer;

public abstract class AbstractNativeDiskUsageFileSizer implements FileSizer {

	public static final Logger LOGGER = Logger.getLogger(AbstractNativeDiskUsageFileSizer.class.getName());

	private final Supplier<Long> blockSizeSupplier = Suppliers.memoize(new AbstractNativeDiskUsageBlockSizeSupplier());

	protected FileSizer fallbackFileSizer = new JavaIOFileSizer();
	
	public boolean canRun() {
		return isTargetPlatform() && canExecuteBinary();
	}

	protected abstract boolean isTargetPlatform();

	protected abstract boolean canExecuteBinary();

	protected String executeCommand(File basedir, InputStream inputStream, String command, String... params) throws NativeDiskUsageFileSizingException {
		final String[] fullCommand = new String[params.length + 1];
		fullCommand[0] = command;
		System.arraycopy(params, 0, fullCommand, 1, params.length);
		ByteArrayOutputStream out = new ByteArrayOutputStream();
		try {
			new Proc.LocalProc(fullCommand, new String[0], inputStream, out, basedir).join();
		} catch (InterruptedException e) {
			throw new NativeDiskUsageFileSizingException("Interrupted while executing command " + Arrays.toString(fullCommand), e);
		} catch (IOException e) {
			throw new NativeDiskUsageFileSizingException("IOException while executing command " + Arrays.toString(fullCommand), e);
		}
		return out.toString();
	}

	protected String executeCommand(File basedir, String command, String... params) throws NativeDiskUsageFileSizingException {
		return executeCommand(basedir, null, command, params);
	}

	public Long calculateFileSize(File f, List<File> exceedFiles) {
		try {
			
		long fileSize = calculateFolderSize(f);
		for (File exceedFile : exceedFiles) {
			if(isParent(f, exceedFile)) {
				final Long exceedFileSize = calculateFileOrFolderSize(exceedFile);
				fileSize -= exceedFileSize;
			}
		}
		return fileSize;
		} catch (NativeDiskUsageFileSizingException e) {
			LOGGER.log(WARNING, "Problem while calculating disk-usage.", e);
		}
		return fallbackCalculateFileSize(f, exceedFiles);

	}

	private boolean isParent(File potentialParent, File file) {
		if(file.equals(potentialParent)) {
			return false;
		}
		final File immediateParent = file.getParentFile();
		if(immediateParent != null) {
			if(immediateParent.equals(potentialParent)) {
				return true;
			}
			return isParent(potentialParent, immediateParent);
		}
		return false;
	}

	protected abstract long calculateFolderSize(File folder) throws NativeDiskUsageFileSizingException;

	private Long calculateFileOrFolderSize(File f) throws NativeDiskUsageFileSizingException {
		if(isSymlink(f)) {
			return 0L;
		}
		if(f.isDirectory()) {
			return calculateFolderSize(f);
		} else if (f.isFile()) {
			return calculateFileSize(f);
		}
		throw new IllegalStateException("unsupported type of file: " + f);
	}

	private boolean isSymlink(File f) throws NativeDiskUsageFileSizingException {
		try {
			return Util.isSymlink(f);
		} catch (IOException e) {
			throw new NativeDiskUsageFileSizingException("cannot determine if symlink", e);
		}
	}

	@Override
	public Long blockSize() {
		return blockSizeSupplier.get();
	}

	protected abstract long calculateFileSize(File file) throws NativeDiskUsageFileSizingException;

	protected Long fallbackCalculateFileSize(File f, List<File> exceedFiles) {
		UnixDiskUsageFileSizer.LOGGER.log(FINE, "Falling back to sizeCalculation via Java (slower).");
		return fallbackFileSizer.calculateFileSize(f, exceedFiles);
	}

	private class AbstractNativeDiskUsageBlockSizeSupplier implements Supplier<Long> {

		@Override
		public Long get() {
			long fileSize = 1L;
			try {
				File file = write1ByteFile();
				fileSize = calculateFileSize(file);
				file.delete();
			} catch (IOException e) {
				e.printStackTrace();  // TODO (${USER}, ${DATE})
			} catch (NativeDiskUsageFileSizingException e) {
				return fallbackFileSizer.blockSize();
			}
			return fileSize;
		}

		private File write1ByteFile() throws IOException {
			final File tempFile = File.createTempFile("blockSize", ".txt");
			Files.write("1", tempFile, Charsets.UTF_8);
			return tempFile;
		}
	}
}
