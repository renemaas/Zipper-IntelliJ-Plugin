import com.intellij.notification.Notification;
import com.intellij.notification.NotificationType;
import com.intellij.notification.Notifications;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.wm.WindowManager;

import javax.swing.*;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.Arrays;
import java.util.Date;
import java.util.HashSet;
import java.util.Locale;
import java.util.Set;
import java.util.zip.Deflater;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

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

	private static final int BUFFER_SIZE = 64 * 1024;
	private static final Set<String> COMPRESSED_EXTENSIONS = new HashSet<String>(Arrays.asList(
			"zip", "jar", "war", "ear", "apk", "gz", "tgz", "bz2", "xz", "7z", "rar", "zst",
			"png", "jpg", "jpeg", "gif", "webp", "avif", "heic",
			"mp3", "mp4", "m4a", "mov", "avi", "mkv", "webm", "ogg", "flac",
			"woff", "woff2", "docx", "xlsx", "pptx", "odt", "ods", "odp"
	));

	public static String showArchiveNameInputDialog(Project project) {
		return removeExtensionFromFileName(
				(String) JOptionPane.showInputDialog(
						WindowManager.getInstance().getFrame(project),
						"Please enter the name of the ZIP archive without extension",
						"Zipper",
						JOptionPane.QUESTION_MESSAGE,
						null,
						null,
						getCurrentDateFormatted()
				)
		);
	}

	public static String getCurrentDateFormatted() {
		DateFormat dateFormat = new SimpleDateFormat(DATE_FORMAT);
		Date date = new Date();
		return dateFormat.format(date);
	}

	public static String removeExtensionFromFileName(String fileName) {
		if (fileName != null && fileName.length() > 4) {
			if (fileName.substring(fileName.length() - 4).equals(FILE_EXTENSION)) {
				return fileName.substring(0, fileName.length() - 4);
			}
		}
		return fileName;
	}

	public static boolean isWindows() {
		return System.getProperty("os.name").toLowerCase().contains("win");
	}

	public static String optimizeContentRootUrl(String contentRoot) {
		if (Zipper.isWindows()) {
			contentRoot = contentRoot.replace("file://", "").replace("/", File.separator) + File.separator;
		} else {
			contentRoot = contentRoot.replace("file:", "") + File.separator;
		}
		return contentRoot;
	}

	public static void throwError() {
		Notifications.Bus.notify(new Notification(Zipper.TITLE, Zipper.TITLE_ERROR, Zipper.MESSAGE_ERROR, NotificationType.ERROR));
	}

	public static void throwSuccess(String execTime) {
		Notifications.Bus.notify(new Notification(Zipper.TITLE, Zipper.TITLE_SUCCESS, Zipper.MESSAGE_SUCCESS + execTime, NotificationType.INFORMATION));
	}

	public static Set<String> getIgnoredFiles(String filePath) throws IOException {
		return new HashSet<String>(Files.readAllLines(Paths.get(filePath), StandardCharsets.UTF_8));
	}

	public static void addArchiveToIgnoreList(String filePath, String s) throws IOException {
		try (Writer writer = new OutputStreamWriter(new FileOutputStream(filePath, true), StandardCharsets.UTF_8)) {
			writer.write(s + "\n");
		}
	}

	public static void createArchive(File contentDirectoryObject, File archiveFile, Set<String> ignoredFiles) throws IOException {
		// Normalized path, the raw content root URL may contain duplicate separators
		String contentRoot = contentDirectoryObject.getAbsolutePath();
		if (!contentRoot.endsWith(File.separator)) {
			contentRoot += File.separator;
		}
		try (ZipOutputStream zipOutputStream = new ZipOutputStream(new BufferedOutputStream(new FileOutputStream(archiveFile), BUFFER_SIZE))) {
			addDirectoryToZip(contentDirectoryObject, zipOutputStream, contentRoot, ignoredFiles, new byte[BUFFER_SIZE]);
		}
	}

	private static void addDirectoryToZip(File contentDirectoryObject, ZipOutputStream zipOutputStream, String contentRoot, Set<String> ignoredFiles, byte[] buffer) throws IOException {
		File[] files = contentDirectoryObject.listFiles();
		if (files == null) {
			throw new IOException("Cannot read directory " + contentDirectoryObject);
		}

		for (File file : files) {
			if (file.isDirectory()) {
				addDirectoryToZip(file, zipOutputStream, contentRoot, ignoredFiles, buffer);
				continue;
			}
			String fileName = file.getName();
			if (ignoredFiles.contains(fileName)) {
				continue;
			}
			// Deflating already compressed data costs CPU and gains nothing
			zipOutputStream.setLevel(isCompressed(fileName) ? Deflater.NO_COMPRESSION : Deflater.DEFAULT_COMPRESSION);
			String entryName = file.getAbsolutePath().substring(contentRoot.length()).replace(File.separatorChar, '/');
			zipOutputStream.putNextEntry(new ZipEntry(entryName));
			try (InputStream inputStream = new FileInputStream(file)) {
				int length;
				while ((length = inputStream.read(buffer)) > 0) {
					zipOutputStream.write(buffer, 0, length);
				}
			}
			zipOutputStream.closeEntry();
		}
	}

	private static boolean isCompressed(String fileName) {
		int dot = fileName.lastIndexOf('.');
		return dot >= 0 && COMPRESSED_EXTENSIONS.contains(fileName.substring(dot + 1).toLowerCase(Locale.ROOT));
	}
}
