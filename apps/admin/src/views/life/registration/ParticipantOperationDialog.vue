<template>
  <el-dialog :title="title" :visible="true" width="760px" append-to-body :close-on-click-modal="false" :close-on-press-escape="false" :before-close="close">
    <p class="operation-context">{{ person.name || '本订单' }} <span v-if="person.id">· 报名 {{ person.id }}</span></p>
    <el-alert :title="instruction" type="info" :closable="false" show-icon />
    <el-alert v-if="error" class="feedback" :title="error" :description="guidance" type="error" :closable="false" show-icon />
    <el-alert v-if="saved" class="feedback" :title="reconciliation ? '实际退款已保存，差异待核对' : '操作已保存'" :description="reconciliation ? '原资金事实与预留已保留。请核对线下凭证和历史；尚无正式差异处理规则，不可另记一笔或擅自调整。' : '请关闭查看最新订单状态。读取失败时可刷新，不要重复登记。'" :type="reconciliation ? 'warning' : 'success'" :closable="false" show-icon />
    <el-form :model="form" label-position="top" :disabled="busy || !!submitted" class="operation-form" @submit.native.prevent>
      <template v-if="kind === 'capture' || kind === 'payment'">
        <div class="form-pair">
          <el-form-item :label="kind === 'capture' ? '实际到账金额（元）' : '实际线下退款金额（元）'" required><el-input v-model="form.amount" inputmode="decimal" placeholder="以实际凭证为准" /></el-form-item>
          <el-form-item :label="kind === 'capture' ? '到账方式' : '退款方式'" required><el-select v-model="form.method" placeholder="请选择实际方式"><el-option v-for="method in methods" :key="method" :label="labels[method]" :value="method" /></el-select></el-form-item>
        </div>
        <el-form-item v-if="kind === 'payment'" label="实际退款时间（北京时间）" required><el-date-picker v-model="form.occurredAt" type="datetime" value-format="yyyy-MM-ddTHH:mm:ss+08:00" placeholder="选择实际退款时间" /></el-form-item>
        <el-form-item label="外部流水号 / 回单标识"><el-input v-model="form.reference" maxlength="100" placeholder="有则填写，帮助核对和防止重复" /></el-form-item>
        <el-form-item label="实际发生依据" required><el-input v-model="form.evidence" type="textarea" :rows="2" maxlength="500" placeholder="记录核实的凭证与事实，不填写密码或完整银行卡号" /></el-form-item>
        <el-form-item v-if="kind === 'capture'" label="到账凭证（选填）"><receipt-images v-model="form.attachments" :disabled="busy || !!submitted" /></el-form-item>
        <el-checkbox v-model="form.actualConfirmed">我已核实该笔款项确已{{ kind === 'capture' ? '到账' : '线下退付' }}，不是计划或申请</el-checkbox>
      </template>

      <template v-if="kind === 'allocate' || kind === 'refund'">
        <p v-if="kind === 'allocate'">本笔到账 {{ yuan(context.receipt.amount) }} · 服务端可分配 {{ yuan(context.receipt.unallocatedAmount) }}</p>
        <p v-else>此人服务端可退 {{ yuan(person.refundableAmount) }}；申请后先预留额度，不代表实际退款。</p>
        <el-table :data="lines" size="small" border>
          <el-table-column :label="kind === 'allocate' ? '报名人' : '原分配记录'" min-width="210"><template slot-scope="scope">{{ scope.row.name }}<div class="muted">{{ scope.row.id }}</div></template></el-table-column>
          <el-table-column :label="kind === 'allocate' ? '应收 / 已分配' : '服务端可退'" min-width="130"><template slot-scope="scope">{{ scope.row.available }}</template></el-table-column>
          <el-table-column label="本次金额（元）" width="150"><template slot-scope="scope"><el-input v-model="scope.row.amount" size="small" inputmode="decimal" placeholder="0.00" :disabled="busy || !!submitted" /></template></el-table-column>
        </el-table>
        <p class="muted">仅提交填写了正金额的行；总额、余额和人员状态由服务端再次校验。</p>
      </template>

      <template v-if="kind === 'cash'">
        <p>此人已分配 {{ yuan(person.allocatedAmount) }} · 原应收 {{ yuan(person.quotedAmount) }}</p>
        <el-form-item label="最终结清金额（元）" required><el-input v-model="form.amount" readonly /><p class="muted">以服务端本次快照中的全部有效分配金额为准。</p></el-form-item>
        <p class="muted">确认会关联此人全部有效分配，并确认此人的报名及名额。不会改变同行人。</p>
        <el-checkbox v-model="form.differenceConfirmed">已核实与原应收的差额（金额不同时必选）</el-checkbox>
        <el-form-item v-if="form.differenceConfirmed" label="差额依据" required><el-input v-model="form.differenceReason" type="textarea" maxlength="500" /></el-form-item>
      </template>

      <template v-if="kind === 'verify'">
        <el-form-item label="卡次来源" required><el-radio-group v-model="form.scope" @change="loadPasses"><el-radio label="self">报名人本人卡次</el-radio><el-radio label="same_order">同订单主报名人代用</el-radio></el-radio-group></el-form-item>
        <el-form-item label="卡次账户" required><el-select v-model="form.accountId" :loading="loadingPasses" :disabled="loadingPasses || !!passError" placeholder="选择一个账户" style="width:100%"><el-option v-for="card in cards" :key="card.id" :value="card.id" :disabled="!card.usable" :label="card.passType + ' · 余额 ' + card.balance + ' 次 · ' + card.id" /></el-select><p v-if="passError" class="error-text">{{ passError }}</p><p v-else-if="!loadingPasses && !cards.length" class="muted">没有可选卡次。须由已有权限人员在卡次管理中核对账户，不会自动开卡或授权。</p></el-form-item>
        <el-form-item label="本次使用次数" required><el-input-number v-model="form.units" :min="1" :max="9999" :precision="0" /></el-form-item>
        <el-form-item label="运营核实依据" required><el-input v-model="form.basis" type="textarea" :rows="3" maxlength="500" placeholder="记录持卡人、实际使用人、使用范围及核实方式；代用须有可追溯依据" /></el-form-item>
        <el-checkbox v-model="form.verified">我已核实本人使用或同订单代用依据</el-checkbox>
      </template>

      <template v-if="kind === 'pass'">
        <p>核实 {{ context.verification.verificationId }}</p>
        <p>{{ context.verification.scope === 'self' ? '本人使用' : '同订单主报名人代用' }} · {{ context.verification.passUnits }} 次</p>
        <p>原持卡人 {{ context.holderName }} · {{ context.verification.holderCustomerId }}<br>实际使用人 {{ person.name }} · {{ context.verification.beneficiaryCustomerId }}<br>账户 {{ context.verification.passAccountId }}</p>
      </template>
      <template v-if="kind === 'returnPass'">
        <p>原轮次 {{ context.use.settlementId }} · {{ context.use.passUnits }} 次</p>
        <p>仅返原持卡人 {{ context.holderName }}（{{ context.use.holderCustomerId }}）的原账户 {{ context.use.passAccountId }}。</p>
      </template>
      <p v-if="kind === 'payment'">退款申请 {{ context.request.requestId }} · 申请金额 {{ yuan(context.request.requestedAmount) }}。实际金额不同时仍按事实登记，转待核对。</p>
      <el-checkbox v-if="kind === 'refundCancel' || kind === 'refundFail'" v-model="form.noActualPaymentConfirmed">我已核实没有发生任何实际退款，才可释放本申请预留</el-checkbox>
      <el-form-item v-if="kind !== 'capture' && kind !== 'verify'" label="操作原因" required><el-input v-model="form.reason" type="textarea" :rows="2" maxlength="500" placeholder="记录处理原因，便于后续审计" /></el-form-item>
    </el-form>
    <div v-if="submitted" class="operation-key">操作编号：{{ submitted.operationId || submitted.requestId }}</div>
    <div slot="footer">
      <el-button :disabled="busy" @click="close">{{ saved ? '关闭并查看订单' : submitted && !uncertain ? '关闭并刷新' : '关闭' }}</el-button>
      <el-button v-if="!saved && (!submitted || uncertain)" type="primary" :loading="busy" @click="submit">{{ uncertain ? '按原请求重试' : title }}</el-button>
    </div>
  </el-dialog>
