# 在线部署与升级手册

适用于平台 5.1.0 基线及后续修复版本。当前 5.1.0 标签保持冻结；后续发布应使用新的版本号和新的 Agent 包，不能覆盖已有标签或复用旧离线包。

## 1. 升级前检查

在维护窗口执行，先确认没有镜像上传、加载、删除、课堂切换或比赛切换任务：

```bash
cd /opt/xkp5-platform
docker compose ps
docker ps --format 'table {{.Names}}\t{{.Status}}\t{{.Image}}'
```

确认数据库、Registry staging、Compose 文件和当前运行镜像均可回退。升级期间不要并发对同一服务器执行镜像 tag/load/delete。

## 2. 数据库和平台备份

使用实际数据库容器名、数据库名和凭据替换占位符：

```bash
mkdir -p backups/$(date +%Y%m%d-%H%M%S)
docker exec <MYSQL_CONTAINER> mysqldump -u<DB_USER> -p'<DB_PASSWORD>' --single-transaction --routines --triggers <DB_NAME> > backups/<TIMESTAMP>/match.sql
docker inspect xkp5-stable-java > backups/<TIMESTAMP>/java.inspect.json
docker inspect xkp5-stable-vue > backups/<TIMESTAMP>/vue.inspect.json
docker image inspect <JAVA_IMAGE> <VUE_IMAGE> > backups/<TIMESTAMP>/images.inspect.json
```

核对 SQL 文件非空且末尾包含 dump 完成标记后再继续。不要把密码写入脚本、提交或报告。

## 3. 平台在线升级

先构建并检查目标镜像，再停止旧容器；旧容器和镜像保留到验收完成：

```bash
docker compose build java vue
docker compose up -d java vue
docker compose ps
curl -fsS http://127.0.0.1:19141/health
curl -fsS http://127.0.0.1:19140/
```

若健康检查或登录失败，立即停止新容器并启动备份容器，确认数据库未被回滚脚本误修改，再分析日志：

```bash
docker logs --tail 200 xkp5-stable-java
docker logs --tail 200 xkp5-stable-vue
```

## 4. Agent 升级

每次只升级一台，成功验收后再进行下一台。先在目标机保存旧二进制、systemd 单元和配置：

```bash
sudo cp /usr/local/bin/xkp-agent /usr/local/bin/xkp-agent.before-<VERSION>-<DATE>
sudo tar -C /etc -czf /root/xkp-agent-backup-<VERSION>-<DATE>.tgz xkp-agent
```

上传候选二进制后必须核对 SHA-256，再替换并重启：

```bash
sha256sum xkp-agent-linux-amd64
sudo install -m 0755 xkp-agent-linux-amd64 /usr/local/bin/xkp-agent
sudo systemctl restart xkp-agent
sudo systemctl is-active --quiet xkp-agent
sudo systemctl status --no-pager xkp-agent
```

平台确认 Agent 版本达到目标版本且心跳在线后，才能升级下一台。当前版本比较由后端统一决定；前端不能用字符串“不相等”判断可升级。

## 5. 镜像清单与删除规则

清单按完整镜像 ID 统计，多个标签是同一镜像的别名。删除一个镜像 ID 时会删除该 ID 的全部标签；若任意运行中或已停止容器引用该 ID，Agent 必须拒绝删除。删除后刷新清单并核对完整 ID 已不存在。逐标签删除不是原子操作，因此必须在没有外部 tag/load 写入的窗口执行；失败时报告部分结果，不重试强制删除。

## 6. 升级验收

至少完成以下检查：

1. 管理员登录、学生登录、课程列表和实训环境列表可读。
2. Agent 版本、在线状态、处理服务器资源和容器数量正常。
3. 创建/启动/停止一个测试环境，核对 Agent 回报、数据库状态和真实 Docker 容器三者一致。
4. 比赛模式、一键上课、实训模式各执行一次切换，确认先停后启、按钮状态和失败提示正确。
5. 镜像列表检查多标签合并展示；删除测试镜像前确认无容器引用，删除后确认所有标签消失。
6. 代码编辑器在学生浏览器中实际加载，验证 WebSocket、编辑和保存；仅 HTTP 状态码或服务端 curl 不算通过。

## 7. 回滚

回滚顺序为：停止新平台容器并恢复旧平台容器；必要时恢复数据库备份；逐台恢复 Agent 旧二进制并重启；最后重新检查心跳、环境和镜像清单。回滚完成前保留新容器日志和任务记录，不删除备份。

## 8. 当前已知边界

F02 内嵌工具和 T100 模型问题按要求暂不修改。未完成全新机器安装、长期压力和全功能浏览器回归时，不应宣称这些场景已验收。
