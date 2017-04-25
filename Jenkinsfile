node {
  try
  {

    // Only keep one build
    properties([[$class: 'BuildDiscarderProperty', strategy: [$class: 'LogRotator', numToKeepStr: '5']]])
    
    // Mark the code checkout 'stage'....
    stage ('Checkout')
		{
			checkout scm
			sh 'git submodule update --init'
		}

    stage ('Clean'){
      withMaven(mavenLocalRepo: '.repository', mavenSettingsFilePath: "${env.MVN_SETTINGS_PATH}") {

        // Run the maven build
				//	sh "mvn clean install -U -PWith-IDE -Pcodesigning"
      }}

    stage ('Build'){
      withMaven(mavenLocalRepo: '.repository', mavenSettingsFilePath: "${env.MVN_SETTINGS_PATH}") {

        // Run the maven build
				//	sh "mvn install -Pall-platforms -PWith-IDE"
				//	step([$class: 'ArtifactArchiver', artifacts: '**/target/*.jar', fingerprint: true])
        //step([$class: 'JUnitResultArchiver', testResults: '**/target/surefire-reports/TEST-*.xml'])
        //step([$class: 'JacocoPublisher'])
        //step([$class: 'TasksPublisher', canComputeNew: false, defaultEncoding: '', excludePattern: '', healthy: '', high: 'FIXME', ignoreCase: true, low: '', normal: 'TODO', pattern: '', unHealthy: ''])
      }}

		stage ('Copy CLI to repo'){

			sh "echo Detecting current version"

			version= DEST = sh script: "mvn -N org.apache.maven.plugins:maven-help-plugin:2.1.1:evaluate -Dexpression=project.version -DDmaven.repo.local=.repository/ | grep -v '\\\['" , returnStdout:true


			sh "cp core/fmu-import-export/target/fmu-import-export-${version}-jar-with-dependencies.jar ide/repository/target/repository/fmu-import-export.jar"

		}

		stage ('Publish Artifactory'){

			if (env.BRANCH_NAME == 'development') {
			
				def server = Artifactory.server "-844406945@1404457436085"
				def buildInfo = Artifactory.newBuildInfo()
				buildInfo.env.capture = true
				//buildInfo.env.filter.addExclude("org/destecs/ide/**")
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

		
		stage ('Deploy'){
			if (env.BRANCH_NAME == 'development') {

				sh "echo branch is now ${env.BRANCH_NAME}"
			
				DEST = sh script: "echo /home/jenkins/web/into-cps/vdm-tool-wrapper/${env.BRANCH_NAME}/Build-${BUILD_NUMBER}_`date +%Y-%m-%d_%H-%M`", returnStdout:true
				REMOTE = "jenkins@overture.au.dk"

				sh "echo The remote dir will be: ${DEST}"
				sh "ssh ${REMOTE} mkdir -p ${DEST}"
				sh "scp -r ide/repository/target/repository/* ${REMOTE}:${DEST}"
				sh "ssh ${REMOTE} /home/jenkins/update-latest.sh web/into-cps/vdm-tool-wrapper/${env.BRANCH_NAME}"
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
