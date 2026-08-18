# Match V2 离线发布包

本发布包用于 Ubuntu x86_64/amd64 服务器。目标服务器必须已安装 Docker Engine 和 Docker Compose v2，不需要互联网、源码、Maven 或 Node.js。

## 首次安装

在发布包外创建服务器专用配置，避免以后替换发布目录时丢失密码：

```bash
cp .env.example /root/match-v2.env
vi /root/match-v2.env
sudo ./install.sh \
  --install-dir /opt/match-v2 \
  --env-file /root/match-v2.env
```

安装脚本发现已有容器、MySQL volume 或运行数据时会拒绝执行。

## 验证

```bash
sudo /opt/match-v2/verify.sh --install-dir /opt/match-v2
```

前端地址为 `http://目标服务器IP:19140`，具体端口以 `.env` 为准。

## 保留数据升级

将新发布包复制并解压到目标服务器，在新发布包目录执行：

```bash
sudo ./upgrade.sh --install-dir /opt/match-v2
```

升级脚本会先备份数据库，只重建 Java 和 Vue 容器。MySQL、FastDFS、赛卷及评分数据不会从发布快照覆盖。

## 停止并移除容器

```bash
sudo /opt/match-v2/uninstall.sh --install-dir /opt/match-v2
```

该命令保留 MySQL volume、运行数据和镜像。

## 从发布快照重置

此操作会覆盖目标服务器数据。必须从希望恢复的发布包目录执行，并按提示输入完整确认字符串：

```bash
sudo ./reset-from-snapshot.sh --install-dir /opt/match-v2
```

脚本会先将现有数据库和运行文件备份到 `/opt/match-v2/backups/`。
