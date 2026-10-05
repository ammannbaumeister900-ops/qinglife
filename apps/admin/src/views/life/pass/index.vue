<template>
  <div class="app-container">
    <el-alert title="卡次余额由不可变流水汇总；开户、次数及有效期调整均保留操作记录，不直接覆盖余额。" type="info" :closable="false" show-icon class="mb20" />
    <el-form :inline="true"><el-form-item label="轻友"><el-select v-model="customerId" filterable clearable placeholder="全部轻友" size="small"><el-option v-for="item in customers" :key="item.id" :label="item.nickname + (item.realName ? '（' + item.realName + '）' : '')" :value="item.id" /></el-select></el-form-item><el-form-item><el-button type="primary" size="small" @click="load">查询</el-button></el-form-item></el-form>
    <el-button v-hasPermi="['life:pass:add']" type="primary" plain icon="el-icon-plus" size="mini" class="mb20" @click="showOpen">卡次开户</el-button>
    <el-table v-loading="loading" :data="rows">
      <el-table-column label="轻友" min-width="150"><template slot-scope="s"><div>{{ s.row.nickname }} <span class="muted">{{ s.row.realName || '' }}</span></div><div class="muted">{{ s.row.customerNo }}</div></template></el-table-column>
      <el-table-column prop="passType" label="卡次名称" min-width="150" />
      <el-table-column label="当前剩余" width="110"><template slot-scope="s"><strong>{{ s.row.balance }} 次</strong></template></el-table-column>
      <el-table-column label="状态" width="100"><template slot-scope="s"><el-tag size="mini" :type="Number(s.row.usable) ? 'success' : 'info'">{{ Number(s.row.usable) ? '可使用' : '不可用' }}</el-tag></template></el-table-column>
      <el-table-column label="有效期" min-width="180"><template slot-scope="s">{{ s.row.validFrom || '不限' }} 至 {{ s.row.validUntil || '长期' }}</template></el-table-column>
      <el-table-column label="操作" width="190"><template slot-scope="s"><el-button v-hasPermi="['life:pass:query']" type="text" @click="showLedger(s.row)">记录</el-button><el-button v-if="Number(s.row.hasImage)" v-hasPermi="['life:pass:query']" type="text" @click="showImage(s.row)">图片</el-button><el-button v-hasPermi="['life:pass:adjust']" type="text" @click="showAdjust(s.row)">调整</el-button></template></el-table-column>
    </el-table>
    <el-empty v-if="!loading && !rows.length" description="暂无卡次账户" />

    <el-dialog title="卡次开户" :visible.sync="openVisible" width="500px" append-to-body><el-form ref="openForm" :model="openForm" :rules="openRules" label-width="92px"><el-form-item label="轻友" prop="customerId"><el-select v-model="openForm.customerId" filterable style="width:100%"><el-option v-for="item in customers" :key="item.id" :label="item.nickname + (item.realName ? '（' + item.realName + '）' : '')" :value="item.id" /></el-select></el-form-item><el-form-item label="卡次名称" prop="passType"><el-input v-model="openForm.passType" maxlength="50" placeholder="例如：轻体营10次卡" /></el-form-item><el-form-item label="初始次数" prop="initialUnits"><el-input-number v-model="openForm.initialUnits" :min="1" :precision="0" style="width:100%" /></el-form-item><el-form-item label="有效期"><el-date-picker v-model="dates" type="daterange" value-format="yyyy-MM-dd" start-placeholder="开始日期" end-placeholder="结束日期" style="width:100%" /></el-form-item><el-form-item label="备注" prop="reason"><el-input v-model="openForm.reason" type="textarea" maxlength="500" placeholder="选填，可记录购买或核验说明" /></el-form-item><el-form-item label="图片"><el-upload action="#" :auto-upload="false" :limit="1" :on-change="selectImage" :on-remove="removeImage" :on-exceed="imageExceeded" :file-list="imageFiles" accept="image/jpeg,image/png"><el-button size="small">选择图片</el-button><div slot="tip" class="el-upload__tip">选填，最多1张 JPG / PNG 图片，大小不超过2MB</div></el-upload><el-image v-if="openingImageUrl" :src="openingImageUrl" class="opening-image" :preview-src-list="[openingImageUrl]" /></el-form-item></el-form><div slot="footer"><el-button type="primary" :loading="saving" :disabled="imageReading" @click="submitOpen">确认开户</el-button><el-button @click="openVisible=false">取消</el-button></div></el-dialog>
    <el-dialog title="调整卡次" :visible.sync="adjustVisible" width="460px" append-to-body><el-alert :title="'当前剩余 ' + (selected.balance || 0) + ' 次；正数增加，负数扣减。'" type="warning" :closable="false" description="仅调整有效期时，调整次数保留0即可。" class="mb20" /><el-form ref="adjustForm" :model="adjustForm" :rules="adjustRules" label-width="86px"><el-form-item label="调整次数" prop="quantityDelta"><el-input-number v-model="adjustForm.quantityDelta" :precision="0" style="width:100%" /></el-form-item><el-form-item label="开始日期"><el-date-picker v-model="adjustForm.validFrom" type="date" value-format="yyyy-MM-dd" placeholder="不限" style="width:100%" /></el-form-item><el-form-item label="截止日期"><el-date-picker v-model="adjustForm.validUntil" type="date" value-format="yyyy-MM-dd" :picker-options="untilOptions" placeholder="长期有效" style="width:100%" /></el-form-item><el-form-item label="调整原因" prop="reason"><el-input v-model="adjustForm.reason" type="textarea" maxlength="500" /></el-form-item></el-form><div slot="footer"><el-button type="primary" :loading="saving" @click="submitAdjust">保存调整</el-button><el-button @click="adjustVisible=false">取消</el-button></div></el-dialog>
    <el-dialog title="卡次图片" :visible.sync="imageVisible" width="660px" append-to-body @closed="clearImagePreview"><el-image v-loading="imageLoading" :src="imageUrl" style="width:100%" fit="contain" /></el-dialog>
    <el-drawer :title="selected.passType + ' · 卡次流水'" :visible.sync="ledgerVisible" size="560px" append-to-body><div class="drawer"><el-empty v-if="!ledger.length" description="暂无流水"/><el-timeline><el-timeline-item v-for="item in ledger" :key="item.id" :timestamp="item.occurredAt"><div v-if="item.entryType === 'validity'"><strong>有效期调整</strong><div>{{ item.previousValidFrom || '不限' }} 至 {{ item.previousValidUntil || '长期' }} → {{ item.validFrom || '不限' }} 至 {{ item.validUntil || '长期' }}</div></div><div v-else><strong>{{ label(item.entryType) }} {{ item.quantityDelta > 0 ? '+' : '' }}{{ item.quantityDelta }} 次</strong> · 余 {{ item.balanceAfter }} 次</div><div>{{ item.reason || '' }}</div><div class="muted">{{ item.operatorName || '系统' }}<span v-if="item.sessionNumber"> · 第{{ item.sessionNumber }}期</span></div></el-timeline-item></el-timeline></div></el-drawer>
  </div>
