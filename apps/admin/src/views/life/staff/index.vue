<template>
  <div class="app-container">
    <el-alert
      title="先为每位工作人员新建个人后台账号并分配角色，再绑定本人登录过小程序的微信身份。后台角色控制后台页面与操作；下方单独配置移动工作台权限。"
      type="info"
      :closable="false"
    />
    <div style="margin: 16px 0">
      <el-button type="primary" @click="create">新增微信绑定</el-button>
      <el-button v-hasPermi="['system:user:list']" @click="$router.push('/system/user')">后台账号</el-button>
      <el-button v-hasPermi="['system:role:list']" @click="$router.push('/system/role')">角色授权</el-button>
      <el-button @click="refresh">刷新</el-button>
    </div>
    <el-table :data="rows">
      <el-table-column label="小程序微信身份" min-width="220"><template slot-scope="s">{{ accountLabel({ name: s.row.appName, phoneHint: s.row.phoneHint }) }}</template></el-table-column>
      <el-table-column label="后台账号" min-width="200">
        <template slot-scope="s">{{ s.row.staffName }}（{{ s.row.userName }}）</template>
      </el-table-column>
      <el-table-column label="后台角色" min-width="160">
        <template slot-scope="s">{{ s.row.sys_user_id === 1 ? '超级管理员' : (s.row.roleNames || '未分配角色') }}</template>
      </el-table-column>
      <el-table-column label="状态">
        <template slot-scope="s">{{ s.row.accountStatus !== '0' || s.row.accountDeleted !== '0' ? '后台账号不可用' : (s.row.enabled ? '启用' : '停用') }}</template>
      </el-table-column>
      <el-table-column label="业务操作">
        <template slot-scope="s">{{ s.row.can_operate ? '允许' : '只读与回访' }}</template>
      </el-table-column>
      <el-table-column label="收款登记">
        <template slot-scope="s">{{ s.row.can_payment ? '允许' : '不允许' }}</template>
      </el-table-column>
      <el-table-column label="操作" min-width="180">
        <template slot-scope="s">
          <el-button type="text" @click="edit(s.row)">编辑绑定与权限</el-button>
          <el-button type="text" @click="unbind(s.row)">解除绑定</el-button>
        </template>
      </el-table-column>
    </el-table>

    <el-dialog title="微信工作台绑定与权限" :visible.sync="open" width="620px">
      <el-form label-position="top">
        <el-form-item label="微信身份（请核实称呼与手机号）">
          <el-select v-model="form.appUserId" filterable remote :remote-method="searchApps" :disabled="editing" :loading="searching" placeholder="输入称呼或手机号搜索" style="width: 100%">
            <el-option v-for="u in apps" :key="u.id" :value="u.id" :label="accountLabel(u)" />
          </el-select>
        </el-form-item>
        <el-form-item label="绑定个人后台账号">
          <el-select v-model="form.sysUserId" filterable style="width: 100%">
            <el-option v-for="u in operators" :key="u.id" :value="u.id" :label="operatorLabel(u)" />
          </el-select>
        </el-form-item>
        <el-checkbox v-model="form.enabled">启用工作台</el-checkbox>
        <el-checkbox v-model="form.canOperate">档案与报名操作</el-checkbox>
        <el-checkbox v-model="form.canPayment">登记线下收款</el-checkbox>
        <p>所有启用工作人员均可查看轻友与期次，并新增、查看回访。后台角色变更不会自动修改以上工作台权限。</p>
        <p v-if="editing">可在此更换绑定的后台账号。每个后台账号只能绑定一个微信身份；停用仍保留绑定，解除绑定后可重新绑定。</p>
      </el-form>
      <span slot="footer">
        <el-button @click="open = false">取消</el-button>
        <el-button type="primary" :loading="saving" @click="save">保存授权</el-button>
      </span>
    </el-dialog>
  </div>
</template>

<script>
import request from '@/utils/request'

export default {
  name: 'InternalAccounts',
  data() {
    return {
      rows: [],
      apps: [],
      operators: [],
      form: {},
      open: false,
      editing: false,
      saving: false,
      searching: false,
      searchVersion: 0
    }
  },
  created() {
    this.load()
    this.loadOperators()
  },
  methods: {
    load() {
      return request({ url: '/life/staff/list' }).then(response => { this.rows = response.data })
    },
    loadOperators() {
      return request({ url: '/life/staff/operators' }).then(response => { this.operators = response.data })
    },
    refresh() {
      return Promise.all([this.load(), this.loadOperators()])
    },
    operatorLabel(account) {
      const roles = account.id === 1 ? '超级管理员' : (account.roleNames || '未分配角色')
      return (account.name || account.userName) + '（' + account.userName + '） · ' + roles
    },
    async unbind(row) {
      try {
        await this.$confirm('解除“' + row.userName + '”的微信绑定后，该微信将立即失去工作台访问权限。后台账号和已有业务记录保留。', '解除微信绑定', { type: 'warning' })
      } catch (_) {
        return
      }
      await request({ url: '/life/staff/' + row.app_user_id, method: 'delete' })
      await this.refresh()
      this.$message.success('微信绑定已解除')
    },
    accountLabel(account) {
      return (account.name || '轻友') + ' · ' + (account.phoneHint || '未登记手机号')
    },
    async searchApps(query) {
      const version = ++this.searchVersion
      this.searching = true
      try {
        const response = await request({ url: '/life/staff/app-users', params: { q: query }})
        if (version === this.searchVersion) this.apps = response.data
      } finally {
        if (version === this.searchVersion) this.searching = false
      }
    },
    create() {
      this.editing = false
      this.form = { enabled: true, canOperate: false, canPayment: false }
      this.searchApps('')
      this.open = true
    },
    edit(row) {
      this.editing = true
      ++this.searchVersion
      this.searching = false
      this.apps = [{ id: row.app_user_id, name: row.appName, phoneHint: row.phoneHint }]
      this.form = {
        appUserId: row.app_user_id,
        sysUserId: row.sys_user_id,
        enabled: Boolean(row.enabled),
        canOperate: Boolean(row.can_operate),
        canPayment: Boolean(row.can_payment)
      }
      this.open = true
    },
    async save() {
      if (!this.form.appUserId || !this.form.sysUserId) {
        return this.$message.error('请选择并核实两端账号')
      }
      this.saving = true
      try {
        await request({ url: '/life/staff', method: 'put', data: this.form })
        this.open = false
        await this.load()
        this.$message.success('授权已保存')
      } finally {
        this.saving = false
      }
    }
  }
}
</script>
