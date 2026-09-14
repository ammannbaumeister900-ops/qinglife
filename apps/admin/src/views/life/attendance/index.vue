<template>
  <div class="app-container">
    <el-form ref="queryForm" :model="queryParams" :inline="true" label-width="72px">
      <el-form-item label="轻友"><el-input v-model="queryParams.keyword" clearable size="small" placeholder="称呼/姓名/编号" @keyup.enter.native="handleQuery" /></el-form-item>
      <el-form-item label="期次"><el-input-number v-model="queryParams.sessionNumber" :controls="false" :min="1" size="small" /></el-form-item>
      <el-form-item label="签到状态"><el-select v-model="queryParams.attendanceStatus" clearable size="small" placeholder="全部"><el-option v-for="item in statusOptions" :key="item.value" :label="item.label" :value="item.value" /></el-select></el-form-item>
      <el-form-item><el-button type="primary" icon="el-icon-search" size="mini" @click="handleQuery">搜索</el-button><el-button icon="el-icon-refresh" size="mini" @click="resetQuery">重置</el-button></el-form-item>
    </el-form>
    <el-alert title="签到按每一期的每个活动日记录；后台修改会保留操作人和原因。" type="info" :closable="false" show-icon class="mb20" />
    <el-table v-loading="loading" :data="rows">
      <el-table-column label="轻友" min-width="150"><template slot-scope="scope"><div>{{ scope.row.nickname }}<span v-if="scope.row.realName">（{{ scope.row.realName }}）</span></div><div class="muted">{{ scope.row.customerNo }}</div></template></el-table-column>
      <el-table-column label="期次" min-width="180"><template slot-scope="scope"><div>第 {{ scope.row.sessionNumber }} 期</div><div class="muted">{{ scope.row.sessionName }}</div></template></el-table-column>
      <el-table-column label="活动日" width="150"><template slot-scope="scope">第{{ scope.row.dayNo }}天 · {{ scope.row.activityDate }}</template></el-table-column>
      <el-table-column label="主题" prop="theme" min-width="130" />
      <el-table-column label="状态" width="100"><template slot-scope="scope"><el-tag size="mini" :type="statusType(scope.row.attendanceStatus)">{{ statusText(scope.row.attendanceStatus) }}</el-tag></template></el-table-column>
      <el-table-column label="签到时间" prop="checkedInAt" width="165" />
      <el-table-column label="来源" prop="checkInSource" width="105" />
      <el-table-column label="操作" width="210" fixed="right"><template slot-scope="scope"><el-button type="text" size="mini" @click="setStatus(scope.row, 'checked_in')" v-hasPermi="['life:attendance:edit']">签到</el-button><el-button type="text" size="mini" @click="setStatus(scope.row, 'late')" v-hasPermi="['life:attendance:edit']">迟到</el-button><el-button type="text" size="mini" @click="setAbsent(scope.row)" v-hasPermi="['life:attendance:edit']">缺席</el-button><el-button type="text" size="mini" @click="showHistory(scope.row)">历史</el-button></template></el-table-column>
    </el-table>
    <el-dialog title="签到更正历史" :visible.sync="historyOpen" width="780px" append-to-body>
      <el-table :data="historyRows">
        <el-table-column label="时间" prop="changedAt" width="165" />
        <el-table-column label="变更" min-width="150"><template slot-scope="scope">{{ statusText(scope.row.fromStatus) }} → {{ statusText(scope.row.toStatus) }}</template></el-table-column>
        <el-table-column label="原原因" prop="previousReason" min-width="130" />
        <el-table-column label="本次原因" prop="changeReason" min-width="130" />
        <el-table-column label="操作人" prop="operatorName" width="100" />
      </el-table>
    </el-dialog>
    <pagination v-show="total > 0" :total="total" :page.sync="queryParams.pageNum" :limit.sync="queryParams.pageSize" @pagination="getList" />
  </div>
</template>

<script>
import { listAttendance, updateAttendanceStatus, attendanceHistory } from '@/api/life/attendance'

export default {
  name: 'QlAttendance',
  data() {
    return {
      loading: false, total: 0, rows: [], historyOpen: false, historyRows: [],
      queryParams: { pageNum: 1, pageSize: 10, keyword: undefined, sessionNumber: undefined, attendanceStatus: undefined },
      statusOptions: [
        { label: '未到场', value: 'not_arrived' }, { label: '已签到', value: 'checked_in' },
        { label: '迟到', value: 'late' }, { label: '缺席', value: 'absent' },
        { label: '早退', value: 'left_early' }, { label: '已取消', value: 'cancelled' }
      ]
    }
  },
  created() { this.getList() },
  methods: {
    getList() {
      const requestId = this.listRequestId = (this.listRequestId || 0) + 1
      this.loading = true
      return listAttendance({ ...this.queryParams }).then(res => {
        if (requestId !== this.listRequestId) return
        this.rows = res.rows || []; this.total = res.total || 0
      }).catch(() => {
        if (requestId !== this.listRequestId) return
        this.rows = []; this.total = 0
      }).finally(() => { if (requestId === this.listRequestId) this.loading = false })
    },
    handleQuery() { this.queryParams.pageNum = 1; this.getList() },
    resetQuery() { this.queryParams = { pageNum: 1, pageSize: 10, keyword: undefined, sessionNumber: undefined, attendanceStatus: undefined }; this.handleQuery() },
    showHistory(row) { this.historyRows = []; this.historyOpen = true; attendanceHistory(row.id).then(res => { this.historyRows = res.data || [] }) },
    setStatus(row, attendanceStatus, changeReason) {
      if (!changeReason && row.attendanceStatus !== 'not_arrived' && row.attendanceStatus !== attendanceStatus) {
        return this.$prompt('请输入签到更正原因', '更正签到', { inputValidator: value => !!(value && value.trim()) || '必须填写原因' }).then(({ value }) => this.setStatus(row, attendanceStatus, value)).catch(() => {})
      }
      updateAttendanceStatus(row.id, { attendanceStatus, changeReason }).then(() => { this.msgSuccess('签到状态已更新'); this.getList() }) },
    setAbsent(row) { this.$prompt('请输入缺席原因', '标记缺席', { inputValidator: value => !!(value && value.trim()) || '必须填写原因' }).then(({ value }) => this.setStatus(row, 'absent', value)).catch(() => {}) },
    statusText(value) { const item = this.statusOptions.find(option => option.value === value); return item ? item.label : value },
    statusType(value) { return ({ checked_in: 'success', late: 'warning', absent: 'danger', cancelled: 'info' })[value] || '' }
  }
}
</script>

<style scoped>
.muted { color: #909399; font-size: 12px; margin-top: 3px; }
</style>
