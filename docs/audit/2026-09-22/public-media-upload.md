# 本地媒体上传入口一致性修复

## 问题与原因

后台 Editor 使用 `/system/oss/localUpload`，EditorLocal 使用 `/common/upload`。前次附件加固只覆盖 common 入口，localUpload 仍写入 profile 根目录，没有实际内容验证，返回的 URL 也不符合当前只允许 profile/public 的资源映射。因此存在校验绕过和上传成功后图片不可见的问题。

## 修复

新增 PublicMediaUpload 统一验证、写入 profile/public 和图片基础地址拼接。两个控制器复用该实现，保留 localUpload 的既有 system:oss:upload 权限与防重复提交配置；不恢复私有根目录公开访问。

另修复普通 Editor 使用 BASE_API + fileName 拼出错误 URL 的问题，改用服务端 url；两个编辑器均在上传后失焦时安全插入到文末，避免 getSelection() 返回 null 报错。

## 验证

新增 PublicMediaUploadTest，直接调用两个真实控制器：合法 PNG 落盘、返回同一公共 URL 契约；伪装图片被拒绝且不新增文件。2026-09-22 09:09，Maven 单元回归共 32 项通过（0 失败/错误）。新增测试最初使用了错误的 AjaxResult getter，编译失败后改为 getData，再完整重跑通过。

该修复不代表所有依赖风险、云 OSS 上传和私有附件产品已完成验收。

前端行为测试：node scripts/test-admin-editor-upload.cjs 通过，覆盖两个编辑器的服务端 URL、失焦插入和失败响应。

后台 staging 构建通过：使用隔离目录中的 Axios 0.34.0 依赖及本轮两个编辑器代码；构建仍有既有体积警告。没有进行真实浏览器上传或部署验收。
