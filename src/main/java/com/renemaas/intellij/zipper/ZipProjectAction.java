package com.renemaas.intellij.zipper;

import com.intellij.openapi.actionSystem.ActionUpdateThread;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.ProjectRootManager;
import com.intellij.openapi.vfs.VirtualFileManager;
import org.jetbrains.annotations.NotNull;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.zip.ZipOutputStream;

public class ZipProjectAction extends AnAction {
	private static final Logger LOG = Logger.getInstance(ZipProjectAction.class);

	@Override
	public @NotNull ActionUpdateThread getActionUpdateThread() {
		return ActionUpdateThread.BGT;
	}

	@Override
	public void update(@NotNull AnActionEvent e) {
		e.getPresentation().setEnabled(e.getProject() != null);
	}

	@Override
	public void actionPerformed(@NotNull AnActionEvent e) {
		final Project project = e.getProject();
		if (project == null) {
			return;
		}
		final String archiveName = Zipper.showArchiveNameInputDialog(project);
		final String ignoreFile = project.getBasePath() + File.separator + ".idea" + File.separator + Zipper.IGNORE_FILE;

		if (archiveName != null) {
			final long startTime = System.currentTimeMillis();
			final List<String> contentRoots = ProjectRootManager.getInstance(project).getContentRootUrls();
			String[] ignoredFiles;
			try {
				Zipper.addArchiveToIgnoreList(ignoreFile, archiveName + Zipper.FILE_EXTENSION);
				ignoredFiles = Zipper.getIgnoredFiles(ignoreFile);
			} catch (IOException e1) {
				// Pack anyway, but at least keep the new archive out of itself
				LOG.warn("Could not update ignore list " + ignoreFile, e1);
				ignoredFiles = new String[]{archiveName + Zipper.FILE_EXTENSION};
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
									File tempFile = File.createTempFile(archiveName, Zipper.FILE_EXTENSION);
									tempFile.deleteOnExit();
									try (ZipOutputStream zipOutputStream = new ZipOutputStream(new FileOutputStream(tempFile))) {
										Zipper.addDirectoryToZip(contentDirectoryObject, zipOutputStream, contentDirectory, finalIgnoredFiles);
									}
									progressIndicator.setText2(Zipper.LABEL_SAVING + archivePath);
									// Files.move falls back to copy + delete when the temp dir is on another file system
									Files.move(tempFile.toPath(), Paths.get(archivePath), StandardCopyOption.REPLACE_EXISTING);
									archivesCreated++;
								}
								if (archivesCreated == contentRootsSize) {
									VirtualFileManager.getInstance().asyncRefresh(null);
									String execTime = TimeUnit.MILLISECONDS.toSeconds((System.currentTimeMillis() - startTime)) + "s";
									Zipper.throwSuccess(project, execTime);
								}
							} catch (Exception e1) {
								Zipper.throwError(project);
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
