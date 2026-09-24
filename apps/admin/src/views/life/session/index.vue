<template>
  <div class="app-container">
    <el-form ref="queryForm" :model="queryParams" :inline="true" v-show="showSearch" label-width="68px">
      <el-form-item label="期次" prop="sessionNumber"><el-input-number v-model="queryParams.sessionNumber" :min="1" :controls="false" placeholder="期次编号" size="small" /></el-form-item>
      <el-form-item label="名称" prop="name"><el-input v-model="queryParams.name" placeholder="期次名称" clearable size="small" @keyup.enter.native="handleQuery" /></el-form-item>
      <el-form-item label="状态" prop="status"><el-select v-model="queryParams.status" placeholder="全部" clearable size="small"><el-option v-for="item in statusOptions" :key="item.value" :label="item.label" :value="item.value" /></el-select></el-form-item>
      <el-form-item><el-button type="primary" icon="el-icon-search" size="mini" @click="handleQuery">搜索</el-button><el-button icon="el-icon-refresh" size="mini" @click="resetQuery">重置</el-button></el-form-item>
    </el-form>
    <el-row :gutter="10" class="mb8"><el-col :span="1.5"><el-button type="primary" plain icon="el-icon-plus" size="mini" @click="handleAdd" v-hasPermi="['life:session:add']">新增期次</el-button></el-col><right-toolbar :showSearch.sync="showSearch" @queryTable="getList" /></el-row>
    <el-table v-loading="loading" :data="sessionList">
      <el-table-column label="期次" prop="sessionNumber" width="120"><template slot-scope="scope">第 {{ scope.row.sessionNumber }} 期</template></el-table-column>
      <el-table-column label="名称" prop="name" min-width="160" />
      <el-table-column label="当期主题" prop="theme" min-width="180" show-overflow-tooltip />
      <el-table-column label="封面" width="100"><template slot-scope="scope"><el-image v-if="scope.row.coverUrl" :src="scope.row.coverUrl" fit="cover" style="width:72px;height:44px" /></template></el-table-column>
      <el-table-column label="活动日期" min-width="190"><template slot-scope="scope">{{ scope.row.startDate }} 至 {{ scope.row.endDate }}</template></el-table-column>
      <el-table-column label="城市" prop="city" width="90" />
      <el-table-column label="公开地点" min-width="140" show-overflow-tooltip><template slot-scope="scope">{{ scope.row.publicVenue || scope.row.venue }}</template></el-table-column>
      <el-table-column label="名额" prop="capacity" width="70" />
      <el-table-column label="新轻友价格" width="110"><template slot-scope="scope">¥{{ Number(scope.row.standardPrice || 0).toFixed(2) }}</template></el-table-column>
      <el-table-column label="老轻友价格" width="110"><template slot-scope="scope">¥{{ Number(scope.row.returningPrice || 0).toFixed(2) }}</template></el-table-column>
      <el-table-column label="确认方式" width="90"><template slot-scope="scope">{{ scope.row.registrationConfirmMode === 'auto' ? '自动确认' : '人工确认' }}</template></el-table-column>
      <el-table-column label="状态" width="90"><template slot-scope="scope"><el-tag size="mini" :type="statusType(scope.row.status)">{{ statusText(scope.row.status) }}</el-tag></template></el-table-column>
      <el-table-column label="操作" width="170" fixed="right"><template slot-scope="scope"><el-button size="mini" type="text" icon="el-icon-edit" @click="handleUpdate(scope.row)" v-hasPermi="['life:session:edit']">编辑</el-button><el-button v-if="scope.row.status === 'open'" size="mini" type="text" @click="handleInvite(scope.row)" v-hasPermi="['life:session:edit']">生成邀请</el-button></template></el-table-column>
    </el-table>
    <pagination v-show="total > 0" :total="total" :page.sync="queryParams.pageNum" :limit.sync="queryParams.pageSize" @pagination="getList" />
    <el-dialog title="活动邀请海报" :visible.sync="posterOpen" width="520px" append-to-body>
      <div v-if="posterSession" class="invite-poster" aria-label="活动邀请海报">
        <div class="poster-brand">轻生活 · 见面与陪伴</div>
        <div class="poster-label">本期活动邀请</div>
        <h2>第 {{ posterSession.sessionNumber }} 期</h2>
        <h1>{{ posterSession.name }}</h1>
        <div class="poster-divider" />
        <p class="poster-date">{{ posterDate }}</p>
        <p v-if="posterSession.city || posterSession.publicVenue || posterSession.venue" class="poster-place">{{ [posterSession.city, posterSession.publicVenue || posterSession.venue].filter(Boolean).join(' · ') }}</p>
        <div class="mini-code-placeholder"><span>小程序码</span><small>发布后自动生成</small></div>
        <p class="poster-hint">小程序发布前，请向工作人员获取报名方式</p>
      </div>
      <div slot="footer"><el-button @click="posterOpen = false">关闭</el-button><el-button type="primary" @click="downloadPoster">下载海报</el-button></div>
    </el-dialog>
  </div>
</template>

<script>
import { listSession, createSessionInvitation } from '@/api/life/session'

