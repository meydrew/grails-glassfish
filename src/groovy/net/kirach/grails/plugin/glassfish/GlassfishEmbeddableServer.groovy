package net.kirach.grails.plugin.glassfish

import com.sun.tools.attach.VirtualMachine
import grails.util.PluginBuildSettings
import groovy.transform.CompileStatic
import org.codehaus.groovy.grails.cli.fork.ExecutionContext
import org.glassfish.embeddable.GlassFishProperties
import org.glassfish.embeddable.web.Context
import org.glassfish.embeddable.web.WebContainer
import org.glassfish.embeddable.web.config.WebContainerConfig

import java.lang.management.ManagementFactory

import static grails.build.logging.GrailsConsole.instance as CONSOLE

import grails.util.BuildSettings
import grails.util.BuildSettingsHolder
import grails.web.container.EmbeddableServer
import org.apache.commons.io.FileUtils
import org.glassfish.embeddable.GlassFish
import org.glassfish.embeddable.GlassFishRuntime

/**
 * Glassfish embeddable server for grails use.
 *
 * @author kiRach
 */
class GlassfishEmbeddableServer implements EmbeddableServer {

	final BuildSettings buildSettings

	GlassFish glassfish
	WebContainer embedded
	String webXml
	String basedir
	String contextPath
	ClassLoader classLoader

	Context context

	File reloadingAgent

	int localHttpPort

	/**
	 * Constructor.
	 */
	GlassfishEmbeddableServer(String basedir, String webXml, String contextPath, ClassLoader classLoader) {
		buildSettings = BuildSettingsHolder.getSettings()

		if (System.getProperty('GF_ROOT') != null) {
			GlassFishProperties glassfishProperties = new GlassFishProperties()
			glassfishProperties.setInstanceRoot(System.getProperty('GF_ROOT'))
			glassfish = GlassFishRuntime.bootstrap().newGlassFish(glassfishProperties)
		}
		else {
			glassfish = GlassFishRuntime.bootstrap().newGlassFish()
		}

		//just remember this params for using later
		this.webXml = webXml
		this.basedir = basedir
		this.contextPath = contextPath

		this.classLoader = new URLClassLoader([] as URL[], classLoader)
	}

	/**
	 * Start embedded glassfish and deploy grails app to it.
	 *
	 * @param host
	 * @param httpPort
	 */
	private doStart(String host, int httpPort) {
		CONSOLE.updateStatus "Starting GlassFish server"

		// sometimes needed by Grails?
		localHttpPort = httpPort

		//start server
		glassfish.start()

		//get web-container
		embedded = glassfish.getService(WebContainer.class)

		//set web-container' config
		WebContainerConfig config = new WebContainerConfig()
		//here we can configure our web container
		config.setPort(httpPort)
		config.setHostNames(host)
		embedded.setConfiguration(config)

		//enable comet support, if needed
		if (getConfigParam("comet")) {
			CONSOLE.updateStatus "Enabling GlassFish Comet support"
			def commandRunner = glassfish.getCommandRunner()
			def res = commandRunner.run("set", "server-config.network-config.protocols.protocol." + config.getListenerName() + ".http.comet-support-enabled=true")
		}

		//enable websocket support, if needed
		if (getConfigParam("websocket")) {
			CONSOLE.updateStatus "Enabling GlassFish Websocket support"
			def commandRunner = glassfish.getCommandRunner()
			def res = commandRunner.run("set", "server-config.network-config.protocols.protocol." + config.getListenerName() + ".http.websockets-support-enabled=true")
		}

		//copy grails' web.xml to our directory
		def tempWebXml = new File("${this.basedir}/WEB-INF/web.xml")
		FileUtils.copyFile(new File(this.webXml), tempWebXml)

		try {
			//let's create context - our web application from basedir war from step before and ClassLoader, provided by grails
			this.context = this.embedded.createContext(new File(this.basedir), this.contextPath, this.classLoader)
		}
		finally {
			//remove previously copied web.xml after deployment
			tempWebXml.delete()
		}

		//loadAgent()
		//setupReloading((URLClassLoader) this.classLoader, buildSettings)
	}

	@Override
	void start() {
		start(null, null)
	}

	@Override
	void start(int port) {
		start(null, port)
	}

	@Override
	void start(String host, int port) {
		doStart(host ?: DEFAULT_HOST, port ?: DEFAULT_PORT)
	}

	@Override
	void startSecure() {
		//TODO
	}

	@Override
	void startSecure(int port) {
		//TODO
	}

	@Override
	void startSecure(String host, int httpPort, int httpsPort) {
		//TODO
	}

	@Override
	void stop() {
		CONSOLE.updateStatus "Stopping GlassFish server"
		if (glassfish != null) {
			glassfish.stop();
			glassfish.dispose();
			glassfish = null;
		}
	}

	@Override
	void restart() {
		CONSOLE.updateStatus "Restarting a GlassFish server"
		//here it is mentioned, that restart doesn't work: http://bit.ly/Hhb9nw
		stop()
		start()
	}

	/**
	 * Get config param from "Config.groovy", related to glassfish.
	 */
	private getConfigParam(String name) {
		buildSettings.config.grails.glassfish[name]
	}

	/**
	 * startup reloading of compiled class files
	 */
	protected void setupReloading(URLClassLoader classLoader, BuildSettings buildSettings) {
		Thread.start {
			final holders = classLoader.loadClass("grails.util.Holders")
			while(!holders.getPluginManager()) {
				sleep(1000)
			}
			startProjectWatcher(classLoader, buildSettings)
		}
	}

	protected void startProjectWatcher(URLClassLoader classLoader, BuildSettings buildSettings) {
		try {
			final projectCompiler = classLoader.loadClass("org.codehaus.groovy.grails.compiler.GrailsProjectCompiler").newInstance(new PluginBuildSettings(buildSettings), classLoader)
			projectCompiler.configureClasspath()
			final holders = classLoader.loadClass("grails.util.Holders")
			final projectWatcher = classLoader.loadClass("org.codehaus.groovy.grails.compiler.GrailsProjectWatcher").newInstance(projectCompiler, holders.getPluginManager())
			projectWatcher.run()
		} catch (e) {
			e.printStackTrace()
			println "WARNING: There was an error setting up reloading. Changes to classes will not be reflected: ${e.message}"
		}
	}

	public static void loadAgent() {
		String nameOfRunningVM = ManagementFactory.getRuntimeMXBean().getName();
		int p = nameOfRunningVM.indexOf('@');
		String pid = nameOfRunningVM.substring(0, p);

		final grailsHome = new File(System.getProperty('grails.home'))

		File agentJar = null
		if (grailsHome && grailsHome.exists()) {
			def agentHome = new File(grailsHome, "lib/org.springframework/springloaded/jars")
			agentJar = agentHome.listFiles().find { File f -> f.name.endsWith(".jar") && !f.name.contains('sources') && !f.name.contains('javadoc')}
		}

		try {
			VirtualMachine vm = VirtualMachine.attach(pid);
			vm.loadAgent(agentJar.canonicalPath, "");
			vm.detach();
		}
		catch (Exception e) {
			throw new RuntimeException(e);
		}
	}

	@CompileStatic
	protected static File findJarFile(Class targetClass) {
		def absolutePath = targetClass.getResource('/' + targetClass.name.replace(".", "/") + ".class").getPath()
		final jarPath = absolutePath.substring("file:".length(), absolutePath.lastIndexOf("!"))
		new File(jarPath)
	}
}
