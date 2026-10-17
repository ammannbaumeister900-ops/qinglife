# M2 参营者资金契约准备

正式来源为本目录已版本化的详细契约、SQL/JSON草案和验证脚本，继承冻结基线1.4.22-uat.1017 / d137423。只整理契约，不实现正式资金服务、API或迁移；participant写入关闭。

- [详细实施契约](M2-详细实施契约.md)、[DDL草案](M2-participant-draft.sql)、[JSON契约](M2-contracts.json)、[历史字段核对](M2-verified-columns.tsv)。TSV为历史草案证据，不能当作当前生产schema。
- 在仓库根目录执行 node docs/product/m2/test-m2-contracts.cjs；脚本也可通过绝对路径从其他工作目录执行。Ajv复用后台已锁定依赖，不新增依赖。
- 隔离SQL机制检查：node docs/product/m2/test-m2-isolated.cjs。必须显式设置 QINGLIFE_M2_MYSQL_PORT 和 QINGLIFE_M2_SOURCE_SCHEMA；仅127.0.0.1。源库必须是本轮新建的 qinglife_it_* 隔离库，在sys_config中设置 qinglife.m2.schema_source=<源库名>:loopback-only。脚本只复制其空表结构，再创建独立随机测试库，不读取源业务数据。严禁给已有UAT/生产库设置该标记。
- MySQL二进制默认复用 .tools/mysql-8.4.0-winx64/bin，也可显式设置 QINGLIFE_M2_MYSQL_BIN。Windows原生MySQL若无法处理中文二进制路径，使用已有ASCII工具路径和ASCII临时数据目录。只支持新建无密码root的本机临时实例，不接入远端或现有验收库。
- 输出默认写到忽略目录 .tools/m2-contract-validation/，可通过 QINGLIFE_M2_EVIDENCE_DIR 指定本机证据目录。测试不会改写已版本化JSON或TSV。运行后只关闭本轮创建的MySQL进程，保留证据，不自动删除数据库。

历史原件和16项SQL/28项契约记录保留在项目上级的 历史归档/2026-10-15-M1内容阅读/M12-M2准备/；阶段0恢复映射位于 历史归档/2026-10-17-UAT版本收敛/phase0-recovery-20261018/manifest.json。旧只读设计不覆盖详细契约已冻结规则。

后续阶段1从兼容读取、旧入口模式防护开始；本目录SQL不在server/migrations，也不会随应用执行。退款/代用角色授予、0元应收例外及真实资金上线仍按契约边界单独处理。
