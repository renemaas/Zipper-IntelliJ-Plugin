import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.progress.ProcessCanceledException;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.progress.Task;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.ProjectRootManager;
import com.intellij.openapi.vfs.VirtualFileManager;
import org.jetbrains.annotations.NotNull;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.concurrent.TimeUnit;

public class ZipProjectAction extends AnAction {
	public void actionPerformed(@NotNull AnActionEvent e) {
		final Project project = e.getProject();
		assert project != null;
		final String archiveName = Zipper.showArchiveNameInputDialog(project);
		final String ignoreFile = project.getBasePath() + File.separator + ".idea" + File.separator + Zipper.IGNORE_FILE;

		if (archiveName != null) {
			final long startTime = System.currentTimeMillis();
			final List<String> contentRoots = ProjectRootManager.getInstance(project).getContentRootUrls();
			Set<String> ignoredFiles = Collections.emptySet();
			try {
				Zipper.addArchiveToIgnoreList(ignoreFile, archiveName + Zipper.FILE_EXTENSION);
				ignoredFiles = Zipper.getIgnoredFiles(ignoreFile);
			} catch (IOException e1) {
				Zipper.throwError();
			}

			final Set<String> finalIgnoredFiles = ignoredFiles;
			ProgressManager.getInstance().run(new Task.Backgroundable(project, Zipper.TITLE, true) {
				@Override
				public void run(@NotNull ProgressIndicator progressIndicator) {
					try {
						progressIndicator.setIndeterminate(true);
						progressIndicator.setText(Zipper.MESSAGE_PACKING_PROJECT);
						int archivesCreated = 0;
						int contentRootsSize = contentRoots.size();
						for (String contentRoot : contentRoots) {
							final String contentDirectory = Zipper.optimizeContentRootUrl(contentRoot);
							final String archivePath = contentDirectory + archiveName + Zipper.FILE_EXTENSION;
							progressIndicator.setText2(Zipper.LABEL_PACKING + contentDirectory);
							File tempFile = File.createTempFile(archiveName, Zipper.FILE_EXTENSION);
							try {
								Zipper.createArchive(new File(contentDirectory), tempFile, finalIgnoredFiles, progressIndicator);
								progressIndicator.setText2(Zipper.LABEL_SAVING + archivePath);
								// Falls back to copy + delete if the temp dir is on another file system
								Files.move(tempFile.toPath(), new File(archivePath).toPath(), StandardCopyOption.REPLACE_EXISTING);
								archivesCreated++;
							} finally {
								Files.deleteIfExists(tempFile.toPath());
							}
						}
						if (archivesCreated == contentRootsSize) {
							VirtualFileManager.getInstance().asyncRefresh(null);
							String execTime = TimeUnit.MILLISECONDS.toSeconds((System.currentTimeMillis() - startTime)) + "s";
							Zipper.throwSuccess(execTime);
						}
					} catch (ProcessCanceledException e1) {
						// Canceled by the user, the platform expects this to be rethrown
						throw e1;
					} catch (Exception e1) {
						Zipper.throwError();
					}
				}
			});
		}
	}
}
