<template>
  <div class="app-container">
    <el-form ref="queryForm" :model="queryParams" :inline="true" v-show="showSearch" label-width="72px">
      <el-form-item label="轻友" prop="nickname"><el-input v-model="queryParams.nickname" placeholder="称呼/姓名/编号" clearable size="small" @keyup.enter.native="handleQuery" /></el-form-item>
      <el-form-item label="期次" prop="sessionNumberInput">
        <el-autocomplete v-model="queryParams.sessionNumberInput" :fetch-suggestions="suggestSessions" placeholder="选择最近5期或输入期数数字" clearable size="small" :maxlength="10" style="width:270px" @input="queryParams.sessionNumber = undefined" @select="selectSessionFilter" @keyup.enter.native="handleQuery">
          <template slot-scope="{ item }"><span>第 {{ item.value }} 期 · {{ item.name }}</span></template>
        </el-autocomplete>
      </el-form-item>
      <el-form-item label="报名状态" prop="registrationStatus"><el-select v-model="queryParams.registrationStatus" placeholder="全部" clearable size="small"><el-option v-for="item in registrationStatusOptions" :key="item.value" :label="item.label" :value="item.value" /></el-select></el-form-item>
      <el-form-item label="付款状态" prop="paymentProgress"><el-select v-model="queryParams.paymentProgress" placeholder="全部" clearable size="small"><el-option label="待付款" value="pending" /><el-option label="已完成" value="completed" /><el-option label="已取消" value="cancelled" /></el-select></el-form-item>
      <el-form-item><el-button type="primary" icon="el-icon-search" size="mini" @click="handleQuery">搜索</el-button><el-button icon="el-icon-refresh" size="mini" @click="resetQuery">重置</el-button></el-form-item>
    </el-form>
    <el-row :gutter="10" class="mb8"><el-col :span="1.5"><el-button type="primary" plain icon="el-icon-plus" size="mini" @click="handleAdd" v-hasPermi="['life:registration:add']">新增报名</el-button></el-col><right-toolbar :showSearch.sync="showSearch" @queryTable="getList" /></el-row>
    <el-table v-loading="loading" :data="registrationList">
      <el-table-column label="订单/主要联系人" min-width="190"><template slot-scope="scope"><div>{{ scope.row.orderNo || '后台单人登记' }}</div><div class="muted">{{ scope.row.contactName || scope.row.buyerNickname || scope.row.nickname }}<span v-if="scope.row.contactPhone"> · {{ scope.row.contactPhone }}</span> · {{ scope.row.participantCount || 1 }}人</div></template></el-table-column>
      <el-table-column label="实际参与人" min-width="130"><template slot-scope="scope"><div>{{ scope.row.nickname }}</div><div class="muted">{{ scope.row.customerNo }}</div></template></el-table-column>
      <el-table-column label="期次" min-width="170"><template slot-scope="scope"><div>第 {{ scope.row.sessionNumber }} 期</div><div class="muted">{{ scope.row.sessionName }}</div></template></el-table-column>
      <el-table-column label="报名状态" width="100"><template slot-scope="scope"><el-tag size="mini" :type="registrationStatusType(scope.row.registrationStatus)">{{ registrationStatusText(scope.row.registrationStatus) }}</el-tag></template></el-table-column>
      <el-table-column label="付款状态" width="160"><template slot-scope="scope"><el-tag size="mini" :type="paymentProgressType(scope.row)">{{ paymentStatusText(scope.row) }}</el-tag><div class="muted">{{ paymentSummary(scope.row) }}</div></template></el-table-column>

      <el-table-column label="参考/付款" width="130"><template slot-scope="scope"><div class="muted">参考 ¥{{ Number(scope.row.quotedAmount == null ? scope.row.standardPrice : scope.row.quotedAmount).toFixed(2) }}</div><div>{{ paymentAmountText(scope.row) }}</div></template></el-table-column>
      <el-table-column label="本期推荐人" min-width="105"><template slot-scope="scope">{{ scope.row.referrerNickname || '—' }}</template></el-table-column>
      <el-table-column label="报名来源" prop="registrationSource" width="110" />
      <el-table-column label="报名时间" prop="registeredAt" width="160" />
      <el-table-column label="备注" prop="remark" min-width="150" show-overflow-tooltip />
      <el-table-column label="操作" width="190" fixed="right"><template slot-scope="scope">
        <el-button v-if="canConfirmRegistration(scope.row)" size="mini" type="text" @click="handleConfirmRegistration(scope.row)">确认报名</el-button>
        <el-button v-else-if="canConfirmPayment(scope.row)" size="mini" type="text" @click="handleConfirmPayment(scope.row)">确认付款</el-button>
        <el-dropdown v-if="rowActions(scope.row).length" trigger="click" @command="action => handleRowAction(action, scope.row)"><el-button type="text" size="mini">更多<i class="el-icon-arrow-down el-icon--right" /></el-button><el-dropdown-menu slot="dropdown"><el-dropdown-item v-for="action in rowActions(scope.row)" :key="action.key" :command="action.key">{{action.label}}</el-dropdown-item></el-dropdown-menu></el-dropdown>
        <span v-if="!canConfirmRegistration(scope.row) && !canConfirmPayment(scope.row) && !rowActions(scope.row).length" class="muted">—</span>
      </template></el-table-column>
    </el-table>
    <pagination v-show="total > 0" :total="total" :page.sync="queryParams.pageNum" :limit.sync="queryParams.pageSize" @pagination="getList" />

    <el-dialog :title="title" :visible.sync="open" width="620px" append-to-body>
      <el-form ref="form" :model="form" :rules="rules" label-width="92px">
        <el-form-item label="轻友" prop="customerId"><el-select v-model="form.customerId" filterable :disabled="!!form.id" placeholder="搜索并选择轻友" style="width:100%"><el-option v-for="item in customerOptions" :key="item.id" :label="item.nickname + (item.realName ? '（' + item.realName + '）' : '')" :value="item.id" /></el-select></el-form-item>
        <el-form-item label="期次" prop="sessionId"><el-select v-model="form.sessionId" filterable :disabled="!!form.id" placeholder="选择期次" style="width:100%"><el-option v-for="item in sessionOptions" :key="item.id" :label="'第 ' + item.sessionNumber + ' 期｜' + item.name" :value="item.id" /></el-select></el-form-item>
        <el-form-item label="报名状态"><el-select v-model="form.registrationStatus" style="width:100%"><el-option v-for="item in registrationStatusOptions" :key="item.value" :label="item.label" :value="item.value" /></el-select></el-form-item>
        <el-form-item label="期次推荐人"><el-select v-model="form.sessionReferrerCustomerId" filterable clearable placeholder="选填，不覆盖轻友首次推荐关系" style="width:100%"><el-option v-for="item in customerOptions" :key="item.id" :label="item.nickname" :value="item.id" /></el-select></el-form-item>
        <el-form-item label="备注"><el-input v-model="form.remark" type="textarea" :rows="3" placeholder="只记录报名相关信息" /></el-form-item>
      </el-form>
      <div slot="footer" class="dialog-footer"><el-button type="primary" :loading="submitting" @click="submitForm">确定</el-button><el-button @click="open=false">取消</el-button></div>
    </el-dialog>

    <el-dialog title="确认付款" :visible.sync="paymentOpen" width="520px" append-to-body :close-on-click-modal="!submitting">
      <el-alert title="请核对实际收款后填写金额与方式；卡次付款将扣减所选账户。" type="info" :closable="false" show-icon class="mb20" />
      <el-alert v-if="paymentRow.settlementStatus==='confirmed'" title="原金额已确认，按原金额登记付款；需调整金额时先撤销原金额确认。" type="warning" :closable="false" class="mb20" />
      <el-form ref="paymentForm" :model="paymentForm" :rules="paymentRules" label-width="96px">
        <el-form-item label="轻友"><span>{{paymentRow.contactName || paymentRow.buyerNickname || paymentRow.nickname}}</span></el-form-item>
        <el-form-item label="参考金额">¥{{ Number(paymentRow.quotedAmount == null ? paymentRow.standardPrice || 0 : paymentRow.quotedAmount).toFixed(2) }}</el-form-item>
        <el-form-item label="付款方式" prop="paymentMethod"><el-select v-model="paymentForm.paymentMethod" placeholder="请选择实际付款方式" style="width:100%" @change="handlePaymentMethodChange"><el-option label="微信" value="wechat_scan" /><el-option label="支付宝" value="alipay_scan" /><el-option label="转账" value="transfer" /><el-option label="现金" value="cash" /><el-option label="其他" value="other" /><el-option v-if="paymentRow.settlementStatus!=='confirmed'" label="消耗卡次" value="pass" /></el-select></el-form-item>
        <el-form-item v-if="paymentForm.paymentMethod!=='pass'" label="付款金额" prop="amount"><el-input-number v-model="paymentForm.amount" :min="0" :max="99999999.99" :precision="2" :disabled="paymentRow.settlementStatus==='confirmed'" style="width:100%" /></el-form-item>
        <el-form-item v-if="paymentForm.paymentMethod==='pass'" label="卡次账户" prop="passAccountId"><el-select v-model="paymentForm.passAccountId" :loading="loadingPasses" style="width:100%" placeholder="选择本次扣减账户"><el-option v-for="item in passAccounts" :key="item.id" :disabled="!Number(item.usable) || Number(item.balance) < Number(paymentForm.passUnits || 1)" :label="item.passType + ' · 剩余' + item.balance + '次'" :value="item.id" /></el-select></el-form-item>
        <el-form-item v-if="paymentForm.paymentMethod==='pass'" label="使用卡次" prop="passUnits"><el-input-number v-model="paymentForm.passUnits" :min="1" :precision="0" style="width:100%" /></el-form-item>
        <el-form-item label="备注"><el-input v-model="paymentForm.note" type="textarea" :rows="3" maxlength="500" /></el-form-item>
      </el-form>
      <div slot="footer"><el-button type="primary" :loading="submitting" :disabled="loadingPasses" @click="submitPaymentConfirmation">确认付款</el-button><el-button :disabled="submitting" @click="paymentOpen=false">取消</el-button></div>
    </el-dialog>

    <el-dialog :title="revokeRow.settlementType==='pass' ? '撤销卡次抵扣' : '撤销金额确认'" :visible.sync="revokeOpen" width="520px" append-to-body :close-on-click-modal="!revokeSubmitting">
      <el-alert v-if="revokeBlockedByPayment()" title="请先通过现有入口撤销收款登记，再撤销结算。" description="撤销收款登记不代表已向轻友实际退款。请先关闭此窗口，在人工付款栏撤销收款登记，再重新打开撤销结算。" type="warning" :closable="false" show-icon class="mb20" />
      <el-alert v-else title="此操作只撤销当前结算及其卡次消耗，不取消报名、不改变参与者、期次、报价或到场记录。" type="info" :closable="false" show-icon class="mb20" />
      <el-form ref="revokeForm" :model="revokeForm" :rules="revokeRules" label-width="104px">
        <el-form-item label="操作对象"><span>{{ revokeRow.batchId ? (revokeRow.orderNo || '报名订单') + ' · 整单' + (revokeRow.participantCount || 1) + '人' : (revokeRow.nickname || '单人报名') }}</span></el-form-item>
        <el-form-item label="期次"><span>第 {{ revokeRow.sessionNumber }} 期 · {{ revokeRow.sessionName }}</span></el-form-item>
        <el-form-item label="当前结算"><span>{{ revokeRow.settlementType === 'pass' ? '卡次结算' : '现金结算' }} · ¥{{ Number(revokeRow.finalAmount || 0).toFixed(2) }}</span></el-form-item>
        <el-form-item v-if="revokeRow.settlementType === 'pass'" label="将退回"><span>{{ revokeRow.passUnits }} 次至原账户 {{ revokePassAccountLabel }}</span></el-form-item>
        <el-form-item label="撤销原因" prop="reason"><el-input v-model="revokeForm.reason" type="textarea" :rows="3" maxlength="500" placeholder="必填，最多500字" :disabled="revokeBlockedByPayment()" /></el-form-item>
      </el-form>
      <div slot="footer" class="dialog-footer"><el-button type="danger" :loading="revokeSubmitting" :disabled="revokeBlockedByPayment()" @click="submitRevokeSettlement">确认撤销结算</el-button><el-button @click="revokeOpen=false">关闭</el-button></div>
    </el-dialog>

  </div>
