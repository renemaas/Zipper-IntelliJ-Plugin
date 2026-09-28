import com.intellij.notification.NotificationGroupManager;
import com.intellij.notification.NotificationType;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.InputValidator;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.util.io.FileUtil;
import org.apache.commons.compress.archivers.zip.ParallelScatterZipCreator;
import org.apache.commons.compress.archivers.zip.ZipArchiveEntry;
import org.apache.commons.compress.archivers.zip.ZipArchiveOutputStream;
import org.apache.commons.compress.parallel.FileBasedScatterGatherBackingStore;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.nio.file.attribute.BasicFileAttributes;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.*;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.zip.ZipEntry;

public class Zipper {

	public static final String TITLE = "Zipper";
	public static final String TITLE_SUCCESS = "Success";
	public static final String TITLE_ERROR = "Error";
	public final static String DATE_FORMAT = "yyyy-MM-dd_HH-mm-ss";
	public final static String FILE_EXTENSION = ".zip";
	public final static String MESSAGE_SUCCESS = "Project was packed successfully in ";
	public final static String MESSAGE_ERROR = "An error occurred while packing the project";
	public final static String MESSAGE_PACKING_PROJECT = "Project is being packed";
	public final static String LABEL_PACKING = "Packing: ";
	public final static String LABEL_SAVING = "Saving: ";
	public final static String IGNORE_FILE = ".zipper";
	public final static String NOTIFICATION_GROUP = "Zipper";

	private static final int COMPRESSION_LEVEL = 4;
	private static final String INVALID_NAME_CHARACTERS = "/\\:*?\"<>|";
	private static final Set<String> COMPRESSED_EXTENSIONS = new HashSet<String>(Arrays.asList(
			"zip", "jar", "war", "ear", "apk", "gz", "tgz", "bz2", "xz", "7z", "rar", "zst",
			"png", "jpg", "jpeg", "gif", "webp", "avif", "heic",
			"mp3", "mp4", "m4a", "mov", "avi", "mkv", "webm", "ogg", "flac",
			"woff", "woff2", "docx", "xlsx", "pptx", "odt", "ods", "odp"
	));

	public static String showArchiveNameInputDialog(Project project) {
		return removeExtensionFromFileName(
				Messages.showInputDialog(
						project,
						"Please enter the name of the ZIP archive without extension",
						TITLE,
						Messages.getQuestionIcon(),
						getCurrentDateFormatted(),
						new InputValidator() {
							@Override
							public boolean checkInput(String inputString) {
								return isValidArchiveName(inputString);
							}

							@Override
							public boolean canClose(String inputString) {
								return isValidArchiveName(inputString);
							}
						}
				)
		);
	}

	public static boolean isValidArchiveName(String name) {
		String baseName = removeExtensionFromFileName(name);
		if (baseName == null || baseName.isEmpty() || baseName.equals(".") || baseName.equals("..")) {
			return false;
		}
		for (char c : baseName.toCharArray()) {
			if (c < 32 || INVALID_NAME_CHARACTERS.indexOf(c) >= 0) {
				return false;
			}
		}
		return true;
	}

	public static String getCurrentDateFormatted() {
		DateFormat dateFormat = new SimpleDateFormat(DATE_FORMAT);
		Date date = new Date();
		return dateFormat.format(date);
	}

	public static String removeExtensionFromFileName(String fileName) {
		if (fileName == null) {
			return null;
		}
		fileName = fileName.trim();
		if (fileName.toLowerCase(Locale.ROOT).endsWith(FILE_EXTENSION)) {
			return fileName.substring(0, fileName.length() - FILE_EXTENSION.length());
		}
		return fileName;
	}

	public static void throwError(String details) {
		NotificationGroupManager.getInstance().getNotificationGroup(NOTIFICATION_GROUP)
				.createNotification(TITLE_ERROR, details == null ? MESSAGE_ERROR : MESSAGE_ERROR + ":<br>" + details, NotificationType.ERROR)
				.notify(null);
	}

	public static void throwSuccess(String execTime) {
		NotificationGroupManager.getInstance().getNotificationGroup(NOTIFICATION_GROUP)
				.createNotification(TITLE_SUCCESS, MESSAGE_SUCCESS + execTime, NotificationType.INFORMATION)
				.notify(null);
	}

	public static Set<String> getIgnoredFiles(Path filePath) throws IOException {
		return new HashSet<String>(Files.readAllLines(filePath, StandardCharsets.UTF_8));
	}

	public static void addArchiveToIgnoreList(Path filePath, String s) throws IOException {
		Files.write(filePath, (s + "\n").getBytes(StandardCharsets.UTF_8), StandardOpenOption.CREATE, StandardOpenOption.APPEND);
	}

