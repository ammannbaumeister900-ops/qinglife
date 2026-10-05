<template>
  <div class="app-container">
    <el-form :inline="true" @submit.native.prevent="search">
      <el-form-item label="账号"><el-input v-model="query.keyword" clearable placeholder="账号或昵称" @keyup.enter.native="search" /></el-form-item>
      <el-form-item><el-button type="primary" @click="search">查询</el-button></el-form-item>
    </el-form>
    <el-table v-loading="loading" :data="rows">
      <el-table-column prop="userName" label="账号" min-width="140" />
      <el-table-column prop="nickName" label="昵称" min-width="140" />
      <el-table-column prop="roleNames" label="现有角色" min-width="180" />
      <el-table-column label="状态" width="90"><template slot-scope="s">{{ s.row.status === '0' ? '正常' : '停用' }}</template></el-table-column>
      <el-table-column label="页面规则" width="150"><template slot-scope="s">{{ s.row.userId === 1 ? '全部页面' : Number(s.row.inherit) ? '沿用角色' : '按账号配置' }}</template></el-table-column>
      <el-table-column label="操作" width="120"><template slot-scope="s"><el-button type="text" @click="configure(s.row)">配置页面</el-button></template></el-table-column>
    </el-table>
    <pagination v-show="total > 0" :total="total" :page.sync="query.pageNum" :limit.sync="query.pageSize" @pagination="load" />
    <el-dialog :title="'账号权限 · ' + (account.nickName || account.userName || '')" :visible.sync="visible" width="720px" append-to-body :close-on-click-modal="!saving">
      <div v-loading="detailLoading">
        <el-alert title="可展示页面受账号现有角色限制；页面内的操作和数据范围继续由角色决定。保存后接口权限立即生效，页面菜单刷新后更新。" type="info" :closable="false" show-icon class="mb20" />
        <el-alert v-if="protectedAccount" title="超级管理员保留全部页面权限。" type="warning" :closable="false" class="mb20" />
        <el-radio-group v-model="inherit" :disabled="readOnly"><el-radio :label="true">沿用角色页面</el-radio><el-radio :label="false">按账号配置</el-radio></el-radio-group>
        <div class="page-list"><el-checkbox-group v-model="pageIds" :disabled="inherit || readOnly">
          <div v-for="group in groups" :key="group.name" class="page-group"><h4>{{ group.name }}</h4><el-checkbox v-for="page in group.pages" :key="page.menuId" :label="page.menuId" :disabled="!page.eligible">{{ page.menuName }}<span v-if="!page.eligible" class="muted">（角色未授权）</span></el-checkbox></div>
        </el-checkbox-group></div>
      </div>
      <div slot="footer"><el-button v-hasPermi="['system:accountPermission:edit']" type="primary" :loading="saving" :disabled="protectedAccount || detailLoading" @click="save">保存权限</el-button><el-button @click="visible=false">关闭</el-button></div>
    </el-dialog>
  </div>
</template>
<script>
import { listAccounts, getAccountPages, saveAccountPages } from '@/api/system/accountPermission'
import { checkPermi } from '@/utils/permission'
export default {
  name: 'AccountPermission',
  data() { return { query: { keyword: '', pageNum: 1, pageSize: 20 }, rows: [], total: 0, loading: false, visible: false, saving: false, detailLoading: false, account: {}, pages: [], pageIds: [], inherit: true, protectedAccount: false } },
  computed: {
    readOnly() { return this.protectedAccount || !checkPermi(['system:accountPermission:edit']) },
    groups() { const groups = []; this.pages.forEach(page => { let group = groups.find(item => item.name === page.groupName); if (!group) { group = { name: page.groupName, pages: [] }; groups.push(group) } group.pages.push(page) }); return groups }
  },
  created() { this.load() },
  methods: {
    search() { this.query.pageNum = 1; this.load() },
    async load() { this.loading = true; try { const res = await listAccounts(this.query); this.rows = res.data.rows || []; this.total = res.data.total || 0 } finally { this.loading = false } },
    async configure(row) {
      this.account = row; this.pages = []; this.pageIds = []; this.visible = true; this.detailLoading = true
      const version = this.detailVersion = (this.detailVersion || 0) + 1
      try { const res = await getAccountPages(row.userId); if (version !== this.detailVersion) return; Object.assign(this, { account: res.data.user, pages: res.data.pages, pageIds: res.data.pageIds, inherit: res.data.inherit, protectedAccount: res.data.protectedAccount }) } finally { if (version === this.detailVersion) this.detailLoading = false }
    },
    async save() {
      if (this.readOnly || this.saving || this.detailLoading) return
      this.saving = true
      try { await saveAccountPages(this.account.userId, { inherit: this.inherit, pageIds: this.inherit ? [] : this.pageIds }); this.msgSuccess('账号页面权限已保存'); this.visible = false; this.load() } finally { this.saving = false }
    }
  }
}
</script>
<style scoped>
.page-list{max-height:48vh;overflow:auto;margin-top:20px}.page-group{padding:4px 0 14px;border-bottom:1px solid #ebeef5}.page-group h4{font-size:14px;margin:12px 0}.page-group .el-checkbox{margin-bottom:12px}.muted{font-size:12px;color:#909399}
</style>
