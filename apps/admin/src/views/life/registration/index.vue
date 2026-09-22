<template>
  <div class="app-container">
    <el-form ref="queryForm" :model="queryParams" :inline="true" v-show="showSearch" label-width="72px">
      <el-form-item label="轻友" prop="nickname"><el-input v-model="queryParams.nickname" placeholder="称呼/姓名/编号" clearable size="small" @keyup.enter.native="handleQuery" /></el-form-item>
      <el-form-item label="期次" prop="sessionNumber"><el-input-number v-model="queryParams.sessionNumber" :min="1" :controls="false" placeholder="期次编号" size="small" /></el-form-item>
      <el-form-item label="报名状态" prop="registrationStatus"><el-select v-model="queryParams.registrationStatus" placeholder="全部" clearable size="small"><el-option v-for="item in registrationStatusOptions" :key="item.value" :label="item.label" :value="item.value" /></el-select></el-form-item>
      <el-form-item label="付款状态" prop="paymentStatus"><el-select v-model="queryParams.paymentStatus" placeholder="全部" clearable size="small"><el-option label="未付款" value="unpaid" /><el-option label="已付款" value="paid" /></el-select></el-form-item>
      <el-form-item><el-button type="primary" icon="el-icon-search" size="mini" @click="handleQuery">搜索</el-button><el-button icon="el-icon-refresh" size="mini" @click="resetQuery">重置</el-button></el-form-item>
    </el-form>
    <el-row :gutter="10" class="mb8"><el-col :span="1.5"><el-button type="primary" plain icon="el-icon-plus" size="mini" @click="handleAdd" v-hasPermi="['life:registration:add']">新增报名</el-button></el-col><right-toolbar :showSearch.sync="showSearch" @queryTable="getList" /></el-row>
    <el-table v-loading="loading" :data="registrationList">
      <el-table-column label="订单/购买人" min-width="160"><template slot-scope="scope"><div>{{ scope.row.orderNo || '后台单人登记' }}</div><div class="muted">{{ scope.row.buyerNickname || scope.row.nickname }} · {{ scope.row.participantCount || 1 }}人</div></template></el-table-column>
      <el-table-column label="实际参与人" min-width="130"><template slot-scope="scope"><div>{{ scope.row.nickname }}</div><div class="muted">{{ scope.row.customerNo }}</div></template></el-table-column>
      <el-table-column label="期次" min-width="170"><template slot-scope="scope"><div>第 {{ scope.row.sessionNumber }} 期</div><div class="muted">{{ scope.row.sessionName }}</div></template></el-table-column>
      <el-table-column label="报名状态" width="100"><template slot-scope="scope"><el-tag size="mini" :type="registrationStatusType(scope.row.registrationStatus)">{{ registrationStatusText(scope.row.registrationStatus) }}</el-tag></template></el-table-column>
      <el-table-column label="人工付款" width="116"><template slot-scope="scope"><el-button size="mini" :type="paymentStatus(scope.row) === 'paid' ? 'success' : 'warning'" class="payment-status-button" @click="handlePaymentClick(scope.row)" v-hasPermi="['life:registration:payment']">{{ paymentStatusText(scope.row) }}</el-button><span v-if="!hasPaymentPermission" :class="paymentStatus(scope.row) === 'paid' ? 'paid-text' : 'unpaid-text'">{{ paymentStatusText(scope.row) }}</span></template></el-table-column>
      <el-table-column label="应付金额" width="100"><template slot-scope="scope">¥{{ Number(scope.row.payableAmount == null ? scope.row.standardPrice : scope.row.payableAmount).toFixed(2) }}</template></el-table-column>
      <el-table-column label="报名来源" prop="registrationSource" width="110" />
      <el-table-column label="报名时间" prop="registeredAt" width="160" />
      <el-table-column label="备注" prop="remark" min-width="150" show-overflow-tooltip />
      <el-table-column label="操作" width="160" fixed="right"><template slot-scope="scope"><el-button size="mini" type="text" icon="el-icon-edit" @click="handleUpdate(scope.row)" v-hasPermi="['life:registration:edit']">编辑</el-button><el-button v-if="scope.row.batchId && scope.row.registrationStatus !== 'cancelled'" size="mini" type="text" @click="handleBatchCancel(scope.row)" v-hasPermi="['life:registration:edit']">取消整单</el-button></template></el-table-column>
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

    <el-dialog title="确认线下付款" :visible.sync="paymentOpen" width="480px" append-to-body>
      <el-alert title="这里只登记线下收款结果，不发起线上支付。" type="warning" :closable="false" show-icon class="mb20" />
      <el-form ref="paymentForm" :model="paymentForm" :rules="paymentRules" label-width="90px">
        <el-form-item label="轻友"><span>{{ paymentRow.nickname }}</span></el-form-item>
        <el-form-item label="实付金额" prop="amount"><el-input-number v-model="paymentForm.amount" :min="0" :precision="2" style="width:100%" /></el-form-item>
        <el-form-item label="付款方式" prop="paymentMethod"><el-select v-model="paymentForm.paymentMethod" style="width:100%"><el-option label="微信扫码" value="wechat_scan" /><el-option label="支付宝扫码" value="alipay_scan" /><el-option label="银行转账" value="transfer" /><el-option label="现金" value="cash" /><el-option label="其他" value="other" /></el-select></el-form-item>
        <el-form-item label="备注"><el-input v-model="paymentForm.changeReason" placeholder="选填，如收款日期或凭证说明" /></el-form-item>
      </el-form>
      <div slot="footer" class="dialog-footer"><el-button type="primary" :loading="submitting" @click="confirmPayment">确认已付款</el-button><el-button @click="paymentOpen=false">取消</el-button></div>
    </el-dialog>
  </div>
