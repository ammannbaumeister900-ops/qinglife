<template>
  <div class="app-container">
    <el-alert
      title="工作人员必须先登录测试小程序，再由管理员核实身份并绑定后台账号。停用后工作台访问立即失效。"
      type="info"
      :closable="false"
    />
    <el-button type="primary" style="margin: 16px 0" @click="create">新增授权</el-button>
    <el-table :data="rows">
      <el-table-column label="小程序账号编号" prop="app_user_id" />
      <el-table-column label="小程序称呼" prop="appName" />
      <el-table-column label="工作人员" prop="staffName" />
      <el-table-column label="状态">
        <template slot-scope="s">{{ s.row.enabled ? '启用' : '停用' }}</template>
      </el-table-column>
      <el-table-column label="业务操作">
        <template slot-scope="s">{{ s.row.can_operate ? '允许' : '只读与回访' }}</template>
      </el-table-column>
      <el-table-column label="收款登记">
        <template slot-scope="s">{{ s.row.can_payment ? '允许' : '不允许' }}</template>
      </el-table-column>
      <el-table-column label="操作">
        <template slot-scope="s"><el-button type="text" @click="edit(s.row)">编辑授权</el-button></template>
      </el-table-column>
    </el-table>

    <el-dialog title="工作人员授权" :visible.sync="open" width="620px">
      <el-form label-position="top">
        <el-form-item label="小程序账号（核实编号与本人身份，不按昵称自动匹配）">
          <el-select v-model="form.appUserId" filterable remote :remote-method="searchApps" :disabled="editing" style="width: 100%">
            <el-option v-for="u in apps" :key="u.id" :value="u.id" :label="u.id + ' · ' + u.name" />
          </el-select>
        </el-form-item>
        <el-form-item label="绑定后台工作人员">
          <el-select v-model="form.sysUserId" filterable style="width: 100%">
            <el-option v-for="u in operators" :key="u.id" :value="u.id" :label="u.name + ' · ' + u.id" />
          </el-select>
        </el-form-item>
        <el-checkbox v-model="form.enabled">启用工作台</el-checkbox>
        <el-checkbox v-model="form.canOperate">档案与报名操作</el-checkbox>
        <el-checkbox v-model="form.canPayment">登记线下收款</el-checkbox>
        <p>所有启用工作人员均可查看轻友与期次，并新增、查看回访。</p>
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
      saving: false
    }
  },
  created() {
    this.load()
    request({ url: '/life/staff/operators' }).then(response => { this.operators = response.data })
  },
  methods: {
    load() {
      return request({ url: '/life/staff/list' }).then(response => { this.rows = response.data })
    },
    searchApps(query) {
      request({ url: '/life/staff/app-users', params: { q: query }}).then(response => { this.apps = response.data })
    },
    create() {
      this.editing = false
      this.form = { enabled: true, canOperate: false, canPayment: false }
      this.searchApps('')
      this.open = true
    },
    edit(row) {
      this.editing = true
      this.apps = [{ id: row.app_user_id, name: row.appName }]
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
