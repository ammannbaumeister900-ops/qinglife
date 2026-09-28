<template>
  <div class="app-container session-editor" v-loading="loading">
    <el-page-header @back="back" :content="form.id ? '编辑期次' : '新增期次'" />
    <el-form ref="form" :model="form" :rules="rules" label-position="top" class="session-form">
      <h3>期次与日期</h3>
      <div class="form-grid">
        <el-form-item label="期次编号" prop="sessionNumber"><el-input-number v-model="form.sessionNumber" :min="1" :max="2147483647" :precision="0" :controls="false" placeholder="例如：501" /></el-form-item>
        <el-form-item label="期次名称" prop="name"><el-input v-model="form.name" maxlength="100" placeholder="例如：轻体营·第501期" /><div class="hint">按“XX营·第XXX期”填写，例如：清体营·第501期。</div></el-form-item>
        <el-form-item label="当期主题" prop="theme"><el-input v-model="form.theme" maxlength="200" placeholder="例如：在秋天，重新听见身体" /></el-form-item>
        <el-form-item label="展示封面" prop="coverUrl"><imageLocalUpload v-model="form.coverUrl" :limit="1" /><div class="hint">用于首页、轻体营列表和活动详情；建议横向图片。</div></el-form-item>
        <el-form-item label="开始日期" prop="startDate"><el-date-picker v-model="form.startDate" type="date" value-format="yyyy-MM-dd" :picker-options="startOptions" placeholder="选择活动第一天" /></el-form-item>
        <el-form-item label="结束日期" prop="endDate"><el-date-picker v-model="form.endDate" type="date" value-format="yyyy-MM-dd" :picker-options="endOptions" placeholder="选择活动最后一天" /><div class="hint">开始日 00:00 起，结束日全天有效（至当天 24:00）。</div></el-form-item>
        <el-form-item label="报名开放日期"><el-date-picker v-model="openDate" type="date" value-format="yyyy-MM-dd" :picker-options="openOptions" placeholder="不填则不限制开放日期" /></el-form-item>
        <el-form-item label="报名截止日期"><el-date-picker v-model="closeDate" type="date" value-format="yyyy-MM-dd" :picker-options="closeOptions" placeholder="不填则至活动结束日" /><div class="hint">开放日从 00:00 开始，截止日全天可报名；仍受活动状态和名额限制。</div></el-form-item>
      </div>
      <h3>地点与导游</h3>
      <div class="form-grid">
        <el-form-item label="城市（省 / 市）" prop="city"><el-cascader v-model="region" :options="regions" filterable placeholder="选择省、市" @change="changeRegion" /><div v-if="legacyCity" class="hint">原城市：{{legacyCity}}。请选择省市后保存。</div></el-form-item>
        <el-form-item label="公开地点"><el-input v-model="form.publicVenue" maxlength="200" placeholder="例如：崇明××营地" /><div class="hint">显示在小程序活动详情中，填写场地或区域，无需重复城市。请勿填写不宜公开的门牌或联系方式。</div></el-form-item>
        <el-form-item label="导游（可多选）"><el-select v-model="guides" multiple placeholder="选择本期导游"><el-option v-for="name in guideOptions" :key="name" :label="name" :value="name" /></el-select></el-form-item>
      </div>
      <h3>名额、价格与报名</h3>
      <div class="form-grid">
        <el-form-item label="名额" prop="capacity"><el-input-number v-model="form.capacity" :min="1" :max="1000000" :precision="0" :controls="false" /></el-form-item>
        <el-form-item label="状态"><el-select v-model="form.status"><el-option v-for="item in statusOptions" :key="item.value" :label="item.label" :value="item.value" /></el-select></el-form-item>
        <el-form-item label="新轻友价格（元 / 人）" prop="standardPrice"><el-input-number v-model="form.standardPrice" :min="0" :max="99999999.99" :precision="2" :controls="false" /></el-form-item>
        <el-form-item label="老轻友价格（元 / 人）" prop="returningPrice"><el-input-number v-model="form.returningPrice" :min="0" :max="99999999.99" :precision="2" :controls="false" /><div class="hint">本人完成过至少一期轻体营，适用老轻友价格。多人报名分别计价；改价不影响已有订单。</div></el-form-item>
        <el-form-item label="报名确认方式" class="full-width"><el-radio-group v-model="form.registrationConfirmMode"><el-radio label="manual">人工确认：报名后等待工作人员确认</el-radio><el-radio label="auto">自动确认：提交报名即确认名额</el-radio></el-radio-group><div class="hint">确认名额不代表已付款，付款状态需另行登记。</div></el-form-item>
        <el-form-item label="活动简介" class="full-width"><el-input v-model="form.intro" type="textarea" :rows="4" /></el-form-item>
        <el-form-item label="取消规则" class="full-width"><el-input v-model="form.cancelPolicy" type="textarea" :rows="3" /></el-form-item>
      </div>
      <div class="form-actions"><el-button type="primary" :loading="submitting" :disabled="loading || loadFailed" @click="submit">保存期次</el-button><el-button @click="back">返回列表</el-button></div>
    </el-form>
  </div>
</template>