</template>

<script>
import { listRegistration, getRegistration, addRegistration, updateRegistration, changePayment, changeBatchPayment, cancelBatchRegistration } from '@/api/life/registration'
import { listCustomer } from '@/api/life/customer'
import { listSession } from '@/api/life/session'
import { checkPermi } from '@/utils/permission'

export default {
  name: 'QlRegistration',
  data() {
    return {
      loading: false, submitting: false, showSearch: true, total: 0, registrationList: [], open: false, paymentOpen: false, title: '', customerOptions: [], sessionOptions: [], paymentRow: {},
      registrationStatusOptions: [{ label: '待确认', value: 'pending' }, { label: '已确认', value: 'confirmed' }, { label: '候补', value: 'waitlisted' }, { label: '已取消', value: 'cancelled' }],
      queryParams: { pageNum: 1, pageSize: 10, nickname: undefined, sessionNumber: undefined, registrationStatus: undefined, paymentStatus: undefined },
      form: {}, paymentForm: {},
      rules: { customerId: [{ required: true, message: '请选择轻友', trigger: 'change' }], sessionId: [{ required: true, message: '请选择期次', trigger: 'change' }] },
      paymentRules: { amount: [{ required: true, message: '请输入实付金额', trigger: 'change' }], paymentMethod: [{ required: true, message: '请选择付款方式', trigger: 'change' }] }
    }
  },
  created() { this.applyRoute(); this.getList(); this.loadOptions() },
  computed: {
    hasPaymentPermission() { return checkPermi(['life:registration:payment']) }
  },
  watch: { '$route.query': { handler() { if(this.$route.path === '/activityOperations/registration') { this.applyRoute(); this.getList() } } } },
  methods: {
    applyRoute() { this.queryParams.nickname = this.$route.query.nickname || undefined; this.queryParams.sessionNumber = this.$route.query.sessionNumber ? Number(this.$route.query.sessionNumber) : undefined; this.queryParams.pageNum = 1 },
    getList() {
      const requestId = this.listRequestId = (this.listRequestId || 0) + 1
      this.loading = true
      return listRegistration({ ...this.queryParams }).then(res => {
        if (requestId !== this.listRequestId) return
        this.registrationList = res.rows || []; this.total = res.total || 0
      }).catch(() => {
        if (requestId !== this.listRequestId) return
        this.registrationList = []; this.total = 0
      }).finally(() => { if (requestId === this.listRequestId) this.loading = false })
    },
    loadOptions() { listCustomer({ pageNum: 1, pageSize: 1000, status: 'active' }).then(res => { this.customerOptions = res.rows }); listSession({ pageNum: 1, pageSize: 1000 }).then(res => { this.sessionOptions = res.rows }) },
    handleBatchCancel(row) {
      this.$prompt('将取消该订单全部参与人的报名；已付款或已有到场记录时不可取消。请填写原因。', '取消整单报名', {
        inputValidator: value => !!(value && value.trim() && value.trim().length <= 500) || '请填写500字以内的原因',
        confirmButtonText: '确认取消整单', cancelButtonText: '返回'
      }).then(({ value }) => cancelBatchRegistration(row.batchId, value.trim())).then(() => { this.msgSuccess('整单已取消'); this.getList() }).catch(() => {})
    },
    handleQuery() { this.queryParams.pageNum = 1; this.getList() }, resetQuery() { this.resetForm('queryForm'); this.handleQuery() },
    reset() { this.form = { id: undefined, customerId: '', sessionId: '', registrationStatus: 'pending', registrationSource: 'web_admin', sessionReferrerCustomerId: '', remark: '' }; this.resetForm('form') },
    handleAdd() { this.reset(); this.title = '新增报名'; this.open = true },
    handleUpdate(row) { this.reset(); getRegistration(row.id).then(res => { this.form = res.data; this.title = '编辑报名'; this.open = true }) },
    submitForm() { this.$refs.form.validate(valid => { if (!valid) return; this.submitting = true; const request = this.form.id ? updateRegistration(this.form) : addRegistration(this.form); request.then(() => { this.msgSuccess(this.form.id ? '修改成功' : '报名成功'); this.open = false; this.getList() }).finally(() => { this.submitting = false }) }) },
    paymentStatus(row) { return row.batchPaymentStatus || row.paymentStatus },
    paymentStatusText(row) { return this.paymentStatus(row) === 'paid' ? '已付款' : '未付款' },
    paymentRequest(row, data) { return row.batchId ? changeBatchPayment(row.batchId, data) : changePayment(row.id, data) },
    handlePaymentClick(row) { if (this.paymentStatus(row) !== 'paid') { this.paymentRow = row; this.paymentForm = { paymentStatus: 'paid', amount: Number(row.payableAmount == null ? row.standardPrice : row.payableAmount), paymentMethod: 'wechat_scan', changeReason: '' }; this.paymentOpen = true; this.$nextTick(() => this.resetForm('paymentForm')) } else { this.$prompt('请输入撤销已付款的原因', '撤销付款登记', { inputValidator: value => !!(value && value.trim()) || '必须填写原因', confirmButtonText: '确认撤销', cancelButtonText: '取消', type: 'warning' }).then(({ value: reason }) => this.paymentRequest(row, { paymentStatus: 'unpaid', changeReason: reason })).then(() => { this.msgSuccess('已撤销付款登记'); this.getList() }).catch(() => {}) } },
    confirmPayment() { this.$refs.paymentForm.validate(valid => { if (!valid) return; this.submitting = true; this.paymentRequest(this.paymentRow, this.paymentForm).then(() => { this.msgSuccess('付款登记成功'); this.paymentOpen = false; this.getList() }).finally(() => { this.submitting = false }) }) },
    registrationStatusText(value) { const item = this.registrationStatusOptions.find(option => option.value === value); return item ? item.label : value },
    registrationStatusType(value) { return ({ confirmed: 'success', waitlisted: 'warning', cancelled: 'info', rescheduled: 'warning' })[value] || '' }
  }
}
</script>

<style scoped>
.muted { color: #909399; font-size: 12px; margin-top: 3px; }
.payment-status-button { min-width: 72px; }
.paid-text { color: #67c23a; }
.unpaid-text { color: #e6a23c; }
</style>
