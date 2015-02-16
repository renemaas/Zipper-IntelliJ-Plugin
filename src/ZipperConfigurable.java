import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.ui.configuration.ModulesConfigurator;
import com.intellij.openapi.roots.ui.configuration.ProjectConfigurable;
import com.intellij.openapi.roots.ui.configuration.projectRoot.ProjectSdksModel;
import com.intellij.openapi.roots.ui.configuration.projectRoot.StructureConfigurableContext;
import org.jetbrains.annotations.Nullable;

public class ZipperConfigurable extends ProjectConfigurable {


	public ZipperConfigurable(Project project, StructureConfigurableContext context, ModulesConfigurator configurator, ProjectSdksModel model) {
		super(project, context, configurator, model);
	}

	public String getDisplayName() {
		return "Zipper";
	}

	@Nullable
	@Override
	public String getHelpTopic() {
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