	public static void createArchive(Path contentDirectory, Path archiveFile, Set<String> ignoredFiles, final ProgressIndicator progressIndicator) throws IOException {
		// Walking is cheap compared to compressing, collect first so a failing walk cannot leave threads behind
		List<Path> files = new ArrayList<Path>();
		List<ZipArchiveEntry> entries = new ArrayList<ZipArchiveEntry>();
		collectFiles(contentDirectory.toAbsolutePath().normalize(), ignoredFiles, progressIndicator, files, entries);

		ExecutorService executor = createCompressionExecutor();
		final Path scatterDirectory = Files.createTempDirectory("zipper");
		try {
			ParallelScatterZipCreator creator = new ParallelScatterZipCreator(
					executor,
					() -> new FileBasedScatterGatherBackingStore(Files.createTempFile(scatterDirectory, "scatter", ".tmp")),
					COMPRESSION_LEVEL
			);
			for (int i = 0; i < files.size(); i++) {
				final Path file = files.get(i);
				creator.addArchiveEntry(entries.get(i), () -> openCancelable(file, progressIndicator));
			}
			try (ZipArchiveOutputStream zipOutputStream = new ZipArchiveOutputStream(archiveFile)) {
				creator.writeTo(zipOutputStream);
			}
		} catch (InterruptedException e) {
			Thread.currentThread().interrupt();
			throw new InterruptedIOException("Packing was interrupted");
		} catch (ExecutionException e) {
			progressIndicator.checkCanceled();
			Throwable cause = e.getCause();
			if (cause instanceof UncheckedIOException) {
				throw ((UncheckedIOException) cause).getCause();
			}
			if (cause instanceof IOException) {
				throw (IOException) cause;
			}
			throw new IOException(cause);
		} finally {
			executor.shutdownNow();
			try {
				executor.awaitTermination(10, TimeUnit.SECONDS);
			} catch (InterruptedException e) {
				Thread.currentThread().interrupt();
			}
			FileUtil.delete(scatterDirectory);
		}
	}

	private static void collectFiles(final Path contentRoot, final Set<String> ignoredFiles, final ProgressIndicator progressIndicator, final List<Path> files, final List<ZipArchiveEntry> entries) throws IOException {
		// Symlinked content stays in the archive, walkFileTree detects link cycles
		Files.walkFileTree(contentRoot, EnumSet.of(FileVisitOption.FOLLOW_LINKS), Integer.MAX_VALUE, new SimpleFileVisitor<Path>() {
			@Override
			public FileVisitResult visitFile(Path file, BasicFileAttributes attributes) {
				progressIndicator.checkCanceled();
				String fileName = file.getFileName().toString();
				// Skips broken links, sockets and pipes
				if (!attributes.isRegularFile() || ignoredFiles.contains(fileName)) {
					return FileVisitResult.CONTINUE;
				}
				ZipArchiveEntry entry = new ZipArchiveEntry(contentRoot.relativize(file).toString().replace(File.separatorChar, '/'));
				// Deflating already compressed data costs CPU and gains nothing
				entry.setMethod(isCompressed(fileName) ? ZipEntry.STORED : ZipEntry.DEFLATED);
				entry.setTime(attributes.lastModifiedTime().toMillis());
				files.add(file);
				entries.add(entry);
				return FileVisitResult.CONTINUE;
			}

			@Override
			public FileVisitResult visitFileFailed(Path file, IOException exception) throws IOException {
				if (exception instanceof FileSystemLoopException) {
					return FileVisitResult.CONTINUE;
				}
				throw exception;
			}
		});
	}

	private static ExecutorService createCompressionExecutor() {
		// Leave one core to the IDE, single core machines still get one thread
		int threads = Math.max(1, Runtime.getRuntime().availableProcessors() - 1);
		final AtomicInteger threadNumber = new AtomicInteger();
		return Executors.newFixedThreadPool(threads, runnable -> {
			Thread thread = new Thread(runnable, "Zipper compression " + threadNumber.incrementAndGet());
			thread.setDaemon(true);
			return thread;
		});
	}

	private static InputStream openCancelable(Path file, final ProgressIndicator progressIndicator) {
		try {
			return new FilterInputStream(Files.newInputStream(file)) {
				@Override
				public int read(byte[] buffer, int offset, int length) throws IOException {
					if (progressIndicator.isCanceled()) {
						throw new InterruptedIOException("Packing was canceled");
					}
					return super.read(buffer, offset, length);
				}
			};
		} catch (IOException e) {
			throw new UncheckedIOException(e);
		}
	}

	private static boolean isCompressed(String fileName) {
		int dot = fileName.lastIndexOf('.');
		return dot >= 0 && COMPRESSED_EXTENSIONS.contains(fileName.substring(dot + 1).toLowerCase(Locale.ROOT));
	}
}
