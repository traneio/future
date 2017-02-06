#!/bin/bash
set -e # Any subsequent(*) commands which fail will cause the shell script to exit immediately

if [[ $TRAVIS_PULL_REQUEST == "false" ]]
then
	if [[ $TRAVIS_BRANCH == "master" && $(cat pom.xml) != *"SNAPSHOT"* ]]
	then
		mvn release
	elif [[ $TRAVIS_BRANCH == "master" ]]
	then
		mvn clean org.jacoco:jacoco-maven-plugin:prepare-agent package sonar:sonar #publish
	else
		mvn clean versions:set -DnewVersion=$TRAVIS_BRANCH-SNAPSHOT org.jacoco:jacoco-maven-plugin:prepare-agent package sonar:sonar #publish
	fi
else
	mvn clean org.jacoco:jacoco-maven-plugin:prepare-agent package sonar:sonar
fi