<template>
  <div class="app-container">
    <el-form :inline="true" :model="query" size="small">
      <el-form-item label="期次"><el-select v-model="query.sessionId" clearable filterable placeholder="全部期次"><el-option v-for="item in sessions" :key="item.id" :label="'第 ' + item.sessionNumber + ' 期 · ' + item.name" :value="item.id" /></el-select></el-form-item>
      <el-form-item label="阶段"><el-select v-model="query.phase" clearable placeholder="全部阶段"><el-option label="来到这里" value="before" /><el-option label="带回日常" value="after" /></el-select></el-form-item>
      <el-form-item label="状态"><el-select v-model="query.status" clearable placeholder="全部状态"><el-option v-for="(label, value) in statuses" :key="value" :label="label" :value="value" /></el-select></el-form-item>
      <el-button type="primary" size="small" @click="search">查询</el-button>
    </el-form>
    <el-table v-loading="loading" :data="rows">
      <el-table-column label="期次" width="95"><template slot-scope="scope">第 {{ scope.row.sessionNumber }} 期</template></el-table-column>
      <el-table-column label="阶段" width="100"><template slot-scope="scope">{{ scope.row.phase === 'before' ? '来到这里' : '带回日常' }}</template></el-table-column>
      <el-table-column label="公开署名" min-width="140"><template slot-scope="scope"><div>{{ scope.row.author }}</div><el-tag size="mini" type="info">{{ Number(scope.row.anonymous) ? '匿名' : '小名' }}</el-tag></template></el-table-column>
      <el-table-column label="原文内容" min-width="250"><template slot-scope="scope"><div class="reflection-note">{{ scope.row.note }}</div></template></el-table-column>
      <el-table-column label="展示内容" prop="displayNote" min-width="160" />
      <el-table-column label="状态" width="110"><template slot-scope="scope"><el-tag :type="scope.row.status === 'published' ? 'success' : 'info'">{{ statuses[scope.row.status] }}</el-tag></template></el-table-column>
      <el-table-column label="提交授权时间" prop="consentAt" width="165" />
      <el-table-column label="操作" width="220" fixed="right"><template slot-scope="scope">
        <el-button v-if="['submitted', 'rejected'].includes(scope.row.status)" v-hasPermi="['life:reflection:review']" type="text" size="mini" @click="approve(scope.row)">审核</el-button>
        <el-button v-if="['submitted', 'approved'].includes(scope.row.status)" v-hasPermi="['life:reflection:review']" type="text" size="mini" @click="act(scope.row, 'reject')">暂不选用</el-button>
        <el-button v-if="['approved', 'hidden'].includes(scope.row.status)" v-hasPermi="['life:reflection:review']" type="text" size="mini" @click="edit(scope.row)">编辑</el-button>
        <el-button v-if="['approved', 'hidden'].includes(scope.row.status)" v-hasPermi="['life:reflection:publish']" type="text" size="mini" @click="act(scope.row, 'publish')">展示在本期</el-button>
        <el-button v-if="scope.row.status === 'published'" v-hasPermi="['life:reflection:publish']" type="text" size="mini" @click="act(scope.row, 'hide')">下架</el-button>
        <el-button v-if="scope.row.status === 'published'" v-hasPermi="['life:reflection:publish']" type="text" size="mini" @click="order(scope.row)">排序 {{ scope.row.sortOrder }}</el-button>
      </template></el-table-column>
    </el-table>
    <pagination v-show="total > 0" :total="total" :page.sync="query.pageNum" :limit.sync="query.pageSize" :page-sizes="[10, 20, 50]" @pagination="load" />
    <el-dialog :title="editing ? '编辑心声' : '审核这份心声'" :visible.sync="reviewOpen" width="560px" append-to-body>
      <p class="reflection-signature">{{ current.author }} · {{ Number(current.anonymous) ? '匿名' : '小名' }}</p>
      <el-form label-position="top" class="reflection-review">
        <el-form-item label="原文内容"><div class="reflection-original reflection-note">{{ current.note }}</div></el-form-item>
        <el-form-item label="展示内容"><el-input v-model="displayNote" type="textarea" :autosize="{ minRows: 6, maxRows: 12 }" /></el-form-item>
      </el-form>
      <div slot="footer"><el-button @click="reviewOpen = false">返回</el-button><el-button type="primary" :loading="acting" @click="submitReview">{{ editing ? '保存' : '审核通过' }}</el-button></div>
    </el-dialog>
  </div>
</template>
<script>
import { listReflections, reviewReflection, displayReflection, approveAndPublishReflection } from '@/api/life/campReflection'
import { listSession } from '@/api/life/session'
import { checkPermi } from '@/utils/permission'
export default {
  name: 'QlCampReflection',
  data() { return { loading: false, acting: false, rows: [], total: 0, sessions: [], reviewOpen: false, editing: false, current: {}, displayNote: '', statuses: { submitted: '待审核', approved: '待展示', published: '展示中', hidden: '已下架', rejected: '未选用' }, query: { sessionId: this.$route.query.sessionId || '', phase: '', status: '', pageNum: 1, pageSize: 10 } } },
  created() { this.load(); listSession({ pageNum: 1, pageSize: 1000 }).then(res => { this.sessions = res.rows }).catch(() => {}) },
  watch: { '$route.query.sessionId'(value) { this.query.sessionId = value || ''; this.search() } },
  methods: {
    search() { this.query.pageNum = 1; this.load() },
    async load() { this.loading = true; try { const res = await listReflections(this.query); this.rows = res.data.rows; this.total = res.data.total } finally { this.loading = false } },
    approve(row) { this.editing = false; this.current = row; this.displayNote = row.displayNote || row.note; this.reviewOpen = true },
    edit(row) { this.approve(row); this.editing = true },
    async submitReview() {
      if (this.acting) return
      const displayNote = this.displayNote.trim()
      if (!displayNote || Array.from(displayNote).length > 150) return this.$message.warning('展示内容需为1—150字')
      this.acting = true
      try { await (this.editing ? reviewReflection(this.current.id, 'edit', { revision: this.current.revision, displayNote }) : checkPermi(['life:reflection:publish']) ? approveAndPublishReflection(this.current.id, { revision: this.current.revision, displayNote }) : reviewReflection(this.current.id, 'approve', { revision: this.current.revision, displayNote })); this.reviewOpen = false; await this.load() } finally { this.acting = false }
    },
    async act(row, action) { if (this.acting) return; this.acting = true; try { await (action === 'reject' ? reviewReflection : displayReflection)(row.id, action, { revision: row.revision }); await this.load() } finally { this.acting = false } },
    async order(row) { const result = await this.$prompt('数字越小越靠前，范围0—999', '调整展示顺序', { inputValue: String(row.sortOrder), inputPattern: /^\d{1,3}$/, inputErrorMessage: '请输入0—999' }); await displayReflection(row.id, 'order', { revision: row.revision, sortOrder: Number(result.value) }); await this.load() }
  }
}
</script>
<style scoped>
.reflection-signature { color: #606266; margin: 0 0 20px; }
.reflection-original { padding: 12px 16px; background: #f7f8f5; border-radius: 4px; }
.reflection-review ::v-deep .el-form-item__label { padding-bottom: 6px; line-height: 24px; }
.reflection-review ::v-deep textarea { line-height: 1.8; }
.reflection-note { white-space: pre-wrap; overflow-wrap: anywhere; line-height: 1.8; color: #3e4e39; }
</style>