</template>

<script>
import { listRegistration, getRegistration, addRegistration, updateRegistration, confirmRegistrationPayment, revokeSettlement, changePayment, changeBatchPayment, cancelBatchRegistration } from '@/api/life/registration'
import { checkPermi } from '@/utils/permission'
import { selectCustomers, selectSessions, selectRegistrationPasses } from '@/api/life/selection'

export default {
  name: 'QlRegistration',
  data() {
    return {
      loading: false, submitting: false, revokeSubmitting: false, showSearch: true, total: 0, registrationList: [], open: false, paymentOpen: false, loadingPasses: false, revokeOpen: false, title: '', customerOptions: [], sessionOptions: [], recentSessionOptions: [], passAccounts: [], paymentRow: {}, revokeRow: {}, revokeForm: {}, revokePassAccountLabel: '',
      registrationStatusOptions: [{ label: '待确认', value: 'pending' }, { label: '已确认', value: 'confirmed' }, { label: '候补', value: 'waitlisted' }, { label: '已取消', value: 'cancelled' }],
      queryParams: { pageNum: 1, pageSize: 10, nickname: undefined, sessionNumber: undefined, sessionNumberInput: '', registrationStatus: undefined, paymentProgress: undefined },
      form: {}, paymentForm: {},
      rules: { customerId: [{ required: true, message: '请选择轻友', trigger: 'change' }], sessionId: [{ required: true, message: '请选择期次', trigger: 'change' }] },

      paymentRules: { amount: [{ required: true, message: '请填写付款金额', trigger: 'change' }], paymentMethod: [{ required: true, message: '请选择实际付款方式', trigger: 'change' }], passAccountId: [{ required: true, message: '请选择卡次账户', trigger: 'change' }], passUnits: [{ required: true, message: '请填写使用卡次', trigger: 'change' }] },
      revokeRules: { reason: [{ required: true, message: '请填写撤销原因', trigger: 'blur' }, { max: 500, message: '撤销原因最多500字', trigger: 'blur' }] }
    }
  },
  created() { this.applyRoute(); this.getList(); this.loadOptions() },
  computed: {
    hasEditPermission() { return checkPermi(['life:registration:edit']) },
    hasPaymentPermission() { return checkPermi(['life:registration:payment']) },
    hasSettlementRevokePermission() { return checkPermi(['life:registration:settlement:revoke']) }
  },
  watch: { '$route.query': { handler() { if(this.$route.path === '/activityOperations/registration') { this.applyRoute(); this.getList() } } } },
  methods: {
    suggestSessions(input, callback) {
      callback(this.recentSessionOptions.filter(item => !input || String(item.sessionNumber).includes(input)).map(item => ({ value: String(item.sessionNumber), name: item.name })))
    },
    selectSessionFilter(item) {
      this.queryParams.sessionNumberInput = item.value
      this.queryParams.sessionNumber = Number(item.value)
      this.handleQuery()
    },
    applyRoute() { this.queryParams.nickname = this.$route.query.nickname || undefined; this.queryParams.sessionNumber = this.$route.query.sessionNumber ? Number(this.$route.query.sessionNumber) : undefined; this.queryParams.sessionNumberInput = this.queryParams.sessionNumber ? String(this.queryParams.sessionNumber) : ''; this.queryParams.pageNum = 1 },
    getList() {
      const requestId = this.listRequestId = (this.listRequestId || 0) + 1
      this.loading = true
      const { sessionNumberInput, ...query } = this.queryParams
      if (query.sessionNumber == null && sessionNumberInput) query.sessionNumberKeyword = sessionNumberInput.trim()
      return listRegistration(query).then(res => {
        if (requestId !== this.listRequestId) return
        this.registrationList = res.rows || []; this.total = res.total || 0
      }).catch(() => {
        if (requestId !== this.listRequestId) return
        this.registrationList = []; this.total = 0
      }).finally(() => { if (requestId === this.listRequestId) this.loading = false })
    },
    loadOptions() { selectCustomers().then(res => { this.customerOptions = res.data || [] }); selectSessions().then(res => { this.sessionOptions = res.data || []; this.recentSessionOptions = this.sessionOptions.slice(0,5) }).catch(() => { this.recentSessionOptions = [] }) },
    handleBatchCancel(row) {
      this.$prompt('将取消该订单全部参与人的报名；已付款或已有到场记录时不可取消。请填写原因。', '取消整单报名', {
        inputValidator: value => !!(value && value.trim() && value.trim().length <= 500) || '请填写500字以内的原因',
        confirmButtonText: '确认取消整单', cancelButtonText: '返回'
      }).then(({ value }) => cancelBatchRegistration(row.batchId, value.trim())).then(() => { this.msgSuccess('整单已取消'); this.getList() }).catch(() => {})
    },
    handleQuery() { if (this.queryParams.sessionNumberInput && !/^[0-9]{1,10}$/.test(this.queryParams.sessionNumberInput.trim())) return this.msgError('请输入最多10位期数数字'); this.queryParams.pageNum = 1; this.getList() },
    resetQuery() { this.resetForm('queryForm'); this.queryParams.sessionNumber = undefined; this.handleQuery() },
    reset() { this.form = { id: undefined, customerId: '', sessionId: '', registrationStatus: 'pending', registrationSource: 'web_admin', sessionReferrerCustomerId: '', remark: '' }; this.resetForm('form') },
    handleAdd() { this.reset(); this.title = '新增报名'; this.open = true },
    handleUpdate(row) { this.reset(); getRegistration(row.id).then(res => { this.form = res.data; this.title = '编辑报名'; this.open = true }) },
    submitForm() { this.$refs.form.validate(valid => { if (!valid) return; this.submitting = true; const request = this.form.id ? updateRegistration(this.form) : addRegistration(this.form); request.then(() => { this.msgSuccess(this.form.id ? '修改成功' : '报名成功'); this.open = false; this.getList() }).finally(() => { this.submitting = false }) }) },
    paymentStatus(row) { return row.batchPaymentStatus || row.paymentStatus },
    paymentStatusText(row) { if (row.registrationStatus === 'cancelled') return '已取消'; if (this.paymentStatus(row) === 'paid') return '已付款'; if (this.passCompleted(row)) return '已抵扣'; return row.registrationStatus !== 'confirmed' || row.batchReadyForPayment === false ? '待报名确认' : '待付款' },
    passCompleted(row) { return row.settlementStatus === 'confirmed' && row.settlementType === 'pass' },
    paymentProgressType(row) { return row.registrationStatus === 'cancelled' ? 'info' : this.paymentStatus(row) === 'paid' || this.passCompleted(row) ? 'success' : 'warning' },
    paymentSummary(row) { return this.passCompleted(row) ? '卡次 · ' + row.passUnits + '次' : row.settlementStatus === 'confirmed' ? '现金金额已核对' : '' },
    paymentAmountText(row) { if (this.passCompleted(row)) return '抵扣 ' + row.passUnits + ' 次'; if (row.settlementStatus === 'confirmed') return (this.paymentStatus(row) === 'paid' ? '付款 ¥' : '待付 ¥') + Number(row.finalAmount || 0).toFixed(2); return '待确认' },
    canConfirmRegistration(row) { return this.hasEditPermission && ['pending', 'waitlisted'].includes(row.registrationStatus) && this.paymentStatus(row) !== 'paid' && row.settlementStatus !== 'confirmed' },
    canConfirmPayment(row) { return this.hasPaymentPermission && row.registrationStatus === 'confirmed' && row.batchReadyForPayment !== false && this.paymentStatus(row) !== 'paid' && !this.passCompleted(row) },
    rowActions(row) {
      if (row.registrationStatus === 'cancelled') return []
      const actions = [], unpaid = this.paymentStatus(row) !== 'paid'
      if (this.hasEditPermission && unpaid && row.settlementStatus !== 'confirmed') { actions.push({key:'edit',label:'编辑报名'}); actions.push({key:'cancel',label:row.batchId ? '取消整单' : '取消报名'}) }
      if (this.hasPaymentPermission && !unpaid) actions.push({key:'revokePayment',label:'撤销付款登记'})
      if (this.hasSettlementRevokePermission && unpaid && row.settlementStatus === 'confirmed' && row.currentSettlementId) actions.push({key:'revokeSettlement',label:row.settlementType === 'pass' ? '撤销卡次抵扣' : '撤销金额确认'})
      return actions
    },
    handleRowAction(action, row) { if (action === 'edit') this.handleUpdate(row); else if (action === 'cancel') this.handleCancel(row); else if (action === 'revokePayment') this.revokePayment(row); else if (action === 'revokeSettlement') this.handleRevokeSettlement(row) },
    handleConfirmRegistration(row) { this.$confirm('确认 ' + row.nickname + ' 的报名？', '确认报名', {type:'warning'}).then(() => updateRegistration({id:row.id,registrationStatus:'confirmed'})).then(() => { this.msgSuccess('报名已确认'); this.getList() }).catch(() => {}) },
    handleCancel(row) { if (row.batchId) return this.handleBatchCancel(row); this.$confirm('取消 ' + row.nickname + ' 的报名？', '取消报名', {type:'warning'}).then(() => updateRegistration({id:row.id,registrationStatus:'cancelled'})).then(() => { this.msgSuccess('报名已取消'); this.getList() }).catch(() => {}) },
    handleConfirmPayment(row) { if (!this.canConfirmPayment(row)) return; this.paymentRow = Object.assign({}, row); this.passAccounts = []; this.passRequestId = (this.passRequestId || 0) + 1; this.loadingPasses = false; this.paymentForm = { amount: Number(row.settlementStatus === 'confirmed' ? row.finalAmount : row.quotedAmount == null ? row.standardPrice || 0 : row.quotedAmount), paymentMethod: '', passUnits: 1, passAccountId: '', note: '' }; this.paymentOpen = true; this.$nextTick(() => this.$refs.paymentForm.clearValidate()) },


    revokeBlockedByPayment() { return !!this.revokeRow && this.paymentStatus(this.revokeRow) === 'paid' },
    handleRevokeSettlement(row) {
      if (!row.currentSettlementId) return this.msgError('本轮结算历史缺少可靠关联，无法安全撤销')
      this.revokeRow = Object.assign({}, row)
      this.revokeForm = { reason: '' }
      this.revokePassAccountLabel = row.passAccountId ? ('原账户ID：' + row.passAccountId) : '原卡次账户'
      this.revokeOpen = true
      if (row.settlementType === 'pass' && row.passAccountId) {
        selectRegistrationPasses(row.id).then(res => {
          const account = (res.data || []).find(item => item.id === row.passAccountId)
          if (account) this.revokePassAccountLabel = account.passType + '（原账户ID：' + account.id + (account.status === 'active' ? '' : '，已停用') + '）'
        }).catch(() => {})
      }
      this.$nextTick(() => this.resetForm('revokeForm'))
    },
    submitRevokeSettlement() {
      if (this.revokeBlockedByPayment() || this.revokeSubmitting) return
      this.$refs.revokeForm.validate(valid => {
        if (!valid) return
        this.revokeSubmitting = true
        revokeSettlement(this.revokeRow.id, this.revokeRow.currentSettlementId, { reason: this.revokeForm.reason.trim() })
          .then(() => {
            this.msgSuccess('金额或卡次确认已撤销，可重新确认付款')
            this.revokeOpen = false
            this.getList()
            if (this.open && this.form.id === this.revokeRow.id) {
              getRegistration(this.revokeRow.id).then(res => { this.form = res.data })
            }
          })
          .catch(() => {
            this.getList()
            if (this.open && this.form.id === this.revokeRow.id) {
              getRegistration(this.revokeRow.id).then(res => { this.form = res.data })
            }
          })
          .finally(() => { this.revokeSubmitting = false })
      })
    },
    selectDefaultPassAccount() { const account = this.passAccounts.find(item => Number(item.usable) === 1 && Number(item.balance) >= Number(this.paymentForm.passUnits || 1)); this.paymentForm.passAccountId = account ? account.id : '' },
    handlePaymentMethodChange(method) {
      const requestId = this.passRequestId = (this.passRequestId || 0) + 1
      this.loadingPasses = false; this.paymentForm.passAccountId = ''
      if (method === 'pass') {
        this.paymentForm.passUnits = 1; this.loadingPasses = true
        selectRegistrationPasses(this.paymentRow.id).then(res => { if (requestId !== this.passRequestId || this.paymentForm.paymentMethod !== 'pass') return; this.passAccounts = res.data || []; this.selectDefaultPassAccount() }).finally(() => { if (requestId === this.passRequestId) this.loadingPasses = false })
      }
      this.$nextTick(() => this.$refs.paymentForm.clearValidate())
    },
    submitPaymentConfirmation() { if (this.submitting || this.loadingPasses) return; this.$refs.paymentForm.validate(valid => { if (!valid) return; const pass = this.paymentForm.paymentMethod === 'pass'; const payload = { paymentMethod: this.paymentForm.paymentMethod, amount: pass ? 0 : this.paymentForm.amount, passAccountId: pass ? this.paymentForm.passAccountId : null, passUnits: pass ? this.paymentForm.passUnits : null, expectedSettlementId: this.paymentRow.currentSettlementId || null, note: this.paymentForm.note }; this.submitting = true; confirmRegistrationPayment(this.paymentRow.id, payload).then(() => { this.msgSuccess(pass ? '卡次已抵扣' : '付款已确认'); this.paymentOpen = false; this.getList() }).finally(() => { this.submitting = false }) }) },
    revokePayment(row) { this.$prompt('请输入撤销付款登记的原因', '撤销付款登记', {inputValidator: value => !!(value && value.trim()) || '必须填写原因',confirmButtonText:'确认撤销',cancelButtonText:'取消',type:'warning'}).then(({value}) => this.paymentRequest(row, {paymentStatus:'unpaid',changeReason:value.trim()})).then(() => {this.msgSuccess('付款登记已撤销');this.getList()}).catch(() => {}) },
    paymentRequest(row, data) { return row.batchId ? changeBatchPayment(row.batchId, data) : changePayment(row.id, data) },


    registrationStatusText(value) { const item = this.registrationStatusOptions.find(option => option.value === value); return item ? item.label : value },
    registrationStatusType(value) { return ({ confirmed: 'success', waitlisted: 'warning', cancelled: 'info', rescheduled: 'warning' })[value] || '' }
  }
}
</script>

<style scoped>
.muted { color: #909399; font-size: 12px; margin-top: 3px; }
.payment-status-button { min-width: 72px; }
.revoke-settlement { padding-top: 2px; }
.paid-text { color: #67c23a; }
.unpaid-text { color: #e6a23c; }
</style>
