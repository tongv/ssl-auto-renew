FROM alpine
LABEL maintainer="tongv<44472@163.com>" app.name="cartbot"
RUN set -ex  \
    && sed -i 's/dl-cdn.alpinelinux.org/mirrors.ustc.edu.cn/g' /etc/apk/repositories \
    && apk add --no-cache certbot nginx \
    && rm -rf /var/cache/apk/* /tmp/* \
    && echo -e "\033[42;37m Build Completed :).\033[0m\n"