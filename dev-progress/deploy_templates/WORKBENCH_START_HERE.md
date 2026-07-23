# Workbench 最简单操作说明

这份目录只给新建的 Ubuntu 24.04 服务器使用。

## 1. 上传

在阿里云实例页面点击“远程连接” -> “Workbench”，进入黑色终端后，用“上传文件”上传：

`bootstrap_server_ubuntu2404.sh`

## 2. 执行基础安装

```bash
cd /root
chmod +x bootstrap_server_ubuntu2404.sh
./bootstrap_server_ubuntu2404.sh
```

脚本会安装 Java、MariaDB、Redis、Nginx、Certbot、Fail2ban，创建应用目录和 2G swap，并打开 22/80/443 防火墙端口。

## 3. 暂时不要做的事

- 不要把数据库端口 3306、Redis 端口 6379 或 Java 端口 8080 开到公网。
- 不要把真实 API key、数据库密码或 JWT secret 写进 Git。
- 不要在域名还没有指向这台服务器前申请 HTTPS 证书。
- 不要在没有备份的情况下导入真实业务数据。
