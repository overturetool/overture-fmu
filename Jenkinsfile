
def userInputAcceptRelease = false

node{
  // Only keep one build
	properties([[$class: 'BuildDiscarderProperty', strategy: [$class: 'LogRotator', numToKeepStr: '5']]])
    
	// Mark the code checkout 'stage'....
	stage ('Checkout')
	{
		checkout scm
		sh 'git submodule update --init --remote'
	}
}


node {
  try
  {


		if (env.BRANCH_NAME == 'release') {

			try {
				timeout(time: 15, unit: 'MINUTES') { // change to a convenient timeout for you
					userInputAcceptRelease = input(message: 'Do want to run the release script?', ok: 'Yes', 
                        parameters: [booleanParam(defaultValue: false, 
                        description: 'If you want to run the release script and perform a release, just push the button',name: 'Yes?')])
				}
			} catch(err) { // timeout reached or input false
				//			def user = err.getCauses()[0].getUser()
				//if('SYSTEM' == user.toString()) { // SYSTEM means timeout.
				//	didTimeout = true
				//} else {
				//	userInput = false
				//echo "Aborted by: [${user}]"
				//	}
				userInputAcceptRelease = true
			}

			if(userInputAcceptRelease)
													{
														echo "Release accepted"
														sh "echo build..."
														sh "wget -q https://raw.githubusercontent.com/overturetool/overture-release-scripts/master/perform-release.sh -O perform-release.sh"
														sh "chmod +x perform-release.sh"
														sh "wget -q https://raw.githubusercontent.com/overturetool/overture-release-scripts/master/git-set-private-key.sh -O git-set-private-key.sh"
														sh "chmod +x git-set-private-key.sh"
														sh "git checkout development"
														sh "batchmode=release ./perform-release.sh ${env.MVN_SETTINGS_PATH}"

												
														sh "echo Detecting current version"

														version = sh "mvn -f target/checkout -q -s ${env.MVN_SETTINGS_PATH} -N org.apache.maven.plugins:maven-help-plugin:2.1.1:evaluate -Dexpression=project.version -DDmaven.repo.local=.repository/"

														version = sh (script: "mvn -f target/checkout -s ${env.MVN_SETTINGS_PATH} -N org.apache.maven.plugins:maven-help-plugin:2.1.1:evaluate -Dexpression=project.version -DDmaven.repo.local=.repository/ | grep -v '\\[' | grep -v -e '^\$'" , returnStdout:true).trim()

														sh "echo Version K${version}K"

														sh "cp target/checkout/core/fmu-import-export/target/fmu-import-export-${version}-jar-with-dependencies.jar target/checkout/ide/repository/target/repository/fmu-import-export.jar"

														sh '''#/bin/bash
cd target/checkout/ide/repository/target/repository
														zip -r ../p2.zip .
														mv ../p2.zip .
'''

											
													}
		}else
														{

															stage ('Clean'){
																withMaven(mavenLocalRepo: '.repository', mavenSettingsFilePath: "${env.MVN_SETTINGS_PATH}") {

																	// Run the maven build
																	sh "mvn clean install -U -PWith-IDE -Pcodesigning"
																}}

															stage ('Build'){
																withMaven(mavenLocalRepo: '.repository', mavenSettingsFilePath: "${env.MVN_SETTINGS_PATH}") {

																	// Run the maven build
																	sh "mvn install -Pall-platforms -PWith-IDE -Pcodesigning"
																	step([$class: 'ArtifactArchiver', artifacts: '**/target/*.jar', fingerprint: true])
																	step([$class: 'JUnitResultArchiver', testResults: '**/target/surefire-reports/TEST-*.xml'])
																	step([$class: 'JacocoPublisher'])
																	step([$class: 'TasksPublisher', canComputeNew: false, defaultEncoding: '', excludePattern: '', healthy: '', high: 'FIXME', ignoreCase: true, low: '', normal: 'TODO', pattern: '', unHealthy: ''])
																}}

															stage ('Integration test'){
				
																sh "cd testing && ./integration-test.sh"
				
															}

															stage ('FMI Compliance Test'){

																sh "cd testing && ./validate.sh"

															}

															stage ('Copy CLI to repo'){

																sh "echo Detecting current version"

																version = sh "mvn -q -s ${env.MVN_SETTINGS_PATH} -N org.apache.maven.plugins:maven-help-plugin:2.1.1:evaluate -Dexpression=project.version -DDmaven.repo.local=.repository/"

																version = sh (script: "mvn -s ${env.MVN_SETTINGS_PATH} -N org.apache.maven.plugins:maven-help-plugin:2.1.1:evaluate -Dexpression=project.version -DDmaven.repo.local=.repository/ | grep -v '\\[' | grep -v -e '^\$'" , returnStdout:true).trim()

																sh "echo Version K${version}K"

																sh "cp core/fmu-import-export/target/fmu-import-export-${version}-jar-with-dependencies.jar ide/repository/target/repository/fmu-import-export.jar"

															}

															stage ('Publish Artifactory'){

																if (env.BRANCH_NAME == 'development') {

																	def server = Artifactory.server "-844406945@1404457436085"
																	def buildInfo = Artifactory.newBuildInfo()
																	buildInfo.env.capture = true
				
																	def rtMaven = Artifactory.newMavenBuild()
																	rtMaven.tool = "Maven 3.1.1" // Tool name from Jenkins configuration
																	rtMaven.opts = "-Xmx1024m -XX:MaxPermSize=256M"
																	rtMaven.deployer releaseRepo:'overture-fmu', snapshotRepo:'overture-fmu', server: server
				
																	rtMaven.run pom: 'pom.xml', goals: 'install', buildInfo: buildInfo

																	//get rid of old snapshots only keep then for a short amount of time
																	buildInfo.retention maxBuilds: 5, maxDays: 7, deleteBuildArtifacts: true
		
																	// Publish build info.
																	server.publishBuildInfo buildInfo
																}
															}

														}

														stage ('Deploy'){
															def deployBranchName = env.BRANCH_NAME
															if (env.BRANCH_NAME == 'development' || env.BRANCH_NAME == 'release') {
																if(env.BRANCH_NAME == 'release')
																			{
																				deployBranchName = "master"
																			}

																			sh "echo branch is now ${env.BRANCH_NAME}"
			
																			DEST = sh script: "echo /home/jenkins/web/into-cps/vdm-tool-wrapper/${deployBranchName}/Build-${BUILD_NUMBER}_`date +%Y-%m-%d_%H-%M`", returnStdout:true
																			REMOTE = "jenkins@overture.au.dk"

																			sh "echo The remote dir will be: ${DEST}"
																			sh "ssh ${REMOTE} mkdir -p ${DEST}"
																			sh "scp -r ide/repository/target/repository/* ${REMOTE}:${DEST}"
																			sh "ssh ${REMOTE} /home/jenkins/update-latest.sh web/into-cps/vdm-tool-wrapper/${deployBranchName}"
															}
														}

 
	} catch (any) {
		currentBuild.result = 'FAILURE'
		throw any //rethrow exception to prevent the build from proceeding
	} finally {
  
		stage('Reporting'){

			// Notify on build failure using the Email-ext plugin
			emailext(body: '${DEFAULT_CONTENT}', mimeType: 'text/html',
							 replyTo: '$DEFAULT_REPLYTO', subject: '${DEFAULT_SUBJECT}',
							 to: emailextrecipients([[$class: 'CulpritsRecipientProvider'],
																			 [$class: 'RequesterRecipientProvider']]))
		}}
}
