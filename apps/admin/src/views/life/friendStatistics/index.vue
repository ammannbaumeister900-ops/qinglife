<template>
  <div class="app-container statistics-page">
    <div class="toolbar">
      <el-radio-group v-model="preset" size="small" @change="applyPreset"><el-radio-button label="7">近7天</el-radio-button><el-radio-button label="30">近30天</el-radio-button><el-radio-button label="month">本月</el-radio-button><el-radio-button label="custom">自定义</el-radio-button></el-radio-group>
      <el-date-picker v-if="preset==='custom'" v-model="range" type="daterange" value-format="yyyy-MM-dd" range-separator="至" start-placeholder="开始日期" end-placeholder="结束日期" size="small" @change="load" />
      <span class="scope-note">登记相关指标按提交时间；新增轻友按建档时间。</span>
    </div>
    <div v-loading="loading">
      <div class="metrics">
        <article v-for="item in metricCards" :key="item.key" class="metric"><span>{{ item.label }}</span><strong>{{ metrics[item.key] || 0 }}</strong><small>{{ item.note }}</small></article>
      </div>
      <div class="chart-grid">
        <section v-for="section in sections" :key="section.key" class="chart-card">
          <h3>{{ section.title }}</h3><p class="muted">{{ section.note }}</p>
          <el-empty v-if="!(data[section.key] || []).length" description="当前范围暂无数据" :image-size="72" />
          <div v-for="row in data[section.key] || []" :key="row.label" class="bar-row"><span>{{ row.label || '未填写' }}</span><div class="bar-track"><i :style="{width:barWidth(section.key,row.value)}" /></div><b>{{ row.value }}</b></div>
        </section>
      </div>
    </div>
  </div>
</template>

<script>
import { getCustomerStatistics } from '@/api/life/customer'
export default {
  name: 'FriendStatistics',
  data() {
    return {
      loading: false, preset: '30', range: [], data: {}, metrics: {},
      metricCards: [{ key: 'friendTotal', label: '轻友总数', note: '当前全部主档' }, { key: 'newFriends', label: '范围内新增轻友', note: '按建档时间' }, { key: 'assessmentPeople', label: '登记人数', note: '去重轻友' }, { key: 'assessmentCount', label: '登记次数', note: '按提交次数' }, { key: 'retrainingFriends', label: '复训轻友数', note: '范围内登记 ≥ 2 次' }],
      sections: [
        { key: 'cleanBodyGoals', title: '清体目的', note: '选择人数；多选项目合计可超过登记人数' },
        { key: 'dietPreference', title: '饮食偏好', note: '按登记记录统计' },
        { key: 'energyStatus', title: '精力状态', note: '按登记记录统计' },
        { key: 'exerciseStatus', title: '运动情况', note: '按登记记录统计' },
        { key: 'emotionalStatus', title: '情绪状态', note: '选择人数；比例不按互斥分布理解' },
        { key: 'healthTop10', title: '身体情况 Top 10', note: '排除“无”，按出现次数统计' },
        { key: 'cities', title: '城市分布', note: '有登记轻友 Top 9 + 其他' }
      ]
    }
  },
  created() { this.applyPreset('30') },
  methods: {
    applyPreset(value) { const end = new Date(); const start = new Date(end); if (value === 'month')start.setDate(1); else start.setDate(end.getDate() - Number(value || 1) + 1); if (value !== 'custom') { this.range = [this.date(start), this.date(end)]; this.load() } },
    load() { if (!this.range || this.range.length !== 2) return; this.loading = true; getCustomerStatistics({ startDate: this.range[0], endDate: this.range[1] }).then(res => { this.data = res.data || {}; this.metrics = this.data.metrics || {} }).finally(() => { this.loading = false }) },
    date(value) { return [value.getFullYear(), String(value.getMonth() + 1).padStart(2, '0'), String(value.getDate()).padStart(2, '0')].join('-') },
    barWidth(key, value) { const rows = this.data[key] || []; const max = Math.max(1, ...rows.map(item => Number(item.value) || 0)); return Math.max(4, Math.round(Number(value || 0) / max * 100)) + '%' }
  }
}
</script>

<style scoped>
.statistics-page{background:#f5f7f5;min-height:calc(100vh - 84px)}.toolbar{display:flex;align-items:center;flex-wrap:wrap;gap:14px;margin-bottom:20px}.scope-note,.muted{color:#849087;font-size:12px}.metrics{display:grid;grid-template-columns:repeat(5,1fr);gap:14px}.metric,.chart-card{padding:20px;border:1px solid #e3e9e4;border-radius:12px;background:#fff}.metric span,.metric small{display:block;color:#728078}.metric strong{display:block;margin:8px 0;font-size:30px;color:#35453d}.chart-grid{display:grid;grid-template-columns:repeat(2,1fr);gap:16px;margin-top:18px}.chart-card h3{margin:0 0 6px}.bar-row{display:grid;grid-template-columns:95px 1fr 42px;align-items:center;gap:10px;margin-top:14px;font-size:13px}.bar-track{height:10px;overflow:hidden;border-radius:10px;background:#edf1ee}.bar-track i{display:block;height:100%;border-radius:10px;background:linear-gradient(90deg,#8aa18c,#b78369)}.bar-row b{text-align:right}
@media(max-width:900px){.metrics{grid-template-columns:repeat(2,1fr)}.chart-grid{grid-template-columns:1fr}}@media(max-width:480px){.metrics{grid-template-columns:1fr}.bar-row{grid-template-columns:78px 1fr 34px}}
</style>
