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
        REPO_WEB = 'https://github.com/SSAFY-A109/Yeogirok'      // 알림의 커밋 링크
        BRANCH   = 'backend/dev'
        SERVER   = 'host.docker.internal'                       // Jenkins 컨테이너에서 본 서버 주소
    }

    stages {
        stage('체크아웃') {
            steps {
                git url: env.REPO_URL, branch: env.BRANCH
                script {
                    env.COMMIT = sh(script: 'git rev-parse HEAD', returnStdout: true).trim()
                    // 알림에 쓸 내용. backend/dev 의 커밋은 대부분 PR 머지 커밋이라,
                    // 머지 커밋이면 "#번호 PR 제목"과 PR 브랜치 작성자를, 아니면 커밋 제목과 작성자를 쓴다.
                    env.COMMIT_SUBJECT = sh(returnStdout: true, script: '''
                        s=$(git log -1 --format=%s)
                        case "$s" in
                          "Merge pull request #"*) echo "$(echo "$s" | cut -d' ' -f4) $(git log -1 --format=%b | sed -n '/./{p;q}')" ;;
                          *) echo "$s" ;;
                        esac
                    ''').trim()
                    env.COMMIT_AUTHOR = sh(returnStdout: true, script: '''
                        git log -1 --format=%an "$(git rev-parse -q --verify HEAD^2 || echo HEAD)"
                    ''').trim()
                }
                echo "커밋 ${env.COMMIT.substring(0, 7)} (${env.COMMIT_SUBJECT}) 을 테스트하고 배포합니다"
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
                // 테스트 수는 알림 카드에 넣는다.
                always {
                    script {
                        def t = junit allowEmptyResults: true, testResults: 'backend/build/test-results/test/*.xml'
                        if (t.totalCount > 0) {
                            env.TEST_SUMMARY = t.failCount > 0 ? "${t.totalCount}개 중 ${t.failCount}개 실패" : "${t.totalCount}개 통과"
                        }
                    }
                }
            }
        }

        stage('배포') {
            steps {
                // 서버에 SSH 로 들어가, 사람이 손으로 치던 배포 스크립트를 대신 실행한다.
                // 방금 테스트한 커밋을 DEPLOY_SHA 로 넘겨, 그사이 새 커밋이 들어와도 테스트하지 않은 코드가 나가지 않게 한다.
                withCredentials([sshUserPrivateKey(credentialsId: 'server-ssh', keyFileVariable: 'SSH_KEY', usernameVariable: 'SSH_USER')]) {
                    script {
                        // 배포 스크립트의 끝나는 코드로 어떤 실패인지 구분해 알림 문구를 고른다 (deploy-backend.sh 맨 위 설명).
                        def code = sh(returnStatus: true, script: '''
                            ssh -i "$SSH_KEY" -o StrictHostKeyChecking=accept-new "$SSH_USER@$SERVER" \
                              "DEPLOY_SHA=$COMMIT bash ~/yeogirok/infra-src/infra/scripts/deploy-backend.sh"
                        ''')
                        env.DEPLOY_CODE = "${code}"
                        if (code != 0) {
                            error("배포 스크립트가 실패했습니다 (끝나는 코드 ${code})")
                        }
                    }
                }
            }
        }
    }

    post {
        success {
            script { notifyMattermost('✅ 백엔드 배포 성공', '#2EB67D') }   // 초록
        }
        failure {
            script { notifyMattermost(failureTitle(), failureColor()) }
        }
    }
}

// 실패 알림 문구와 색. 배포 단계까지 가지 못했으면(테스트 실패 등) DEPLOY_CODE 가 없다.
def failureTitle() {
    switch (env.DEPLOY_CODE) {
        case '2': return '⛔ 백엔드 배포 중단: 서버에 필요한 값이 없음 (서버는 그대로)'
        case '3': return '⚠️ 백엔드 새 버전이 뜨지 않아 이전 버전으로 되돌림'
        default:  return '❌ 백엔드 배포 실패'
    }
}

def failureColor() {
    switch (env.DEPLOY_CODE) {
        case '2': return '#ECB22E'   // 노랑: 서버는 그대로
        case '3': return '#F2994A'   // 주황: 이전 버전으로 살아 있음
        default:  return '#E01E5A'   // 빨강
    }
}

// Mattermost 채널에 알림 카드를 보낸다(Incoming Webhook, 가이드 10-4 ②). 알림이 실패해도 빌드 결과는 바꾸지 않는다.
// 카드: 왼쪽 색 띠(결과), 제목(누르면 빌드 로그), 커밋·작성자·PR 제목·테스트 수·걸린 시간.
// 보내는 이름(Jenkins)과 아이콘은 Mattermost 서버가 웹훅의 이름·사진 바꾸기를 허용할 때만 적용된다.
// 허용하지 않으면 웹훅을 만든 사람 이름에 BOT 표시가 붙어 보이고, 카드 내용은 같다.
def notifyMattermost(String title, String color) {
    def sha = env.COMMIT ?: ''
    def fields = []
    if (sha) {
        fields.add(['short': true, title: '커밋', value: "[${sha.substring(0, 7)}](${env.REPO_WEB}/commit/${sha})"])
        fields.add(['short': true, title: '작성자', value: env.COMMIT_AUTHOR ?: '-'])
        fields.add(['short': false, title: '내용', value: env.COMMIT_SUBJECT ?: '-'])
    }
    if (env.TEST_SUMMARY) {
        fields.add(['short': true, title: '테스트', value: env.TEST_SUMMARY])
    }
    fields.add(['short': true, title: '걸린 시간', value: durationText(System.currentTimeMillis() - currentBuild.startTimeInMillis)])
    def payload = [
        username   : 'Jenkins',
        icon_url   : 'https://www.jenkins.io/images/logos/jenkins/jenkins.png',
        attachments: [[
            fallback  : "${title} · 커밋 ${sha ? sha.substring(0, 7) : '-'}",   // 휴대폰 푸시 알림에 보이는 한 줄
            color     : color,
            title     : "${title} · 빌드 #${env.BUILD_NUMBER}",
            title_link: "${env.BUILD_URL}console",
            fields    : fields,
        ]],
    ]
    // 커밋 제목에 따옴표 같은 기호가 있어도 깨지지 않게 JSON 은 직접 만들지 않고 변환한다.
    writeFile file: 'mattermost.json', text: groovy.json.JsonOutput.toJson(payload)
    try {
        withCredentials([string(credentialsId: 'mattermost-webhook', variable: 'MM_URL')]) {
            sh 'curl -sS -o /dev/null -X POST -H "Content-Type: application/json" --data @mattermost.json "$MM_URL" || true'
        }
    } catch (err) {
        echo "Mattermost 알림을 보내지 못했습니다: ${err}"
    }
}

// 밀리초를 "2분 13초"처럼 바꾼다.
def durationText(long ms) {
    long sec = (long) (ms / 1000)
    return sec >= 60 ? "${(long) (sec / 60)}분 ${sec % 60}초" : "${sec}초"
}
