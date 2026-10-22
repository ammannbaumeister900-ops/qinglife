# M2 阶段1：逐人现金到账、分配与结算

本地实现于 dev/m2；沿用详细契约已冻结规则。participant 写入默认关闭；未部署、未在UAT/生产应用迁移。退款、取消报名、卡次代用和新后台UI不在本阶段。

## 数据与入口

新增增量为 server/migrations/V1_4_22__participant_receipts.sql，追加订单 settlement_mode、finance_revision 及3张表：ql_finance_operation、ql_receipt_allocation、ql_participant_settlement_funding。原收款仍复用 ql_transaction，原结算轮次仍复用 ql_settlement_log；不为多人复制到账、不回填历史分摊、不改已发布迁移。

开关 qinglife.finance.participant_write_enabled 初始为 false；缺失、异常或重复配置也关闭。仅独立隔离测试显式启用。开启后只有新提交订单（含后台新增单人订单）选择 participant，已有 legacy_batch 不转换。关闭写入后新模式只读与占位仍保留。后续部署和开关启用需另行授权，不能以回滚到旧二进制替代兼容读取。

| 后台API | 阶段1行为 |
| --- | --- |
| GET /life/registration/batch/{id}/finance | 模式、订单资金revision、人员应收/确认/占位、到账及分配历史、独立汇总；不返回私人收款图片或完整凭证 |
| POST /life/registration/{id}/offline-receipt | participant 必须 captureOnly=true、requestId、receivedConfirmed=true；登记已核实的一笔真实到账，返回receiptId及余款，不自动确认报名 |
| POST /life/registration/batch/{id}/receipt-allocations | 一笔到账向同单1至50个不同人员追加分配；每次只消耗实际可用余额 |
| PUT /life/registration/{id}/settlement | mode=participant、settlementType=money；所选全部有效分配合计等于此人最终金额后，只确认此人并占1席 |

新写动作要求后台 payment+edit 权限；资金查询要求 payment+query。操作者来自后台登录身份，不接受body指定。小程序token不能访问此后台API；原工作人员旧写入口也不能广播处理participant订单。legacy请求和返回方式保留原契约。

## 请求与重试

- 现金金额建议用十进制字符串；到账沿用8位整数/2位小数上限，分配和结算沿用10位整数/2位小数上限。只允许真实正额到账。新模式0元免收确认仍未开放，不伪造0元流水。
- capture 必须有UUID requestId、支付方式、核实依据；附件复用原最多3张JPG/PNG及字节验证、私有存储。新模式不接受旧到账修正、整单结清等字段。
- allocate 请求含 UUID operationId、订单 expectedRevision、receiptId、expectedReceiptRevision、lines[{registrationId,amount}]、reason。通过finance读取两种revision。
- settle 请求含 UUID operationId、此报名人的 expectedRevision、allocationIds、finalAmount、reason；金额不同于此人unit_price快照时另需 differenceConfirmed=true 和 differenceReason。应收快照和原到账金额不改写。
- revision分别表示订单资金、到账分配及人员资料，不可互换；分配会推进受影响人员revision。刷新后可从finance.allocations恢复资金依据，不依赖前端保存第一次响应。
- 幂等键全局唯一，独立保存动作、资源、后台操作者及规范化内容指纹，失败也不允许换内容复用。成功同请求重试返回原结果；换目标/金额/操作者复用标识返回409。资金事务回滚时保留指纹、结果保持未完成，可用原标识原样重试；已过期revision或更改内容需刷新并以新标识表达新请求。成功结果与资金变更同事务提交。开关关闭时新写及其重试均返回423。
- 同一次操作不接受重复报名人或分配ID，不接受现金+卡次、多卡及未知资金字段；存在卡次来源、旧整单结算或不可靠应收时拒绝，保留资金事实供人工核对。

## 一致性与审计

到账、分配、结算分别为独立事务。分配或确认失败不会回滚先前到账；登记到账时不占位，只有结算/确认/资金关联/状态审计整体成功才占位。复用原报名结算Mapper和统一QlRegistrationPolicy名额判断；旧整单逻辑不被复制到新服务。

所有写事务按期次→订单→报名人稳定ID→到账/分配顺序锁定，使用READ_COMMITTED；死锁/锁竞争最多以原标识重试3次。个人结算不改同行人或旧batch付款/结算枚举；部分进度放在派生汇总中。

成功操作保存幂等结果，分配追加记录关联到账和最终轮次；原收款/原分配不覆盖。失败分配回滚明细后，复用原收款审计表追加操作标识及失败原因；若数据库无法保存这条失败审计，记录不含私人资料的运维告警，原错误继续返回，到账仍可核对。资金查询单独显示未分配余额，不因尚未分配漏算已核实到账。

旧付款、整单收款、结算撤销、整单取消及旧状态编辑入口对participant拒绝，避免旧客户端直接改全单。新取消和退款将在下一阶段按冻结契约实现；本阶段不会以撤销付款假装退款。

## 验证与下一阶段

Java8 reactor test/package/verify、ParticipantFinanceHttpIT（真实登录/HTTP/MySQL/Redis并继承legacy回归）、ParticipantMigrationIT（V1_4_21旧事实→增量→重放）、原RegistrationReadinessIT、SessionRulesIT为本阶段检查入口。运行既有mysql-it/http-it profiles时，仅给本轮新建的qinglife_it_*库及独立Redis端口配置测试环境变量；旧RegistrationReadinessIT还要求预先建立同名前缀的_registration库。未连接微信真实支付、生产数据或已有验收库。

2026-10-18本地验证：独立MySQL 8.4.0及Redis上149项集成测试、193项Java单元测试、54个小程序测试文件和30项契约格式校验全部通过，reactor verify含打包通过。覆盖并发分配/最后名额、幂等重放/失败指纹保留、数据库中途失败回滚、权限边界、旧订单完整回归及38→39条迁移重放。证据集中在项目上级历史归档/2026-10-18-M2阶段1/；不代表已有UAT环境或生产版本验收。

可在验证通过后进入“退款与取消”：已有逐人轮次、原到账分配、全局幂等及统一锁序可复用。下一阶段仍须追加独立退款事实/额度预留和动作权限，以及逐人取消和重报保护；本阶段暂时拒绝旧取消入口，不能据此宣称退款/取消已实现或批准上线。角色授予、0元免收例外和正式启用保持原契约边界。
