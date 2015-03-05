package hudson.plugins.disk_usage.sizing.strategy;

import static org.hamcrest.Matchers.*;
import static org.junit.Assert.*;
import static org.mockito.Mockito.*;

import java.io.File;
import java.util.Collections;
import java.util.List;
import org.junit.Test;
import org.mockito.Mockito;
import hudson.plugins.disk_usage.sizing.FileSizer;

public class UnixDiskUsageFileSizerTest {
	
	UnixDiskUsageFileSizer fileSizer = new UnixDiskUsageFileSizer();

	@Test
	public void shouldBeAbleToRunOnUnix() throws Exception {
		assertThat(fileSizer.canRun(), is(equalTo(unix())));
	}

	@Test
	public void shouldCalculateFileSize() throws Exception {
		if(unix()) {
			final Long homeSize = fileSizer.calculateFileSize(new File("."), Collections.<File>emptyList());
			assertThat(homeSize, is(greaterThan(0L)));
		}

	}

	@Test
	public void shouldNotFallBackToJavaFileSizer() throws Exception {
		if(unix()) {
			fileSizer.fallbackFileSizer = mock(FileSizer.class);
			fileSizer.calculateFileSize(new File("."), Collections.<File>emptyList());
			verify(fileSizer.fallbackFileSizer, never()).calculateFileSize(Mockito.<File>any(), Mockito.<List<File>>any());
		}

	}

	protected boolean unix() {
		return File.pathSeparatorChar==':';
	}
}
