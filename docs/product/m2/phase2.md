# M2阶段2：逐人退款与取消

基于阶段1 dev/m2 / 90833d4 实施现金退款与独立取消；participant写入仍默认关闭。不提供卡次折现、共享、返卡或完整后台UI，不部署到UAT/生产。

## 数据与权限

V1_4_23__participant_refunds.sql仅追加已审查草案中的4张表：ql_refund_request、ql_refund_reservation、ql_refund_payment、ql_refund_attribution。原到账、分配、结算轮次、funding、付款状态均保留；不回填旧单、不修改已发布迁移。每份申请最多一笔实际退款；多次部分退款通过多份申请表达，一次申请可预留此人多笔原分配。数据库唯一约束同时保护操作标识、申请实际付款及同方式回单号。

所有新接口仅接受后台身份。新增独立动作 life:registration:refund 与 life:registration:cancel；迁移只登记权限入口，不自动给运营角色授权，原payment/edit权限不能代替。操作者来自会话，body中的operatorId、customerId、未知字段均拒绝。资金查询继续要求payment+query，不返回退款凭证、完整回单或私人附件。

| 入口 | 语义与revision |
| --- | --- |
| POST /life/registration/{id}/refund-requests | operationId、人员expectedRevision、requestedAmount、allocations[{allocationId,amount}]、reason；合计等于申请额，逐笔扣除已归因退款及有效预留后校验 |
| PUT /life/registration/{id}/refund-requests/{requestId}/processing | 申请expectedRevision、operationId、reason；仅requested可进入processing，不计已退 |
| PUT 同申请/cancel、/fail | 同上，另需noActualPaymentConfirmed=true方能在无实际事实时释放预留；未明确确认则保留额度并进入reconciliation_required；已有实际事实一律拒绝释放 |
| POST 同申请/payments | 申请expectedRevision、operationId、actualAmount、paymentMethod、externalReference、evidence、occurredAt、reason；实际时间使用有效+08:00日期时间 |
| PUT /life/registration/{id}/cancel | 人员expectedRevision、operationId、reason；只取消此人，不生成退款，不改变同行人或原资金轮次；沿用已有到场记录保护 |
| GET /life/registration/batch/{id}/finance | 增加逐人refundState、refundedAmount、refundReserved、refundableAmount、netReceived及安全申请历史；订单另列累计实退、有效预留、净到账 |

每个动作使用阶段1全局幂等指纹，失败也不能换内容复用；成功请求重试不重复钱、不再次取消或占位。申请变更与资金动作推进人员及订单revision。原付款状态保持paid，退款不释放名额；registration_status仍confirmed时继续占1席，只有取消改变有效席位。

## 事务与异常核对

阶段1事务门禁、有界重试及期次→订单→排序报名人锁已收敛到QlParticipantFinanceGuard，退款和原收款/分配/确认共同复用；原资金职责未复制到报名热点服务。随后锁原到账/分配，再锁申请/实际退款；所有竞争路径在同一期次锁下串行核算。QlRegistrationPolicy只补必要的按模式重报检查和共用到场保护。

实际退款先以recorded_unreconciled独立提交，再用另一事务追加归因并置applied/completed。归因写入失败时，实际事实及原预留仍在，响应明确reconciliationRequired=true；不能将这种响应展示成退款完成。用原操作标识、原报文重试可再次归因，不生成第二笔实际退款。申请/实际事实/操作者/操作原因及不可变归因可相互追溯；人员取消与状态审计同事务。

正常实际退款须等于已核实申请，累计已归因+有效预留不得超过原分配。真实已退额与申请不符时保留原额并冻结此人新的退款/分配/结清，不能为通过限额改小真实事实。对账异常不是授权超额退款；没有后台支付执行器。refundReserved展示尚未实际支付的有效预留，已保存但未归因的实际退款消耗同一预留池，不重复相加；原预留明细完整保留。异常时refundableAmount为0，不自动释放资金。差异人工核对后的纠正/解除流程不在本轮新增，不能直接改表掩盖事实。

取消报名释放此人名额，未处理资金、有效预留、未归因实际退款及卡次继续阻止重报。现金全部归因退清且无预留后，允许以新订单/新报名ID重新申请；原paid/current_settlement_id及审计仍留在已取消历史行，不把旧行恢复成pending。旧幂等结果只代表原操作结果，不得据其历史状态覆盖当前数据。共享订单尚有未分配到账时无法确定归属，保守阻止重报，需先核对分配；同行人已经明确分配的现金不阻挡已退清人员重报。

legacy_batch继续原整单流程，不分摊历史款项；participant旧整单入口继续拒绝，门禁关闭后新写及重试均拒绝，现有事实仍可查询。

## 验证与后续

真实HTTP/MySQL/Redis验证入口为ParticipantRefundHttpIT（继承阶段1及legacy回归）、ParticipantMigrationIT（旧单升级及有资金的阶段1→阶段2增量/重放）、RegistrationReadinessIT、SessionRulesIT。只使用本轮新建qinglife_it_*库及独立回环端口；不连接微信真实付款或已有验收库。本轮日志集中在项目上级历史归档/2026-10-18-M2阶段2/。

2026-10-18最终结果：独立MySQL 8.4.0/Redis上181项集成检查（75项HTTP资金回归、2项迁移、76项报名兼容、19项期次规则、9项登录并发）全部通过；193项Java单元测试、54个小程序测试文件、32项契约格式校验均通过，reactor verify含打包成功。验证覆盖部分/多次/并发退款、超额与预留、真实事实保留及原请求重试、取消审计回滚、同行人隔离、释放名额竞争、重报唯一性、权限和门禁。未修改小程序或后台UI源码；这些隔离结果不代表生产、微信真机或运营业务验收。

本阶段完成后可进入阶段3的卡次共享、扣减与原账户返还开发；复用原QlPassService/consume→void和本轮独立取消语义。运营代用核实权限、持卡人与受益人范围、不可混合资金及不可重复返卡仍需按冻结契约实现和验证。本轮未实现返卡，不把现金退款用于卡次折现。正式角色授予、0元免收例外、业务验收及上线启用均保留原边界。
