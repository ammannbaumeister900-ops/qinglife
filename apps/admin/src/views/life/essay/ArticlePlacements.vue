<template>
  <el-dialog title="认知引导页 · 文章关联" :visible.sync="open" width="920px" append-to-body :close-on-click-modal="false">
    <el-alert title="只关联已确认的人物与文章，不改变认知页故事编排。未审核或已停用的文章不会形成公开链接。" type="info" :closable="false" />
    <p>当前文章：{{ article.title || '未选择' }}（{{ article.id || '—' }}）</p>
    <el-alert v-if="error" :title="error" description="请刷新核对最新配置与文章状态；权限不足须由管理员核对角色，不会自动授权。" type="error" :closable="false" />
    <el-button size="small" :disabled="busy" @click="load">刷新配置</el-button>
    <div v-for="row in rows" :key="row.placementKey" class="placement">
      <h3>{{ names[row.placementKey] }} <small>修订 {{ row.revision }}</small></h3>
      <p>关联文章：{{ row.title || '尚未配置' }} · {{ row.essayId || '—' }} · {{ Number(row.articleStatus) === 1 ? '已启用' : '未启用或不存在' }}</p>
      <p v-if="row.essayId && !row.reviewCurrent">正文或图片已变化／尚未完成审核，请重新核实全文与授权后保存。</p>
      <el-button v-if="editable" size="small" :disabled="busy || !article.id" @click="attach(row)">关联当前文章</el-button>
      <el-button size="small" :disabled="busy" @click="history(row)">查看关联历史</el-button>
      <el-form :disabled="busy || !editable" label-position="top">
        <el-form-item label="公开全文入口"><el-switch v-model="row.enabled" /></el-form-item>
        <el-checkbox v-model="row.identityConfirmed">已核实文章确属此人物／故事</el-checkbox>
        <el-checkbox v-model="row.authorizationConfirmed">已核实素材与公开使用授权</el-checkbox>
        <el-checkbox v-model="row.editorialApproved">全文已审核，可作为认知页延伸阅读</el-checkbox>
        <el-form-item label="核实与审核依据"><el-input v-model="row.evidenceRef" type="textarea" :rows="2" maxlength="500" placeholder="记录实际授权、身份与全文审核依据；不可用免责声明替代审核" /></el-form-item>
        <el-button v-if="editable" type="primary" size="small" :loading="busy" @click="save(row)">保存关联</el-button>
      </el-form>
    </div>
    <el-dialog title="关联历史（最多50条）" :visible.sync="auditOpen" width="760px" append-to-body><el-table :data="audit"><el-table-column label="修订" prop="revision" width="70" /><el-table-column label="操作人ID" prop="operatorId" width="100" /><el-table-column label="时间" prop="createdAt" width="180" /><el-table-column label="快照" min-width="240"><template slot-scope="scope"><pre class="snapshot">{{ scope.row.snapshot }}</pre></template></el-table-column></el-table></el-dialog>
  </el-dialog>
</template>
<script>
import { listArticlePlacements, saveArticlePlacement, articlePlacementAudit, articleContentReview } from '@/api/life/essay'
import { checkPermi } from '@/utils/permission'
export default {
  name: 'ArticlePlacements',
  data() { return { open: false, article: {}, rows: [], error: '', busy: false, auditOpen: false, audit: [], names: { 'about.huahua-family': '花花一家', 'about.leilei': '蕾蕾', 'about.founders': '创办故事' }} },
  computed: { editable() { return checkPermi(['life:essay:edit']) && checkPermi(['life:essay:query']) } },
  methods: {
    async show(article = {}) { this.article = article; this.rows = []; this.open = true; await this.load() },
    async load() { this.busy = true; this.error = ''; try { this.rows = (await listArticlePlacements()).data || []; for (const row of this.rows) if (!row.reviewCurrent) Object.assign(row, { enabled: false, identityConfirmed: false, editorialApproved: false, authorizationConfirmed: false }); if (this.article.id) this.article = { ...this.article, ...(await articleContentReview(this.article.id)).data } } catch (error) { this.rows = []; this.error = error.message || '配置读取失败' } finally { this.busy = false } },
    attach(row) { Object.assign(row, { essayId: this.article.id, title: this.article.title, articleStatus: this.article.status, currentArticleHash: this.article.hash, reviewCurrent: false, enabled: false, authorizationConfirmed: false, editorialApproved: false, identityConfirmed: false, evidenceRef: '' }) },
    async history(row) { this.audit = (await articlePlacementAudit(row.placementKey)).data || []; this.auditOpen = true },
    async save(row) {
      if (this.busy) return
      this.busy = true
      try { await this.$confirm('保存“' + this.names[row.placementKey] + '”的文章关联？启用后可被游客阅读，请核对人物及全文审核依据。', '确认内容关联', { type: 'warning' }) } catch (_) { this.busy = false; return }
      this.busy = true; this.error = ''
      try {
        await saveArticlePlacement(row.placementKey, { expectedRevision: row.revision, expectedArticleHash: row.currentArticleHash, essayId: row.essayId, enabled: row.enabled, authorizationConfirmed: row.authorizationConfirmed, editorialApproved: row.editorialApproved, identityConfirmed: row.identityConfirmed, evidenceRef: row.evidenceRef })
        this.msgSuccess('关联已保存'); await this.load()
      } catch (error) { this.error = (error.message || '保存未完成') + '；先刷新核对已保存记录，再重试，避免使用旧修订。' } finally { this.busy = false }
    }
  }
}
</script>
<style scoped>
.placement { border-top: 1px solid #dce6df; margin-top: 24px; padding-top: 12px; }
.placement small { color: #64726d; font-size: 13px; font-weight: normal; }
.placement .el-checkbox { display: block; margin: 12px 0; }
.snapshot { white-space: pre-wrap; overflow-wrap: anywhere; font-size: 12px; }
</style>
