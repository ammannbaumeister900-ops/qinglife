<template>
  <el-dialog :title="readonly ? '收款记录' : step === 'difference' ? '确认差额结清' : '确认报名'" :visible.sync="open" width="680px" append-to-body :close-on-click-modal="!submitting" :close-on-press-escape="!submitting" :show-close="!submitting" @close="invalidate" @closed="clearImages">
    <div v-loading="loading">
      <p>{{ row.orderNo || row.nickname || '报名' }} · 第 {{ row.sessionNumber }} 期 · 整单 {{ row.participantCount || 1 }} 人</p>
      <p v-if="row.participantCount > 1" class="receipt-hint">本操作处理同一订单的全部参与人，共用一笔收款，不按参与人重复记账。</p>
      <el-alert v-if="loadFailed" title="收款记录读取失败，请关闭后重新打开；暂不可提交。" type="error" :closable="false" />
      <template v-if="!readonly">
        <p class="receipt-hint">仅在核实实际到账后操作；名额充足且本单结清后确认报名。</p>
        <p v-if="pending.length" class="receipt-hint">{{ pending.length === 1 ? '继续核对已有到账，可修正信息或保存并暂缓，不重复记账。' : '存在多笔待处理收款，请人工核查，暂不可继续确认。' }}</p>
        <p>整单应收：<strong>{{ money(due) }}</strong></p>
        <template v-if="step === 'difference'">
          <p>实际到账 <strong>{{ money(form.amount) }}</strong> · 差额 <strong>{{ differenceText }}</strong></p>
          <el-input v-model="form.differenceReason" type="textarea" :rows="2" maxlength="200" show-word-limit placeholder="必填，简要说明差额原因" :disabled="submitting" />
          <el-checkbox v-model="form.finalSettlementConfirmed" class="receipt-ack" :disabled="submitting">差额已作为本单最终结算结果，无待付尾款；本笔不是部分付款</el-checkbox>
        </template>
        <!-- 金额控件在挂载时缓存 aria-disabled；读取完成后再挂载。 -->
        <el-form v-if="step === 'edit' && !loading && !loadFailed" ref="form" :key="viewToken" :model="form" :rules="rules" label-width="110px" :disabled="submitting">
          <el-form-item label="收款方式" prop="paymentMethod">
            <el-select v-model="form.paymentMethod" placeholder="选择实际收款方式" style="width:100%">
              <el-option v-for="(label, value) in methods" :key="value" :label="label" :value="value" />
            </el-select>
          </el-form-item>
          <el-form-item label="实际到账金额" prop="amount"><el-input-number v-model="form.amount" :min="0.01" :max="99999999.99" :precision="2" style="width:100%" /></el-form-item>
          <el-form-item label="款项性质"><el-radio-group v-model="form.partialPayment"><el-radio :label="false">本单最终结清</el-radio><el-radio :label="true">部分付款，尾款未结清</el-radio></el-radio-group></el-form-item>
          <el-form-item label="收款凭据编号" prop="receiptReference"><el-input v-model="form.receiptReference" maxlength="100" placeholder="选填，转账单号或收款凭据编号" /></el-form-item>
          <el-form-item label="备注" prop="evidence"><el-input v-model="form.evidence" type="textarea" :rows="2" maxlength="500" show-word-limit placeholder="选填，收款或核验相关备注" /></el-form-item>
          <el-form-item v-if="form.receiptId" label="修正原因"><el-input v-model="form.correctionReason" maxlength="200" placeholder="修改金额、方式、凭据、备注或款项性质时必填" /></el-form-item>
          <el-form-item label="图片附件"><receipt-images :key="imageKey" v-model="form.attachments" :readonly="!!form.receiptId" :saved="pending.length === 1 ? pending[0].attachments || [] : []" :registration-id="row.id" :disabled="submitting" @reading="imageReading = $event" /></el-form-item>
        </el-form>
        <el-checkbox v-if="step === 'edit'" v-model="form.receivedConfirmed" :disabled="submitting || loading || loadFailed || imageReading">我已核实上述款项实际到账</el-checkbox>
        <p v-if="error" class="receipt-error" role="alert">{{ error }}</p>
      </template>
      <el-table v-if="records.length && step === 'edit'" :data="records" size="small" class="receipt-records">
        <el-table-column label="收款" min-width="150"><template slot-scope="{ row: receipt }"><div>{{ methods[receipt.paymentMethod] || receipt.paymentMethod }}</div><div>{{ money(receipt.amount) }}</div><div>{{ receipt.receiptReference || '未填写凭据编号' }}</div><div v-if="receipt.expectedAmount != null">应收 {{ money(receipt.expectedAmount) }}</div></template></el-table-column>
        <el-table-column label="备注与处理结果" min-width="180"><template slot-scope="{ row: receipt }"><div>{{ receipt.evidence }}</div><strong>{{ statusText(receipt.status) }}</strong><div v-if="receipt.partialPayment">部分付款，尾款未结清</div><div v-if="receipt.differenceReason">差额原因：{{ receipt.differenceReason }}</div><div>{{ receipt.processingMessage }}</div><details v-if="receipt.revisions && receipt.revisions.length"><summary>核对与修正历史（{{ receipt.revisions.length }}）</summary><div v-for="revision in receipt.revisions" :key="revision.revision">{{ revision.reason }} · {{ recordedAtText(revision.changedAt) }} · 操作人 {{ revision.operatorId }}<div>{{ revisionText(revision) }}</div></div></details></template></el-table-column>
        <el-table-column label="图片附件" width="112"><template slot-scope="{ row: receipt }"><receipt-images :key="receipt.id" readonly :saved="receipt.attachments || []" :registration-id="row.id" /></template></el-table-column>
        <el-table-column label="记录人 / 时间" min-width="150"><template slot-scope="{ row: receipt }">{{ receipt.operatorId || '历史导入' }}<div>{{ recordedAtText(receipt.recordedAt) }}</div></template></el-table-column>
      </el-table>
      <p v-else-if="readonly && !loading && !loadFailed">暂无收款记录</p>
    </div>
    <div slot="footer">
      <template v-if="!readonly">
        <el-button v-if="step === 'difference'" :disabled="submitting" @click="back">返回修改</el-button>
        <el-button type="primary" :loading="submitting" :disabled="blocked || form.partialPayment || (step === 'difference' && (!form.finalSettlementConfirmed || !form.differenceReason.trim()))" @click="submit(false)">{{ step === 'difference' ? '按实收结清并确认报名' : pending.length ? '继续处理并确认报名' : '确认报名' }}</el-button>
        <el-button :disabled="blocked || submitting" @click="submit(true)">保存并暂缓</el-button>
      </template>
      <el-button v-else-if="pending.length === 1 && editableOrder" :disabled="loading || loadFailed" type="primary" @click="readonly=false">继续核对已有到账</el-button>
      <el-button :disabled="submitting" @click="open=false">{{ readonly ? '关闭' : '关闭，稍后处理' }}</el-button>
    </div>
  </el-dialog>
