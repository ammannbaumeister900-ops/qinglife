<template>
  <div class="app-container">
    <el-alert
      title="请先核对报名与结算，再填写处理结果。标记处理完成不会自动改期或退款。"
      type="warning"
      :closable="false"
    />
    <el-button style="margin: 16px 0" @click="load">刷新申请</el-button>
    <el-table :data="rows">
      <el-table-column label="轻友" prop="nickname" />
      <el-table-column label="原期次" prop="originalSession" />
      <el-table-column label="目标期次" prop="targetSession" />
      <el-table-column label="申请人" prop="operatorName" />
      <el-table-column label="原因" prop="reason" />
      <el-table-column label="处理结果" prop="resolution" />
      <el-table-column label="操作">
        <template slot-scope="s">
          <el-button v-if="s.row.status === 'pending'" type="text" @click="resolve(s.row.id)">填写处理结果</el-button>
          <span v-else>已处理</span>
        </template>
      </el-table-column>
    </el-table>
  </div>
</template>

<script>
import request from '@/utils/request'

export default {
  name: 'RescheduleReview',
  data() {
    return { rows: [] }
  },
  created() {
    this.load()
  },
  methods: {
    load() {
      return request({ url: '/life/staff/reschedules' }).then(response => { this.rows = response.data })
    },
    async resolve(id) {
      try {
        const result = await this.$prompt('请记录实际处理结果（先在业务后台核对完成）', '改期申请处理', {
          inputValidator: value => Boolean(value && value.trim().length && value.length <= 500),
          inputErrorMessage: '请填写500字以内的处理结果'
        })
        await request({ url: '/life/staff/reschedules/' + id, method: 'put', data: { resolution: result.value }})
        await this.load()
      } catch (error) {
        if (error !== 'cancel' && error !== 'close') this.$message.error('保存失败，请刷新核实')
      }
    }
  }
}
</script>
