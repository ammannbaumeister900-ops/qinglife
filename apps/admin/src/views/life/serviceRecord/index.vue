<template>
  <div class="app-container">
    <el-alert title="记录工作人员对轻友的服务回访，包括回访人、时间、评价与图片。" type="info" :closable="false" />
    <el-form :inline="true" :model="query" class="search-form" @submit.native.prevent="search">
      <el-form-item label="轻友姓名"><el-input v-model="query.realName" placeholder="请输入轻友姓名" clearable @keyup.enter.native="search" /></el-form-item>
      <el-form-item label="轻友小名"><el-input v-model="query.nickname" placeholder="请输入轻友小名" clearable @keyup.enter.native="search" /></el-form-item>
      <el-form-item label="回访人姓名"><el-input v-model="query.operatorName" placeholder="请输入回访人姓名" clearable @keyup.enter.native="search" /></el-form-item>
      <el-form-item>
        <el-button type="primary" icon="el-icon-search" @click="search">搜索</el-button>
        <el-button icon="el-icon-refresh" @click="reset">重置</el-button>
        <el-button @click="load">刷新记录</el-button>
      </el-form-item>
    </el-form>
    <el-table v-loading="loading" :data="rows" empty-text="暂无匹配的回访记录">
      <el-table-column label="轻友姓名" prop="realName" min-width="110" />
      <el-table-column label="轻友小名" prop="nickname" min-width="130" />
      <el-table-column label="回访人" prop="operatorName" min-width="130" />
      <el-table-column label="回访时间" prop="createdAt" width="165" />
      <el-table-column label="评价与感受" prop="content" min-width="250" />
      <el-table-column label="回访图片" min-width="290">
        <template slot-scope="s">
          <div class="thumbnails">
            <el-image v-for="item in (s.row.images || []).slice(0, 5)" :key="item.id" class="thumbnail" :src="imageUrls[item.id]" fit="cover" :preview-src-list="previewUrls((s.row.images || []).slice(0, 5))">
              <div slot="error" class="image-placeholder">加载失败</div>
              <div slot="placeholder" class="image-placeholder">加载中</div>
            </el-image>
            <span v-if="!(s.row.images || []).length" class="muted">本次无图片</span>
          </div>
          <el-button type="text" @click="openAlbum(s.row)">更多 · 轻友回访合集</el-button>
        </template>
      </el-table-column>
    </el-table>
    <pagination v-show="total > 0" :total="total" :page.sync="query.pageNum" :limit.sync="query.pageSize" @pagination="load" />
    <el-dialog :title="albumTitle" :visible.sync="albumOpen" width="85%" top="6vh" @closed="closeAlbum">
      <div v-loading="albumLoading" class="album-body">
        <el-alert v-if="albumError" title="图片合集加载失败，请关闭后重试。" type="error" :closable="false" />
        <p v-else-if="!albumLoading && !albumImages.length" class="muted">该轻友暂无回访图片</p>
        <p v-if="albumImages.length" class="muted">共 {{ albumImages.length }} 张图片，按回访时间从新到旧排列；点击缩略图可放大。</p>
        <div class="album-grid">
          <div v-for="item in albumPageImages" :key="item.id" class="album-card">
            <el-image class="album-image" :src="imageUrls[item.id]" fit="contain" :preview-src-list="previewUrls(albumPageImages)">
              <div slot="error" class="image-placeholder">图片加载失败</div>
              <div slot="placeholder" class="image-placeholder">加载中</div>
            </el-image>
            <div class="image-time">回访时间：{{ item.createdAt }}</div>
            <div class="muted">回访人：{{ item.operatorName || '—' }}</div>
          </div>
        </div>
        <el-pagination v-if="albumImages.length > 20" class="album-pagination" layout="total, prev, pager, next" :total="albumImages.length" :page-size="20" :current-page.sync="albumPage" @current-change="loadAlbumPage" />
      </div>
    </el-dialog>
  </div>
</template>

<script>
import request from '@/utils/request'

