# M2 参营者资金契约准备

正式来源为本目录已版本化契约及现有代码，继承冻结基线1.4.22-uat.1017 / d137423。[阶段1现金闭环](phase1.md)及[阶段2逐人退款与取消](phase2.md)已在本地实现必要Service/API及增量迁移，participant写入仍默认关闭；卡次仍是草案，未部署或应用到UAT/生产。

- [详细实施契约](M2-详细实施契约.md)、[DDL草案](M2-participant-draft.sql)、[JSON契约](M2-contracts.json)、[历史字段核对](M2-verified-columns.tsv)。TSV为历史草案证据，不能当作当前生产schema。
- 在仓库根目录执行 node docs/product/m2/test-m2-contracts.cjs；脚本也可通过绝对路径从其他工作目录执行。Ajv复用后台已锁定依赖，不新增依赖。
- 原完整草案机制检查：node docs/product/m2/test-m2-isolated.cjs，仅适用于V1_4_21作为前置结构的新建隔离源库；已含阶段1迁移的库不可再次执行完整草案。必须显式设置 QINGLIFE_M2_MYSQL_PORT 和 QINGLIFE_M2_SOURCE_SCHEMA；仅127.0.0.1。源库须标记 qinglife.m2.schema_source=<源库名>:loopback-only。正式阶段1验证使用phase1.md列出的Java真实服务/HTTP集成，不以草案SQL模拟代替。
- MySQL二进制默认复用 .tools/mysql-8.4.0-winx64/bin，也可显式设置 QINGLIFE_M2_MYSQL_BIN。Windows原生MySQL若无法处理中文二进制路径，使用已有ASCII工具路径和ASCII临时数据目录。只支持新建无密码root的本机临时实例，不接入远端或现有验收库。
- 输出默认写到忽略目录 .tools/m2-contract-validation/，可通过 QINGLIFE_M2_EVIDENCE_DIR 指定本机证据目录。测试不会改写已版本化JSON或TSV。运行后只关闭本轮创建的MySQL进程，保留证据，不自动删除数据库。

历史原件和16项SQL/28项契约记录保留在项目上级的 历史归档/2026-10-15-M1内容阅读/M12-M2准备/；阶段0恢复映射位于 历史归档/2026-10-17-UAT版本收敛/phase0-recovery-20261018/manifest.json。旧只读设计不覆盖详细契约已冻结规则。

本目录完整SQL仍是含卡次的完整草案，不随应用执行；阶段1/2分别将所需增量追加到server/migrations。退款/取消/代用角色授予、0元应收例外及真实资金上线仍按契约边界单独处理。
