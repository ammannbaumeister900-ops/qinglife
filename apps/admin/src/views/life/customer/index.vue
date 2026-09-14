<template>
  <div class="app-container">
    <el-form ref="queryForm" :model="queryParams" :inline="true" v-show="showSearch" label-width="68px">
      <el-form-item label="轻友" prop="nickname">
        <el-input v-model="queryParams.nickname" placeholder="称呼/姓名/编号/微信昵称" clearable size="small" @keyup.enter.native="handleQuery" />
      </el-form-item>
      <el-form-item label="状态" prop="status">
        <el-select v-model="queryParams.status" placeholder="全部" clearable size="small">
          <el-option label="正常" value="active" />
          <el-option label="停用" value="inactive" />
        </el-select>
      </el-form-item>
      <el-form-item label="手机号" prop="phone">
        <el-input v-model.trim="queryParams.phone" inputmode="numeric" maxlength="11" placeholder="11位手机号精准搜索" clearable size="small" @keyup.enter.native="handleQuery" />
      </el-form-item>
      <el-form-item>
        <el-button type="primary" icon="el-icon-search" size="mini" @click="handleQuery">搜索</el-button>
        <el-button icon="el-icon-refresh" size="mini" @click="resetQuery">重置</el-button>
      </el-form-item>
    </el-form>

    <el-row :gutter="10" class="mb8">
      <el-col :span="1.5">
        <el-button type="primary" plain icon="el-icon-plus" size="mini" @click="handleAdd" v-hasPermi="['life:customer:add']">新增轻友</el-button>
      </el-col>
      <right-toolbar :showSearch.sync="showSearch" @queryTable="getList" />
    </el-row>

    <el-table v-loading="loading" :data="customerList">
      <el-table-column label="轻友编号" prop="customerNo" min-width="160" show-overflow-tooltip />
      <el-table-column label="称呼" prop="nickname" min-width="110" />
      <el-table-column label="小程序账号" min-width="150">
        <template slot-scope="scope">
          <div v-if="scope.row.wechatNickname" class="account-cell">
            <el-image :src="scope.row.wechatAvatar" fit="cover" class="account-avatar" />
            <span>{{ scope.row.wechatNickname }}</span>
          </div>
          <span v-else class="muted">未绑定</span>
        </template>
      </el-table-column>
      <el-table-column label="账号状态" min-width="85">
        <template slot-scope="scope">
          <el-tag size="mini" :type="accountStatusType(scope.row.accountStatus)">{{ accountStatusText(scope.row.accountStatus) }}</el-tag>
        </template>
      </el-table-column>
      <el-table-column label="最后登录" prop="lastLoginTime" width="160">
        <template slot-scope="scope">{{ scope.row.lastLoginTime || '-' }}</template>
      </el-table-column>
      <el-table-column label="手机号" prop="phoneHint" width="120"><template slot-scope="scope">{{ scope.row.phoneHint || '-' }}</template></el-table-column>
      <el-table-column label="身份证" prop="idCardHint" width="140"><template slot-scope="scope">{{ scope.row.idCardHint || '-' }}</template></el-table-column>
      <el-table-column label="真实姓名" prop="realName" min-width="100" />
      <el-table-column label="性别" min-width="70">
        <template slot-scope="scope">{{ genderText(scope.row.gender) }}</template>
      </el-table-column>
      <el-table-column label="城市" prop="city" min-width="100" />
      <el-table-column label="首次来源" prop="firstSource" min-width="110" />
      <el-table-column label="可信度" min-width="90">
        <template slot-scope="scope">
          <el-tag size="mini" :type="scope.row.dataConfidence === 'verified' ? 'success' : 'info'">{{ confidenceText(scope.row.dataConfidence) }}</el-tag>
        </template>
      </el-table-column>
      <el-table-column label="状态" min-width="80">
        <template slot-scope="scope"><el-tag size="mini" :type="scope.row.status === 'active' ? 'success' : 'info'">{{ scope.row.status === 'active' ? '正常' : '停用' }}</el-tag></template>
      </el-table-column>
      <el-table-column label="更新时间" prop="updatedAt" width="160" />
      <el-table-column label="操作" width="150" fixed="right">
        <template slot-scope="scope">
          <el-button size="mini" type="text" icon="el-icon-time" @click="handleTimeline(scope.row)" v-hasPermi="['life:customer:query']">详情</el-button>
          <el-button size="mini" type="text" icon="el-icon-edit" @click="handleUpdate(scope.row)" v-hasPermi="['life:customer:edit']">编辑</el-button>
        </template>
      </el-table-column>
    </el-table>
    <pagination v-show="total > 0" :total="total" :page.sync="queryParams.pageNum" :limit.sync="queryParams.pageSize" @pagination="getList" />

    <customer-dossier ref="dossier" @edit="handleUpdate" />

    <el-dialog :title="title" :visible.sync="open" width="620px" append-to-body>
      <el-alert title="先录入称呼即可建档；小程序账号由关联关系自动同步，不在此处手工修改。" type="info" :closable="false" show-icon class="mb20" />
      <el-form ref="form" :model="form" :rules="rules" label-width="92px">
        <el-row :gutter="16">
          <el-col :span="12"><el-form-item label="称呼" prop="nickname"><el-input v-model="form.nickname" placeholder="必填，如：小雨" /></el-form-item></el-col>
          <el-col :span="12"><el-form-item label="真实姓名"><el-input v-model="form.realName" placeholder="选填" /></el-form-item></el-col>
          <el-col :span="12"><el-form-item label="性别"><el-select v-model="form.gender" style="width:100%"><el-option label="未知" value="unknown" /><el-option label="女" value="female" /><el-option label="男" value="male" /><el-option label="其他" value="other" /></el-select></el-form-item></el-col>
          <el-col :span="12"><el-form-item label="出生日期"><el-date-picker v-model="form.birthDate" value-format="yyyy-MM-dd" type="date" placeholder="选填" style="width:100%" /></el-form-item></el-col>
          <el-col :span="12"><el-form-item label="城市"><el-input v-model="form.city" placeholder="选填" /></el-form-item></el-col>
          <el-col :span="12"><el-form-item label="首次来源"><el-select v-model="form.firstSource" filterable allow-create style="width:100%"><el-option label="未知" value="unknown" /><el-option label="朋友推荐" value="referral" /><el-option label="线下活动" value="offline" /><el-option label="小程序" value="mini_program" /></el-select></el-form-item></el-col>
          <el-col :span="12"><el-form-item label="状态"><el-select v-model="form.status" style="width:100%"><el-option label="正常" value="active" /><el-option label="停用" value="inactive" /></el-select></el-form-item></el-col>
        </el-row>
        <template v-if="form.wechatNickname">
          <el-divider content-position="left">关联小程序账号</el-divider>
          <el-row :gutter="16">
            <el-col :span="12"><el-form-item label="微信昵称"><el-input :value="form.wechatNickname" disabled /></el-form-item></el-col>
            <el-col :span="12"><el-form-item label="账号状态"><el-input :value="accountStatusText(form.accountStatus)" disabled /></el-form-item></el-col>
            <el-col :span="24"><el-form-item label="最后登录"><el-input :value="form.lastLoginTime || '-'" disabled /></el-form-item></el-col>
          </el-row>
        </template>
      </el-form>
      <div slot="footer" class="dialog-footer"><el-button type="primary" :loading="submitting" @click="submitForm">确定</el-button><el-button @click="open=false">取消</el-button></div>
    </el-dialog>
  </div>
