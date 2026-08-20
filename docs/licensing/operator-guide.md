# XKP5.0 离线授权运营手册

本文供许可证签发人员使用。签发工具位于独立仓库 `xkp-license-tool`，不属于平台
源码。整个流程可在不联网的 Windows 电脑上完成。

## 安全边界

- 私钥只保存在签发电脑的离线受控目录，并设置 Windows ACL。
- 私钥不得发给客户、复制到管理服务器、放进发布包或提交到 Git。
- 管理服务器只配置 X.509 Base64 公钥。
- `.xkpreq` 是公开的平台请求文件；`.xkplic` 是签名结果，不包含私钥。
- 签发前核对客户名称、环境、平台版本、有效期和处理服务器数量。

## 首次生成密钥

在 `xkp-license-tool` 仓库构建 Windows 程序：

```powershell
docker run --rm -v "${PWD}:/src" -w /src -e GOOS=windows -e GOARCH=amd64 `
  golang:1.22 go build -trimpath -o dist/xkp-license-tool.exe ./cmd/xkp-license
.\dist\xkp-license-tool.exe keygen `
  --private keys\production-private.key `
  --public keys\production-public.key
icacls keys\production-private.key /inheritance:r /grant:r "$env:USERNAME:(R,W)"
```

离线备份私钥。将公钥文件中的一行内容配置到管理服务器 `.env`：

```text
XKP_LICENSE_PUBLIC_KEYS=production-2026-01=Base64公钥内容
```

`production-2026-01` 是密钥标识，签发时必须完全一致。

## 检查并签发

客户发来 `.xkpreq` 后先查看非敏感摘要：

```powershell
.\dist\xkp-license-tool.exe inspect --request requests\customer.xkpreq
```

确认 `environment` 为目标环境、`platformVersion` 主版本为 5，再签发：

```powershell
.\dist\xkp-license-tool.exe issue `
  --request requests\customer.xkpreq `
  --private keys\production-private.key `
  --output issued\customer-2026.xkplic `
  --key-id production-2026-01 `
  --organization "客户单位名称" `
  --not-before 2026-08-18T00:00:00Z `
  --expires-at 2027-08-18T00:00:00Z `
  --max-processing-servers 4
```

工具默认拒绝覆盖已有密钥和许可证。将生成的 `.xkplic` 发给客户管理员。

## 续期与密钥轮换

续期必须让管理员重新下载平台信息，再用新的请求签发，不能修改旧许可证。导入新
许可证成功后，平台才会停用旧许可证；无效文件不会影响当前有效授权。

轮换密钥时，先把新旧两个公钥同时部署到平台：

```text
XKP_LICENSE_PUBLIC_KEYS=production-2026-01=旧公钥,production-2027-01=新公钥
```

重启并确认新公钥标识可见后，才用新私钥签发。所有仍在有效期内的旧许可证都完成
迁移后，才能删除旧公钥。私钥泄露时立即停止使用对应 `key-id`，部署新公钥并为每个
平台重新签发。
