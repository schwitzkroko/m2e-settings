package nl.topicus.m2e.settings.internal;

import java.io.IOException;
import java.io.InputStream;
import java.util.List;
import java.util.jar.JarFile;
import java.util.zip.ZipEntry;

import javax.xml.parsers.ParserConfigurationException;

import org.apache.maven.model.Plugin;
import org.apache.maven.project.MavenProject;
import org.eclipse.core.resources.IFile;
import org.eclipse.core.resources.IProject;
import org.eclipse.core.resources.IResource;
import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.IPath;
import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.core.runtime.Path;
import org.eclipse.m2e.core.project.configurator.AbstractProjectConfigurator;
import org.eclipse.m2e.core.project.configurator.ProjectConfigurationRequest;
import org.osgi.service.prefs.BackingStoreException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.xml.sax.SAXException;

public class ProjectSettingsConfigurator extends AbstractProjectConfigurator {

	private static final Logger LOGGER = LoggerFactory
			.getLogger(ProjectSettingsConfigurator.class);

	private static final String ORG_APACHE_MAVEN_PLUGINS_MAVEN_ECLIPSE_PLUGIN = "org.apache.maven.plugins:maven-eclipse-plugin";

	@Override
	public void configure(
			ProjectConfigurationRequest projectConfigurationRequest,
			IProgressMonitor monitor) throws CoreException {

		IProject project = projectConfigurationRequest.getProject();

		MavenProject mavenProject = projectConfigurationRequest
				.getMavenProject();

		Plugin eclipsePlugin = mavenProject.getPluginManagement()
				.getPluginsAsMap()
				.get(ORG_APACHE_MAVEN_PLUGINS_MAVEN_ECLIPSE_PLUGIN);
		if (eclipsePlugin == null) {
			LOGGER.info("Could not set eclipse settings, consider "
					+ ORG_APACHE_MAVEN_PLUGINS_MAVEN_ECLIPSE_PLUGIN + "!");
		} else {
			LOGGER.info("Using "
					+ ORG_APACHE_MAVEN_PLUGINS_MAVEN_ECLIPSE_PLUGIN
					+ " configuration");
			try {
				if (configureEclipseMeta(project, eclipsePlugin, monitor)) {
					LOGGER.info("Project configured.");
				} else {
					LOGGER.error("Project not configured.");
				}
			} catch (IOException e) {
				LOGGER.error("Failure during settings configuration", e);
			}
		}

	}

	/**
	 * Use the org.apache.maven.plugins:maven-eclipse-plugin to force the
	 * eclipse settngs.
	 *
	 * @param project
	 * @param buildPluginMap
	 * @param monitor
	 * @return
	 * @throws SAXException
	 * @throws ParserConfigurationException
	 * @throws IOException
	 * @throws CoreException
	 */
	private boolean configureEclipseMeta(IProject project,
			Plugin eclipsePlugin, IProgressMonitor monitor) throws IOException,
			CoreException {

		List<EclipseSettingsFile> settingsFiles = ConfigurationHelper
				.extractSettingsFile(eclipsePlugin);

		if (settingsFiles == null) {
			LOGGER.warn("No settings specified.");
			return false;
		}

		List<JarFile> jarFiles = JarFileUtil.resolveJar(maven,
				eclipsePlugin.getDependencies(), monitor);

		applyEclipsePreferencesPref(project, settingsFiles, jarFiles);

		return true;
	}

	private void applyEclipsePreferencesPref(IProject project,
			List<EclipseSettingsFile> settingsFiles, List<JarFile> jarFiles)
			throws IOException, CoreException {
		for (EclipseSettingsFile eclipsePreference : settingsFiles) {

			InputStream contentStream = null;
			try {
				contentStream = openStream(eclipsePreference.getLocation(),
						jarFiles);
				if (contentStream == null) {
					LOGGER.error("Could not find content for: "
							+ eclipsePreference.getLocation());
				} else {
					String prefName = eclipsePreference.getName();
					if (prefName.startsWith(".settings/")
							&& prefName.endsWith(".prefs")) {
						ProjectPreferencesUtils.setOtherPreferences(project,
								contentStream,
								prefName.substring(10, prefName.length() - 6));
					} else {
						IPath outputPath = Path.fromOSString(eclipsePreference
								.getName());
						IResource outputCurrent = project
								.findMember(outputPath);
						if (outputCurrent != null)
							outputCurrent.delete(true, null);
						IFile outputFile = project.getFile(outputPath);
						Utils.createDirectory(outputFile.getParent());
						outputFile.create(contentStream, true, null);
					}
				}

			} catch (BackingStoreException e) {
				throw new IOException(e);
			} finally {
				if (contentStream != null) {
					contentStream.close();
				}
			}
		}

	}

	private InputStream openStream(String filePath, List<JarFile> jarFiles)
			throws IOException {
		if (filePath.startsWith("/"))
			filePath = filePath.substring(1);

		for (JarFile jarFile : jarFiles) {
			ZipEntry entry = jarFile.getEntry(filePath);
			if (entry != null) {
				return jarFile.getInputStream(entry);
			}
		}
		LOGGER.warn("Entry " + filePath + " not found in " + jarFiles);
		return null;
	}

}