</template>

<script>
import CustomerDossier from './Dossier.vue'
import { listCustomer, getCustomer, getCustomerTimeline, addCustomer, updateCustomer } from '@/api/life/customer'

export default {
  name: 'QlCustomer',
  components: { CustomerDossier },
  data() {
    return {
      loading: false, submitting: false, showSearch: true, total: 0, customerList: [], open: false, title: '',
      timelineOpen: false, timelineLoading: false, timelineCustomer: {}, timelineRows: [],
      queryParams: { pageNum: 1, pageSize: 10, nickname: undefined, phone: undefined, status: undefined },
      form: {}, rules: { nickname: [{ required: true, message: '请输入称呼', trigger: 'blur' }] }
    }
  },
  created() { this.getList() },
  computed: {
    timelineTitle() {
      return this.timelineCustomer.nickname
        ? `${this.timelineCustomer.nickname} · 完整时间线`
        : '完整轻友详情时间线'
    }
  },
  methods: {
    getList() { this.loading = true; listCustomer(this.queryParams).then(res => { this.customerList = res.rows; this.total = res.total; this.loading = false }).catch(() => { this.loading = false }) },
    handleQuery() {
      const phone = (this.queryParams.phone || '').trim()
      if (phone && !/^1[3-9]\d{9}$/.test(phone)) return this.$message.warning('请输入完整的11位手机号进行精准搜索')
      this.queryParams.pageNum = 1; this.getList()
    },
    resetQuery() { this.resetForm('queryForm'); this.handleQuery() },
    reset() { this.form = { id: undefined, nickname: '', realName: '', birthDate: undefined, gender: 'unknown', city: '', firstSource: 'unknown', dataSource: 'manual_entry', dataConfidence: 'unknown', status: 'active', wechatNickname: null, wechatAvatar: null, accountStatus: 'unbound', lastLoginTime: null }; this.resetForm('form') },
    handleAdd() { this.reset(); this.title = '新增轻友'; this.open = true },
    handleUpdate(row) { this.reset(); getCustomer(row.id).then(res => { this.form = res.data; this.title = '编辑轻友档案'; this.open = true }) },
    handleTimeline(row) { this.$refs.dossier.open(row.id) },
    submitForm() { this.$refs.form.validate(valid => { if (!valid) return; this.submitting = true; const request = this.form.id ? updateCustomer(this.form) : addCustomer(this.form); request.then(() => { this.msgSuccess(this.form.id ? '修改成功' : '建档成功'); this.open = false; this.getList(); if (this.$refs.dossier.visible) this.$refs.dossier.load() }).finally(() => { this.submitting = false }) }) },
    genderText(value) { return ({ female: '女', male: '男', other: '其他', unknown: '未知' })[value] || '未知' },
    confidenceText(value) { return ({ verified: '已核实', probable: '较可信', unknown: '待核实' })[value] || '待核实' },
    accountStatusText(value) { return ({ enabled: '启用', disabled: '停用', unbound: '未绑定' })[value] || '未绑定' },
    accountStatusType(value) { return ({ enabled: 'success', disabled: 'danger', unbound: 'info' })[value] || 'info' },
    timelineColor(value) { return ({ payment_confirmed: '#67c23a', attendance: '#409eff', staff_interview: '#e6a23c', session_completed: '#909399' })[value] || '#b78369' },
    sourceText(value) { return ({ mini_program: '小程序', web_admin: '电脑后台', mobile_h5: '移动工作台', mobile_workspace: '移动工作台', operator_entry: '人工登记', system: '系统', legacy_app: '微信登录建档' })[value] || value || '未知' }
  }
}
</script>

<style scoped>
.account-cell { display: flex; align-items: center; gap: 8px; }
.account-avatar { width: 28px; height: 28px; border-radius: 50%; flex: none; }
.muted { color: #909399; }
.timeline-panel { padding: 0 24px 24px; }
.timeline-title { color: #303133; font-size: 16px; font-weight: 600; }
.timeline-session { margin-top: 8px; color: #606266; }
.timeline-detail { margin-top: 10px; white-space: pre-wrap; line-height: 1.65; }
.timeline-meta { margin-top: 10px; color: #909399; font-size: 12px; }
</style>
