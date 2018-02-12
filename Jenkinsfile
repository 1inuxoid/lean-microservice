#!/usr/bin/env groovy

pipeline {
    agent {
        label 'centos7&&linux-tst-zone'
    }

    options {
        timeout(time: 60, unit: 'MINUTES')
        timestamps()
        buildDiscarder(logRotator(artifactDaysToKeepStr: '', artifactNumToKeepStr: '10', daysToKeepStr: '', numToKeepStr: '100'))
        gitLabConnection('gitlab.ti8m.ch')
        gitlabCommitStatus(name: 'jenkins')
    }

    triggers {
        gitlab(
                triggerOnPush: false,
                triggerOnMergeRequest: false,
                triggerOnNoteRequest: false,
                noteRegex: "Jenkins please retry a build",
                skipWorkInProgressMergeRequest: true,
                ciSkip: true,
                setBuildDescription: true,
                addNoteOnMergeRequest: false,
                addCiMessage: false,
                addVoteOnMergeRequest: false,
                acceptMergeRequestOnSuccess: false,
                branchFilterType: "All",
        )
    }

    tools {
        gradle 'Gradle 4.2.x'
        jdk 'jdk-8_latest'
        nodejs 'NodeJS_latest'
    }

    // put here environment variables use in following steps
    // environment {}

    stages {
        stage('Preparation') {
            steps {
                script {
                    def gitCommit = sh(returnStdout: true, script: 'git rev-parse HEAD').trim()
                    env.GIT_COMMIT = gitCommit
                    env.GIT_BRANCH = env.BRANCH_NAME

                    if (env.GIT_BRANCH == 'master') {
                        env.DEPLOY_PROPS = "org.jacoco:jacoco-maven-plugin:prepare-agent deploy -Dci-deploy=true -Dgit-push=true"
                    } else {
                        env.DEPLOY_PROPS = "package"
                    }
                }

                sh 'env | sort'
            }
        }

        stage('Clean') {
            steps {
                ansiColor('xterm') {
                    sh 'mvn -B clean'
                }
            }
        }



        stage('Build and Test') {
            steps {
                ansiColor('xterm') {
                    sh 'gradle build'
                }
                junit allowEmptyResults: true, testResults: '*/target/surefire-reports/*.xml'
            }
        }



        stage('Publish Documentation') {
            steps {
                ansiColor('xterm') {
                    sshagent(['cf9fce6a-cbc2-4ab0-9816-31b17421cdfd']) {
                        script {
                            if (env.GIT_BRANCH == 'master') {
                                def documentationServer = "admin@10.10.36.79"
                                def pom = readMavenPom file: 'pom.xml'
                                // publishes the documentation under latest and the major version following the format
                                // 1.x, 2.x, ...
                                def docuVersion = "${pom.version.split('\\.')[0]}.x"

                                // build documentation
                                sh 'mvn package -pl documentation -Pasciidoc'

                                // create documentation folders
                                sh "ssh ${documentationServer} -o StrictHostKeyChecking=no 'mkdir -p /opt/docker/volumes/cdk-docu/modules/appointment/latest'"
                                sh "ssh ${documentationServer} -o StrictHostKeyChecking=no 'mkdir -p /opt/docker/volumes/cdk-docu/modules/appointment/${docuVersion}'"

                                // transfer documentation documents
                                sh "scp -o StrictHostKeyChecking=no -r documentation/target/generated-docs/docs/* ${documentationServer}:/opt/docker/volumes/cdk-docu/modules/appointment/latest"
                                sh "ssh ${documentationServer} -o StrictHostKeyChecking=no 'cp -r /opt/docker/volumes/cdk-docu/modules/appointment/latest/* /opt/docker/volumes/cdk-docu/modules/appointment/${docuVersion}'"
                            }
                        }
                    }
                }
            }
        }

    }

    post {
        failure {
            updateGitlabCommitStatus name: 'jenkins', state: 'failed'
            emailext(
                    body: '${DEFAULT_CONTENT}',
                    attachLog: true,
                    compressLog: true,
                    recipientProviders: [
                            [$class: 'DevelopersRecipientProvider'],
                            [$class: 'UpstreamComitterRecipientProvider'],
                            [$class: 'FailingTestSuspectsRecipientProvider'],
                            [$class: 'FirstFailingBuildSuspectsRecipientProvider'],
                            [$class: 'RequesterRecipientProvider'],
                            [$class: 'CulpritsRecipientProvider']
                    ],
                    subject: "FAILED: Continuous Build -> ${env.JOB_BASE_NAME}"
            )
        }

        unstable {
            updateGitlabCommitStatus name: 'jenkins', state: 'failed'
            emailext(
                    body: '${DEFAULT_CONTENT}',
                    attachLog: false,
                    recipientProviders: [
                            [$class: 'DevelopersRecipientProvider'],
                            [$class: 'UpstreamComitterRecipientProvider'],
                            [$class: 'FailingTestSuspectsRecipientProvider'],
                            [$class: 'FirstFailingBuildSuspectsRecipientProvider'],
                            [$class: 'RequesterRecipientProvider'],
                            [$class: 'CulpritsRecipientProvider']
                    ],
                    subject: "UNSTABLE: Continuous Build -> ${env.JOB_BASE_NAME}"
            )
        }

        success {
            updateGitlabCommitStatus name: 'jenkins', state: 'success'
            script {
                if(!hudson.model.Result.SUCCESS.equals(currentBuild.getPreviousBuild()?.getResult())) {
                    emailext(
                            body: '${DEFAULT_CONTENT}',
                            attachLog: false,
                            recipientProviders: [
                                    [$class: 'DevelopersRecipientProvider'],
                                    [$class: 'UpstreamComitterRecipientProvider'],
                                    [$class: 'FailingTestSuspectsRecipientProvider'],
                                    [$class: 'FirstFailingBuildSuspectsRecipientProvider'],
                                    [$class: 'RequesterRecipientProvider'],
                                    [$class: 'CulpritsRecipientProvider']
                            ],
                            subject: "SUCCESS: Continuous Build fixed -> ${env.JOB_BASE_NAME}"
                    )
                }
            }
        }
    }
}
