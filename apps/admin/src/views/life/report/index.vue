<template>
  <div class="app-container">
    <el-form :model="queryParams" :inline="true">
      <el-form-item label="状态"><el-select v-model="queryParams.status" clearable size="small" @change="getList"><el-option label="待处理" value="pending" /><el-option label="处理中" value="processing" /><el-option label="已处理" value="resolved" /><el-option label="已驳回" value="rejected" /></el-select></el-form-item>
    </el-form>
    <el-table v-loading="loading" :data="rows">
      <el-table-column label="举报人" min-width="130"><template slot-scope="scope"><div>{{ scope.row.reporterNickname }}</div><div class="muted">{{ scope.row.reporterNo }}</div></template></el-table-column>
      <el-table-column label="动态内容" prop="postContent" min-width="240" show-overflow-tooltip />
      <el-table-column label="原因" min-width="180"><template slot-scope="scope"><div>{{ scope.row.reasonCode }}</div><div class="muted">{{ scope.row.reasonNote }}</div></template></el-table-column>
      <el-table-column label="提交时间" prop="createdAt" width="165" />
      <el-table-column label="状态" prop="status" width="90" />
      <el-table-column label="处理结果" prop="handleResult" min-width="160" show-overflow-tooltip />
      <el-table-column label="操作" width="120" fixed="right"><template slot-scope="scope"><el-button type="text" size="mini" @click="openHandle(scope.row)" v-hasPermi="['life:report:edit']">处理</el-button></template></el-table-column>
    </el-table>
    <el-dialog title="处理内容举报" :visible.sync="open" width="480px" append-to-body>
      <el-form ref="form" :model="form" :rules="rules" label-width="86px">
        <el-form-item label="处理状态" prop="status"><el-radio-group v-model="form.status"><el-radio label="resolved">已处理</el-radio><el-radio label="rejected">驳回</el-radio></el-radio-group></el-form-item>
        <el-form-item label="处理结果" prop="handleResult"><el-input v-model="form.handleResult" type="textarea" :rows="3" /></el-form-item>
      </el-form>
      <div slot="footer"><el-button type="primary" @click="submit">确定</el-button><el-button @click="open=false">取消</el-button></div>
    </el-dialog>
  </div>
</template>

<script>
import { listReport, handleReport } from '@/api/life/report'

export default {
  name: 'QlReport',
  data() { return { loading: false, rows: [], queryParams: { status: 'pending' }, open: false, currentId: '', form: { status: 'resolved', handleResult: '' }, rules: { status: [{ required: true, message: '请选择状态' }], handleResult: [{ required: true, message: '请填写处理结果', trigger: 'blur' }] } } },
  created() { this.getList() },
  methods: {
    getList() { this.loading = true; listReport(this.queryParams).then(res => { this.rows = res.rows }).finally(() => { this.loading = false }) },
    openHandle(row) { this.currentId = row.id; this.form = { status: 'resolved', handleResult: '' }; this.open = true },
    submit() { this.$refs.form.validate(valid => { if (!valid) return; handleReport(this.currentId, this.form).then(() => { this.msgSuccess('举报已处理'); this.open = false; this.getList() }) }) }
  }
}
</script>

<style scoped>
.muted { color: #909399; font-size: 12px; margin-top: 3px; }
</style>
