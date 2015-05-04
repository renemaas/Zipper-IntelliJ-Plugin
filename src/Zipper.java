import javax.swing.*;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

public class Zipper {

	public final static String DATE_FORMAT = "yyyy-MM-dd_HH-mm-ss";
	public final static String FILE_EXTENSION = ".zip";
	public final static String MESSAGE_SUCCESS = "Project was packed successfully";
	public final static String MESSAGE_ERROR = "An error occurred while packing the project";

	public static String showArchiveNameInputDialog() {
		return removeExtensionFromFileName(
				(String) JOptionPane.showInputDialog(
						null,
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
		if (fileName != null) {
			if (fileName.substring(fileName.length() - 4).equals(FILE_EXTENSION)) {
				return fileName.substring(0, fileName.length() - 4);
			}
		}
		return fileName;
	}

	public static void addDirectoryToZip(File dirObj, ZipOutputStream out, String projectRoot) throws IOException {
		//dirObj = dirObj.replace("file:", "file://");
		File[] files = dirObj.listFiles();
		byte[] tmpBuf = new byte[1024];

		assert files != null;
		for (File file : files) {
			if (file.isDirectory()) {
				addDirectoryToZip(file, out, projectRoot);
				continue;
			}
			FileInputStream in = new FileInputStream(file.getAbsolutePath());
			out.putNextEntry(new ZipEntry(file.getAbsolutePath().replace(projectRoot, "")));
			int len;
			while ((len = in.read(tmpBuf)) > 0) {
				out.write(tmpBuf, 0, len);
			}
			out.closeEntry();
			in.close();
		}
	}
}
