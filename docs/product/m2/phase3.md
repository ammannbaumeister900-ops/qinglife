# M2阶段3：逐人卡次核实、扣减与原账户返还

基于dev/m2 / 1d4d5a6本地实施。participant写入仍默认关闭；不部署，不改冻结UAT，不开发持卡人自行授权或完整运营UI。阶段4可复用下列后台入口。

## 复用边界

QlParticipantPassService负责核实范围与逐人编排；QlPassService仍是账户可用性、余额、consume和原consume→void的唯一来源。后者仅将既有可用性检查提为可复用方法，不改变套餐、账户持有人、有效期或旧整单算法。QlRegistrationServiceImpl只增加模式分派，不承载新的卡次规则。名额、并发锁、事务重试与全局幂等沿用前两阶段。

V1_4_24__participant_pass_settlement.sql新增运营核实及不可变撤销记录，给既有结算轮次增加可空、唯一pass_verification_id。历史结算不回填，原到账、退款和卡账不改写。核实记录保留订单、人员revision、实际使用人、持卡人、账户、次数、依据和核实人；撤销只追加关联记录。原consume/void及ql_settlement_reversal继续作为实际扣返卡与原轮次审计，不再建设第二套卡账。

## 接口与权限

| 入口 | 参数与权限 |
| --- | --- |
| POST /life/registration/{id}/pass-verifications | operationId、人员expectedRevision、passAccountId、passUnits、scope=self/same_order、basis；registrationId如提供须匹配路径。须payment+edit+life:pass:consume+life:pass:verify |
| PUT /life/registration/{id}/pass-verifications/{verificationId}/revoke | operationId、人员expectedRevision、reason；同核实权限。只撤销未被结算使用的核实，不改变余额 |
| PUT /life/registration/{id}/settlement | mode=participant、settlementType=pass、operationId、人员expectedRevision、verificationId、一个passAccountId、passUnits、reason；同核实权限。finalAmount可省略或为0，不接受正额现金、现金分配、多卡或差额字段 |
| PUT /life/registration/{id}/settlement/{settlementId}/revoke | mode=participant、operationId、人员expectedRevision、reason；须原life:registration:settlement:revoke及新增life:pass:restore。账户、次数、持卡人只能从原轮次与原核实取得，不能由请求改选 |
| GET /life/registration/batch/{id}/finance | 沿用payment+query。增加安全核实状态、逐人passUsage、原扣次/返还关联及余额快照；不返回完整核实依据、卡图片或私人凭证 |

新增verify、consume、restore权限不自动授予任何普通角色。旧payment、edit、pass:adjust或仅有旧撤销权限均不足以执行新敏感动作。小程序appToken不能访问这些后台入口。原legacy_batch请求仍走原流程。

核实不预占卡次或名额。self必须使用本人账户；same_order仅可使用订单submitted_by_customer_id对应的主报名人账户，且实际使用人来自该订单。服务端决定持卡人与受益人，不能通过body指定。双方档案须有效，账户在核实及实际扣次时都重新检查归属、状态和有效期；余额在真正扣次时锁内判断。核实权限表示运营已核实持卡人的代用依据，不代表实现用户自行授权。

核实推进人员revision；后续核实或其他人员动作使旧核实失效。扣次要求当前revision、账户、范围、使用人和次数全部匹配同一条未撤销、未使用核实。每条核实只绑定一个轮次；撤销已使用核实不能替代返卡。新轮次须重新核实，不复用已用依据。

## 一致性、取消与重报

期次→订单→排序报名人→原现金分配→核实/结算→账户的锁序与前两阶段一致。先完成名额检查，再锁账户，随后复用原consume；结算轮次、核实绑定、卡账、余额快照、目标人员确认和审计在一个事务内完成。共享账户跨营期也由账户锁及锁内账本读取保护，避免双扣和负余额。事务失败保留请求指纹，可原标识原报文重试。

卡次轮次现金金额为0，不伪造实际到账，payment_status保留unpaid；participant的confirmed人员仍占1席。现金分配、退款记录或未核对旧资金存在时拒绝卡次结算；同一有效轮次只允许一个账户。返卡是原consume的整笔反向void，只返原持卡人原账户及原次数，不能分次返、改返使用人账户或折现。

取消报名与返卡分别操作：取消仅释放目标人员席位，原卡次仍待处理并阻止重报；返卡不自动取消或释放confirmed席位。返卡后保留原结算和原扣次，当前settlement回pending，报名状态保持原值。未取消人员若要再次使用卡次，必须重新核实并建立新轮次，原返卡请求的重试不能撤销新轮次。已取消人员完成原账户返还后才能用新报名ID重报，旧报名不会自动恢复。

原账户即便expired或void也可按原扣次返还，但状态、有效期均不改变；不会激活旧卡。原扣次缺失、账户/人员/营期/次数不符、原轮次缺少可靠核实或已返还时拒绝新的返还。成功操作的原标识重放仍返回原结果，不再写卡账；当前状态须以刷新查询为准。姓名、核实依据和业务身份不从测试样本推断。

## 验证与阶段4

真实HTTP/MySQL/Redis检查由ParticipantPassHttpIT承接本人使用、同单代用、核实撤销、共享余额竞争、失败回滚、取消/返卡/重报及阶段1/2和legacy回归；ParticipantMigrationIT验证历史版本及有真实结构资金记录的阶段2→阶段3增量/重放。其他相关入口为RegistrationReadinessIT、SessionRulesIT和LoginConcurrencyIT。仅使用本轮独立回环实例及新建qinglife_it_*库；日志保留于项目上级历史归档/2026-10-18-M2阶段3/。

2026-10-18最终结果：独立MySQL 8.4.0/Redis上207项集成检查全部通过（101项HTTP资金回归、2项迁移、76项报名兼容、19项期次规则、9项登录并发）；193项Java单元测试、54个小程序测试文件、37项契约格式校验通过，reactor verify含打包成功。共用锁入口另验证订单与所有人员营期一致，异常数据在写入前拒绝。未修改前端源码，不代表运营/真机验收或上线批准。

JSON契约implemented表示这些后台路由已在本地实现，不代表上线或运营验收；写门禁仍关闭。原完整SQL是历史独立草案，不能直接在已迁移库重放；实际增量以server/migrations为准。

后续阶段4可接运营核实、逐人确认、独立退款/取消/返卡及状态刷新界面；仍需确认实际运营权限授予、核实依据呈现与业务验收。前两阶段保留的现金退款差异人工核对、0元免收例外及正式开关启用不在本轮扩展。
