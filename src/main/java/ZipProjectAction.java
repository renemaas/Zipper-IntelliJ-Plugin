import com.intellij.openapi.actionSystem.ActionUpdateThread;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.progress.ProcessCanceledException;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.progress.Task;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.ProjectRootManager;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vfs.VfsUtil;
import com.intellij.openapi.vfs.VfsUtilCore;
import com.intellij.openapi.vfs.VirtualFile;
import org.jetbrains.annotations.NotNull;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.TimeUnit;

public class ZipProjectAction extends AnAction {
	private static final Logger LOG = Logger.getInstance(ZipProjectAction.class);

	@Override
	public @NotNull ActionUpdateThread getActionUpdateThread() {
		return ActionUpdateThread.BGT;
	}

	@Override
	public void update(@NotNull AnActionEvent e) {
		e.getPresentation().setEnabledAndVisible(e.getProject() != null);
	}

	public void actionPerformed(@NotNull AnActionEvent e) {
		final Project project = e.getProject();
		if (project == null) {
			return;
		}
		final List<VirtualFile> contentRoots = getTopLevelContentRoots(project);
		if (contentRoots.isEmpty()) {
			Zipper.throwError("The project has no content roots");
			return;
		}
		final String archiveName = Zipper.showArchiveNameInputDialog(project);
		if (archiveName == null) {
			return;
		}

		final String archiveFileName = archiveName + Zipper.FILE_EXTENSION;
		final long startTime = System.currentTimeMillis();
		ProgressManager.getInstance().run(new Task.Backgroundable(project, Zipper.TITLE, true) {
			@Override
			public void run(@NotNull ProgressIndicator progressIndicator) {
				progressIndicator.setIndeterminate(true);
				progressIndicator.setText(Zipper.MESSAGE_PACKING_PROJECT);
				Set<String> ignoredFiles = loadIgnoredFiles(project, archiveFileName);
				List<VirtualFile> packedRoots = new ArrayList<VirtualFile>();
				List<String> errors = new ArrayList<String>();
				try {
					for (VirtualFile contentRoot : contentRoots) {
						Path contentDirectory = contentRoot.toNioPath();
						try {
							progressIndicator.setText2(Zipper.LABEL_PACKING + contentDirectory);
							Zipper.createArchive(contentDirectory, contentDirectory.resolve(archiveFileName), ignoredFiles, progressIndicator);
							packedRoots.add(contentRoot);
						} catch (ProcessCanceledException e1) {
							throw e1;
						} catch (Exception e1) {
							// Keep packing the other content roots
							LOG.warn("Packing " + contentDirectory + " failed", e1);
							errors.add(StringUtil.escapeXmlEntities(contentDirectory + ": " + e1.getMessage()));
						}
					}
				} finally {
					if (!packedRoots.isEmpty()) {
						// Only the content root directories changed, no need to refresh the whole VFS
						VfsUtil.markDirtyAndRefresh(true, false, true, packedRoots.toArray(VirtualFile.EMPTY_ARRAY));
					}
				}

				if (errors.isEmpty()) {
					String execTime = TimeUnit.MILLISECONDS.toSeconds((System.currentTimeMillis() - startTime)) + "s";
					Zipper.throwSuccess(execTime);
				} else {
					Zipper.throwError(StringUtil.join(errors, "<br>"));
				}
			}
		});
	}

	private static List<VirtualFile> getTopLevelContentRoots(Project project) {
		VirtualFile[] contentRoots = ProjectRootManager.getInstance(project).getContentRoots();
		List<VirtualFile> topLevelRoots = new ArrayList<VirtualFile>();
		for (VirtualFile contentRoot : contentRoots) {
			if (!contentRoot.isInLocalFileSystem() || !contentRoot.isDirectory()) {
				continue;
			}
			// Nested content roots are already part of the archive of their parent
			boolean nested = false;
			for (VirtualFile other : contentRoots) {
				if (!other.equals(contentRoot) && VfsUtilCore.isAncestor(other, contentRoot, true)) {
					nested = true;
					break;
				}
			}
			if (!nested && !topLevelRoots.contains(contentRoot)) {
				topLevelRoots.add(contentRoot);
			}
		}
		return topLevelRoots;
	}

	private static Set<String> loadIgnoredFiles(Project project, String archiveFileName) {
		Set<String> ignoredFiles = new HashSet<String>();
		ignoredFiles.add(archiveFileName);
		String basePath = project.getBasePath();
		if (basePath == null) {
			return ignoredFiles;
		}
		// The ignore list lives in .idea, projects without it only skip the current archive
		Path ideaDirectory = Paths.get(basePath, Project.DIRECTORY_STORE_FOLDER);
		if (Files.isDirectory(ideaDirectory)) {
			Path ignoreFile = ideaDirectory.resolve(Zipper.IGNORE_FILE);
			try {
				Zipper.addArchiveToIgnoreList(ignoreFile, archiveFileName);
				ignoredFiles.addAll(Zipper.getIgnoredFiles(ignoreFile));
			} catch (IOException e) {
				LOG.warn("Cannot update " + ignoreFile, e);
			}
		}
		return ignoredFiles;
	}
}
