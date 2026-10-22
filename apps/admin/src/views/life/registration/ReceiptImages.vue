<template>
  <div>
    <template v-if="!readonly">
      <el-upload action="#" :auto-upload="false" :limit="3" :disabled="disabled || reading" :file-list="files" :on-change="select" :on-remove="remove" :on-exceed="exceed" accept="image/jpeg,image/png">
        <el-button size="small" :disabled="disabled || reading">选择图片</el-button>
        <div slot="tip" class="el-upload__tip">选填，最多3张 JPG / PNG，每张不超过2MB</div>
      </el-upload>
      <el-image v-for="(image, i) in value" :key="i" :src="localUrl(image)" :preview-src-list="value.map(localUrl)" class="receipt-image" fit="contain" />
    </template>
    <template v-else>
      <el-button v-if="saved.length && !urls.length" type="text" size="small" :loading="loading" @click="load">查看图片（{{ saved.length }}）</el-button>
      <el-image v-for="url in urls" :key="url" :src="url" :preview-src-list="urls" class="receipt-image" fit="contain" />
      <span v-if="!saved.length">—</span>
    </template>
  </div>
</template>
<script>
import { getOfflineReceiptImage } from '@/api/life/registration'
export default {
  name: 'ReceiptImages',
  props: {
    value: { type: Array, default: () => [] },
    saved: { type: Array, default: () => [] },
    registrationId: { type: String, default: '' },
    readonly: Boolean,
    disabled: Boolean
  },
  data() { return { files: this.value.map((image, i) => ({ name: '图片' + (i + 1), uid: i, status: 'success', receiptImage: image })), reading: false, loading: false, urls: [], version: 0 } },
  watch: { saved() { if (this.readonly) this.clear() } },
  beforeDestroy() { this.clear() },
  methods: {
    localUrl(image) { return 'data:' + image.mime + ';base64,' + image.data },
    async select(file, files) {
      const raw = file.raw
      if (!['image/jpeg', 'image/png'].includes(raw.type) || raw.size > 2 * 1024 * 1024) {
        this.files = files.filter(item => item.uid !== file.uid)
        this.msgError('请选择2MB以内的 JPG / PNG 图片')
        return
      }
      const version = ++this.version
      this.files = files; this.reading = true; this.$emit('reading', true)
      try {
        const image = await new Promise((resolve, reject) => {
          const reader = new FileReader()
          reader.onload = () => resolve({ mime: raw.type, data: String(reader.result).split(',')[1] })
          reader.onerror = reject
          reader.readAsDataURL(raw)
        })
        if (version !== this.version) return
        this.$set(file, 'receiptImage', image)
        this.$emit('input', this.files.map(item => item.receiptImage).filter(Boolean))
      } catch (e) {
        if (version === this.version) { this.files = this.files.filter(item => item.uid !== file.uid); this.msgError('图片读取失败，请重试') }
      } finally {
        if (version === this.version) { this.reading = false; this.$emit('reading', false) }
      }
    },
    remove(file, files) {
      ++this.version; this.reading = false; this.$emit('reading', false)
      this.files = files
      this.$emit('input', files.map(item => item.receiptImage).filter(Boolean))
    },
    exceed() { this.msgError('最多上传3张图片') },
    clear() {
      ++this.version
      this.urls.forEach(url => URL.revokeObjectURL(url))
      this.urls = []; this.loading = false
    },
    async load() {
      if (this.loading) return
      const version = ++this.version
      this.loading = true
      const urls = []
      try {
        for (const image of this.saved) {
          const blob = await getOfflineReceiptImage(this.registrationId, image.id)
          if (version !== this.version) return
          if (!['image/jpeg', 'image/png'].includes(blob.type)) throw new Error('image unavailable')
          urls.push(URL.createObjectURL(blob))
        }
        this.urls = urls
      } catch (e) {
        urls.forEach(url => URL.revokeObjectURL(url))
        if (version === this.version) this.msgError('附件读取失败，请重试')
      } finally {
        if (version !== this.version) urls.forEach(url => URL.revokeObjectURL(url))
        else this.loading = false
      }
    }
  }
}
</script>
<style scoped>
.receipt-image { width: 72px; height: 72px; margin: 8px 8px 0 0; }
</style>