export default {
  name: 'ServiceRecord',
  data() {
    return {
      query: { realName: '', nickname: '', operatorName: '', pageNum: 1, pageSize: 20 },
      rows: [], total: 0, loading: false, imageUrls: {},
      albumOpen: false, albumLoading: false, albumError: false,
      albumTitle: '', albumImages: [], albumPage: 1
    }
  },
  computed: {
    albumPageImages() { return this.albumImages.slice((this.albumPage - 1) * 20, this.albumPage * 20) }
  },
  created() {
    this.pendingImages = new Map()
    this.requestVersion = 0
    this.albumVersion = 0
    this.disposed = false
    this.load()
  },
  beforeDestroy() {
    this.disposed = true
    Object.values(this.imageUrls).forEach(url => URL.revokeObjectURL(url))
  },
  methods: {
    search() { this.query.pageNum = 1; return this.load() },
    reset() {
      this.query = { realName: '', nickname: '', operatorName: '', pageNum: 1, pageSize: 20 }
      return this.load()
    },
    async load() {
      const version = ++this.requestVersion
      this.loading = true
      try {
        const response = await request({ url: '/life/staff/interview-page', params: this.query })
        if (version !== this.requestVersion || this.disposed) return
        this.rows = response.data.rows || []
        this.total = response.data.total || 0
        this.loadImages(this.rows.reduce((images, row) => images.concat((row.images || []).slice(0, 5)), []))
      } catch (e) {
        if (version === this.requestVersion) { this.rows = []; this.total = 0 }
      } finally {
        if (version === this.requestVersion) this.loading = false
      }
    },
    previewUrls(images) { return (images || []).map(item => this.imageUrls[item.id]).filter(Boolean) },
    loadImage(id) {
      if (this.imageUrls[id] || this.disposed) return Promise.resolve()
      if (this.pendingImages.has(id)) return this.pendingImages.get(id)
      const pending = request({ url: '/life/staff/images/' + encodeURIComponent(id), responseType: 'blob' })
        .then(blob => { if (!this.disposed) this.$set(this.imageUrls, id, URL.createObjectURL(blob)) })
        .catch(() => {})
        .finally(() => this.pendingImages.delete(id))
      this.pendingImages.set(id, pending)
      return pending
    },
    async loadImages(images) {
      for (let i = 0; i < images.length && !this.disposed; i += 4) {
        await Promise.all(images.slice(i, i + 4).map(item => this.loadImage(item.id)))
      }
    },
    async openAlbum(row) {
      const version = ++this.albumVersion
      this.albumTitle = (row.nickname || row.realName || '轻友') + ' · 回访图片合集'
      this.albumImages = []; this.albumPage = 1; this.albumError = false
      this.albumOpen = true; this.albumLoading = true
      try {
        const response = await request({ url: '/life/staff/interview-images', params: { customerId: row.customerId } })
        if (version !== this.albumVersion || this.disposed) return
        this.albumImages = response.data || []
        this.loadAlbumPage()
      } catch (e) {
        if (version === this.albumVersion) this.albumError = true
      } finally {
        if (version === this.albumVersion) this.albumLoading = false
      }
    },
    loadAlbumPage() { return this.loadImages(this.albumPageImages) },
    closeAlbum() { this.albumVersion++; this.albumImages = [] }
  }
}
</script>

<style scoped>
.search-form { margin-top: 20px; }
.thumbnails { display: flex; flex-wrap: wrap; gap: 6px; }
.thumbnail { width: 44px; height: 44px; border-radius: 4px; cursor: pointer; background: #f5f7fa; }
.muted { color: #909399; font-size: 13px; }
.album-body { min-height: 140px; max-height: 70vh; overflow-y: auto; }
.album-grid { display: grid; grid-template-columns: repeat(auto-fill, minmax(220px, 1fr)); gap: 16px; }
.album-card { padding: 12px; border: 1px solid #ebeef5; border-radius: 6px; }
.album-image { width: 100%; height: 180px; background: #f5f7fa; cursor: pointer; }
.image-placeholder { height: 100%; display: flex; align-items: center; justify-content: center; color: #909399; font-size: 12px; }
.image-time { margin: 10px 0 6px; font-size: 13px; color: #303133; }
.album-pagination { margin-top: 20px; text-align: center; }
</style>
