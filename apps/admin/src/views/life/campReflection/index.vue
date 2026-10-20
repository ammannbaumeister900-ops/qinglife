<template>
  <div class="app-container">
    <el-form :inline="true" :model="query" size="small">
      <el-form-item label="期次"><el-select v-model="query.sessionId" clearable filterable placeholder="全部期次"><el-option v-for="item in sessions" :key="item.id" :label="'第 ' + item.sessionNumber + ' 期 · ' + item.name" :value="item.id" /></el-select></el-form-item>
      <el-form-item label="阶段"><el-select v-model="query.phase" clearable placeholder="全部阶段"><el-option label="来到这里" value="before" /><el-option label="带回日常" value="after" /></el-select></el-form-item>
      <el-form-item label="状态"><el-select v-model="query.status" clearable placeholder="全部状态"><el-option v-for="(label, value) in statuses" :key="value" :label="label" :value="value" /></el-select></el-form-item>
      <el-button type="primary" size="small" :disabled="acting" @click="search">查询</el-button>
    </el-form>
    <div v-if="canPublish()" class="reflection-toolbar">
      <el-button type="primary" :loading="acting" :disabled="!batchReady()" @click="publishSelection">确认选用并发布（{{ selected.length }}条）</el-button>
      <span>勾选后点击一次即可选用并发布。本人公开授权由系统校验；已展示内容不受本次勾选影响。</span>
    </div>
    <el-alert v-if="batchError" :title="batchError" type="error" :closable="false" show-icon />
    <el-table ref="table" v-loading="loading" :data="rows" row-key="id" @selection-change="selectionChanged" @expand-change="expandChanged">
      <el-table-column v-if="canPublish()" type="selection" width="45" :selectable="selectable" />
      <el-table-column v-if="canInspect()" type="expand">
        <template slot-scope="scope">
          <div class="reflection-inline">
            <p>{{ scope.row.author }} · 本人公开授权：{{ scope.row.consentVersion && scope.row.consentAt ? '已同意' : '授权记录不完整' }} · {{ scope.row.consentAt }}</p>
            <el-form v-if="drafts[scope.row.id]" label-position="top" class="reflection-review">
              <el-form-item label="展示内容"><el-input v-model="drafts[scope.row.id].displayNote" type="textarea" :autosize="{ minRows: 3, maxRows: 8 }" :disabled="acting || !canReview()" /></el-form-item>
            </el-form>
            <el-button v-if="!canPublish() && ['approved', 'hidden'].includes(scope.row.status)" type="primary" :disabled="acting || !ready(scope.row)" @click="publishApproved(scope.row)">确认发布（1条）</el-button>
          </div>
        </template>
      </el-table-column>
      <el-table-column label="期次" width="95"><template slot-scope="scope">第 {{ scope.row.sessionNumber }} 期</template></el-table-column>
      <el-table-column label="阶段" width="100"><template slot-scope="scope">{{ scope.row.phase === 'before' ? '来到这里' : '带回日常' }}</template></el-table-column>
      <el-table-column label="公开署名" min-width="140"><template slot-scope="scope"><div>{{ scope.row.author }}</div><el-tag size="mini" type="info">{{ Number(scope.row.anonymous) ? '匿名' : '小名' }}</el-tag></template></el-table-column>
      <el-table-column label="原文内容" min-width="250"><template slot-scope="scope"><div class="reflection-note">{{ scope.row.note }}</div></template></el-table-column>
      <el-table-column label="展示内容" prop="displayNote" min-width="160" />
      <el-table-column label="状态" width="110"><template slot-scope="scope"><el-tag :type="scope.row.status === 'published' ? 'success' : 'info'">{{ statuses[scope.row.status] }}</el-tag></template></el-table-column>
      <el-table-column label="公开授权" min-width="190"><template slot-scope="scope"><el-tag :type="scope.row.consentVersion && scope.row.consentAt ? 'success' : 'warning'">{{ scope.row.consentVersion && scope.row.consentAt ? '本人已同意公开' : '授权记录不完整' }}</el-tag><div>{{ scope.row.consentAt }}</div><div>{{ authorizationStatus(scope.row) }}</div></template></el-table-column>
      <el-table-column label="操作" width="220" fixed="right"><template slot-scope="scope">
        <el-button v-if="selectable(scope.row) || (canInspect() && ['approved', 'hidden'].includes(scope.row.status))" type="text" size="mini" :disabled="acting" @click="prepare(scope.row)">编辑展示内容</el-button>
        <el-button v-else-if="['submitted', 'rejected'].includes(scope.row.status)" v-hasPermi="['life:reflection:review']" type="text" size="mini" @click="approve(scope.row)">审核</el-button>
        <el-button v-if="['submitted', 'approved'].includes(scope.row.status)" v-hasPermi="['life:reflection:review']" type="text" size="mini" :disabled="acting" @click="act(scope.row, 'reject')">暂不选用</el-button>
        <el-button v-if="!canPublish() && ['approved', 'hidden'].includes(scope.row.status)" v-hasPermi="['life:reflection:review']" type="text" size="mini" @click="edit(scope.row)">编辑</el-button>
        <el-button v-if="scope.row.status === 'published'" v-hasPermi="['life:reflection:publish']" type="text" size="mini" :disabled="acting" @click="act(scope.row, 'hide')">下架</el-button>
        <el-button v-if="scope.row.status === 'published'" v-hasPermi="['life:reflection:publish']" type="text" size="mini" :disabled="acting" @click="order(scope.row)">排序 {{ scope.row.sortOrder }}</el-button>
        <el-button v-hasPermi="['life:reflection:publish']" type="text" size="mini" :disabled="acting" @click="showReviewHistory(scope.row)">操作记录</el-button>
      </template></el-table-column>
    </el-table>
    <pagination v-show="total > 0" :total="total" :page.sync="query.pageNum" :limit.sync="query.pageSize" :page-sizes="[10, 20, 50]" @pagination="load" />
    <el-dialog :title="editing ? '编辑心声' : '审核这份心声'" :visible.sync="reviewOpen" width="560px" append-to-body>
      <p class="reflection-signature">{{ current.author }} · {{ Number(current.anonymous) ? '匿名' : '小名' }}</p>
      <el-form label-position="top" class="reflection-review"><el-form-item label="原文内容"><div class="reflection-original reflection-note">{{ current.note }}</div></el-form-item><el-form-item label="展示内容"><el-input v-model="displayNote" type="textarea" :autosize="{ minRows: 6, maxRows: 12 }" /></el-form-item></el-form>
      <div slot="footer"><el-button @click="reviewOpen = false">返回</el-button><el-button type="primary" :loading="acting" @click="submitReview">{{ editing ? '保存' : '审核通过' }}</el-button></div>
    </el-dialog>
    <el-dialog title="审核与发布记录" :visible.sync="historyOpen" width="640px" append-to-body>
      <el-empty v-if="!(reviewContext.rows || []).length" description="尚无操作记录" />
      <div v-for="(item,index) in reviewContext.rows || []" :key="index" class="reflection-original">
        <p>{{ reviewActions[item.action] || item.action }} · 版本 {{ item.revision }} · {{ item.operatorName || item.operatorId || '本人操作' }} · {{ item.createdAt }}</p>
        <p v-if="item.publicationReview && item.publicationReview.mode === 'selection'">运营选用并发布 · 已记录本人公开授权与展示内容版本</p>
        <template v-else-if="item.publicationReview">
          <p>{{ item.publicationReview.decision === 'ready' ? '发布核对通过' : '依据不足，暂缓' }}</p>
          <div class="reflection-note">{{ item.publicationReview.contentSafetyConclusion }}</div>
          <div class="reflection-note">{{ item.publicationReview.identityConclusion }}</div>
          <div class="reflection-note">{{ authorizationLabels[item.publicationReview.authorizationConclusion] || item.publicationReview.authorizationConclusion }} · {{ item.publicationReview.basisSummary }}</div>
          <p v-for="refItem in item.publicationReview.references || []" :key="refItem.type + ':' + refItem.id">{{ referenceLabels[refItem.type] }} · {{ refItem.id }}</p>
        </template>
        <p v-else>{{ item.basisRestricted ? '缺少相关业务资料读取权限，历史依据已隐藏' : '操作已记录' }}</p>
      </div>
    </el-dialog>
  </div>
