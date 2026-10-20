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
      <span>仅发布本页勾选的心声；缺少审核依据的内容需先补齐。已展示内容不受本次勾选影响。</span>
    </div>
    <el-alert v-if="batchError" :title="batchError" type="error" :closable="false" show-icon />
    <el-table ref="table" v-loading="loading" :data="rows" row-key="id" @selection-change="selectionChanged" @expand-change="expandChanged">
      <el-table-column v-if="canPublish()" type="selection" width="45" :selectable="selectable" />
      <el-table-column v-if="canInspect()" type="expand">
        <template slot-scope="scope">
          <div v-loading="preparing[scope.row.id]" class="reflection-inline">
            <template v-if="drafts[scope.row.id]">
              <el-alert v-if="drafts[scope.row.id].revision !== scope.row.revision" title="心声已变化，请重新读取依据后审核；之前输入暂时保留。" type="warning" :closable="false" />
              <p>{{ scope.row.author }} · 本人公开授权：{{ scope.row.consentVersion ? '已同意' : '缺少有效记录' }} · {{ scope.row.consentAt }}</p>
              <p v-if="drafts[scope.row.id].reusing">已载入与当前展示稿、授权及业务资料一致的审核依据；发布时仍会重新校验。</p>
              <el-button type="text" @click="$refs.customerDossier.open(contexts[scope.row.id].customerId)">查看已有业务资料</el-button>
              <el-button type="text" :disabled="acting" @click="prepare(scope.row, true)">重新读取依据</el-button>
              <el-form label-position="top" class="reflection-review">
                <el-form-item label="展示内容"><el-input v-model="drafts[scope.row.id].displayNote" type="textarea" :autosize="{ minRows: 3, maxRows: 8 }" :disabled="acting || !canReview()" @input="displayChanged(scope.row)" /></el-form-item>
                <el-form-item label="内容安全结论"><el-input v-model="drafts[scope.row.id].contentSafetyConclusion" type="textarea" :rows="2" maxlength="300" :disabled="acting" /></el-form-item>
                <el-form-item label="身份与适用条件结论"><el-input v-model="drafts[scope.row.id].identityConclusion" type="textarea" :rows="2" maxlength="300" :disabled="acting" /></el-form-item>
                <el-form-item label="必要用途授权"><el-select v-model="drafts[scope.row.id].authorizationConclusion" :disabled="acting"><el-option label="依据不足，暂缓" value="insufficient" /><el-option label="经审核，无需特殊授权" value="not_applicable" /><el-option label="必要授权已具备有效依据" value="satisfied" /></el-select></el-form-item>
                <el-form-item label="业务依据引用"><el-select v-model="drafts[scope.row.id].referenceKeys" style="width: 100%" multiple filterable :multiple-limit="5" :disabled="acting" placeholder="选择已实际核对的资料"><el-option v-for="item in contexts[scope.row.id].availableReferences || []" :key="item.type + ':' + item.id" :value="item.type + ':' + item.id" :label="referenceLabel(item)" /></el-select></el-form-item>
                <div v-for="item in selectedReferences(scope.row)" :key="item.type + ':' + item.id" class="reflection-note">{{ referenceLabel(item) }}<div v-if="item.summary">{{ item.summary }}</div><div v-if="item.policyVersion">授权版本：{{ item.policyVersion }} · 记录人：{{ item.recordedBy }} · 私有依据引用：{{ item.evidenceAttachmentId }}</div></div>
                <el-form-item label="审核事实、适用依据或待补充事项"><el-input v-model="drafts[scope.row.id].basisSummary" type="textarea" :rows="3" maxlength="600" :disabled="acting" placeholder="记录必要结论与来源，不复制证件、健康资料或完整授权原文" /></el-form-item>
              </el-form>
              <p>本人同意公开已由系统记录。普通登记不能替代特殊情形所需的用途授权；依据不足的内容暂不发布。</p>
              <el-button v-if="['approved', 'hidden'].includes(scope.row.status)" :disabled="acting" @click="defer(scope.row)">留痕并暂缓</el-button>
              <el-button v-if="!canPublish() && ['approved', 'hidden'].includes(scope.row.status)" type="primary" :disabled="acting || !ready(scope.row)" @click="publishApproved(scope.row)">确认发布（1条）</el-button>
            </template>
            <el-button v-else :disabled="acting || preparing[scope.row.id]" @click="prepare(scope.row)">读取审核依据</el-button>
            <p v-if="preparationErrors[scope.row.id]">{{ preparationErrors[scope.row.id] }}</p>
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
        <el-button v-if="selectable(scope.row) || (canInspect() && ['approved', 'hidden'].includes(scope.row.status))" type="text" size="mini" :disabled="acting" @click="prepare(scope.row)">审核与选用</el-button>
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
        <template v-if="item.publicationReview">
          <p>{{ item.publicationReview.decision === 'ready' ? '发布核对通过' : '依据不足，暂缓' }}</p>
          <div class="reflection-note">{{ item.publicationReview.contentSafetyConclusion }}</div>
          <div class="reflection-note">{{ item.publicationReview.identityConclusion }}</div>
          <div class="reflection-note">{{ authorizationLabels[item.publicationReview.authorizationConclusion] || item.publicationReview.authorizationConclusion }} · {{ item.publicationReview.basisSummary }}</div>
          <p v-for="refItem in item.publicationReview.references || []" :key="refItem.type + ':' + refItem.id">{{ referenceLabels[refItem.type] }} · {{ refItem.id }}</p>
        </template>
        <p v-else>{{ item.basisRestricted ? '缺少相关业务资料读取权限，核对依据已隐藏' : '此操作没有发布核对依据记录' }}</p>
      </div>
    </el-dialog>
    <customer-dossier ref="customerDossier" class="publication-dossier" />
  </div>
