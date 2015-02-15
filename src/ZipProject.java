import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFileManager;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.zip.ZipOutputStream;

public class ZipProject extends AnAction {
	public void actionPerformed(AnActionEvent e) {
		final Project project = e.getProject();
		assert project != null;
		final String projectRoot = project.getBasePath() + "\\";
		final String archiveName = Zipper.showArchiveNameInputDialog();
		final String archivePath = projectRoot + archiveName + Zipper.FILE_EXTENSION;

		try {
			ProgressManager.getInstance().runProcessWithProgressSynchronously(
					new Runnable() {
						@Override
						public void run() {
							ProgressManager.getInstance().getProgressIndicator().setIndeterminate(true);

							try {
								File dirObj = new File(projectRoot);
								ZipOutputStream out;
								File temp;
								try {
									temp = File.createTempFile(archiveName, Zipper.FILE_EXTENSION);
									temp.deleteOnExit();
									out = new ZipOutputStream(new FileOutputStream(temp));
									Zipper.addDirectoryToZip(dirObj, out, projectRoot);
									out.close();
									if (temp.renameTo(new File(archivePath))) {
										Thread.sleep(50);
										VirtualFileManager.getInstance().asyncRefresh(null);
									}
								} catch (IOException e1) {
									e1.printStackTrace();
								} catch (InterruptedException e1) {
									e1.printStackTrace();
								}
							} catch (Exception ignored) {
							}

						}
					},
					"Project is being packed",
					false,
					project);
		} catch (Exception ignored) {
		}
	}
}
