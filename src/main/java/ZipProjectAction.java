import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.ProjectRootManager;
import com.intellij.openapi.vfs.VirtualFileManager;
import org.jetbrains.annotations.NotNull;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.zip.ZipOutputStream;

public class ZipProjectAction extends AnAction {
	public void actionPerformed(@NotNull AnActionEvent e) {
		final Project project = e.getProject();
		assert project != null;
		final String archiveName = Zipper.showArchiveNameInputDialog(project);
		final String ignoreFile = project.getBasePath() + File.separator + ".idea" + File.separator + Zipper.IGNORE_FILE;

		if (archiveName != null) {
			final long startTime = System.currentTimeMillis();
			final List<String> contentRoots = ProjectRootManager.getInstance(project).getContentRootUrls();
			String[] ignoredFiles = new String[0];
			try {
				Zipper.addArchiveToIgnoreList(ignoreFile, archiveName + Zipper.FILE_EXTENSION);
				ignoredFiles = Zipper.getIgnoredFiles(ignoreFile);
			} catch (IOException e1) {
				Zipper.throwError();
			}

			final String[] finalIgnoredFiles = ignoredFiles;
			ProgressManager.getInstance().runProcessWithProgressSynchronously(
					new Runnable() {
						@Override
						public void run() {
							try {
								ProgressIndicator progressIndicator = ProgressManager.getInstance().getProgressIndicator();
								progressIndicator.setIndeterminate(true);
								progressIndicator.setText(Zipper.MESSAGE_PACKING_PROJECT);
								int archivesCreated = 0;
								int contentRootsSize = contentRoots.size();
								for (String contentRoot : contentRoots) {
									final String contentDirectory = Zipper.optimizeContentRootUrl(contentRoot);
									final String archivePath = contentDirectory + archiveName + Zipper.FILE_EXTENSION;
									progressIndicator.setText2(Zipper.LABEL_PACKING + contentDirectory);
									File contentDirectoryObject = new File(contentDirectory);
									ZipOutputStream zipOutputStream;
									File tempFile;
									tempFile = File.createTempFile(archiveName, Zipper.FILE_EXTENSION);
									tempFile.deleteOnExit();
									zipOutputStream = new ZipOutputStream(new FileOutputStream(tempFile));
									Zipper.addDirectoryToZip(contentDirectoryObject, zipOutputStream, contentDirectory, finalIgnoredFiles);
									zipOutputStream.close();
									progressIndicator.setText2(Zipper.LABEL_SAVING + archivePath);
									if (tempFile.renameTo(new File(archivePath))) {
										archivesCreated++;
									} else {
										Zipper.throwError();
									}
								}
								if (archivesCreated == contentRootsSize) {
									VirtualFileManager.getInstance().asyncRefresh(null);
									String execTime = TimeUnit.MILLISECONDS.toSeconds((System.currentTimeMillis() - startTime)) + "s";
									Zipper.throwSuccess(execTime);
								}
							} catch (Exception e1) {
								Zipper.throwError();
							}
						}
					},
					Zipper.TITLE,
					false,
					project
			);
		}
	}
}