export default {
  name: 'QlSession',
  data() {
    return {
      loading: false, showSearch: true, total: 0, sessionList: [], posterOpen: false, posterSession: null,
      statusOptions: [{ label: '草稿', value: 'draft' }, { label: '报名中', value: 'open' }, { label: '已关闭', value: 'closed' }, { label: '进行中', value: 'in_progress' }, { label: '已完成', value: 'completed' }, { label: '已取消', value: 'cancelled' }],
      queryParams: { pageNum: 1, pageSize: 10, sessionNumber: undefined, name: undefined, status: undefined }
    }
  },
  created() { this.getList() },
  activated() { this.getList() },
  computed: {
    posterDate() {
      if (!this.posterSession) return ''
      return this.posterSession.startDate === this.posterSession.endDate
        ? this.posterSession.startDate
        : this.posterSession.startDate + ' 至 ' + this.posterSession.endDate
    }
  },
  methods: {
    getList() { this.loading = true; listSession(this.queryParams).then(res => { this.sessionList = res.rows; this.total = res.total; this.loading = false }).catch(() => { this.loading = false }) },
    handleQuery() { this.queryParams.pageNum = 1; this.getList() }, resetQuery() { this.resetForm('queryForm'); this.handleQuery() },
    handleAdd() { this.$router.push('/activityOperations/session-edit') },
    handleUpdate(row) { this.$router.push('/activityOperations/session-edit/' + row.id) },
    handleInvite(row) {
      createSessionInvitation(row.id).then(() => { this.posterSession = row; this.posterOpen = true })
    },
    escapeSvg(value) { return String(value || '').replace(/[&<>"']/g, char => ({ '&': '&amp;', '<': '&lt;', '>': '&gt;', '"': '&quot;', "'": '&apos;' })[char]) },
    downloadPoster() {
      const session = this.posterSession
      if (!session) return
      const name = this.escapeSvg(session.name)
      const detail = this.escapeSvg(`第 ${session.sessionNumber} 期 · ${this.posterDate}`)
      const place = this.escapeSvg([session.city, session.publicVenue || session.venue].filter(Boolean).join(' · ') || '地点以活动通知为准')
      const svg = `<svg xmlns="http://www.w3.org/2000/svg" width="1080" height="1520" viewBox="0 0 1080 1520"><rect width="1080" height="1520" fill="#f1f5ef"/><rect x="70" y="70" width="940" height="1380" rx="36" fill="#ffffff"/><text x="130" y="180" fill="#688276" font-size="34" font-family="Arial,Microsoft YaHei">轻生活 · 见面与陪伴</text><text x="130" y="272" fill="#84958b" font-size="28" font-family="Arial,Microsoft YaHei">本期活动邀请</text><text x="130" y="390" fill="#375244" font-size="56" font-weight="bold" font-family="Arial,Microsoft YaHei">${detail}</text><text x="130" y="490" fill="#20362c" font-size="62" font-weight="bold" font-family="Arial,Microsoft YaHei">${name}</text><line x1="130" y1="570" x2="950" y2="570" stroke="#dbe5dc" stroke-width="3"/><text x="130" y="655" fill="#52685b" font-size="34" font-family="Arial,Microsoft YaHei">${place}</text><rect x="344" y="790" width="392" height="392" rx="16" fill="#f3f6f3" stroke="#9bb3a1" stroke-width="4"/><path d="M382 828h72v72h-72zm116 0h48v48h-48zm92 0h72v72h-72zm-208 116h48v48h-48zm92 0h72v72h-72zm116 0h48v48h-48zm-208 92h72v72h-72zm116 0h48v48h-48zm92 0h72v72h-72z" fill="#557264"/><text x="540" y="1245" text-anchor="middle" fill="#557264" font-size="34" font-family="Arial,Microsoft YaHei">小程序码待接入</text><text x="540" y="1322" text-anchor="middle" fill="#84958b" font-size="26" font-family="Arial,Microsoft YaHei">小程序发布后将自动替换为动态码</text></svg>`
      const blob = new Blob([svg], { type: 'image/svg+xml;charset=utf-8' })
      const url = URL.createObjectURL(blob); const link = document.createElement('a')
      link.href = url; link.download = `轻生活第${session.sessionNumber}期邀请海报.svg`; link.click(); URL.revokeObjectURL(url)
    },
    statusText(value) { const item = this.statusOptions.find(option => option.value === value); return item ? item.label : value },
    statusType(value) { return ({ open: 'success', in_progress: 'warning', completed: 'info', cancelled: 'danger' })[value] || '' }
  }
}
</script>

<style scoped>
.invite-poster { min-height: 610px; padding: 38px; color: #274134; border-radius: 14px; background: linear-gradient(145deg, #e7efe8, #f8faf7 45%, #dce9dd); text-align: center; }
.poster-brand,.poster-label,.poster-hint { color: #728579; font-size: 13px; letter-spacing: 1px; }.poster-label { margin-top: 50px; }.invite-poster h2 { margin: 12px 0; font-size: 24px; }.invite-poster h1 { margin: 0; font-size: 30px; line-height: 1.4; }.poster-divider { width: 42px; height: 3px; margin: 22px auto; background: #88a48f; }.poster-date { font-size: 17px; }.poster-place { color: #607468; }.mini-code-placeholder { width: 160px; height: 160px; margin: 42px auto 18px; display: flex; flex-direction: column; justify-content: center; gap: 8px; background: repeating-linear-gradient(45deg,#edf3ee,#edf3ee 8px,#e3ece5 8px,#e3ece5 16px); border: 2px solid #91a995; color: #42604c; }.mini-code-placeholder span { font-weight: 700; font-size: 20px; }.mini-code-placeholder small { color: #607468; }.poster-hint { letter-spacing: 0; }
</style>
