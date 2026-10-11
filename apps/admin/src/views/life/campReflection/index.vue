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
        <el-button v-if="['approved', 'hidden'].includes(scope.row.status)" v-hasPermi="['life:reflection:publish']" type="text" size="mini" @click="openPublication(scope.row)">发布核对</el-button>
        <el-button v-if="scope.row.status === 'published'" v-hasPermi="['life:reflection:publish']" type="text" size="mini" @click="act(scope.row, 'hide')">下架</el-button>
        <el-button v-if="scope.row.status === 'published'" v-hasPermi="['life:reflection:publish']" type="text" size="mini" @click="order(scope.row)">排序 {{ scope.row.sortOrder }}</el-button>
        <el-button v-hasPermi="['life:reflection:publish']" type="text" size="mini" @click="showReviewHistory(scope.row)">核对记录</el-button>
      </template></el-table-column>
    </el-table>
    <pagination v-show="total > 0" :total="total" :page.sync="query.pageNum" :limit.sync="query.pageSize" :page-sizes="[10, 20, 50]" @pagination="load" />
    <el-dialog :title="editing ? '编辑心声' : '审核这份心声'" :visible.sync="reviewOpen" width="560px" append-to-body>
      <p class="reflection-signature">{{ current.author }} · {{ Number(current.anonymous) ? '匿名' : '小名' }}</p>
      <el-form label-position="top" class="reflection-review">
        <el-form-item label="原文内容"><div class="reflection-original reflection-note">{{ current.note }}</div></el-form-item>
        <el-form-item label="展示内容"><el-input v-model="displayNote" type="textarea" :autosize="{ minRows: 6, maxRows: 12 }" /></el-form-item>
      </el-form>
      <p>内容审核通过后仍需核对身份及必要授权，才能公开发布。</p>
      <div slot="footer"><el-button @click="reviewOpen = false">返回</el-button><el-button type="primary" :loading="acting" @click="submitReview">{{ editing ? '保存' : '审核通过' }}</el-button></div>
    </el-dialog>
    <el-dialog title="正式发布前核对" :visible.sync="publicationOpen" width="640px" append-to-body :close-on-click-modal="false">
      <p class="reflection-note">{{ current.displayNote || current.note }}</p>
      <el-button type="text" @click="$refs.customerDossier.open(reviewContext.customerId)">查看已有业务资料</el-button>
      <p>请实际核对资料后记录结论。普通登记或本人勾选不能替代必要授权；依据不足请暂缓。</p>
      <el-form label-position="top">
        <el-form-item label="内容安全结论"><el-input v-model="publication.contentSafetyConclusion" type="textarea" :rows="2" maxlength="300" /></el-form-item>
        <el-form-item label="身份与适用条件核对结论"><el-input v-model="publication.identityConclusion" type="textarea" :rows="2" maxlength="300" /></el-form-item>
        <el-form-item label="必要授权"><el-select v-model="publication.authorizationConclusion"><el-option label="依据不足，暂缓" value="insufficient" /><el-option label="经核对，无需特殊授权" value="not_applicable" /><el-option label="必要授权已具备有效依据" value="satisfied" /></el-select></el-form-item>
        <el-form-item label="业务依据引用"><el-select v-model="publication.referenceKeys" style="width: 100%" multiple filterable :multiple-limit="5" placeholder="选择已实际核对的资料">
          <el-option v-for="item in reviewContext.availableReferences || []" :key="item.type + ':' + item.id" :value="item.type + ':' + item.id" :label="referenceLabel(item)" />
        </el-select></el-form-item>
        <div v-for="item in selectedReferences(true)" :key="item.type + ':' + item.id" class="reflection-note">
          {{ referenceLabel(item) }}<div v-if="item.summary">{{ item.summary }}</div><div v-if="item.policyVersion">授权版本：{{ item.policyVersion }} · 记录人：{{ item.recordedBy }} · 私有依据引用：{{ item.evidenceAttachmentId }}</div>
        </div>
        <el-form-item label="核对事实、适用依据或待补充事项"><el-input v-model="publication.basisSummary" type="textarea" :rows="3" maxlength="600" placeholder="记录必要结论与来源，不复制证件、健康资料或完整授权原文" /></el-form-item>
      </el-form>
      <div slot="footer"><el-button :disabled="acting" @click="publicationOpen = false">返回</el-button><el-button :loading="acting" @click="submitPublication('deferred')">留痕并暂缓</el-button><el-button type="primary" :loading="acting" @click="submitPublication('ready')">核对完成，正式发布</el-button></div>
    </el-dialog>
    <el-dialog title="审核与发布核对记录" :visible.sync="historyOpen" width="640px" append-to-body>
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
import { listReflections, reviewReflection, displayReflection, deferReflection, reflectionReviewHistory } from '@/api/life/campReflection'
import { listSession } from '@/api/life/session'
import CustomerDossier from '@/views/life/customer/Dossier'
export default {
  name: 'QlCampReflection',
  components: { CustomerDossier },
  data() { return { reviewActions: { submit: '主动投稿', private: '私人修改', approve: '内容审核通过', reject: '审核未选用', edit: '编辑展示', publish: '正式发布', approvePublish: '审核并发布', defer: '暂缓发布', hide: '下架', order: '调整顺序', withdraw: '本人撤回' }, authorizationLabels: { satisfied: '必要授权依据已核对', not_applicable: '经核对无需特殊授权', insufficient: '必要授权依据不足' }, publicationOpen: false, historyOpen: false, reviewLoading: false, reviewRequest: 0, reviewContext: {}, publication: {}, referenceLabels: { registration: '活动登记', assessment: '轻友登记', interaction: '已确认业务记录', consent: '线下用途授权' }, loading: false, acting: false, rows: [], total: 0, sessions: [], reviewOpen: false, editing: false, current: {}, displayNote: '', statuses: { submitted: '待审核', approved: '待发布核对', published: '展示中', hidden: '已下架', rejected: '未选用' }, query: { sessionId: this.$route.query.sessionId || '', phase: '', status: '', pageNum: 1, pageSize: 10 }} },
  watch: { '$route.query.sessionId'(value) { this.query.sessionId = value || ''; this.search() } },
  created() { this.load(); listSession({ pageNum: 1, pageSize: 1000 }).then(res => { this.sessions = res.rows }).catch(() => {}) },
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
      try { await (reviewReflection(this.current.id, this.editing ? 'edit' : 'approve', { revision: this.current.revision, displayNote })); this.reviewOpen = false; await this.load() } finally { this.acting = false }
    },
    async act(row, action) { if (this.acting) return; this.acting = true; try { await (action === 'reject' ? reviewReflection : displayReflection)(row.id, action, { revision: row.revision }); await this.load() } finally { this.acting = false } },
    referenceLabel(item) { return this.referenceLabels[item.type] + ' · ' + (item.recordedAt || '') + ' · ' + item.id },
    selectedReferences(withDetails = false) { return (this.reviewContext.availableReferences || []).filter(item => (this.publication.referenceKeys || []).includes(item.type + ':' + item.id) && (!withDetails || item.summary || item.policyVersion)) },
    async loadReviewContext(row) {
      if (this.acting) return
      this.publicationOpen = false
      this.historyOpen = false
      this.reviewContext = {}
      const request = ++this.reviewRequest
      this.reviewLoading = true
      try {
        const result = await reflectionReviewHistory(row.id)
        if (request !== this.reviewRequest) return
        if (Number(result.data.revision) !== Number(row.revision)) { this.$message.warning('心声已变化，请刷新后核对'); await this.load(); return }
        this.current = row
        this.reviewContext = result.data
        return true
      } finally { if (request === this.reviewRequest) this.reviewLoading = false }
    },
    async openPublication(row) {
      if (!await this.loadReviewContext(row)) return
      this.publication = { contentSafetyConclusion: '', identityConclusion: '', authorizationConclusion: 'insufficient', basisSummary: '', referenceKeys: [] }
      this.historyOpen = false
      this.publicationOpen = true
    },
    async showReviewHistory(row) {
      if (!await this.loadReviewContext(row)) return
      this.publicationOpen = false
      this.historyOpen = true
    },
    async submitPublication(decision) {
      if (this.acting || this.reviewLoading) return
      const references = this.selectedReferences().map(item => ({ type: item.type, id: item.id }))
      const review = {
        version: 1, decision, contentSafetyConclusion: (this.publication.contentSafetyConclusion || '').trim(),
        identityConclusion: (this.publication.identityConclusion || '').trim(),
        authorizationConclusion: this.publication.authorizationConclusion,
        basisSummary: (this.publication.basisSummary || '').trim(), references
      }
      if (!review.basisSummary) return this.$message.warning('请记录核对依据或待补充事项')
      if (decision === 'ready' && (!references.length || review.authorizationConclusion === 'insufficient' ||
          [review.contentSafetyConclusion, review.identityConclusion, review.basisSummary].some(text => Array.from(text).length < 10))) {
        return this.$message.warning('请实际核对并补充结论和业务依据；依据不足请暂缓')
      }
      this.acting = true
      try {
        const body = { revision: this.current.revision, publicationReview: review }
        if (decision === 'ready') await displayReflection(this.current.id, 'publish', body)
        else await deferReflection(this.current.id, body)
        this.publicationOpen = false
        await this.load()
      } finally { this.acting = false }
    },
    async order(row) { const result = await this.$prompt('数字越小越靠前，范围0—999', '调整展示顺序', { inputValue: String(row.sortOrder), inputPattern: /^\d{1,3}$/, inputErrorMessage: '请输入0—999' }); await displayReflection(row.id, 'order', { revision: row.revision, sortOrder: Number(result.value) }); await this.load() }
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
</style>
