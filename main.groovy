properties([
        pipelineTriggers([
                cron('H 1 * * 1')
        ])
])

def config
def msgurl = "https://qyapi.weixin.qq.com/cgi-bin/webhook/send?key={你的KEY}"

node {
    checkout scm

    stage('init') {
        withCredentials([string(credentialsId: 'etcd-endpoint', variable: 'ETCD_ENDPOINT')]) {

            def base64Key = sh(script: "echo -n /ops/cicd/certbot.json | base64 -w 0", returnStdout: true).trim()
            def jsonResponse = sh(script: "curl -s -X POST -d '{\"key\":\"${base64Key}\"}' ${ETCD_ENDPOINT}/v3/kv/range", returnStdout: true).trim()
            if (jsonResponse == null || jsonResponse.trim().isEmpty()) {
                error "error config info from etcd"
            }
            def etcdValue = readJSON text: jsonResponse
            if (!etcdValue.containsKey('kvs')) {
                error 'empty config info from etcd'
            }
            config = readJSON text: sh(script: "echo -n ${etcdValue.kvs[0].value} | base64 -d", returnStdout: true).trim()

            sh """
                if ! docker images | grep -q "ssl-certbot"; then
                    echo "build with expire images"
                    docker build -t ssl-certbot .
                fi
            """
            config.each{domain,item ->
                def arg = domain
                if(item.containsKey('ext_domain')){
                    item.ext_domain.each{eitem->
                        arg += " -d $eitem"
                    }
                }
                sh """
                    docker run --rm -p 40400:80 -v /data/kairui/jenkins/data/workspace/${env.JOB_NAME}:/srv/ -v /data/kairui/certbot/data:/etc/letsencrypt ssl-certbot /srv/hook/exec.sh ${arg}
                """
            }

            try {
                sh """
                    docker run --rm -p 40400:80 -v /data/kairui/jenkins/data/workspace/${env.JOB_NAME}:/srv/ -v /data/kairui/certbot/data:/etc/letsencrypt ssl-certbot /srv/hook/exec.sh
                """
            } catch (Exception e) {
                sh """
                    curl -s -H 'Content-Type: application/json' -X POST -d '{"msgtype": "text", "text": {"content": "！！！域名续期失败"}}' "$msgurl"
                """
            }

        }
    }
    stage('deploy') {
        sh(script: 'find dst -type d || true', returnStdout: true).trim().tokenize('\n').each{line ->
            domain = line.length()>4?line.substring(4):line
            conf = config.get(domain)
            if (conf != null && conf.containsKey('mode')) {
                println("run domain "+domain)
                if (conf.mode == 'single'){
                    withCredentials([usernamePassword(credentialsId: "$conf.server_secret", usernameVariable: 'SSH_USER', passwordVariable: 'SSH_PASS')]) {
                        try {
                            sh """
                                sshpass -p '${SSH_PASS}' ssh -p ${conf.server_port} ${SSH_USER}@${conf.server_host} <<EOF
                                mkdir -p /etc/ssl/${domain} ||true
                            """
                            sh """
                                sshpass -p '${SSH_PASS}' scp -o StrictHostKeyChecking=no -P ${conf.server_port} dst/${domain}/fullchain.pem ${SSH_USER}@${conf.server_host}:/etc/ssl/${domain}/
                                sshpass -p '${SSH_PASS}' scp -o StrictHostKeyChecking=no -P ${conf.server_port} dst/${domain}/privkey.pem ${SSH_USER}@${conf.server_host}:/etc/ssl/${domain}/
                            """
                            sh """
                                sshpass -p '${SSH_PASS}' ssh -p ${conf.server_port} ${SSH_USER}@${conf.server_host} <<EOF
                                nginx -t
                                nginx -s reload
                            """
                            sh """
                                curl -s -H 'Content-Type: application/json' -X POST -d '{"msgtype": "text", "text": {"content": "[STG]${domain} 续期部署成功"}}' "$msgurl"
                            """
                        } catch (Exception e) {
                            sh """
                                curl -s -H 'Content-Type: application/json' -X POST -d '{"msgtype": "text", "text": {"content": "！！！[STG]${domain} 续期部署失败"}}' "$msgurl"
                            """
                        }
                        sh "rm -rf dst/${domain} || true"
                    }
                }else if(conf.mode == 'cluster'){
                    if(
                            saveRemote(sh(script: "cat dst/${domain}/fullchain.pem | base64 -w 0", returnStdout: true),"/ops/sync/data/ssl/"+domain+"/"+domain+".pem") == "200120"
                            && saveRemote(sh(script: "cat dst/${domain}/privkey.pem | base64 -w 0", returnStdout: true),"/ops/sync/data/ssl/"+domain+"/"+domain+".key") == "200120"
                            && reloadGateway() == "200120"
                    ){
                        sh """
                            curl -s -H 'Content-Type: application/json' -X POST -d '{"msgtype": "text", "text": {"content": "【PRD】${domain} 续期部署成功"}}' "$msgurl"
                        """
                        sh "rm -rf dst/${domain} || true"
                    }else{
                        sh """
                                curl -s -H 'Content-Type: application/json' -X POST -d '{"msgtype": "text", "text": {"content": "！！！【PRD】${domain} 续期部署失败"}}' "$msgurl"
                        """
                    }
                }
            }
        }
    }
}

def saveRemote(content, path) {
    withCredentials([string(credentialsId: 'etcd-endpoint', variable: 'ETCD_ENDPOINT')]) {
        def base64Key = sh(script: "echo -n "+path+" | base64 -w 0", returnStdout: true).trim()
        def responseCode = sh(script: "curl -X POST -d '{\"key\":\"${base64Key}\",\"value\":\"${content}\"}' ${ETCD_ENDPOINT}/v3/kv/put -w \"%{http_code}%{size_download}\" -o /dev/null", returnStdout: true).trim()
        if (responseCode == null || responseCode.trim().isEmpty()) {
            error "error http for for etcd"
        }
        return responseCode
    }
}

def reloadGateway() {
    withCredentials([string(credentialsId: 'etcd-endpoint', variable: 'ETCD_ENDPOINT')]) {
        //readfirst
        def base64Key = sh(script: "echo -n /ops/gateway/vhost/default.conf | base64 -w 0", returnStdout: true).trim()
        def jsonResponse = sh(script: "curl -s -X POST -d '{\"key\":\"${base64Key}\"}' ${ETCD_ENDPOINT}/v3/kv/range", returnStdout: true).trim()
        if (jsonResponse == null || jsonResponse.trim().isEmpty()) {
            error "error config info from etcd"
        }
        def etcdValue = readJSON text: jsonResponse
        if (!etcdValue.containsKey('kvs')) {
            error 'empty config info from etcd'
        }

        //buildparams
        config = sh(script: "echo -n ${etcdValue.kvs[0].value} | base64 -d | sed \'s/hostname\\-[0-9]\\+/hostname\\-"+new Date().getTime()+"/\' | base64 -w 0", returnStdout: true).trim()

        //wirteafter
        def responseCode = sh(script: "curl -X POST -d '{\"key\":\"${base64Key}\",\"value\":\"${config}\"}' ${ETCD_ENDPOINT}/v3/kv/put -w \"%{http_code}%{size_download}\" -o /dev/null", returnStdout: true).trim()
        if (responseCode == null || responseCode.trim().isEmpty()) {
            error "error http for for etcd"
        }
        return responseCode
    }
}