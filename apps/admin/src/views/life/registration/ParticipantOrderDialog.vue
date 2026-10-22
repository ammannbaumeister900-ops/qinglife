<template>
  <el-dialog title="多人订单 · 逐人管理" :visible.sync="visible" width="94%" top="4vh" :close-on-click-modal="false" :before-close="close" append-to-body>
    <div v-loading="loading" class="participant-order">
      <header class="order-heading"><div><h2>{{ row.orderNo || '报名订单' }}</h2><p>{{ row.sessionName || '营期' }} · {{ row.contactName || row.buyerNickname || row.nickname }} <span v-if="row.contactPhone">· {{ row.contactPhone }}</span></p></div><el-button icon="el-icon-refresh" :disabled="loading || !!action" @click="refresh">刷新订单</el-button></header>
      <el-alert v-if="loadError" :title="loadError" description="当前数据不可用于新操作。请刷新重新读取；已保存的操作不会因读取失败而撤销。" type="error" :closable="false" show-icon />
      <el-alert v-if="finance && !finance.writeEnabled" title="逐人结算写入尚未开放 · 当前只读" description="默认关闭的开关保持不变；如需开放，应另行完成验收与授权。" type="warning" :closable="false" show-icon />
      <template v-if="finance && finance.mode === 'participant'">
        <div class="order-ledger" aria-label="订单资金摘要">
          <div><span>实际到账</span><strong>{{ yuan(summary.grossReceived) }}</strong></div><div><span>尚未分配</span><strong>{{ yuan(summary.unallocatedAmount) }}</strong></div><div><span>实际已退</span><strong>{{ yuan(summary.refundedAmount) }}</strong></div><div><span>有效退款预留</span><strong>{{ yuan(summary.refundReserved) }}</strong></div><div><span>净到账</span><strong>{{ yuan(summary.netReceived) }}</strong></div><div><span>有效占位 / 本单人数</span><strong>{{ summary.confirmed }} / {{ summary.total }}</strong></div>
        </div>
        <div class="section-heading"><h3>每位报名人的结算与名额</h3><el-button v-if="allowed('capture')" type="primary" size="small" :disabled="!writable" @click="start('capture')">登记实际到账</el-button></div>
        <p class="section-note">现金与卡次分别结算。退款、取消报名、返还卡次分别操作；任何一步都不会自动替你完成另一步。</p>
        <el-table :data="finance.participants" row-key="id" size="small" class="people-table">
          <el-table-column type="expand"><template slot-scope="scope"><div class="person-detail"><p>报名 ID：{{ scope.row.id }} · 修订 {{ scope.row.revision }}</p><p v-if="scope.row.rebookingHint">重报条件：{{ scope.row.rebookingHint }}</p><p v-else>重报须先取消原报名，并处理旧资金、预留和卡次；须建立新报名，不恢复旧记录。</p><p v-if="scope.row.currentSettlementId">当前结算轮次：{{ scope.row.currentSettlementId }}</p><div v-for="use in scope.row.passUsage" :key="use.settlementId" class="history-line"><b>{{ use.returnedAt ? '已返原账户' : '已扣卡次' }} · {{ use.passUnits }} 次</b><br>实际使用人 {{ use.beneficiaryCustomerId }} · 持卡人 {{ customerName(use.holderCustomerId) }}（{{ use.holderCustomerId }}）<br>账户 {{ use.passAccountId }} · 营期 {{ row.sessionName || row.sessionId }}<br>结算 {{ use.settlementId }} · 原扣账 {{ use.consumeLedgerId }}<br>扣次 {{ use.confirmedAt }}<span v-if="use.returnedAt"> · 返还 {{ use.returnedAt }} · 原扣账关联 {{ use.originalLedgerId }}</span><el-button v-if="!use.returnedAt && scope.row.currentSettlementId === use.settlementId && allowed('returnPass')" type="text" :disabled="!writable" @click="start('returnPass', scope.row, {use})">返还原账户</el-button></div></div></template></el-table-column>
          <el-table-column label="报名人" min-width="120"><template slot-scope="scope"><b>{{ scope.row.name }}</b><div class="muted">{{ labels[scope.row.registrationStatus] || scope.row.registrationStatus }}</div></template></el-table-column>
          <el-table-column label="应收 / 最终现金" min-width="150"><template slot-scope="scope">{{ yuan(scope.row.quotedAmount) }}<div class="muted">{{ scope.row.settlementType === 'pass' && scope.row.settlementStatus === 'confirmed' ? '卡次结算 · 无现金欠款' : scope.row.finalAmount == null ? '尚未确认最终金额' : '最终 ' + yuan(scope.row.finalAmount) }}</div></template></el-table-column>
          <el-table-column label="结算 / 到账分配" min-width="180"><template slot-scope="scope"><el-tag :type="scope.row.settlementStatus === 'confirmed' ? 'success' : 'info'" size="small">{{ settlementLabel(scope.row) }}</el-tag><div class="muted">现金已分配 {{ yuan(scope.row.allocatedAmount) }}</div></template></el-table-column>
          <el-table-column label="退款" min-width="160"><template slot-scope="scope"><span :class="{'warning-text':scope.row.reconciliationRequired}">{{ scope.row.reconciliationRequired ? '差异待核对' : '已退 ' + yuan(scope.row.refundedAmount) }}</span><div class="muted">预留 {{ yuan(scope.row.refundReserved) }} · 可退 {{ yuan(scope.row.refundableAmount) }}</div></template></el-table-column>
          <el-table-column label="名额" width="100"><template slot-scope="scope"><el-tag :type="scope.row.seatOccupied ? 'success' : 'info'" size="small">{{ scope.row.seatOccupied ? '占用 1 席' : '不占位' }}</el-tag></template></el-table-column>
          <el-table-column label="操作" min-width="245"><template slot-scope="scope"><div class="person-actions">
            <el-button v-if="cashPending(scope.row) && allowed('cash')" type="text" :disabled="!writable" @click="start('cash', scope.row)">确认现金结清</el-button>
            <el-button v-if="passPending(scope.row) && allowed('verify')" type="text" :disabled="!writable" @click="start('verify', scope.row)">卡次使用核实</el-button>
            <el-button v-if="Number(scope.row.refundableAmount) > 0 && allowed('refund')" type="text" :disabled="!writable || scope.row.reconciliationRequired" @click="start('refund', scope.row)">申请退款</el-button>
            <el-button v-if="scope.row.registrationStatus !== 'cancelled' && allowed('cancel')" type="text" class="danger-action" :disabled="!writable" @click="start('cancel', scope.row)">取消此人</el-button>
            <template v-if="allowed('returnPass')"><el-button v-for="use in activeUses(scope.row)" :key="use.settlementId" type="text" :disabled="!writable" @click="start('returnPass', scope.row, {use})">原账户返卡</el-button></template>
          </div><span v-if="!hasAnyActionPermission" class="muted">只读 · 当前账号无操作权限</span></template></el-table-column>
        </el-table>
        <el-tabs v-model="tab" class="finance-tabs">
          <el-tab-pane label="到账与分配" name="cash">
            <p class="section-note">到账事实独立保存。分配失败时不重复登记；先核对余额和人员状态，再重新分配。</p>
            <el-table :data="finance.receipts" size="small"><el-table-column type="expand"><template slot-scope="scope"><div class="person-detail">到账 ID：{{ scope.row.id }} · 修订 {{ scope.row.revision }}<p>方式：{{ labels[scope.row.paymentMethod] || scope.row.paymentMethod }} · 回单：{{ scope.row.reference || '未填' }} · 操作人 ID：{{ scope.row.operatorId }}</p><p>依据：{{ scope.row.evidence }}</p></div></template></el-table-column><el-table-column label="到账时间" prop="receivedAt" min-width="165" /><el-table-column label="实际到账" min-width="120"><template slot-scope="scope">{{ yuan(scope.row.amount) }}</template></el-table-column><el-table-column label="已分配" min-width="120"><template slot-scope="scope">{{ yuan(scope.row.allocatedAmount) }}</template></el-table-column><el-table-column label="未分配" min-width="120"><template slot-scope="scope">{{ yuan(scope.row.unallocatedAmount) }}</template></el-table-column><el-table-column label="分配操作" min-width="150"><template slot-scope="scope"><el-button v-if="allowed('allocate')" type="text" :disabled="!writable || scope.row.status !== 'paid' || Number(scope.row.unallocatedAmount) <= 0" @click="start('allocate', null, {receipt:scope.row})">分配给报名人</el-button></template></el-table-column></el-table>
            <h4>不可变分配明细</h4><el-table :data="finance.allocations" size="small"><el-table-column label="报名人" min-width="120"><template slot-scope="scope">{{ personName(scope.row.registrationId) }}</template></el-table-column><el-table-column label="分配 / 到账 ID" min-width="320"><template slot-scope="scope"><div class="mono">{{ scope.row.allocationId }}<br>{{ scope.row.receiptId }}</div></template></el-table-column><el-table-column label="金额" width="110"><template slot-scope="scope">{{ yuan(scope.row.amount) }}</template></el-table-column><el-table-column label="记录类型 / 关联" min-width="180"><template slot-scope="scope">{{ scope.row.entryType === 'allocate' ? '分配' : '冲销' }} · {{ scope.row.settlementId ? '已用于结算' : '未用于结算' }}<div class="muted">{{ scope.row.createdAt }}</div></template></el-table-column></el-table>
          </el-tab-pane>
          <el-tab-pane label="退款申请与实际退款" name="refunds">
            <p class="section-note">申请、额度预留、实际退付分别记录。出现差异只标记待核对，不能自行修改原款项。</p>
            <el-table :data="finance.refundRequests" size="small"><el-table-column type="expand"><template slot-scope="scope"><div class="person-detail">申请 {{ scope.row.requestId }} · 修订 {{ scope.row.revision }}<p>申请原因：{{ scope.row.reason }} · 申请人 ID {{ scope.row.requestedBy }} · {{ scope.row.requestedAt }}</p><p v-if="scope.row.paymentId">退款记录 {{ scope.row.paymentId }} · {{ labels[scope.row.paymentMethod] }} · 回单 {{ scope.row.reference || '未填' }}<br>实际发生 {{ scope.row.occurredAt }} · {{ labels[scope.row.paymentStatus] || scope.row.paymentStatus }}<br>依据 {{ scope.row.evidence }}</p></div></template></el-table-column><el-table-column label="报名人" min-width="120"><template slot-scope="scope">{{ personName(scope.row.registrationId) }}</template></el-table-column><el-table-column label="申请金额 / 状态" min-width="180"><template slot-scope="scope">{{ yuan(scope.row.requestedAmount) }}<div :class="scope.row.status === 'reconciliation_required' ? 'warning-text' : 'muted'">{{ labels[scope.row.status] }}</div></template></el-table-column><el-table-column label="实际退款" min-width="130"><template slot-scope="scope">{{ scope.row.paymentId ? yuan(scope.row.actualAmount) : '尚未登记' }}<div class="muted">{{ labels[scope.row.paymentStatus] || '' }}</div></template></el-table-column><el-table-column label="处理" min-width="280"><template slot-scope="scope"><template v-if="allowed('refund') && ['requested','processing'].includes(scope.row.status) && !scope.row.paymentId"><el-button v-if="scope.row.status === 'requested'" type="text" :disabled="!writable" @click="refundAction('processing', scope.row)">开始处理</el-button><el-button type="text" :disabled="!writable" @click="refundAction('payment', scope.row)">登记实际退款</el-button><el-button type="text" :disabled="!writable" @click="refundAction('refundCancel', scope.row)">取消申请</el-button><el-button type="text" :disabled="!writable" @click="refundAction('refundFail', scope.row)">记录失败</el-button></template><span v-else class="muted">{{ scope.row.status === 'reconciliation_required' ? '保留事实，联系负责人核对' : '查看历史' }}</span></template></el-table-column></el-table>
          </el-tab-pane>
          <el-tab-pane label="卡次核实与使用" name="passes">
            <p class="section-note">本人卡次或同订单主报名人代用，均须运营核实。核实不预留余额；扣次时由服务端检查账户和名额。</p>
            <el-table :data="finance.passVerifications" size="small"><el-table-column label="实际使用人 / 持卡人" min-width="260"><template slot-scope="scope">{{ personName(scope.row.registrationId) }} · {{ scope.row.scope === 'self' ? '本人使用' : '同订单代用' }}<div class="muted">使用人 {{ scope.row.beneficiaryCustomerId }}<br>持卡人 {{ customerName(scope.row.holderCustomerId) }}（{{ scope.row.holderCustomerId }}）</div></template></el-table-column><el-table-column label="账户 / 核实 ID" min-width="310"><template slot-scope="scope"><div class="mono">{{ scope.row.passAccountId }}<br>{{ scope.row.verificationId }}</div><div class="muted">{{ scope.row.verifiedAt }}</div></template></el-table-column><el-table-column label="次数 / 状态" min-width="140"><template slot-scope="scope">{{ scope.row.passUnits }} 次<div class="muted">{{ labels[scope.row.state] }}</div></template></el-table-column><el-table-column label="操作" min-width="240"><template slot-scope="scope"><template v-if="scope.row.state === 'verified' && allowed('verify')"><el-button type="text" :disabled="!writable" @click="start('pass', findPerson(scope.row.registrationId), {verification:scope.row})">确认扣次与报名</el-button><el-button type="text" :disabled="!writable" @click="start('revokeVerification', findPerson(scope.row.registrationId), {verification:scope.row})">撤销未用核实</el-button></template><span v-else class="muted">保留历史</span></template></el-table-column></el-table>
            <p class="section-note">扣次与返还明细在上方每人的展开行中查看；返还只回原持卡人原账户。</p>
          </el-tab-pane>
        </el-tabs>
        <p v-if="!hasAnyActionPermission" class="permission-note">当前账号只可查看。收款、退款、取消、核实与返卡分别需要现有角色权限，不会自动授权。</p>
      </template>
    </div>
    <participant-operation-dialog v-if="action" ref="operation" :context="action" :finance="finance" @saved="onSaved" @close="onActionClose" />
    <div slot="footer"><el-button :disabled="!!action" @click="close">关闭订单</el-button></div>
  </el-dialog>
