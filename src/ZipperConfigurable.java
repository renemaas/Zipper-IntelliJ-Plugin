import com.intellij.openapi.options.Configurable;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;

public class ZipperConfigurable implements Configurable {


	public String getDisplayName() {
		return "Zipper";
	}

	@Nullable
	@Override
	public String getHelpTopic() {
		return null;
	}

	@Nullable
	@Override
	public JComponent createComponent() {
		return null;
	}

	public boolean isModified() {
		return true;
	}

	public void apply() {

	}

	public void reset() {

	}

	@Override
	public void disposeUIResources() {

	}
}
