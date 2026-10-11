<template>
  <div v-loading="loading" class="app-container session-editor">
    <el-page-header :content="form.id ? '编辑期次' : '新增期次'" @back="back" />
    <el-form ref="form" :model="form" :rules="rules" label-position="top" class="session-form">
      <h3>期次与日期</h3>
      <div class="form-grid">
        <el-form-item label="期次编号" prop="sessionNumber"><el-input-number v-model="form.sessionNumber" :min="1" :max="2147483647" :precision="0" :controls="false" placeholder="例如：501" /></el-form-item>
        <el-form-item label="期次名称" prop="name"><el-select v-model="form.name" placeholder="选择期次名称"><el-option v-for="item in nameOptions" :key="item.dictValue" :label="item.dictLabel" :value="item.dictValue" /></el-select></el-form-item>
        <el-form-item label="展示封面" prop="coverUrl">
          <el-radio-group v-model="coverMode"><el-radio label="auto">自动生成</el-radio><el-radio label="upload">上传图片</el-radio></el-radio-group>
          <div v-if="coverMode === 'auto'" class="cover-area">
            <img v-if="coverPreviewUrl" :src="coverPreviewUrl" class="cover-preview" alt="本期自动封面预览">
            <div v-else class="hint">{{ coverPreviewLoading ? '正在生成预览…' : coverPreviewError || '填写期次编号后自动生成封面' }}</div>
            <div class="hint">固定模板，期数自动更新；保存时生成，无需上传图片。</div>
          </div>
          <image-upload v-else v-model="form.coverUrl" :limit="1" />
          <div class="hint">首页、列表和详情统一使用 2:1 横向封面；上传建议 1200 × 600 像素。</div>
        </el-form-item>
        <el-form-item label="开始日期" prop="startDate"><el-date-picker v-model="form.startDate" type="date" value-format="yyyy-MM-dd" :picker-options="startOptions" placeholder="选择活动第一天" /></el-form-item>
        <el-form-item label="结束日期" prop="endDate"><el-date-picker v-model="form.endDate" type="date" value-format="yyyy-MM-dd" :picker-options="endOptions" placeholder="选择活动最后一天" /><div class="hint">开始日 00:00 起，结束日全天有效（至当天 24:00）。</div></el-form-item>
        <el-form-item label="报名开放日期"><el-date-picker v-model="openDate" type="date" value-format="yyyy-MM-dd" :picker-options="openOptions" placeholder="不填则不限制开放日期" @change="openManual = true" /></el-form-item>
        <el-form-item label="报名截止日期"><el-date-picker v-model="closeDate" type="date" value-format="yyyy-MM-dd" :picker-options="closeOptions" placeholder="不填则至活动结束日" @change="closeManual = true" /><div class="hint">开放日从 00:00 开始，截止日全天可报名；仍受活动状态和名额限制。</div></el-form-item>
      </div>
      <h3>地点与导游</h3>
      <div class="form-grid">
        <el-form-item label="城市（省 / 市）" prop="city"><el-cascader v-model="region" :options="regions" filterable placeholder="选择省、市" @change="changeRegion" /><div v-if="legacyCity" class="hint">原城市：{{ legacyCity }}。请选择省市后保存。</div></el-form-item>
        <el-form-item label="公开地点" prop="publicVenue"><el-select v-model="form.publicVenue" placeholder="选择公开地点"><el-option v-for="item in venueOptions" :key="item.dictValue" :label="item.dictLabel" :value="item.dictValue" /></el-select></el-form-item>
        <el-form-item label="导游（可多选）"><el-select v-model="guides" multiple placeholder="选择本期导游" @change="guidesTouched = true"><el-option v-for="person in guideOptions" :key="person.id" :label="person.name + ' · ' + person.id" :value="person.id" /></el-select><div v-if="guideDefaultsError || legacyGuides" class="hint">{{ guideDefaultsError || ('历史导游：' + legacyGuides) }}</div></el-form-item>
      </div>
      <h3>名额、价格与报名</h3>
      <div class="form-grid">
        <el-form-item label="名额" prop="capacity"><el-input-number v-model="form.capacity" :min="1" :max="1000000" :precision="0" :controls="false" /></el-form-item>
        <el-form-item label="状态"><el-select v-model="form.status"><el-option v-for="item in statusOptions" :key="item.value" :label="item.label" :value="item.value" /></el-select><div class="hint">报名中、进行中随日期变化；其他状态保留人工设置。</div></el-form-item>
        <el-form-item label="新轻友价格（元 / 人）" prop="standardPrice"><el-input-number v-model="form.standardPrice" :min="0" :max="99999999.99" :precision="2" :controls="false" /></el-form-item>
        <el-form-item label="老轻友价格（元 / 人）" prop="returningPrice"><el-input-number v-model="form.returningPrice" :min="0" :max="99999999.99" :precision="2" :controls="false" /><div class="hint">本人完成过至少一期轻体营，适用老轻友价格。多人报名分别计价；改价不影响已有订单。</div></el-form-item>
        <el-form-item label="报名确认方式" class="full-width"><el-radio-group v-model="form.registrationConfirmMode"><el-radio label="manual">人工确认</el-radio><el-radio label="auto">缴费核验后自动确认</el-radio></el-radio-group><div class="hint">缴费后确认尚未接通。新报名保持待确认，不占名额。</div></el-form-item>
        <el-form-item label="活动简介" class="full-width"><el-input v-model="form.intro" type="textarea" :rows="4" /></el-form-item>
        <el-form-item label="取消规则" class="full-width"><el-input v-model="form.cancelPolicy" type="textarea" :rows="3" /></el-form-item>
      </div>
      <div class="form-actions"><el-button type="primary" :loading="submitting" :disabled="loading || loadFailed" @click="submit">保存期次</el-button><el-button @click="back">返回列表</el-button></div>
    </el-form>
  </div>
