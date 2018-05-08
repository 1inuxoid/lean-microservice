#!/usr/bin/env groovy
pipeline {
    agent {
        label 'linux-tst-zone'
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
                addVoteOnMergeRequest: true,
                acceptMergeRequestOnSuccess: false,
                branchFilterType: "All",
        )
    }

    // put here environment variables use in following steps
    // environment {}

    stages {
        stage('Build, test and deploy') {
            steps{
                ansiColor('xterm') {
                    script {
                        sh 'env | sort'

                        sh './gradlew build war'

                        if (env.BRANCH_NAME == 'master') {
                            sh './gradlew uploadArchives'
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
                if (!hudson.model.Result.SUCCESS.equals(currentBuild.getPreviousBuild()?.getResult())) {
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
