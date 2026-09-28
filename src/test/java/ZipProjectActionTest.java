import com.intellij.notification.Notification;
import com.intellij.notification.NotificationType;
import com.intellij.notification.Notifications;
import com.intellij.openapi.actionSystem.impl.SimpleDataContext;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.ui.TestDialogManager;
import com.intellij.openapi.ui.TestInputDialog;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.vfs.LocalFileSystem;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.testFramework.HeavyPlatformTestCase;
import com.intellij.testFramework.PsiTestUtil;
import com.intellij.testFramework.TestActionEvent;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

public class ZipProjectActionTest extends HeavyPlatformTestCase {
	private Path root;
	private final List<Notification> notifications = new ArrayList<Notification>();

	@Override
	protected void setUp() throws Exception {
		super.setUp();
		root = Files.createTempDirectory("zipper-test").toRealPath();
		ApplicationManager.getApplication().getMessageBus().connect(getTestRootDisposable())
				.subscribe(Notifications.TOPIC, new Notifications() {
					@Override
					public void notify(Notification notification) {
						notifications.add(notification);
					}
				});
		TestDialogManager.setTestInputDialog(new TestInputDialog() {
			@Override
			public String show(String message) {
				return "backup.zip";
			}
		});
	}

	@Override
	protected void tearDown() throws Exception {
		try {
			TestDialogManager.setTestInputDialog(TestInputDialog.DEFAULT);
			FileUtil.delete(root);
		} finally {
			super.tearDown();
		}
	}

	public void testPacksContentRootWithRelativeEntries() throws Exception {
		write("a.txt", "a");
		write("src/Main.java", "class Main {}");
		write("assets/image.png", "png");
		write("backup.zip", "previous archive");
		Files.createSymbolicLink(root.resolve("src/loop"), root.resolve("src"));
		Files.createSymbolicLink(root.resolve("broken"), root.resolve("does-not-exist"));
		addContentRoot(root);

		runAction();

		Map<String, String> entries = readZip(root.resolve("backup.zip"));
		assertEquals(new TreeSet<String>(Arrays.asList("a.txt", "src/Main.java", "assets/image.png")), entries.keySet());
		assertEquals("class Main {}", entries.get("src/Main.java"));
		assertSingleNotification(NotificationType.INFORMATION);
	}

	public void testNestedContentRootIsNotPackedTwice() throws Exception {
		write("a.txt", "a");
		write("module/b.txt", "b");
		addContentRoot(root);
		addContentRoot(root.resolve("module"));

		runAction();

		assertEquals(new TreeSet<String>(Arrays.asList("a.txt", "module/b.txt")), readZip(root.resolve("backup.zip")).keySet());
		assertFalse(Files.exists(root.resolve("module/backup.zip")));
		assertSingleNotification(NotificationType.INFORMATION);
	}

	public void testArchiveNameValidation() {
		assertTrue(Zipper.isValidArchiveName("backup"));
		assertTrue(Zipper.isValidArchiveName("backup.ZIP"));
		assertFalse(Zipper.isValidArchiveName(""));
		assertFalse(Zipper.isValidArchiveName("  "));
		assertFalse(Zipper.isValidArchiveName(".zip"));
		assertFalse(Zipper.isValidArchiveName(".."));
		assertFalse(Zipper.isValidArchiveName("a/b"));
		assertFalse(Zipper.isValidArchiveName("a:b"));
		assertEquals("backup", Zipper.removeExtensionFromFileName(" backup.ZIP "));
	}

	private void runAction() {
		new ZipProjectAction().actionPerformed(TestActionEvent.createTestEvent(SimpleDataContext.getProjectContext(getProject())));
	}

	private void write(String relativePath, String content) throws IOException {
		Path file = root.resolve(relativePath);
		Files.createDirectories(file.getParent());
		Files.write(file, content.getBytes(StandardCharsets.UTF_8));
	}

	private void addContentRoot(Path directory) {
		VirtualFile virtualFile = LocalFileSystem.getInstance().refreshAndFindFileByNioFile(directory);
		assertNotNull(virtualFile);
		PsiTestUtil.addContentRoot(getModule(), virtualFile);
	}

	private void assertSingleNotification(NotificationType type) {
		assertEquals(1, notifications.size());
		assertEquals(notifications.get(0).getContent(), type, notifications.get(0).getType());
	}

	private static Map<String, String> readZip(Path archive) throws IOException {
		Map<String, String> entries = new TreeMap<String, String>();
		try (ZipFile zipFile = new ZipFile(archive.toFile())) {
			for (ZipEntry entry : Collections.list(zipFile.entries())) {
				try (InputStream inputStream = zipFile.getInputStream(entry)) {
					entries.put(entry.getName(), new String(inputStream.readAllBytes(), StandardCharsets.UTF_8));
				}
			}
		}
		return entries;
	}
}
