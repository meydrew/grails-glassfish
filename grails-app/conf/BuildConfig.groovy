grails.project.work.dir = 'target'

grails.project.dependency.resolution = {
	inherits('global') {
		excludes 'hibernate', 'tomcat', 'slf4j-log4j12', 'grails-plugin-log4j'
	}
	log 'warn'

	repositories {
		grailsCentral()
		mavenLocal()
		mavenCentral()
	}

	dependencies {
		compile 'org.slf4j:slf4j-api:1.6.6'
		compile 'org.slf4j:slf4j-log4j12:1.6.6'
		compile 'org.glassfish.main.extras:glassfish-embedded-all:4.0'
	}

	plugins {
		build ':release:2.2.1', ':rest-client-builder:1.0.3', {
			export = false
		}
	}
}
