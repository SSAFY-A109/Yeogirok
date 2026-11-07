// 여기록 백엔드 CI/CD 파이프라인 (가이드 10단계)
//
// backend/dev 에 push 되면 GitHub 웹훅이 Jenkins 를 깨운다.
// 체크아웃 → 테스트 → 배포(서버에 SSH 로 들어가 deploy-backend.sh 실행) 순서로 돌고, 결과를 Mattermost 로 알린다.
//
// Jenkins job "yeogirok-backend" 가 infra/dev 브랜치의 이 파일을 읽어 실행한다.
// 그래서 파이프라인을 고칠 때는 백엔드 PR 없이 infra/dev 로 PR 하면 된다.
//
// Jenkins Credentials 에 미리 넣어 둘 값 (가이드 10-4, 값은 저장소에 쓰지 않는다):
//   server-ssh            SSH Username with private key   서버(ubuntu) 배포용 키
//   mattermost-webhook    Secret text                     Mattermost Incoming Webhook 주소
//   github-webhook-token  Secret text                     GitHub 웹훅 주소 끝에 붙이는 토큰

pipeline {
    // Jenkins 자신(컨테이너)에서 실행한다. 일을 나눠 맡길 다른 컴퓨터(에이전트)는 쓰지 않는다.
    agent any

    options {
        // job 은 이 파일을 읽으려고 infra/dev 를 가리키지만, 빌드할 코드는 backend/dev 다.
        // Jenkins 가 infra/dev 를 통째로 받는 기본 동작을 끄고 '체크아웃' 단계에서 backend/dev 만 받는다.
        skipDefaultCheckout()
        disableConcurrentBuilds()                       // 앞 빌드가 끝날 때까지 다음 빌드는 기다린다
        timeout(time: 30, unit: 'MINUTES')              // 멈춘 빌드는 30분 뒤 끝낸다
        buildDiscarder(logRotator(numToKeepStr: '30'))  // 빌드 기록은 최근 30개만 남긴다
        timestamps()                                    // 로그 줄마다 시각을 붙인다
    }

    triggers {
        // GitHub 웹훅이 보낸 내용(JSON)에서 브랜치 이름(ref)을 꺼내 backend/dev 일 때만 실행한다.
        // 다른 브랜치에 push 해도 웹훅은 오지만 여기서 걸러진다.
        // 이 설정은 job 을 한 번 실행해야 Jenkins 에 등록된다 (가이드 10-5).
        GenericTrigger(
            genericVariables: [[key: 'ref', value: '$.ref']],
            tokenCredentialId: 'github-webhook-token',
            regexpFilterText: '$ref',
            regexpFilterExpression: '^refs/heads/backend/dev$',
            causeString: 'backend/dev 에 push',
            printContributedVariables: false,
            printPostContent: false
        )
    }

    environment {
        REPO_URL = 'https://github.com/SSAFY-A109/Yeogirok.git'  // 공개 저장소라 로그인 없이 받는다
        BRANCH   = 'backend/dev'
        SERVER   = 'host.docker.internal'                       // Jenkins 컨테이너에서 본 서버 주소
    }

    stages {
        stage('체크아웃') {
            steps {
                git url: env.REPO_URL, branch: env.BRANCH
                script {
                    env.COMMIT = sh(script: 'git rev-parse HEAD', returnStdout: true).trim()
                }
                echo "커밋 ${env.COMMIT.substring(0, 7)} 을 테스트하고 배포합니다"
            }
        }

        stage('테스트') {
            steps {
                dir('backend') {
                    sh 'chmod +x gradlew && ./gradlew --no-daemon test'
                }
            }
            post {
                // 테스트가 깨져도 결과 표는 남긴다. 빌드 화면의 "Test Result" 에서 어떤 테스트가 실패했는지 본다.
                always {
                    junit allowEmptyResults: true, testResults: 'backend/build/test-results/test/*.xml'
                }
            }
        }

        stage('배포') {
            steps {
                // 서버에 SSH 로 들어가, 사람이 손으로 치던 배포 스크립트를 대신 실행한다.
                // 방금 테스트한 커밋을 DEPLOY_SHA 로 넘겨, 그사이 새 커밋이 들어와도 테스트하지 않은 코드가 나가지 않게 한다.
                withCredentials([sshUserPrivateKey(credentialsId: 'server-ssh', keyFileVariable: 'SSH_KEY', usernameVariable: 'SSH_USER')]) {
                    sh '''
                        ssh -i "$SSH_KEY" -o StrictHostKeyChecking=accept-new "$SSH_USER@$SERVER" \
                          "DEPLOY_SHA=$COMMIT bash ~/yeogirok/infra-src/infra/scripts/deploy-backend.sh"
                    '''
                }
            }
        }
    }

    post {
        success {
            script { notifyMattermost('✅ 백엔드 배포 성공') }
        }
        failure {
            script { notifyMattermost('❌ 백엔드 배포 실패') }
        }
    }
}

// Mattermost 채널에 한 줄 알림을 보낸다. 알림이 실패해도 빌드 결과는 바꾸지 않는다.
// 메시지에 큰따옴표(")를 넣지 않는다. JSON 을 손으로 만들기 때문에 깨진다.
def notifyMattermost(String title) {
    def commit = env.COMMIT ? env.COMMIT.substring(0, 7) : '-'
    def text = "${title} · 커밋 ${commit} · [빌드 #${env.BUILD_NUMBER} 로그](${env.BUILD_URL}console)"
    writeFile file: 'mattermost.json', text: """{"text": "${text}"}"""
    try {
        withCredentials([string(credentialsId: 'mattermost-webhook', variable: 'MM_URL')]) {
            sh 'curl -sS -o /dev/null -X POST -H "Content-Type: application/json" --data @mattermost.json "$MM_URL" || true'
        }
    } catch (err) {
        echo "Mattermost 알림을 보내지 못했습니다: ${err}"
    }
}
