<template>
  <el-drawer title="轻友档案" :visible.sync="visible" size="100%" custom-class="customer-dossier" append-to-body @closed="closed">
    <div v-loading="loading" class="dossier">
      <div v-if="error" class="empty"><p>档案加载失败，请重试。</p><el-button @click="load">重新加载</el-button></div>
      <template v-else-if="customer.id && !loading">
        <header class="identity-card">
          <div class="identity-heading">
            <el-avatar :size="64" :src="customer.wechatAvatar" @error="() => true">{{ (customer.nickname || '轻友').slice(0, 1) }}</el-avatar>
            <div class="identity-name"><div class="eyebrow">轻友 · {{ customer.customerNo }}</div><h1>{{ customer.nickname }} <small>{{ customer.realName || '姓名未记录' }}</small></h1><el-tag size="small" :type="visits.length ? 'success' : 'info'">{{ visits.length ? '老轻友' : '新轻友' }}</el-tag><span v-if="customer.status !== 'active'" class="muted"> 档案已停用</span></div>
            <el-button v-hasPermi="['life:customer:edit']" plain size="small" @click="$emit('edit', customer)">编辑资料</el-button>
          </div>
          <div class="contact-line"><span>手机：{{ customer.phoneHint || '未记录' }}</span><span>微信号：{{ wechatId }}</span><span>城市：{{ customer.city || '未记录' }}</span></div>
          <div class="summary-grid">
            <div><span>实际参加</span><strong>{{ visits.length }} <small>次</small></strong><p>同一期多天签到计 1 次</p></div>
            <div><span>首次参加</span><strong>{{ firstVisit ? '第 ' + firstVisit.sessionNumber + ' 期' : '暂无参加记录' }}</strong><p>{{ firstVisit ? firstVisit.firstDate : '报名和付款不计入参加' }}</p></div>
            <div><span>最近参加</span><strong>{{ lastVisit ? '第 ' + lastVisit.sessionNumber + ' 期' : '暂无参加记录' }}</strong><p>{{ lastVisit ? lastVisit.lastDate : '以实际到场记录为准' }}</p></div>
            <div><span>当前可用卡次</span><strong>{{ usableBalance }} <small>次</small></strong><p>{{ passes.length ? '有效账户的流水余额合计' : '尚无卡次账户' }}</p></div>
          </div>
          <div class="source-line">首次推荐人：{{ (overview.identity || {}).referrerName || '未记录' }}<span>首次来源：{{ label(customer.firstSource) }}</span></div>
        </header>
        <div class="focus-grid">
          <section class="focus-card"><h2>最近沟通</h2><template v-if="contacts.length"><div class="muted">{{ contacts[0].occurredAt }} · {{ contacts[0].operatorName || '记录人未记录' }}</div><p class="clamp">{{ contacts[0].summary }}</p><el-button type="text" @click="active='contacts'">查看沟通记录</el-button></template><p v-else class="muted">尚无可查看的沟通记录</p></section>
          <section class="focus-card"><h2>下一步 <el-tag v-if="overdueCount" size="mini" type="danger">{{ overdueCount }} 项逾期</el-tag></h2><template v-if="pendingTasks.length"><p>{{ pendingTasks[0].title }}</p><div class="muted">{{ pendingTasks[0].dueAt || '未设置计划日期' }} · {{ pendingTasks[0].assigneeName || '负责人未记录' }}</div><el-button type="text" @click="active='contacts'">查看待办</el-button></template><p v-else class="muted">暂无已登记的待办</p></section>
        </div>
        <div class="actions"><el-button v-hasPermi="['life:registration:list']" type="primary" size="small" @click="business()">报名与付款</el-button></div>
        <el-tabs v-model="active">
          <el-tab-pane label="时间线" name="timeline">
            <div class="section-toolbar"><h2>与轻生活的关系记录</h2><el-select v-model="eventFilter" size="small" aria-label="时间线筛选"><el-option label="全部记录" value="all"/><el-option label="报名与参与" value="activity"/><el-option label="付款与卡次" value="payment"/><el-option label="沟通回访" value="contact"/></el-select></div>
            <el-empty v-if="!filteredTimeline.length" description="暂无此类记录" />
            <el-timeline v-else class="history"><el-timeline-item v-for="item in visibleTimeline" :key="item.eventType + item.referenceId + item.occurredAt" :timestamp="item.occurredAt" placement="top" :color="item.eventType === 'staff_interview' ? '#bd8d56' : '#648778'"><article class="history-card"><div class="history-heading"><h3>{{ item.title }}</h3><el-tag size="mini" :type="isContact(item) ? 'warning' : 'info'">{{ item.contentType ? label(item.contentType) : isContact(item) ? '工作人员回访原文' : '业务记录' }}</el-tag></div><p v-if="item.sessionNumber" class="muted">第 {{ item.sessionNumber }} 期 · {{ item.sessionName }}</p><p class="record-text">{{ translateDetail(item.detail) }}</p><div class="muted">{{ label(item.source) }}<span v-if="item.operatorName"> · {{ item.operatorName }}</span></div><el-button v-if="item.registrationId" v-hasPermi="['life:registration:list']" type="text" @click="business(item.sessionNumber)">查看该期业务</el-button></article></el-timeline-item></el-timeline>
            <el-button v-if="visibleTimeline.length < filteredTimeline.length" class="load-more" @click="timelineLimit += 30">加载更多记录</el-button>
          </el-tab-pane>
          <el-tab-pane :label="'活动记录（' + activities.length + '）'" name="activities">
            <el-empty v-if="!activities.length" description="尚无报名记录"/>
            <article v-for="item in activities" :key="item.id" class="record-card"><div class="history-heading"><h3>第 {{ item.sessionNumber }} 期 · {{ item.sessionName }}</h3><el-tag size="small">{{ label(item.registrationStatus) }}</el-tag></div><p>{{ item.startDate }} · {{ label(item.sessionStatus) }}</p><p>实际参与：{{ visits.some(v => v.sessionId === item.sessionId) ? '已有到场记录' : '暂无到场记录' }} <span class="muted">｜{{ label(item.paymentStatus) }}</span></p><div class="muted">报名时间：{{ item.registeredAt }}</div><el-button v-hasPermi="['life:registration:list']" type="text" @click="business(item.sessionNumber)">查看报名与付款</el-button></article>
          </el-tab-pane>
          <el-tab-pane label="付款与卡次" name="payment">
            <h2>卡次账户</h2><el-empty v-if="!passes.length" description="尚无卡次账户"/>
            <div class="focus-grid"><article v-for="item in passes" :key="item.id" class="record-card"><h3>{{ item.passType }}</h3><p class="balance">{{ item.balance }} <small>次余额</small></p><el-tag size="small" :type="Number(item.usable) ? 'success' : 'info'">{{ Number(item.usable) ? '可使用' : '当前不可用' }}</el-tag><p class="muted">有效期：{{ item.validFrom || '不限起始日期' }} 至 {{ item.validUntil || '长期有效' }}</p></article></div>
            <h2>付款记录</h2><el-empty v-if="!payments.length" description="暂无付款记录"/><article v-for="item in payments" :key="item.id" class="record-card"><div class="history-heading"><h3>¥{{ Number(item.amount).toFixed(2) }}</h3><el-tag size="small">{{ label(item.status) }}</el-tag></div><p>{{ label(item.paymentMethod) }} · {{ item.sessionNumber ? '第 ' + item.sessionNumber + ' 期' : '未关联期次' }}</p><div class="muted">{{ item.occurredAt }} · {{ item.operatorName || '工作人员未记录' }}</div></article>
            <h2>卡次流水</h2><el-empty v-if="!ledger.length" description="暂无卡次流水"/><article v-for="item in ledger" :key="item.id" class="record-card"><h3>{{ item.passType }} · {{ label(item.entryType) }} {{ Number(item.quantityDelta) > 0 ? '+' : '' }}{{ item.quantityDelta }} 次</h3><p>{{ item.reason || '未填写说明' }}</p><div class="muted">{{ item.occurredAt }} · {{ item.operatorName || '操作人未记录' }}<span v-if="item.sessionNumber"> · 第 {{ item.sessionNumber }} 期</span></div></article>
          </el-tab-pane>
          <el-tab-pane label="沟通与跟进" name="contacts">
            <h2>待办事项</h2><el-empty v-if="!tasks.length" description="暂无已登记的待办"/><article v-for="item in tasks" :key="item.id" class="record-card"><div class="history-heading"><h3>{{ item.title }}</h3><el-tag :type="Number(item.overdue) ? 'danger' : 'info'" size="small">{{ Number(item.overdue) ? '已逾期' : label(item.status) }}</el-tag></div><p class="muted">{{ item.dueAt || '未设置计划日期' }} · {{ item.assigneeName || '负责人未记录' }}</p></article>
            <h2>沟通记录</h2><el-empty v-if="!contacts.length" description="暂无可查看的沟通记录"/><article v-for="item in contacts" :key="item.id" class="record-card"><div class="history-heading"><h3>{{ label(item.channel) }}</h3><el-tag size="mini" type="warning">{{ label(item.contentType) }}</el-tag></div><p class="record-text">{{ item.summary }}</p><div class="muted">{{ item.occurredAt }} · {{ item.operatorName || '记录人未记录' }}</div></article>
          </el-tab-pane>
          <el-tab-pane label="资料与图片" name="materials">
            <h2>身份与建档信息</h2><div class="record-card"><p>微信昵称：{{ customer.wechatNickname || '未绑定小程序账号' }}</p><p>建档时间：{{ customer.createdAt || '未记录' }}</p><p>资料更新时间：{{ customer.updatedAt || '未记录' }}</p><p>首次来源：{{ label(customer.firstSource) }}</p><div v-for="(item,index) in overview.identifiers || []" :key="index"><p>{{ item.type === 'phone' ? '手机号线索' : '微信号线索' }}：{{ item.hint || '未记录' }} <span class="muted">{{ item.validTo ? '历史线索 · 截至 ' + item.validTo : '当前线索' }}</span></p></div></div>
            <h2>回访图片</h2><div v-if="!canImages" class="muted">当前账号没有服务记录图片查看权限</div><div v-else v-loading="imagesLoading"><p class="muted">按回访时间从新到旧展示，点击图片可放大。</p><el-alert v-if="imagesError" title="图片加载失败，请重试" type="error" :closable="false"/><el-button v-if="imagesError" @click="loadImages">重新加载</el-button><el-empty v-if="!imagesLoading && !images.length && !imagesError" description="暂无回访图片"/><div class="photo-grid"><article v-for="item in visibleImages" :key="item.id" class="record-card"><el-image :src="imageUrls[item.id]" :preview-src-list="photoUrls" fit="contain"><div slot="error" class="muted">图片未能加载</div></el-image><p>回访时间：{{ item.createdAt }}</p><div class="muted">{{ item.operatorName }}</div></article></div><el-button v-if="visibleImages.length < images.length" @click="moreImages">加载更多图片</el-button></div>
          </el-tab-pane>
        </el-tabs>
      </template>
    </div>
  </el-drawer>