</template>

<script>
import { getParticipantPasses, writeParticipantFinance } from '@/api/life/registration'
import ReceiptImages from './ReceiptImages.vue'
import { yuan, money, uuid, recovery, labels } from './participantFinance'

const titles = { capture: '登记实际到账', allocate: '分配到账', cash: '确认现金结清', refund: '申请逐人退款', processing: '开始处理退款', payment: '登记实际退款', refundCancel: '取消退款申请', refundFail: '记录退款失败', verify: '记录卡次核实', pass: '确认扣次与报名', revokeVerification: '撤销未用核实', returnPass: '返还原账户卡次', cancel: '取消此人报名' }
const instructions = { capture: '只保存实际到账事实，不自动分配、结清或占位。', allocate: '同一笔到账可分给多人；同一人也可接收多笔现金到账。', cash: '现金结清与卡次互斥。确认成功后，此人占用一个名额。', refund: '退款申请与实际退付分别记录；退款不自动取消报名或释放名额。', processing: '开始处理只改变申请状态，不登记实际退款。', payment: '请先在线下完成退款，再登记实际金额。记录成功后不可把它当作失败重新退付。', refundCancel: '只允许撤销尚未实际退付的申请；保留申请及审计历史。', refundFail: '失败状态必须核实未实际退付；已有退款事实须进入核对。', verify: '此步只记录运营核实，不预留卡次或名额；下一步确认时再次检查余额与期次。', pass: '扣减一个卡次账户并确认此人报名，不产生现金欠款或现金到账。', revokeVerification: '只撤销尚未使用的核实，不返卡、不取消报名。', returnPass: '返卡是独立操作，不自动取消报名或释放名额，不改变原账户有效期或状态。', cancel: '只取消此人并释放其有效名额，不自动退款、返卡或恢复旧报名。' }
function required(value, name) { if (!String(value || '').trim()) throw new Error('请填写' + name); return String(value).trim() }