</template>
<script>
import { listReflections, reviewReflection, displayReflection, deferReflection, reflectionReviewHistory, publishReflections } from '@/api/life/campReflection'
import { listSession } from '@/api/life/session'
import { checkPermi } from '@/utils/permission'
import CustomerDossier from '@/views/life/customer/Dossier'
export default {
  name: 'QlCampReflection',
  components: { CustomerDossier },
  data() { return { reviewActions: { submit: '主动投稿', private: '私人修改', approve: '内容审核通过', reject: '审核未选用', edit: '编辑展示', publish: '正式发布', approvePublish: '审核并发布', defer: '暂缓发布', hide: '下架', order: '调整顺序', withdraw: '本人撤回' }, authorizationLabels: { satisfied: '必要授权依据已核对', not_applicable: '经核对无需特殊授权', insufficient: '必要授权依据不足' }, historyOpen: false, reviewLoading: false, reviewRequest: 0, reviewContext: {}, referenceLabels: { registration: '活动登记', assessment: '轻友登记', interaction: '已确认业务记录', consent: '线下用途授权' }, loading: false, acting: false, rows: [], total: 0, sessions: [], selected: [], drafts: {}, contexts: {}, preparing: {}, preparationErrors: {}, loadVersion: 0, batchError: '', reviewOpen: false, editing: false, current: {}, displayNote: '', statuses: { submitted: '待审核', approved: '待选用', published: '展示中', hidden: '已下架', rejected: '未选用' }, query: { sessionId: this.$route.query.sessionId || '', phase: '', status: '', pageNum: 1, pageSize: 10 }} },
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
    async selectionChanged(rows) { this.selected = rows; await Promise.all(rows.filter(row => !this.preparing[row.id]).map(row => this.prepare(row))) },
    expandChanged(row, expanded) { if (expanded.some(item => item.id === row.id) && !this.preparing[row.id] && !this.drafts[row.id]) this.prepare(row) },
    emptyReview() { return { contentSafetyConclusion: '', identityConclusion: '', authorizationConclusion: 'insufficient', basisSummary: '', referenceKeys: [], referenceHashes: {}, reusing: false } },
    async prepare(row, reload = false) {
      if (this.acting || !this.canInspect() || this.preparing[row.id]) return
      if (!reload && this.drafts[row.id]) { if (this.$refs.table) this.$refs.table.toggleRowExpansion(row, true); return }
      const version = this.loadVersion
      this.$set(this.preparing, row.id, true); this.$set(this.preparationErrors, row.id, '')
      try {
        const result = await reflectionReviewHistory(row.id)
        if (version !== this.loadVersion) return
        if (Number(result.data.revision) !== Number(row.revision)) { this.$set(this.preparationErrors, row.id, '心声已变化，请刷新列表后审核'); return }
        const previous = result.data.reusableReview
        const draft = { ...this.emptyReview(), revision: row.revision, displayNote: row.displayNote || row.note }
        if (previous) Object.assign(draft, { contentSafetyConclusion: previous.contentSafetyConclusion, identityConclusion: previous.identityConclusion, authorizationConclusion: previous.authorizationConclusion, basisSummary: previous.basisSummary, referenceKeys: previous.references.map(item => item.type + ':' + item.id), referenceHashes: Object.fromEntries(previous.references.map(item => [item.type + ':' + item.id, item.sourceHash])), reusing: true })
        this.$set(this.contexts, row.id, result.data); this.$set(this.drafts, row.id, draft)
        if (this.$refs.table) this.$refs.table.toggleRowExpansion(row, true)
      } catch (error) { if (version === this.loadVersion) this.$set(this.preparationErrors, row.id, error.message || '审核依据读取失败，请重试') } finally { this.$set(this.preparing, row.id, false) }
    },
    displayChanged(row) { const draft = this.drafts[row.id]; if (draft.reusing) Object.assign(draft, this.emptyReview()) },
    referenceLabel(item) { return this.referenceLabels[item.type] + ' · ' + (item.recordedAt || '') + ' · ' + item.id },
    selectedReferences(row) { const draft = this.drafts[row.id] || {}; return ((this.contexts[row.id] || {}).availableReferences || []).filter(item => (draft.referenceKeys || []).includes(item.type + ':' + item.id)) },
    reviewFor(row, decision = 'ready') {
      const draft = this.drafts[row.id]
      return { version: 1, decision, contentSafetyConclusion: (draft.contentSafetyConclusion || '').trim(), identityConclusion: (draft.identityConclusion || '').trim(), authorizationConclusion: draft.authorizationConclusion, basisSummary: (draft.basisSummary || '').trim(), references: this.selectedReferences(row).map(item => ({ type: item.type, id: item.id, sourceHash: draft.referenceHashes[item.type + ':' + item.id] })) }
    },
    ready(row) {
      const draft = this.drafts[row.id]
      if (!draft || this.preparing[row.id] || draft.revision !== row.revision || !row.consentVersion || !row.consentAt) return false
      const review = this.reviewFor(row)
      const note = (draft.displayNote || '').trim()
      return !!note && Array.from(note).length <= 150 && ['satisfied', 'not_applicable'].includes(review.authorizationConclusion) && review.references.length > 0 && review.references.length === draft.referenceKeys.length && [review.contentSafetyConclusion, review.identityConclusion, review.basisSummary].every(text => Array.from(text).length >= 10)
    },
    authorizationStatus(row) { return row.status === 'published' ? '已展示 · 可查看操作记录' : this.ready(row) ? '审核依据已齐备' : this.preparationErrors[row.id] || '必要用途授权及适用条件待审核' },
    batchReady() { return this.canPublish() && !this.acting && !this.loading && this.selected.length > 0 && this.selected.length <= 50 && this.selected.every(row => this.selectable(row) && this.ready(row)) },
    async publishSelection() {
      if (!this.batchReady()) return
      const items = this.selected.map(row => ({ id: row.id, revision: row.revision, displayNote: this.drafts[row.id].displayNote.trim(), publicationReview: this.reviewFor(row) }))
      this.acting = true; this.batchError = ''
      try {
        await publishReflections({ items })
        this.$message.success('已选用并发布 ' + items.length + ' 条心声')
        this.drafts = {}; this.contexts = {}; await this.load()
      } catch (error) { this.batchError = error.message || '整批未发布，请核对变化后重试'; throw error } finally { this.acting = false }
    },
    async defer(row) {
      if (this.acting || !this.drafts[row.id]) return
      const review = this.reviewFor(row, 'deferred')
      if (!review.basisSummary) return this.$message.warning('请记录待补充事项')
      this.acting = true
      try { await deferReflection(row.id, { revision: row.revision, publicationReview: review }); await this.load() } finally { this.acting = false }
    },
    async publishApproved(row) {
      if (this.acting || !this.canInspect() || !['approved', 'hidden'].includes(row.status) || !this.ready(row)) return
      this.acting = true
      try { await displayReflection(row.id, 'publish', { revision: row.revision, publicationReview: this.reviewFor(row) }); await this.load() } finally { this.acting = false }
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
/* This host only reads the existing dossier; editing stays in customer management. */
.publication-dossier ::v-deep .identity-heading > .el-button { display: none; }
.reflection-signature { color: #606266; margin: 0 0 20px; }
.reflection-original { padding: 12px 16px; background: #f7f8f5; border-radius: 4px; }
.reflection-review ::v-deep .el-form-item__label { padding-bottom: 6px; line-height: 24px; }
.reflection-review ::v-deep textarea { line-height: 1.8; }
.reflection-note { white-space: pre-wrap; overflow-wrap: anywhere; line-height: 1.8; color: #3e4e39; }
.reflection-toolbar { display: flex; align-items: center; gap: 16px; margin: 12px 0 18px; }
.reflection-inline { max-width: 900px; padding: 12px 28px; }
</style>
