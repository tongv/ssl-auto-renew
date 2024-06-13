### HTTPS\SSL证书脚本式续期
`目前各大云服务商的免费一年证书都下线，网上好多续期脚本要么是基于云服务的、要么就是项目复杂，自己写一个Jenkins脚本进行证书自动续期。`

#### 特性
- 自动续期
- 自动部署
- 无长驻服务、占用少
- 无云服务、更安全
- 支持报警

#### 依赖
- 依赖Jenkins
- 依赖Docker
- 依赖ETCD（可自行移除）


#### 配置

1、配置生产域名转发,一般http都用于https转发，这里稍加修改即可。
```shell
server {
    listen       80;
    server_name www.n40.cn;
    location /{ return 301 https://www.n40.cn$request_uri; }
    location /.well-known/acme-challenge/{ proxy_pass http://{Jenkins里面启动服务的地址}:40400; }
}
```

2、配置配置文件，目前支持要机模式和集群模式，单机使用scp交付，集群模式暂支持etcd。
```shell
    "www.n40.cn":{
        "mode": "single",
        "server_host": "psych.srv.n40.cn",
        "server_port": "22",
        "server_secret": "prd-sshpwd-psych",
        "ext_domain":["n40.cn","www2.n40.cn"]
    },
    
    "blog.n40.cn":{
        "mode": "cluster",
        "ext_domain":["blog2.n40.cn","blog3.n40.cn"]
    },
```

3、配置Jenkins任务，将main.groovy文件配置到Jenkins。