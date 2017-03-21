stage 'Commit'
node {
  git 'https://github.com/harmpauw/cd-java-app.git'
  dir ('app') {
    sh 'mvn clean cobertura:cobertura sonar:sonar package'

    dir('target') {stash name: 'war', includes: 'petclinic.war'}
    step([$class: 'JUnitResultArchiver', testResults: 'target/surefire-reports/TEST-*.xml'])
  }
  stash name: 'test', includes: 'test/**'
  stash name: 'config', includes: 'config/**'
  stash name: 'db', includes: 'db/**'
}
//test

stage 'QA'
deploy 'test'
node {
  unstash 'test'
  wrap([$class: 'Xvnc', takeScreenshot: true, useXauthority: true]) {
    sh 'mvn -f test clean test -Durl=http://172.17.0.4:8080/petclinic'
    step([$class: 'JUnitResultArchiver', testResults: 'test/target/surefire-reports/TEST-*.xml'])
  }
}


def deploy(environment) {
  node {
    unstash 'config'
    unstash 'db'
    // Database migration
    sh "/opt/flyway-3.2.1/flyway -configFile=config/$environment/flyway.conf migrate"

    unstash 'war'
    // WAR deployment
    sh 'curl -X PUT -T petclinic.war -u tomcat:tomcat "http://172.17.0.4:8080/manager/text/deploy?path=/petclinic&update=true"'
  }
}