</template>
<script>
import { listPass, getPassLedger, openPass, adjustPass, getPassImage } from '@/api/life/pass'
import { selectCustomers } from '@/api/life/selection'
import { checkPermi } from '@/utils/permission'
export default {
  name: 'QlPass',
  data() { return {
    loading:false,saving:false,rows:[],customers:[],customerId:'',openVisible:false,adjustVisible:false,ledgerVisible:false,dates:[],selected:{},ledger:[],openForm:{},adjustForm:{},
    imageFiles:[],openingImageUrl:'',imageReading:false,imageVisible:false,imageUrl:'',imageLoading:false,
    openRules:{customerId:[{required:true,message:'请选择轻友',trigger:'change'}],passType:[{required:true,message:'请填写卡次名称',trigger:'blur'}],initialUnits:[{required:true,message:'请填写初始次数',trigger:'change'}]},
    adjustRules:{quantityDelta:[{required:true,message:'请填写调整次数',trigger:'change'}],reason:[{required:true,message:'请填写调整原因',trigger:'blur'}]}
  } },
  computed: { untilOptions() { return {disabledDate: date => !!this.adjustForm.validFrom && this.parseTime(date,'{y}-{m}-{d}') < this.adjustForm.validFrom} } },
  created() { this.loadCustomers();this.load() },
  beforeDestroy() { this.clearImagePreview();this.imageVersion=(this.imageVersion||0)+1 },
  methods: {
    loadCustomers() { if(checkPermi(['life:pass:list']))selectCustomers().then(r=>{this.customers=r.data||[]}) },
    load() { this.loading=true;return listPass({customerId:this.customerId||undefined}).then(r=>{this.rows=r.data||[]}).finally(()=>{this.loading=false}) },
    showOpen() { this.removeImage();this.openForm={customerId:this.customerId||'',passType:'',initialUnits:1,reason:'',image:null};this.dates=[];this.openVisible=true;this.$nextTick(()=>this.$refs.openForm.clearValidate()) },
    selectImage(file) {
      this.removeImage();const raw=file.raw;
      if(!['image/jpeg','image/png'].includes(raw.type)||raw.size>2*1024*1024) return this.msgError('请选择2MB以内的 JPG / PNG 图片');
      this.imageFiles=[file];this.imageReading=true;const version=this.imageVersion;
      const reader=new FileReader();reader.onload=()=>{if(version!==this.imageVersion)return;this.openingImageUrl=String(reader.result);this.$set(this.openForm,'image',{mime:raw.type,data:this.openingImageUrl.split(',')[1]});this.imageReading=false};
      reader.onerror=()=>{if(version===this.imageVersion){this.removeImage();this.msgError('图片读取失败，请重试')}};reader.readAsDataURL(raw);
    },
    removeImage() { this.imageVersion=(this.imageVersion||0)+1;this.imageFiles=[];this.openingImageUrl='';this.imageReading=false;this.$set(this.openForm,'image',null) },
    imageExceeded() { this.msgError('最多上传1张图片，请先移除当前图片') },
    submitOpen() { if(this.imageReading||this.saving)return;this.$refs.openForm.validate(valid=>{if(!valid)return;this.saving=true;openPass({...this.openForm,validFrom:this.dates[0]||null,validUntil:this.dates[1]||null}).then(()=>{this.msgSuccess('卡次账户已建立');this.openVisible=false;this.load()}).finally(()=>{this.saving=false})}) },
    showAdjust(row) { this.selected=row;this.adjustForm={quantityDelta:0,reason:'',changeValidity:true,validFrom:row.validFrom||null,validUntil:row.validUntil||null};this.adjustVisible=true;this.$nextTick(()=>this.$refs.adjustForm.clearValidate()) },
    submitAdjust() {
      if(this.saving)return;this.$refs.adjustForm.validate(valid=>{if(!valid)return;
        const f=this.adjustForm;if(f.validFrom&&f.validUntil&&f.validUntil<f.validFrom)return this.msgError('截止日期不能早于开始日期');
        if(!f.quantityDelta&&(f.validFrom||null)===(this.selected.validFrom||null)&&(f.validUntil||null)===(this.selected.validUntil||null))return this.msgError('请调整次数或有效期');
        this.saving=true;adjustPass(this.selected.id,{...f,validFrom:f.validFrom||null,validUntil:f.validUntil||null}).then(()=>{this.msgSuccess('调整已保存');this.adjustVisible=false;this.load()}).finally(()=>{this.saving=false})
      })
    },
    showLedger(row) { this.selected=row;this.ledger=[];this.ledgerVisible=true;getPassLedger(row.id).then(r=>{this.ledger=r.data||[]}) },
    clearImagePreview() { this.previewVersion=(this.previewVersion||0)+1;if(this.imageUrl)URL.revokeObjectURL(this.imageUrl);this.imageUrl='' },
    async showImage(row) { this.clearImagePreview();const version=this.previewVersion;this.imageVisible=true;this.imageLoading=true;try {const blob=await getPassImage(row.id);if(version===this.previewVersion){if(!['image/jpeg','image/png','image/webp'].includes(blob.type))return this.msgError('图片未能加载，请重试');this.imageUrl=URL.createObjectURL(blob)}} finally {if(version===this.previewVersion)this.imageLoading=false} },
    label(v) { return ({grant:'发放',consume:'使用',adjust:'调整',expire:'到期',void:'作废',refund:'退回'})[v]||v }
  }
}
</script>
<style scoped>.muted{font-size:12px;color:#909399}.drawer{padding:20px}.mb20{margin-bottom:20px}.opening-image{width:120px;height:100px;margin-top:10px}</style>
