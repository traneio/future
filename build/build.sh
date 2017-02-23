#!/bin/bash
set -e # Any subsequent(*) commands which fail will cause the shell script to exit immediately

if [[ $TRAVIS_PULL_REQUEST == "false" ]]
then
	openssl aes-256-cbc -pass pass:$ENCRYPTION_PASSWORD -in $BUILD_DIR/pubring.gpg.enc -out $BUILD_DIR/local.pubring.gpg -d
	openssl aes-256-cbc -pass pass:$ENCRYPTION_PASSWORD -in $BUILD_DIR/secring.gpg.enc -out $BUILD_DIR/local.secring.gpg -d
	openssl aes-256-cbc -pass pass:$ENCRYPTION_PASSWORD -in $BUILD_DIR/deploy_key.pem.enc -out $BUILD_DIR/local.deploy_key.pem -d
	if [ -e "release.properties" ] && [ $TRAVIS_BRANCH == "master" ]
	then
		echo "Performing a release..."
		eval "$(ssh-agent -s)"
		chmod 600 $BUILD_DIR/local.deploy_key.pem
		ssh-add $BUILD_DIR/local.deploy_key.pem
		git config --global user.name "TraneIO CI"
		git config --global user.email "ci@trane.io"
		git remote set-url origin git@github.com:traneio/future.git
		git fetch --unshallow
		git checkout master || git checkout -b master
		git reset --hard origin/master
		mvn clean release:perform --settings $BUILD_DIR/settings.xml
	elif [[ $TRAVIS_BRANCH == "master" ]]
	then
		echo "Publishing a snapshot..."
		mvn clean org.jacoco:jacoco-maven-plugin:prepare-agent package sonar:sonar deploy --settings $BUILD_DIR/settings.xml
	else
		echo "Publishing a branch snapshot..."
		mvn clean versions:set -DnewVersion=$TRAVIS_BRANCH-SNAPSHOT org.jacoco:jacoco-maven-plugin:prepare-agent package sonar:sonar deploy --settings $BUILD_DIR/settings.xml 
	fi
else
	echo "Running build..."
	mvn clean org.jacoco:jacoco-maven-plugin:prepare-agent package sonar:sonar
fi