</template>

<script>
import { getRegistration, listOfflineReceipts, confirmOfflineReceipt } from '@/api/life/registration'
import { checkPermi } from '@/utils/permission'
import ReceiptImages from './ReceiptImages.vue'
export default {
  name: 'OfflineReceiptDialog',
  components: { ReceiptImages },
  data() {
    const required = message => [{ required: true, message, trigger: 'blur' }]
    return {
      open: false, loading: false, loadFailed: false, submitting: false, readonly: false,
      row: {}, records: [], form: {}, error: '', requestId: 0, viewToken: 0, step: 'edit', imageKey: 0, imageReading: false,
      methods: { wechat_scan: '微信转账 / 收款码', alipay_scan: '支付宝', transfer: '银行转账', cash: '现金', other: '其他线下收款' },
      rules: { paymentMethod: required('请选择实际收款方式'), amount: required('请填写实际到账金额') }
    }
  },
  computed: {
    pending() { return this.records.filter(r => r.status === 'pending_confirmation') },
    due() { return this.pending.length === 1 && this.pending[0].expectedAmount != null ? this.pending[0].expectedAmount : this.row.settlementStatus === 'confirmed' ? this.row.finalAmount : this.row.batchId ? this.row.quotedAmount : this.row.standardPrice },
    blocked() { return this.loading || this.loadFailed || !this.form.receivedConfirmed || this.pending.length > 1 || this.imageReading },
    editableOrder() { return checkPermi(['life:registration:edit']) && ['pending', 'waitlisted', 'confirmed'].includes(this.row.registrationStatus) && (this.row.batchPaymentStatus || this.row.paymentStatus) === 'unpaid' },
    differenceText() { const n = (Math.round(Number(this.form.amount) * 100) - Math.round(Number(this.due) * 100)) / 100; return (n < 0 ? '少收 ' : '多收 ') + this.money(Math.abs(n)) }
  },
  methods: {
    money(value) { return value == null ? '金额待核对' : '¥' + Number(value).toFixed(2) },
    recordedAtText(value) { return value ? String(value).replace('T', ' ').replace(/\.\d+$/, '') : '—' },
    revisionText(revision) {
      try { const before = JSON.parse(revision.beforeValue); const after = JSON.parse(revision.afterValue); return '原实收 ' + this.money(before.amount) + ' → ' + this.money(after.amount) + '；原备注：' + (before.remark || '—') + '；新备注：' + (after.remark || '—') } catch (e) { return '历史详情请人工核查' }
    },
    invalidate() { this.viewToken++; this.requestId++; this.loading = false; this.submitting = false },
    clearImages() { if (this.open) return; this.records = []; this.imageKey++; this.imageReading = false },
    newRequestId() {
      const bytes = new Uint8Array(16)
      window.crypto.getRandomValues(bytes)
      bytes[6] = (bytes[6] & 15) | 64; bytes[8] = (bytes[8] & 63) | 128
      const hex = Array.from(bytes, b => b.toString(16).padStart(2, '0')).join('')
      return [hex.slice(0, 8), hex.slice(8, 12), hex.slice(12, 16), hex.slice(16, 20), hex.slice(20)].join('-')
    },
    async show(row, readonly = false) {
      this.invalidate(); this.step = 'edit'
      this.row = { ...row }; this.readonly = readonly; this.error = ''; this.records = []
      this.imageKey++; this.imageReading = false
      this.form = { paymentMethod: '', amount: undefined, receiptReference: '', evidence: '', attachments: [], requestId: this.newRequestId(), receivedConfirmed: false, partialPayment: false, finalSettlementConfirmed: false, differenceReason: '', correctionReason: '' }
      this.open = true
      await this.refresh()
      this.$nextTick(() => { if (this.$refs.form) this.$refs.form.clearValidate() })
    },
    async refresh() {
      const requestId = ++this.requestId; const id = this.row.id
      this.loading = true; this.loadFailed = false
      try {
        const [detail, receipts] = await Promise.all([getRegistration(id), listOfflineReceipts(id)])
        if (requestId !== this.requestId || !this.open || id !== this.row.id) return
        this.row = detail.data; this.records = receipts.data || []
        if (this.pending.length === 1) {
          const r = this.pending[0]
          this.form = { paymentMethod: r.paymentMethod, amount: Number(r.amount), receiptReference: r.receiptReference || '', evidence: r.evidence || '', receiptId: r.id, expectedRevision: r.revision, requestId: r.requestId || undefined, attachments: [], receivedConfirmed: false, partialPayment: !!r.partialPayment, finalSettlementConfirmed: false, differenceReason: r.differenceReason || '', correctionReason: '' }
          this.error = r.processingMessage || ''
        } else if (this.form.amount == null && this.due != null) this.form.amount = Number(this.due)
      } catch (e) { if (requestId === this.requestId) this.loadFailed = true } finally { if (requestId === this.requestId) this.loading = false }
    },
    back() { this.step = 'edit'; this.form.finalSettlementConfirmed = false; this.error = '' },
    submit(defer) {
      if (this.submitting || this.blocked || !defer && this.form.partialPayment) return
      const token = this.viewToken; const id = this.row.id
      const validated = async valid => {
        if (!valid || this.submitting || token !== this.viewToken) return
        if (!defer && this.due == null) { this.error = '应收金额缺少可靠依据，请人工核查'; return }
        if (!defer && Math.round(Number(this.form.amount) * 100) !== Math.round(Number(this.due) * 100)) {
          if (this.step !== 'difference') { this.step = 'difference'; this.error = ''; return }
          if (!this.form.finalSettlementConfirmed || !this.form.differenceReason.trim()) return
        }
        this.submitting = true; this.error = ''
        const payload = { ...this.form, defer, finalSettlementConfirmed: !defer && this.step === 'difference' && this.form.finalSettlementConfirmed, receiptReference: this.form.receiptReference.trim(), evidence: this.form.evidence.trim(), differenceReason: this.form.differenceReason.trim(), correctionReason: this.form.correctionReason.trim() }
        try {
          await confirmOfflineReceipt(id, payload)
          if (token !== this.viewToken) return
          this.msgSuccess(defer ? '到账已保存，可稍后继续核对' : '到账已登记，整单报名已确认')
          this.open = false
        } catch (e) {
          if (token !== this.viewToken) return
          this.step = 'edit'; this.error = '未完成处理，请核对下方记录后继续；可修正或暂缓。'
          await this.refresh()
        } finally { if (token === this.viewToken) this.submitting = false; this.$emit('changed') }
      }
      if (this.step === 'edit') this.$refs.form.validate(validated)
      else validated(true)
    },
    statusText(status) {
      return ({ pending_confirmation: '到账待处理，报名未确认', paid: '已收款', refund_pending: '退款待处理', refunded: '已退款', cancelled: '登记已撤销', failed: '未完成' })[status] || status
    }
  }
}
</script>
<style scoped>
.receipt-error { color: #b34b32; line-height: 1.6; }
.receipt-hint { color: #606266; font-size: 13px; line-height: 1.6; }
.receipt-records { margin-top: 20px; }
.receipt-ack { display: block; margin-top: 16px; white-space: normal; line-height: 1.6; }
.receipt-ack /deep/ .el-checkbox__label { white-space: normal; vertical-align: top; }
</style>