</template>

<script>
import { getParticipantFinance } from '@/api/life/registration'
import { checkPermi } from '@/utils/permission'
import ParticipantOperationDialog from './ParticipantOperationDialog.vue'
import { yuan, settlementLabel, labels } from './participantFinance'
const permissions = {
  capture: ['life:registration:payment', 'life:registration:edit'],
  refund: ['life:registration:refund'], cancel: ['life:registration:cancel'],
  verify: ['life:registration:payment', 'life:registration:edit', 'life:pass:consume', 'life:pass:verify'],
  returnPass: ['life:registration:settlement:revoke', 'life:pass:restore']
}
export default {
  name: 'ParticipantOrderDialog',
  components: { ParticipantOperationDialog },
  data() { return { visible: false, row: {}, finance: null, loading: false, loadError: '', sequence: 0, action: null, tab: 'cash', labels } },
  computed: {
    summary() { return this.finance.orderSummary || {} },
    writable() { return !!(this.finance && this.finance.writeEnabled && !this.loading && !this.loadError && !this.action) },
    hasAnyActionPermission() { return Object.keys(permissions).some(this.allowed) }
  },
  beforeDestroy() { this.sequence++ },
  methods: {
    yuan, settlementLabel,
    hasPendingWrite() { return !!(this.$refs.operation && (this.$refs.operation.busy || this.$refs.operation.uncertain)) },
    allowed(kind) { return (permissions[kind === 'allocate' || kind === 'cash' ? 'capture' : kind] || []).length > 0 && permissions[kind === 'allocate' || kind === 'cash' ? 'capture' : kind].every(p => checkPermi([p])) },
    async show(row) { if (this.action) return; this.row = { ...row }; this.finance = null; this.tab = 'cash'; this.visible = true; await this.refresh() },
    close(done) { if (this.action) return; this.visible = false; this.sequence++; if (typeof done === 'function') done() },
    async refresh() {
      const sequence = ++this.sequence
      this.loading = true; this.loadError = ''
      try { const res = await getParticipantFinance(this.row.batchId); if (sequence !== this.sequence) return; if (res.data.mode !== 'participant') throw new Error('订单模式发生变化，请关闭后重新查询'); this.finance = res.data } catch (error) { if (sequence === this.sequence) this.loadError = error.message || '订单读取失败' } finally { if (sequence === this.sequence) this.loading = false }
    },
    findPerson(id) { return this.finance.participants.find(p => p.id === id) || {} },
    personName(id) { return this.findPerson(id).name || id },
    customerName(id) { const person = this.finance.participants.find(p => p.customerId === id); return person && person.name || (this.row.buyerCustomerId === id && this.row.buyerNickname) || id },
    cashPending(p) { return ['pending', 'waitlisted'].includes(p.registrationStatus) && p.settlementStatus === 'pending' && Number(p.allocatedAmount) > 0 && !p.reconciliationRequired },
    passPending(p) { return p.registrationStatus !== 'cancelled' && p.settlementStatus === 'pending' && Number(p.allocatedAmount) === 0 && !p.reconciliationRequired },
    activeUses(p) { return (p.passUsage || []).filter(u => !u.returnedAt && u.settlementId === p.currentSettlementId) },
    start(kind, person, extra = {}) { if (!this.writable) return; const provenance = extra.verification || extra.use; this.action = { kind, person, batchId: this.row.batchId, registrationId: person && person.id || this.row.id, holderName: provenance && this.customerName(provenance.holderCustomerId), ...extra } },
    refundAction(kind, request) { this.start(kind, this.findPerson(request.registrationId), { request }) },
    async onSaved() { await this.refresh(); this.$emit('changed') },
    async onActionClose(refresh) { this.action = null; if (refresh) { await this.refresh(); this.$emit('changed') } }
  }
}
</script>