export default {
  name: 'ParticipantOperationDialog',
  components: { ReceiptImages },
  props: { context: { type: Object, required: true }, finance: { type: Object, required: true }},
  data() {
    return { form: { amount: '', method: '', evidence: '', reference: '', attachments: [], actualConfirmed: false, reason: '', differenceConfirmed: false, differenceReason: '', scope: 'self', accountId: '', units: 1, basis: '', verified: false, occurredAt: '', noActualPaymentConfirmed: false }, lines: [], cards: [], loadingPasses: false, passError: '', passVersion: 0, busy: false, submitted: null, uncertain: false, error: '', guidance: '', saved: false, reconciliation: false, labels, methods: ['cash', 'wechat_scan', 'alipay_scan', 'transfer', 'other'] }
  },
  computed: {
    kind() { return this.context.kind },
    person() { return this.context.person || {} },
    title() { return titles[this.kind] },
    instruction() { return instructions[this.kind] }
  },
  created() {
    if (this.kind === 'cash') this.form.amount = this.person.allocatedAmount
    if (this.kind === 'allocate') this.lines = this.finance.participants.filter(p => ['pending', 'waitlisted'].includes(p.registrationStatus) && p.settlementStatus === 'pending' && p.fundingSource !== 'pass').map(p => ({ id: p.id, name: p.name, available: yuan(p.quotedAmount) + ' / ' + yuan(p.allocatedAmount), amount: '' }))
    if (this.kind === 'refund') this.lines = this.finance.allocations.filter(a => a.registrationId === this.person.id && a.entryType === 'allocate' && Number(a.refundableAmount) > 0).map(a => ({ id: a.allocationId, name: '分配 ' + a.createdAt, available: yuan(a.refundableAmount), amount: '' }))
    if (this.kind === 'verify') this.loadPasses()
    window.addEventListener('beforeunload', this.beforeUnload)
  },
  beforeDestroy() { this.passVersion++; window.removeEventListener('beforeunload', this.beforeUnload) },
  methods: {
    yuan,
    beforeUnload(event) { if (this.busy || this.uncertain) { event.preventDefault(); event.returnValue = '' } },
    async close() {
      if (this.busy) return
      if (this.uncertain) {
        try { await this.$confirm('操作结果尚未确认。关闭会丢失本次原请求重试入口；请优先重试确认。确需关闭时，下次必须先核对历史，不能重新登记同一事实。', '结果待确认', { type: 'warning', confirmButtonText: '保留并继续重试', cancelButtonText: '返回窗口' }) } catch (_) { /* Keep the immutable request available. */ }
        return
      }
      this.$emit('close', !!this.submitted)
    },
    async loadPasses() {
      const version = ++this.passVersion
      this.cards = []; this.form.accountId = ''; this.loadingPasses = true; this.passError = ''
      try { const res = await getParticipantPasses(this.person.id, this.form.scope); if (version === this.passVersion) this.cards = res.data || [] } catch (error) { if (version === this.passVersion) this.passError = error.message + '；请核对卡次查看权限与所选使用范围。' } finally { if (version === this.passVersion) this.loadingPasses = false }
    },
    body() {
      const f = this.form; const operationId = uuid(); const kind = this.kind
      if (kind === 'capture') {
        if (!f.actualConfirmed) throw new Error('请先核实实际到账')
        return { requestId: operationId, captureOnly: true, receivedConfirmed: true, amount: money(f.amount), paymentMethod: required(f.method, '到账方式'), evidence: required(f.evidence, '实际发生依据'), receiptReference: f.reference.trim() || null, attachments: f.attachments }
      }
      const body = { operationId, expectedRevision: this.context.request ? this.context.request.revision : this.person.revision, reason: required(f.reason || (kind === 'verify' && f.basis), '操作原因') }
      if (kind === 'allocate' || kind === 'refund') {
        const lines = this.lines.filter(l => String(l.amount).trim() && Number(l.amount) !== 0).map(l => ({ [kind === 'allocate' ? 'registrationId' : 'allocationId']: l.id, amount: money(l.amount) }))
        if (!lines.length) throw new Error('请至少填写一条正金额')
        if (kind === 'allocate') return { ...body, expectedRevision: this.finance.revision, receiptId: this.context.receipt.id, expectedReceiptRevision: this.context.receipt.revision, lines }
        // Sum submitted choices in integer cents; refundable limits remain exclusively server-owned.
        const total = lines.reduce((sum, l) => sum + Math.round(Number(l.amount) * 100), 0)
        return { ...body, requestedAmount: (total / 100).toFixed(2), allocations: lines }
      }
      if (kind === 'cash') return { ...body, mode: 'participant', settlementType: 'money', finalAmount: money(f.amount), allocationIds: this.finance.allocations.filter(a => a.registrationId === this.person.id && a.entryType === 'allocate' && !a.settlementId).map(a => a.allocationId), differenceConfirmed: f.differenceConfirmed, differenceReason: f.differenceConfirmed ? required(f.differenceReason, '差额依据') : null }
      if (kind === 'payment') {
        if (!f.actualConfirmed) throw new Error('请先核实已实际线下退款')
        return { ...body, actualAmount: money(f.amount), paymentMethod: required(f.method, '退款方式'), externalReference: f.reference.trim() || null, evidence: required(f.evidence, '实际发生依据'), occurredAt: required(f.occurredAt, '实际退款时间') }
      }
      if (kind === 'refundCancel' || kind === 'refundFail') {
        if (!f.noActualPaymentConfirmed) throw new Error('请先核实没有任何实际退款')
        return { ...body, noActualPaymentConfirmed: true }
      }
      if (kind === 'verify') {
        if (!f.verified) throw new Error('请确认已完成运营核实')
        const verification = { ...body }
        delete verification.reason
        return { ...verification, passAccountId: required(f.accountId, '卡次账户'), passUnits: f.units, scope: f.scope, basis: required(f.basis, '核实依据') }
      }
      if (kind === 'pass') return { ...body, mode: 'participant', settlementType: 'pass', verificationId: this.context.verification.verificationId, passAccountId: this.context.verification.passAccountId, passUnits: this.context.verification.passUnits }
      if (kind === 'returnPass') return { ...body, mode: 'participant' }
      return body
    },
    async submit() {
      if (this.busy || this.saved || this.submitted && !this.uncertain) return
      this.busy = true
      if (!this.submitted) {
        let body
        try { body = this.body() } catch (error) { this.error = error.message; this.guidance = '请补全输入后再确认。'; this.busy = false; return }
        try { await this.$confirm(this.title + '？' + this.instruction, '请核对敏感操作', { type: 'warning', confirmButtonText: '确认执行', cancelButtonText: '返回检查' }) } catch (_) { this.busy = false; return }
        this.submitted = JSON.parse(JSON.stringify(body))
      }
      this.error = ''; this.guidance = ''
      const target = this.context.request && this.context.request.requestId || this.context.verification && this.context.verification.verificationId || this.context.use && this.context.use.settlementId
      try {
        const res = await writeParticipantFinance(this.kind, this.kind === 'allocate' ? this.context.batchId : this.context.registrationId || this.person.id, target, this.submitted)
        this.saved = true; this.uncertain = false; this.reconciliation = !!(res.data && res.data.reconciliationRequired)
        this.$emit('saved', res.data)
      } catch (error) { this.error = error.message || '操作未完成'; this.uncertain = !!error.uncertain; this.guidance = recovery(error) } finally { this.busy = false }
    }
  }
}
</script>

<style scoped>
.operation-context { margin-top: 0; color: #234b40; font-weight: 600; overflow-wrap: anywhere; }
.operation-context span, .muted, .operation-key { font-weight: normal; color: #64726d; font-size: 13px; overflow-wrap: anywhere; }
.operation-form { margin-top: 20px; }
.operation-form .el-form-item { margin-bottom: 18px; }
.form-pair { display: flex; gap: 24px; }
.form-pair > * { flex: 1; min-width: 0; }
.feedback { margin-top: 12px; }
.error-text { color: #ae3d35; }
.operation-key { margin-top: 18px; }
</style>
