#!/bin/bash
set -e # Any subsequent(*) commands which fail will cause the shell script to exit immediately

if [[ $TRAVIS_PULL_REQUEST == "false" ]]
then
	openssl aes-256-cbc -pass pass:$ENCRYPTION_PASSWORD -in $GPG_DIR/pubring.gpg.enc -out $GPG_DIR/pubring.gpg -d
	openssl aes-256-cbc -pass pass:$ENCRYPTION_PASSWORD -in $GPG_DIR/secring.gpg.enc -out $GPG_DIR/secring.gpg -d
	if [[ $TRAVIS_BRANCH == "master" && $(cat pom.xml) != *"SNAPSHOT"* ]]
	then
		mvn clean org.jacoco:jacoco-maven-plugin:prepare-agent package sonar:sonar deploy --settings build/settings.xml -DperformRelease=true
	elif [[ $TRAVIS_BRANCH == "master" ]]
	then
		mvn clean org.jacoco:jacoco-maven-plugin:prepare-agent package sonar:sonar deploy
	else
		mvn clean versions:set -DnewVersion=$TRAVIS_BRANCH-SNAPSHOT org.jacoco:jacoco-maven-plugin:prepare-agent package sonar:sonar deploy
	fi
else
	mvn clean org.jacoco:jacoco-maven-plugin:prepare-agent package sonar:sonar
fi