</template>

<script>
import request from '@/utils/request'
import { getCustomer, getCustomerTimeline } from '@/api/life/customer'
import { checkPermi } from '@/utils/permission'
export default {
  name: 'CustomerDossier',
  data() { return { visible:false,loading:false,error:false,customer:{},overview:{},timeline:[],active:'timeline',eventFilter:'all',timelineLimit:30,images:[],imageUrls:{},imagesLoading:false,imagesError:false,imageLimit:20 } },
  computed: {
    visits() { return this.overview.participations || [] }, firstVisit() { return this.visits[0] },
    lastVisit() { return [...this.visits].sort((a,b)=>String(b.lastDate).localeCompare(String(a.lastDate)))[0] },
    passes() { return this.overview.passes || [] }, activities() { return this.overview.activities || [] }, payments() { return this.overview.payments || [] }, ledger() { return this.overview.ledger || [] }, tasks() { return this.overview.tasks || [] },
    usableBalance() { return this.passes.filter(item=>Number(item.usable)).reduce((n,item)=>n+Number(item.balance),0) },
    pendingTasks() { return this.tasks.filter(item=>['pending','in_progress'].includes(item.status)) },
    overdueCount() { return this.pendingTasks.filter(item=>Number(item.overdue)).length },
    wechatId() { const item=(this.overview.identifiers || []).find(item=>item.type==='wechat_id' && !item.validTo); return item && item.hint || '未记录' },
    contacts() { return [...(this.overview.contacts || []),...this.timeline.filter(item=>item.eventType==='staff_interview').map(item=>({id:item.referenceId,occurredAt:item.occurredAt,operatorName:item.operatorName,summary:item.detail,channel:'staff_interview',contentType:'unclassified'}))].sort((a,b)=>b.occurredAt.localeCompare(a.occurredAt)) },
    mergedTimeline() { return [...this.timeline.map(item=>item.eventType==='session_completed' ? {...item,title:'关联期次已结束',detail:'期次已结束；个人实际参与请查看到场记录。'} : item),...(this.overview.contacts || []).map(item=>({...item,eventType:'contact',referenceId:item.id,title:this.label(item.channel),detail:item.summary,source:'operator_entry'})),...this.ledger.map(item=>({...item,eventType:'pass_ledger',referenceId:item.id,title:'卡次'+this.label(item.entryType),detail:item.passType+' · '+item.quantityDelta+' 次；'+(item.reason || ''),source:'system'}))].sort((a,b)=>String(b.occurredAt).localeCompare(String(a.occurredAt))) },
    filteredTimeline() { return this.mergedTimeline.filter(item=>this.eventFilter==='all' || (this.eventFilter==='contact' ? this.isContact(item) : this.eventFilter==='payment' ? ['payment_confirmed','pass_ledger'].includes(item.eventType) : ['registration_submitted','registration_status','attendance','session_completed'].includes(item.eventType))) },
    visibleTimeline() { return this.filteredTimeline.slice(0,this.timelineLimit) },
    canImages() { return checkPermi(['life:serviceRecord:list']) }, visibleImages() { return this.images.slice(0,this.imageLimit) }, photoUrls() { return this.visibleImages.map(item=>this.imageUrls[item.id]).filter(Boolean) }
  },
  watch: { eventFilter() { this.timelineLimit=30 }, active() { this.tabChanged() } },
  beforeDestroy() { this.closed() },
  methods: {
    open(id) { this.customer={id};this.visible=true;this.active='timeline';this.eventFilter='all';this.timelineLimit=30;this.overview={};this.timeline=[];this.clearImages();return this.load() },
    async load() { const id=this.customer.id;const version=this.version=(this.version || 0)+1;this.loading=true;this.error=false;try { const [customer,overview,timeline]=await Promise.all([getCustomer(id),request({url:'/life/customer/'+id+'/dossier'}),getCustomerTimeline(id)]);if(version!==this.version)return;this.customer=customer.data;this.overview=overview.data;this.timeline=timeline.data || [] } catch(e) { if(version===this.version)this.error=true } finally {if(version===this.version)this.loading=false} },
    closed() { this.version=(this.version || 0)+1;this.clearImages() },
    clearImages() { this.imageVersion=(this.imageVersion || 0)+1;Object.values(this.imageUrls).forEach(url=>URL.revokeObjectURL(url));this.imageUrls={};this.images=[];this.imageLimit=20;this.imagesError=false },
    async tabChanged() { if(this.active==='materials' && this.canImages && !this.images.length)await this.loadImages() },
    async loadImages() { const version=this.imageVersion;this.imagesLoading=true;this.imagesError=false;try {const response=await request({url:'/life/staff/interview-images',params:{customerId:this.customer.id}});if(version!==this.imageVersion)return;this.images=response.data || [];await this.fetchImages(version)} catch(e){if(version===this.imageVersion)this.imagesError=true} finally {if(version===this.imageVersion)this.imagesLoading=false} },
    async fetchImages(version) {const images=this.visibleImages.filter(item=>!this.imageUrls[item.id]);for(let i=0;i<images.length;i+=4){if(version!==this.imageVersion)return;await Promise.all(images.slice(i,i+4).map(async item=>{try{const blob=await request({url:'/life/staff/images/'+item.id,responseType:'blob'});if(version===this.imageVersion)this.$set(this.imageUrls,item.id,URL.createObjectURL(blob))}catch(e){if(version===this.imageVersion)this.imagesError=true}}))} },
    moreImages(){this.imageLimit+=20;this.fetchImages(this.imageVersion)},
    business(sessionNumber) {this.visible=false;this.$router.push({path:'/activityOperations/registration',query:{nickname:this.customer.customerNo,sessionNumber:sessionNumber || undefined}})},
    isContact(item){return ['staff_interview','contact'].includes(item.eventType)},
    translateDetail(value){return String(value || '').replace(/\b(pending_confirmation|not_arrived|checked_in|left_early|pending|confirmed|waitlisted|cancelled|rescheduled|unpaid|paid|late|absent|completed|wechat_scan|alipay_scan|transfer|cash|other)\b/g,word=>this.label(word))},
    label(value){return ({unknown:'未记录',referral:'朋友推荐',offline:'线下',wechat:'微信',phone:'电话',voice_note:'语音',other:'其他',mini_program:'小程序',wechat_scan:'微信扫码',alipay_scan:'支付宝扫码',cash:'现金',transfer:'银行转账',waived:'免单',pending:'待确认',confirmed:'已确认',waitlisted:'候补',cancelled:'已取消',rescheduled:'已改期',paid:'已付款',unpaid:'未付款',pending_confirmation:'待确认',failed:'失败',refund_pending:'退款处理中',refunded:'已退款',open:'开放报名',closed:'已关闭',draft:'草稿',in_progress:'进行中',completed:'已完成',not_arrived:'未到场',checked_in:'已到场',late:'迟到',absent:'缺席',left_early:'早退',grant:'发放',consume:'使用',adjust:'调整',expire:'到期',void:'作废',refund:'退回',customer_statement:'轻友本人自述',objective_fact:'人工记录的事实',staff_observation:'工作人员观察',staff_judgement:'工作人员判断',ai_suggestion:'AI建议',unclassified:'工作人员回访原文',staff_interview:'服务回访',operator_entry:'人工登记',manual_entry:'人工建档',web_admin:'电脑后台',mobile_workspace:'移动工作台',mobile_h5:'移动工作台',system:'系统记录',legacy_app:'微信登录建档',skipped:'已跳过'})[value] || value || '未记录'}
  }
}
</script>

