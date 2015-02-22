import com.intellij.notification.Notification;
import com.intellij.notification.NotificationType;
import com.intellij.notification.Notifications;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.ProjectRootManager;
import com.intellij.openapi.vfs.VirtualFileManager;
import org.jetbrains.annotations.NotNull;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.List;
import java.util.zip.ZipOutputStream;

public class ZipProject extends AnAction {
	public void actionPerformed(@NotNull AnActionEvent e) {
		final Project project = e.getProject();
		assert project != null;
		final String archiveName = Zipper.showArchiveNameInputDialog();

		if (archiveName != null) {
			List<String> contentRoots = ProjectRootManager.getInstance(project).getContentRootUrls();
			String contentRoot = contentRoots.get(0);

			if (System.getProperty("os.name").toLowerCase().contains("win")) {
				contentRoot = contentRoot.replace("file://", "").replace("/", File.separator) + File.separator;
			}

			final String contentDirectory = contentRoot;
			final String archivePath = contentDirectory + archiveName + Zipper.FILE_EXTENSION;

			try {
				ProgressManager.getInstance().runProcessWithProgressSynchronously(
						new Runnable() {
							@Override
							public void run() {
								ProgressManager.getInstance().getProgressIndicator().setIndeterminate(true);

								try {
									File dirObj = new File(contentDirectory);
									ZipOutputStream out;
									File temp;
									try {
										temp = File.createTempFile(archiveName, Zipper.FILE_EXTENSION);
										temp.deleteOnExit();
										out = new ZipOutputStream(new FileOutputStream(temp));
										Zipper.addDirectoryToZip(dirObj, out, contentDirectory);
										out.close();
										if (temp.renameTo(new File(archivePath))) {
											Thread.sleep(50);
											VirtualFileManager.getInstance().asyncRefresh(null);
											Notifications.Bus.notify(new Notification("Zipper", "Success", Zipper.MESSAGE_SUCCESS, NotificationType.INFORMATION));
										} else {
											Notifications.Bus.notify(new Notification("Zipper", "Error", Zipper.MESSAGE_ERROR, NotificationType.ERROR));
										}
									} catch (IOException e1) {
										Notifications.Bus.notify(new Notification("Zipper", "Error", Zipper.MESSAGE_ERROR, NotificationType.ERROR));
									} catch (InterruptedException e1) {
										Notifications.Bus.notify(new Notification("Zipper", "Error", Zipper.MESSAGE_ERROR, NotificationType.ERROR));
									}
								} catch (Exception e1) {
									Notifications.Bus.notify(new Notification("Zipper", "Error", Zipper.MESSAGE_ERROR, NotificationType.ERROR));
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
}