<script>
import { getSession, addSession, updateSession } from '@/api/life/session'
import cities from '@/data/citys.json'
const regions = cities.map(p => ({ value: p.label, label: p.label, children: (p.children || []).map(c => ({ value: c.label, label: c.label })) }))
const day = date => { const pad = n => String(n).padStart(2, '0'); return `${date.getFullYear()}-${pad(date.getMonth()+1)}-${pad(date.getDate())}` }
export default {
  name: 'QlSessionEdit',
  data() {
    return {
      loading: false, loadFailed: false, submitting: false, regions, region: ['上海市', '上海城区'], legacyCity: '', guides: [], openDate: '', closeDate: '',
      form: { sessionNumber: undefined, name: '', theme: '', coverUrl: '', startDate: '', endDate: '', capacity: 30, standardPrice: 3800, returningPrice: 2500, province: '上海市', city: '上海市', publicVenue: '', status: 'draft', registrationConfirmMode: 'manual', intro: '', cancelPolicy: '' },
      statusOptions: [{label:'草稿',value:'draft'},{label:'报名中',value:'open'},{label:'已关闭',value:'closed'},{label:'进行中',value:'in_progress'},{label:'已完成',value:'completed'},{label:'已取消',value:'cancelled'}],
      rules: { sessionNumber: [{required:true,message:'请输入期次编号',trigger:'change'}], name: [{required:true,message:'请输入期次名称，例如：轻体营·第501期',trigger:'blur'}], theme: [{required:true,message:'请输入本期主题',trigger:'blur'}], coverUrl: [{required:true,message:'请上传本期展示封面',trigger:'change'}], startDate: [{required:true,message:'请选择开始日期',trigger:'change'}], endDate: [{required:true,message:'请选择结束日期',trigger:'change'}], city: [{required:true,message:'请选择省、市',trigger:'change'}], capacity: [{required:true,message:'请输入名额',trigger:'change'}], standardPrice: [{required:true,message:'请输入新轻友价格',trigger:'change'}], returningPrice: [{required:true,message:'请输入老轻友价格',trigger:'change'}] }
    }
  },
  computed: {
    guideOptions() { return [...new Set(['公主','大海','军军',...this.guides])] },
    startOptions() { return { disabledDate: d => !!this.form.endDate && day(d) > this.form.endDate } },
    endOptions() { return { disabledDate: d => !!this.form.startDate && day(d) < this.form.startDate } },
    openOptions() { return { disabledDate: d => !!this.closeDate && day(d) > this.closeDate } },
    closeOptions() { return { disabledDate: d => !!this.openDate && day(d) < this.openDate } }
  },
  created() {
    if (!this.$route.params.id) return
    this.loading = true
    getSession(this.$route.params.id).then(res => {
      if (!res.data) throw new Error('期次不存在')
      this.form = res.data
      this.guides = (this.form.leaderName || '').split(/[,，、]/).map(x=>x.trim()).filter(Boolean)
      this.openDate = (this.form.registrationOpenAt || '').slice(0,10)
      this.closeDate = (this.form.registrationCloseAt || '').slice(0,10)
      const p = regions.find(p => p.label === this.form.province || p.label === this.form.city || p.label.replace(/[省市]$/, '') === this.form.city)
        || regions.find(p => p.children.some(c=>c.label===this.form.city))
      const c = p && (p.children.find(c=>c.label===this.form.city) || (/^(上海|北京|天津|重庆)/.test(p.label) ? p.children[0] : null))
      this.region = p && c ? [p.value,c.value] : []
      if (this.region.length) this.changeRegion(this.region)
      else { this.legacyCity = this.form.city || ''; this.form.city = '' }
    }).catch(error => { this.loadFailed = true; this.msgError(error.message || '加载期次失败，请返回重试') }).finally(()=>{this.loading=false})
  },
  methods: {
    changeRegion(value) { this.form.province = value[0] || ''; this.form.city = /城区$/.test(value[1] || '') ? value[0] : value[1] || ''; this.legacyCity = '' },
    back() { this.$router.push('/activityOperations/session') },
    submit() {
      if (this.submitting || this.loading || this.loadFailed) return
      this.$refs.form.validate(valid => {
        if (!valid) return
        if (this.form.endDate < this.form.startDate) return this.msgError('结束日期不能早于开始日期')
        if (this.openDate && this.closeDate && this.closeDate < this.openDate) return this.msgError('报名截止日期不能早于开放日期')
        const payload = { ...this.form, leaderName: this.guides.join('、'), registrationOpenAt: this.openDate ? this.openDate+' 00:00:00' : null, registrationCloseAt: this.closeDate ? this.closeDate+' 23:59:59' : null }
        this.submitting = true
        const request = payload.id ? updateSession(payload) : addSession(payload)
        request.then(()=>{this.msgSuccess('期次已保存');this.back()}).finally(()=>{this.submitting=false})
      })
    }
  }
}
</script>

<style scoped>
.session-editor { max-width: 1240px; margin: 0 auto; }
.session-form { margin-top: 28px; padding-bottom: 80px; }
.session-form h3 { margin: 28px 0 20px; padding-bottom: 12px; border-bottom: 1px solid #ebeef5; }
.form-grid { display: grid; grid-template-columns: minmax(0,1fr) minmax(0,1fr); column-gap: 32px; }
.full-width { grid-column: 1 / -1; }
.session-form >>> .el-input-number, .session-form >>> .el-select, .session-form >>> .el-cascader, .session-form >>> .el-date-editor { width: 100%; }
.session-form >>> .el-input-number .el-input__inner { text-align: left; padding: 0 15px; }
.session-form >>> .el-radio { margin-bottom: 12px; line-height: 24px; white-space: normal; }
.hint { color: #606266; font-size: 13px; line-height: 1.7; margin-top: 6px; }
.form-actions { padding: 20px 0; border-top: 1px solid #ebeef5; }
@media (max-width: 760px) { .form-grid { grid-template-columns: minmax(0,1fr); } }
</style>
