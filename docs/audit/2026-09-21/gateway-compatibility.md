# 测试网关与整合API切换检查

2026-09-21只读核对当前 /opt/dify/docker/nginx/conf.d/qinglife-test.locations：允许app/qinglife、life、system及基础登录路由，但未放行common上传/下载和profile/public。它代理到API根路径/；整合源码默认context-path=/admin。没有修改当前网关或重启API。

候选配置：server/operations/nginx/qinglife-test.locations.example。只允许具体common上传/导出/公共资源下载入口及profile/public，仍不放行profile其他目录、data/static、文档或管理诊断接口。认证由Java原过滤链执行，Nginx转发不赋予下载权限。

切换时需同时核对：

- 新API使用/admin上下文；若部署配置显式改成/，候选proxy_pass也必须与其一致。不能把候选直接用于旧API。
- QINGLIFE_PUBLIC_IMAGE_BASE_URL设置为https://qinglife.guijianju.cn/test-api/profile/（API追加public/与随机文件名）。仅公开媒体进入public目录；私有回访图片保留鉴权接口。
- Java请求上限、文件上限与Nginx上限一起核对；候选20m不能解释为50MB视频必定可传。超过配置上限应明确拒绝。
- 对已有公开媒体地址建立清单，保留历史文件，先验证迁移与链接兼容，不把整个文件根目录重新匿名映射。
- 先使用nginx -t，再在隔离应用检查登录、导出本人/他人、匿名私有拒绝、公开图片200和历史地址；保存证据及旧配置后再切换。

候选配置已在现有Nginx容器中以独立临时完整配置执行 nginx -t，syntax is ok / test is successful；临时文件已删除，未reload或部署。语法通过不是网关业务验收通过。