</template>
<script>
import { listReflections, reviewReflection, displayReflection, reflectionReviewHistory, publishReflections } from '@/api/life/campReflection'
import { listSession } from '@/api/life/session'
import { checkPermi } from '@/utils/permission'
export default {
  name: 'QlCampReflection',
  data() { return { reviewActions: { submit: '主动投稿', private: '私人修改', approve: '内容审核通过', reject: '审核未选用', edit: '编辑展示', publish: '正式发布', approvePublish: '审核并发布', defer: '暂缓发布', hide: '下架', order: '调整顺序', withdraw: '本人撤回' }, authorizationLabels: { satisfied: '必要授权依据已核对', not_applicable: '经核对无需特殊授权', insufficient: '必要授权依据不足' }, historyOpen: false, reviewLoading: false, reviewRequest: 0, reviewContext: {}, referenceLabels: { registration: '活动登记', assessment: '轻友登记', interaction: '已确认业务记录', consent: '线下用途授权' }, loading: false, acting: false, rows: [], total: 0, sessions: [], selected: [], drafts: {}, loadVersion: 0, batchError: '', reviewOpen: false, editing: false, current: {}, displayNote: '', statuses: { submitted: '待审核', approved: '待选用', published: '展示中', hidden: '已下架', rejected: '未选用' }, query: { sessionId: this.$route.query.sessionId || '', phase: '', status: '', pageNum: 1, pageSize: 10 }} },
  watch: { '$route.query.sessionId'(value) { this.query.sessionId = value || ''; this.search() } },
  created() { this.load(); listSession({ pageNum: 1, pageSize: 1000 }).then(res => { this.sessions = res.rows }).catch(() => {}) },
  beforeDestroy() { this.loadVersion++; this.reviewRequest++ },
  methods: {
    canReview() { return checkPermi(['life:reflection:review']) },
    canInspect() { return checkPermi(['life:reflection:publish']) },
    canPublish() { return checkPermi(['life:reflection:review']) && checkPermi(['life:reflection:publish']) },
    selectable(row) { return this.canPublish() && !this.acting && !this.loading && ['submitted', 'rejected', 'approved', 'hidden'].includes(row.status) && !!row.consentVersion && !!row.consentAt },
    search() { if (this.acting) return; this.query.pageNum = 1; this.load() },
    async load() {
      const version = ++this.loadVersion
      this.loading = true; this.selected = []
      if (this.$refs.table) this.$refs.table.clearSelection()
      try { const res = await listReflections(this.query); if (version === this.loadVersion) { this.rows = res.data.rows; this.total = res.data.total } } finally { if (version === this.loadVersion) this.loading = false }
    },
    selectionChanged(rows) { this.selected = rows; rows.forEach(row => this.ensureDraft(row)) },
    expandChanged(row, expanded) { if (expanded.some(item => item.id === row.id)) this.ensureDraft(row) },
    ensureDraft(row) {
      if (!this.drafts[row.id] || this.drafts[row.id].revision !== row.revision) this.$set(this.drafts, row.id, { revision: row.revision, displayNote: row.displayNote || row.note || '' })
    },
    prepare(row) {
      if (this.acting || !this.canInspect()) return
      this.ensureDraft(row)
      if (this.$refs.table) this.$refs.table.toggleRowExpansion(row, true)
    },
    ready(row) {
      const draft = this.drafts[row.id]
      if (!draft || draft.revision !== row.revision || !row.consentVersion || !row.consentAt) return false
      const note = (draft.displayNote || '').trim()
      return !!note && Array.from(note).length <= 150
    },
    authorizationStatus(row) { return row.status === 'published' ? '已展示 · 可查看操作记录' : row.consentVersion && row.consentAt ? '已授权，可选用发布' : '授权记录不完整' },
    batchReady() { return this.canPublish() && !this.acting && !this.loading && this.selected.length > 0 && this.selected.length <= 50 && this.selected.every(row => this.selectable(row) && this.ready(row)) },
    async publishSelection() {
      if (!this.batchReady()) return
      const items = this.selected.map(row => ({ id: row.id, revision: row.revision, displayNote: this.drafts[row.id].displayNote.trim() }))
      this.acting = true; this.batchError = ''
      try {
        await publishReflections({ items })
        this.$message.success('已选用并发布 ' + items.length + ' 条心声')
        this.drafts = {}; await this.load()
      } catch (error) { this.batchError = error.message || '整批未发布，请核对变化后重试'; throw error } finally { this.acting = false }
    },
    async publishApproved(row) {
      if (this.acting || !this.canInspect() || !['approved', 'hidden'].includes(row.status) || !this.ready(row)) return
      this.acting = true
      try { await displayReflection(row.id, 'publish', { revision: row.revision }); await this.load() } finally { this.acting = false }
    },
    approve(row) { this.editing = false; this.current = row; this.displayNote = row.displayNote || row.note; this.reviewOpen = true },
    edit(row) { this.approve(row); this.editing = true },
    async submitReview() {
      if (this.acting) return
      const displayNote = this.displayNote.trim()
      if (!displayNote || Array.from(displayNote).length > 150) return this.$message.warning('展示内容需为1—150字')
      this.acting = true
      try { await reviewReflection(this.current.id, this.editing ? 'edit' : 'approve', { revision: this.current.revision, displayNote }); this.reviewOpen = false; await this.load() } finally { this.acting = false }
    },
    async act(row, action) { if (this.acting) return; this.acting = true; try { await (action === 'reject' ? reviewReflection : displayReflection)(row.id, action, { revision: row.revision }); await this.load() } finally { this.acting = false } },
    async showReviewHistory(row) {
      if (this.acting) return
      const request = ++this.reviewRequest
      this.historyOpen = false; this.reviewContext = {}; this.reviewLoading = true
      try { const result = await reflectionReviewHistory(row.id); if (request !== this.reviewRequest) return; this.reviewContext = result.data; this.historyOpen = true } finally { if (request === this.reviewRequest) this.reviewLoading = false }
    },
    async order(row) { if (this.acting) return; const result = await this.$prompt('数字越小越靠前，范围0—999', '调整展示顺序', { inputValue: String(row.sortOrder), inputPattern: /^\d{1,3}$/, inputErrorMessage: '请输入0—999' }); await this.actOrder(row, Number(result.value)) },
    async actOrder(row, sortOrder) { if (this.acting) return; this.acting = true; try { await displayReflection(row.id, 'order', { revision: row.revision, sortOrder }); await this.load() } finally { this.acting = false } }
  }
}
</script>
<style scoped>
.reflection-signature { color: #606266; margin: 0 0 20px; }
.reflection-original { padding: 12px 16px; background: #f7f8f5; border-radius: 4px; }
.reflection-review ::v-deep .el-form-item__label { padding-bottom: 6px; line-height: 24px; }
.reflection-review ::v-deep textarea { line-height: 1.8; }
.reflection-note { white-space: pre-wrap; overflow-wrap: anywhere; line-height: 1.8; color: #3e4e39; }
.reflection-toolbar { display: flex; align-items: center; gap: 16px; margin: 12px 0 18px; }
.reflection-inline { max-width: 900px; padding: 12px 28px; }
</style>