<style>
.customer-dossier { max-width:1160px; background:#f5f7f5; }
.customer-dossier .el-drawer__header { margin:0;padding:18px 24px;background:#fff;border-bottom:1px solid #e6ebe7;color:#34463c; }
.customer-dossier .el-drawer__body { overflow:auto; }
</style>
<style scoped>
.dossier { padding:24px; color:#35453d; }.dossier h1,.dossier h2,.dossier h3{margin:0}.dossier h1{font-size:26px;margin:6px 0 10px}.dossier h1 small{font-size:15px;font-weight:400;color:#728078;margin-left:10px}.dossier h2{font-size:17px;margin:12px 0 16px}.dossier h3{font-size:15px;line-height:1.5}.identity-card,.focus-card{background:#fff;border:1px solid #e2e9e3;border-radius:12px;padding:22px}.identity-heading{display:flex;align-items:center;gap:16px}.identity-name{flex:1;min-width:0}.eyebrow,.muted{font-size:12px;color:#77837c;line-height:1.6}.contact-line{display:flex;flex-wrap:wrap;gap:12px 28px;margin:22px 0;font-size:14px}.summary-grid{display:grid;grid-template-columns:repeat(4,1fr);border-top:1px solid #edf0ec;border-bottom:1px solid #edf0ec;padding:20px 0;gap:16px}.summary-grid span{font-size:13px;color:#728078}.summary-grid strong{display:block;font-size:21px;margin-top:10px}.summary-grid small{font-size:13px;font-weight:400}.summary-grid p{font-size:12px;color:#849087;margin:8px 0 0}.source-line{font-size:13px;margin-top:18px;display:flex;flex-wrap:wrap;gap:10px 28px}.focus-grid{display:grid;grid-template-columns:1fr 1fr;gap:16px;margin:16px 0}.focus-card{padding:18px 22px}.focus-card h2{margin-top:0}.focus-card p{font-size:14px;line-height:1.7}.clamp{display:-webkit-box;-webkit-line-clamp:2;-webkit-box-orient:vertical;overflow:hidden}.actions{display:flex;flex-wrap:wrap;gap:8px;margin:20px 0}.actions .el-button{margin:0}.section-toolbar{display:flex;justify-content:space-between;align-items:center;gap:12px;margin-bottom:20px}.section-toolbar .el-select{width:145px}.history{padding-left:8px}.history-card,.record-card{background:#fff;border:1px solid #e3e9e4;border-radius:9px;padding:18px;margin-bottom:12px;font-size:14px}.history-heading{display:flex;justify-content:space-between;align-items:flex-start;gap:12px}.record-text{white-space:pre-wrap;overflow-wrap:anywhere;line-height:1.8}.load-more{width:100%}.balance{font-size:26px}.balance small{font-size:13px}.photo-grid{display:grid;grid-template-columns:repeat(auto-fill,minmax(215px,1fr));gap:12px}.photo-grid .el-image{width:100%;height:180px}.empty{padding:60px;text-align:center}
@media(max-width:600px){.dossier{padding:14px}.identity-card{padding:16px}.identity-heading{gap:10px;flex-wrap:wrap}.dossier h1{font-size:22px}.dossier h1 small{display:block;margin:5px 0}.summary-grid{grid-template-columns:1fr 1fr;gap:20px 12px}.summary-grid strong{font-size:18px}.focus-grid{grid-template-columns:1fr}.focus-card{padding:16px}.history-heading{flex-wrap:wrap}.contact-line{gap:10px;flex-direction:column}.actions .el-button{min-height:40px}.section-toolbar h2{font-size:15px}.source-line{flex-direction:column}.record-card{padding:14px}.dossier /deep/ .el-tabs__item{padding:0 15px}}
</style>
