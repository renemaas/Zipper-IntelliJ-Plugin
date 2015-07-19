import com.intellij.notification.Notification;
import com.intellij.notification.NotificationType;
import com.intellij.notification.Notifications;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.wm.WindowManager;

import javax.swing.*;
import java.io.*;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Date;
import java.util.List;
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

	public static String[] getIgnoredFiles(String filePath) throws IOException {
		FileReader fileReader = new FileReader(filePath);
		BufferedReader bufferedReader = new BufferedReader(fileReader);
		List<String> lines = new ArrayList<String>();
		String line;
		while ((line = bufferedReader.readLine()) != null) {
			lines.add(line);
		}
		bufferedReader.close();
		return lines.toArray(new String[lines.size()]);
	}

	public static void addArchiveToIgnoreList(String filePath, String s) throws IOException {
		FileWriter writer = new FileWriter(filePath, true);
		writer.write(s + "\n");
		writer.close();
	}

	public static void addDirectoryToZip(File contentDirectoryObject, ZipOutputStream zipOutputStream, String contentRoot, String[] ignoredFiles) throws IOException {
		File[] files = contentDirectoryObject.listFiles();
		byte[] tmpBuf = new byte[1024];

		assert files != null;
		for (File file : files) {
			if (file.isDirectory()) {
				addDirectoryToZip(file, zipOutputStream, contentRoot, ignoredFiles);
				continue;
			}
			if (!Arrays.asList(ignoredFiles).contains(file.getName())) {
				FileInputStream fileInputStream = new FileInputStream(file.getAbsolutePath());
				zipOutputStream.putNextEntry(new ZipEntry(file.getAbsolutePath().replace(contentRoot, "")));
				int length;
				while ((length = fileInputStream.read(tmpBuf)) > 0) {
					zipOutputStream.write(tmpBuf, 0, length);
				}
				zipOutputStream.closeEntry();
				fileInputStream.close();
			}
		}
	}
}
