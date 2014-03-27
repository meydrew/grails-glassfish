grails.project.work.dir = 'target'

grails.project.dependency.resolution = {
	inherits('global') {
		excludes 'hibernate', 'tomcat', 'grails-plugin-log4j', 'slf4j-log4j12'
	}
	log 'warn'

	repositories {
		grailsPlugins()
		grailsHome()
		mavenLocal()
		grailsCentral()
		mavenCentral()
	}

	dependencies {
		compile 'org.slf4j:slf4j-api:1.6.6'
		compile 'org.slf4j:slf4j-log4j12:1.6.6'
		compile 'org.glassfish.main.extras:glassfish-embedded-all:4.0'
	}

	plugins {
		build ':release:3.0.1', ':rest-client-builder:2.0.1', {
			export = false
		}
	}
}
