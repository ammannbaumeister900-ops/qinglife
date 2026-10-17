# 历史文章本机隔离导入工具

本轮交付仅支持本机隔离演练，没有新增 HTTP 导入接口，没有生产迁移能力。默认干运行；每批最多 5 篇。源码/原文/媒体的使用授权须由操作者核实，approvalRef 必须指向实际授权记录，不是工具自动判断的授权。

## 环境与命令

使用既有 JDK 8 和 Maven 构建 server/api 的 box-web reactor。先按现有 DatabaseMigration 机制，在**全新**本机测试库应用 server/bootstrap 和 server/migrations；本次增量为 V1_4_21__article_import.sql。该迁移不会在应用启动时自动执行。

工具只接受 127.0.0.1、数据库名 qinglife_it_article_*，并要求 sys_config 中 qinglife.article.import_isolation 的值为“实际库名:loopback-only”。此标识仅在已核实的新建本机环境中设置，不能用于给现有环境改名或绕过隔离要求。JDBC 配置仅从 QINGLIFE_ARTICLE_IMPORT_URL / USER / PASSWORD 环境变量读取；不要将密码写入参数、Git或报告。

Windows 入口：

    ./scripts/import-articles.ps1 -Manifest <受限本地manifest.json>
    ./scripts/import-articles.ps1 -Manifest <受限本地manifest.json> -Apply

Java 入口由已构建 JAR 的 PropertiesLauncher 直接调用 com.yicai.web.tools.ArticleImport，不启动 Web 应用。默认只读检查，--apply 才写入；批次遇到 REJECTED / CONFLICT / FAILED 返回失败退出码。使用全批数据库互斥锁，逐篇事务提交，失败不回滚之前已成功的文章；每篇结果必须核对。

## 输入契约

顶层：sourceSystem、batchId、readableInLocalTest（默认 false）、articles 数组。sourceSystem 应稳定区别 legacy-admin 与不同公众号，不能用“本轮批次”冒充来源。

每篇：sourceId（字符串，必须来自合法源）、sourceUrl（HTTPS）、title、introduction、content（HTML）、authorName（原署名）、publishedAt（原时间字符串）、timeBasis、cover、status（0/1）、orderNum、approvalRef、labels、mediaMap。

timeBasis 为 legacy_insert_time 或 published_at；旧站只有创建时间证据时使用前者，不能声称已取得首次发布时间。工具保留该时间到 essay.insert_time；不更换时区或以导入时间覆盖。sourceId 始终作为字符串处理，大数字不转 JS Number。

labels 每项：sourceId、name、status、level。完整保留含 & 的标签名，不从列表拼接字符串恢复关联。mediaMap 是“精确旧 URL → 已核验的新 URL”；不做全局 http 替换。不支持的 iframe/audio/embed 必须先报告和处理，不能静默丢弃。

图片/视频/封面必须为 HTTPS，或本机测试专用 http://127.0.0.1。路径可达、内容类型、完整媒体哈希、人物/声音授权及真机兼容须另行验证；工具不抓取媒体或推断授权，也不能把语法合法的 URL 视为可达。空封面被保留。

readableInLocalTest=true 只控制**本机隔离**阅读测试；source status=0 仍保持 0。默认导入停用，home_featured=0。不存在任何正式环境发布入口。

## 保存、幂等和兼容

- 数据写入 essay / label / essay_label，以及三张文章迁移专表；不调用 EssayServiceImpl.insertByBo，不读写 app_user_info 或 app_essay_user。
- essay.author_name 保存原署名；旧 author 数值与目标 SysUser 身份不能混用。小程序现有阅读 API 和后台列表优先显示该署名，普通文章保留原有回退。
- ql_article_source 以 (source_system,source_id) 唯一，目标 ID 独立生成，记录源链接、源哈希、目标哈希、媒体映射、原状态、批准引用和批次。
- ql_article_label_source 记录源标签与目标标签关系；匹配相同完整名称时只允许唯一、状态相同的既存目标标签，歧义或状态冲突拒绝。
- 同源相同内容且目标未改，返回 UNCHANGED。源变化返回 SOURCE_CHANGED，目标编辑/关联变化返回 TARGET_CHANGED，均不覆盖已有文章。标签源信息变化、目标标签改名/停用同样拒绝。
- 每篇事务包含标签、正文、关联及来源映射。失败回滚该篇，并在外部事务记录 ql_article_import_attempt 的结果代码，不记录原文、SQL错误细节或凭据。
- 原 HTML 经过已有 EssayHtmlSanitizer；原始输入应留在受限本地，不放 Git。源哈希用于追溯，不能从哈希还原全文。

## 清理与下一批

先按批次查询 ql_article_source、ql_article_import_attempt 和新建标签映射，形成精确 ID 清单。核对目标哈希与来源关系后才清理本批新增记录；不要删除共享标签或桃子已经编辑的文章。工具本轮没有自动删除入口，避免把重跑或回滚变成误删。

本轮测试实例可以正常停止并保留专属数据目录作证据；不清理其他库、工具或用户数据。正式迁移前仍须确认新旧运行时数据源是否共用同一实例/schema/文章表；共库时禁止复制，仅验共享读取。