</template>

<script>
import { getSession, addSession, updateSession, previewSessionCover, sessionGuides } from '@/api/life/session'
import { getDicts } from '@/api/system/dict/data'
import cities from '@/data/citys.json'
import ImageUpload from '@/components/ImageUpload'
const isAutoCover = value => !value || /\/session-covers\/v[123]-\d+\.png$/.test(value)
const regions = cities.map(p => ({ value: p.label, label: p.label, children: (p.children || []).map(c => ({ value: c.label, label: c.label })) }))
const day = date => { const pad = n => String(n).padStart(2, '0'); return `${date.getFullYear()}-${pad(date.getMonth() + 1)}-${pad(date.getDate())}` }
const offsetDay = (value, offset) => { if (!value) return ''; const [year, month, date] = value.split('-').map(Number); return day(new Date(year, month - 1, date + offset)) }
const withHistorical = (options, value) => value && !options.some(item => item.dictValue === value) ? options.concat({ dictValue: value, dictLabel: value + '（历史值）' }) : options
export default {
  name: 'QlSessionEdit',
  components: { ImageUpload },
  data() {
    return {
      initialSessionNumber: undefined, coverMode: 'auto', coverPreviewUrl: '', coverPreviewLoading: false, coverPreviewError: '',
      loading: false, loadFailed: false, submitting: false, regions, region: ['上海市', '上海城区'], legacyCity: '', guides: [], guideChoices: [], guidesTouched: false, guideDefaultsError: '', legacyGuides: '', nameChoices: [], venueChoices: [], openDate: '', closeDate: '', openManual: false, closeManual: false,
      form: { sessionNumber: undefined, name: '', theme: '', coverUrl: '', startDate: '', endDate: '', capacity: 50, standardPrice: 3800, returningPrice: 2500, province: '上海市', city: '上海市', publicVenue: '', status: 'draft', registrationConfirmMode: 'auto', intro: '', cancelPolicy: '' },
      statusOptions: [{ label: '草稿', value: 'draft' }, { label: '报名中', value: 'open' }, { label: '已关闭', value: 'closed' }, { label: '进行中', value: 'in_progress' }, { label: '已完成', value: 'completed' }, { label: '已取消', value: 'cancelled' }],
      rules: { sessionNumber: [{ required: true, message: '请输入期次编号', trigger: 'change' }], name: [{ required: true, message: '请选择期次名称', trigger: 'change' }], startDate: [{ required: true, message: '请选择开始日期', trigger: 'change' }], endDate: [{ required: true, message: '请选择结束日期', trigger: 'change' }], city: [{ required: true, message: '请选择省、市', trigger: 'change' }], capacity: [{ required: true, message: '请输入名额', trigger: 'change' }], standardPrice: [{ required: true, message: '请输入新轻友价格', trigger: 'change' }], returningPrice: [{ required: true, message: '请输入老轻友价格', trigger: 'change' }] }
    }
  },
  computed: {
    nameOptions() { return withHistorical(this.nameChoices, this.$route.params.id && this.form.name) },
    venueOptions() { return withHistorical(this.venueChoices, this.$route.params.id && this.form.publicVenue) },
    guideOptions() { return this.guideChoices.concat(this.guides.filter(id => !this.guideChoices.some(person => person.id === id)).map(id => ({ id, name: '历史导游' }))) },
    startOptions() { return { disabledDate: d => !!this.form.endDate && day(d) > this.form.endDate } },
    endOptions() { return { disabledDate: d => !!this.form.startDate && day(d) < this.form.startDate } },
    openOptions() { return { disabledDate: d => !!this.closeDate && day(d) > this.closeDate } },
    closeOptions() { return { disabledDate: d => !!this.openDate && day(d) < this.openDate } }
  },
  watch: {
    'form.sessionNumber'() { this.queueCoverPreview() },
    'form.startDate'(value) {
      if (this.$route.params.id) return
      if (!this.openManual) this.openDate = offsetDay(value, -14)
      if (!this.closeManual) this.closeDate = offsetDay(value, -1)
    },
    coverMode(value) {
      if (value === 'upload' && isAutoCover(this.form.coverUrl)) this.form.coverUrl = ''
      this.queueCoverPreview()
    }
  },
  beforeDestroy() {
    clearTimeout(this.coverTimer)
    this.coverRequestId = (this.coverRequestId || 0) + 1
    if (this.coverPreviewUrl) URL.revokeObjectURL(this.coverPreviewUrl)
  },
  async created() {
    this.loading = true
    try {
      const [names, venues, people] = await Promise.all([getDicts('ql_session_name'), getDicts('ql_session_venue'), sessionGuides()])
      this.nameChoices = names.data || []; this.venueChoices = venues.data || []; this.guideChoices = people.data || []
      if (!this.$route.params.id) {
        const defaultOption = options => options.find(item => item.isDefault === 'Y') || options[0]
        this.form.name = (defaultOption(this.nameChoices) || {}).dictValue || ''
        this.form.publicVenue = (defaultOption(this.venueChoices) || {}).dictValue || ''
        const matches = ['公主', '大海', '军军'].map(name => this.guideChoices.filter(person => person.name === name))
        if (matches.every(rows => rows.length === 1)) this.guides = matches.map(rows => rows[0].id)
        else this.guideDefaultsError = '默认导游未唯一匹配现有人员，请核对人员ID后选择'
        return
      }
      const res = await getSession(this.$route.params.id)
      if (!res.data) throw new Error('期次不存在')
      this.form = res.data
      this.initialSessionNumber = this.form.sessionNumber
      this.form.status = res.data.configuredStatus || res.data.status
      this.coverMode = isAutoCover(this.form.coverUrl) ? 'auto' : 'upload'
      this.queueCoverPreview()
      this.guides = this.form.guideIds || []
      this.legacyGuides = this.form.guideIds == null ? this.form.leaderName || '' : ''
      this.openDate = (this.form.registrationOpenAt || '').slice(0, 10)
      this.closeDate = (this.form.registrationCloseAt || '').slice(0, 10)
      const p = regions.find(p => p.label === this.form.province || p.label === this.form.city || p.label.replace(/[省市]$/, '') === this.form.city) ||
        regions.find(p => p.children.some(c => c.label === this.form.city))
      const c = p && (p.children.find(c => c.label === this.form.city) || (/^(上海|北京|天津|重庆)/.test(p.label) ? p.children[0] : null))
      this.region = p && c ? [p.value, c.value] : []
      if (this.region.length) this.changeRegion(this.region)
      else { this.legacyCity = this.form.city || ''; this.form.city = '' }
    } catch (error) { this.loadFailed = true; this.msgError(error.message || '加载期次选项失败，请返回重试') } finally { this.loading = false }
  },
  methods: {
    queueCoverPreview() {
      clearTimeout(this.coverTimer)
      const requestId = this.coverRequestId = (this.coverRequestId || 0) + 1
      if (this.coverPreviewUrl) URL.revokeObjectURL(this.coverPreviewUrl)
      this.coverPreviewUrl = ''; this.coverPreviewError = ''; this.coverPreviewLoading = false
      if (this.coverMode !== 'auto' || !Number.isInteger(this.form.sessionNumber) || this.form.sessionNumber < 1) return
      if (this.$route.params.id && this.form.sessionNumber === this.initialSessionNumber && this.form.coverUrl) {
        this.coverPreviewUrl = this.form.coverUrl
        return
      }
      this.coverPreviewLoading = true
      this.coverTimer = setTimeout(() => {
        previewSessionCover(this.form.sessionNumber).then(blob => {
          if (requestId !== this.coverRequestId) return
          if (!(blob instanceof Blob) || blob.type !== 'image/png') throw new Error('自动封面预览失败')
          this.coverPreviewUrl = URL.createObjectURL(blob)
        }).catch(() => { if (requestId === this.coverRequestId) this.coverPreviewError = '预览暂不可用，保存时将重新生成' })
          .finally(() => { if (requestId === this.coverRequestId) this.coverPreviewLoading = false })
      }, 250)
    },
    changeRegion(value) { this.form.province = value[0] || ''; this.form.city = /城区$/.test(value[1] || '') ? value[0] : value[1] || ''; this.legacyCity = '' },
    back() { this.$router.push('/activityOperations/session') },
    submit() {
      if (this.submitting || this.loading || this.loadFailed) return
      this.$refs.form.validate(valid => {
        if (!valid) return
        if (!this.$route.params.id && !this.form.publicVenue) return this.msgError('请选择公开地点')
        if (this.form.endDate < this.form.startDate) return this.msgError('结束日期不能早于开始日期')
        if (this.openDate && this.closeDate && this.closeDate < this.openDate) return this.msgError('报名截止日期不能早于开放日期')
        if (this.guideDefaultsError && !this.guidesTouched) return this.msgError(this.guideDefaultsError)
        if (this.coverMode === 'upload' && !this.form.coverUrl) return this.msgError('请上传封面，或选择自动生成')
        const payload = { ...this.form, coverUrl: this.coverMode === 'auto' && this.form.sessionNumber !== this.initialSessionNumber ? '' : this.form.coverUrl, guideIds: this.$route.params.id && this.form.guideIds == null && !this.guidesTouched ? undefined : this.guides, registrationOpenManual: this.openManual, registrationCloseManual: this.closeManual, registrationOpenAt: this.openDate ? this.openDate + ' 00:00:00' : null, registrationCloseAt: this.closeDate ? this.closeDate + ' 23:59:59' : null }
        this.submitting = true
        const request = payload.id ? updateSession(payload) : addSession(payload)
        request.then(() => { this.msgSuccess('期次已保存'); this.back() }).finally(() => { this.submitting = false })
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
.cover-area { margin-top: 12px; }
.cover-preview { display: block; width: 100%; max-width: 480px; height: auto; border-radius: 8px; }
.hint { color: #606266; font-size: 13px; line-height: 1.7; margin-top: 6px; }
.form-actions { padding: 20px 0; border-top: 1px solid #ebeef5; }
@media (max-width: 760px) { .form-grid { grid-template-columns: minmax(0,1fr); } }
</style>