<style scoped>
.participant-order { color: #263e35; }
.order-heading, .section-heading { display: flex; justify-content: space-between; align-items: center; gap: 20px; }
.order-heading { margin-bottom: 20px; }
h2 { margin: 0 0 8px; font-size: 23px; color: #234b40; }
h3 { margin: 0; font-size: 17px; }
.order-heading p, .section-note { color: #64726d; margin: 6px 0 16px; line-height: 1.6; }
.order-ledger { display: flex; flex-wrap: wrap; gap: 24px 40px; padding: 20px 0 24px; margin-bottom: 24px; border-bottom: 1px solid #dce6df; }
.order-ledger span { display: block; color: #64726d; font-size: 13px; margin-bottom: 8px; }
.order-ledger strong { font-size: 23px; font-weight: 600; font-variant-numeric: tabular-nums; }
.muted, .mono { color: #64726d; font-size: 12px; margin-top: 6px; line-height: 1.55; overflow-wrap: anywhere; }
.person-detail { padding: 8px 28px; line-height: 1.75; overflow-wrap: anywhere; }
.history-line { padding: 12px 0; border-top: 1px solid #e5ebe7; }
.person-actions { display: flex; flex-wrap: wrap; gap: 0 16px; }
.person-actions .el-button { margin-left: 0; }
.danger-action { color: #ae3d35; }
.warning-text { color: #a25a17; }
.finance-tabs { margin-top: 28px; }
.permission-note { border-left: 3px solid #aabeb0; padding-left: 12px; color: #64726d; }
</style>
