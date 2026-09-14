<template>
  <div class="app-container">
    <el-form :model="queryParams" ref="queryForm" :inline="true" v-show="showSearch" label-width="68px">
     <el-form-item label="动态内容" prop="content">
        <el-input
          v-model="queryParams.content"
          placeholder="请输入动态内容"
          clearable
          size="small"
          @keyup.enter.native="handleQuery"
        />
      </el-form-item>
      <el-form-item label="作者昵称" prop="nickName">
        <el-input
          v-model="queryParams.nickName"
          placeholder="请输入用户昵称"
          clearable
          size="small"
          @keyup.enter.native="handleQuery"
        />
      </el-form-item>
      <el-form-item label="标签" prop="tag">
        <el-input
          v-model="queryParams.tag"
          placeholder="请输入标签"
          clearable
          size="small"
          @keyup.enter.native="handleQuery"
        />
      </el-form-item>
      <el-form-item label="发布时间">
        <el-date-picker
          v-model="dateRange"
          size="small"
          style="width: 240px"
          value-format="yyyy-MM-dd"
          type="daterange"
          range-separator="-"
          start-placeholder="开始日期"
          end-placeholder="结束日期"
        ></el-date-picker>
      </el-form-item>
      <el-form-item>
        <el-button type="primary" icon="el-icon-search" size="mini" @click="handleQuery">搜索</el-button>
        <el-button icon="el-icon-refresh" size="mini" @click="resetQuery">重置</el-button>
      </el-form-item>
    </el-form>

    <el-row :gutter="10" class="mb8">
      <el-col :span="1.5">
        <el-button
          type="danger"
          plain
          icon="el-icon-delete"
          size="mini"
          :disabled="multiple"
          @click="handleDelete"
          v-hasPermi="['life:userPublish:remove']"
        >删除</el-button>
      </el-col>
      <right-toolbar :showSearch.sync="showSearch" @queryTable="getList"></right-toolbar>
    </el-row>

    <el-table v-loading="loading" :data="userPublishList" @selection-change="handleSelectionChange">
      <el-table-column type="selection" width="55" align="center" />
      <el-table-column label="动态内容" align="center" prop="content"  width="600"/>
      <el-table-column label="配图" align="center" prop="images" >
        <template slot-scope="scope">
          <el-image
            style="width: 120px; height: 60px"
            :src="scope.row.images"
            :preview-src-list="scope.row.imageList"
          ></el-image>
        </template>
      </el-table-column>
      <el-table-column label="作者昵称" align="center" prop="nickName" width="200" />
      <el-table-column label="标签" align="center" prop="tag" />
      <el-table-column label="点赞数" align="center" prop="praiseNum"/>
      <el-table-column label="评论数" align="center" prop="commentNum" />
      <el-table-column label="发布时间" align="center" prop="insertTime" width="150">
        <template slot-scope="scope">
          <span>{{ parseTime(scope.row.insertTime, '{y}-{m}-{d} {h}:{i}:{s}') }}</span>
        </template>
      </el-table-column>
      <el-table-column label="操作" align="center" class-name="small-padding fixed-width" >
        <template slot-scope="scope">
          <el-button
            size="mini"
            type="text"
            icon="el-icon-delete"
            @click="handleDelete(scope.row)"
            v-hasPermi="['life:userPublish:remove']"
          >删除</el-button>
        </template>
      </el-table-column>
    </el-table>

    <pagination
      v-show="total>0"
      :total="total"
      :page.sync="queryParams.pageNum"
      :limit.sync="queryParams.pageSize"
      @pagination="getList"
    />

    <!-- 添加或修改用户动态对话框 -->
    <el-dialog :title="title" :visible.sync="open" width="500px" append-to-body>
      <el-form ref="form" :model="form" :rules="rules" label-width="80px">
        <el-form-item label="用户" prop="userId">
          <el-input v-model="form.userId" placeholder="请输入用户" />
        </el-form-item>
        <el-form-item label="动态内容">
          <editor v-model="form.content" :min-height="192"/>
        </el-form-item>
        <el-form-item label="标签" prop="tag">
          <el-input v-model="form.tag" placeholder="请输入标签" />
        </el-form-item>
        <el-form-item label="配图，分号隔开" prop="images">
          <el-input v-model="form.images" type="textarea" placeholder="请输入内容" />
        </el-form-item>
        <el-form-item label="逻辑删除" prop="deleted">
          <el-input v-model="form.deleted" placeholder="请输入逻辑删除" />
        </el-form-item>
        <el-form-item label="逻辑删除" prop="insertTime">
          <el-date-picker clearable size="small"
            v-model="form.insertTime"
            type="datetime"
            value-format="yyyy-MM-dd HH:mm:ss"
            placeholder="选择逻辑删除">
          </el-date-picker>
        </el-form-item>
        <el-form-item label="逻辑删除" prop="insertUser">
          <el-input v-model="form.insertUser" placeholder="请输入逻辑删除" />
        </el-form-item>
        <el-form-item label="逻辑删除" prop="updateTime">
          <el-date-picker clearable size="small"
            v-model="form.updateTime"
            type="datetime"
            value-format="yyyy-MM-dd HH:mm:ss"
            placeholder="选择逻辑删除">
          </el-date-picker>
        </el-form-item>
        <el-form-item label="逻辑删除" prop="updateUser">
          <el-input v-model="form.updateUser" placeholder="请输入逻辑删除" />
        </el-form-item>
        <el-form-item label="点赞数" prop="praiseNum">
          <el-input v-model="form.praiseNum" placeholder="请输入点赞数" />
        </el-form-item>
        <el-form-item label="评论数" prop="commentNum">
          <el-input v-model="form.commentNum" placeholder="请输入评论数" />
        </el-form-item>
        <el-form-item label="评论数" prop="revision">
          <el-input v-model="form.revision" placeholder="请输入评论数" />
        </el-form-item>
      </el-form>
      <div slot="footer" class="dialog-footer">
        <el-button :loading="buttonLoading" type="primary" @click="submitForm">确 定</el-button>
        <el-button @click="cancel">取 消</el-button>
      </div>
    </el-dialog>
  </div>
</template>

<script>
import { listUserPublish, getUserPublish, delUserPublish, addUserPublish, updateUserPublish, exportUserPublish } from "@/api/life/userPublish";

export default {
  name: "UserPublish",
  data() {
    return {
      // 按钮loading
      buttonLoading: false,
      // 遮罩层
      loading: true,
      // 导出遮罩层
      exportLoading: false,
      // 选中数组
      ids: [],
      // 非单个禁用
      single: true,
      // 非多个禁用
      multiple: true,
      // 显示搜索条件
      showSearch: true,
      // 总条数
      total: 0,
      // 用户动态表格数据
      userPublishList: [],
      // 弹出层标题
      title: "",
      // 是否显示弹出层
      open: false,
      dateRange:[],
      // 查询参数
      queryParams: {
        pageNum: 1,
        pageSize: 10,
        userId: undefined,
        content: undefined,
        tag: undefined,
        images: undefined,
        insertTime: undefined,
        insertUser: undefined,
        updateUser: undefined,
        praiseNum: undefined,
        commentNum: undefined,
      },
      // 表单参数
      form: {},
      // 表单校验
      rules: {
      }
    };
  },
  created() {
    this.getList();
  },
  methods: {
    /** 查询用户动态列表 */
    getList() {
      this.loading = true;
      listUserPublish(this.addDateRange(this.queryParams, this.dateRange)).then(response => {
        this.userPublishList = response.rows;
        this.total = response.total;
        this.loading = false;
      });
    },
    // 取消按钮
    cancel() {
      this.open = false;
      this.reset();
    },
    // 表单重置
    reset() {
      this.form = {
        id: undefined,
        userId: undefined,
        content: undefined,
        tag: undefined,
        images: undefined,
        deleted: undefined,
        insertTime: undefined,
        insertUser: undefined,
        updateTime: undefined,
        updateUser: undefined,
        praiseNum: undefined,
        commentNum: undefined,
        revision: undefined
      };
      this.resetForm("form");
    },
    /** 搜索按钮操作 */
    handleQuery() {
      this.queryParams.pageNum = 1;
      this.getList();
    },
    /** 重置按钮操作 */
    resetQuery() {
      this.resetForm("queryForm");
      this.handleQuery();
    },
    // 多选框选中数据
    handleSelectionChange(selection) {
      this.ids = selection.map(item => item.id)
      this.single = selection.length!==1
      this.multiple = !selection.length
    },
    /** 新增按钮操作 */
    handleAdd() {
      this.reset();
      this.open = true;
      this.title = "添加用户动态";
    },
    /** 修改按钮操作 */
    handleUpdate(row) {
      this.loading = true;
      this.reset();
      const id = row.id || this.ids
      getUserPublish(id).then(response => {
        this.loading = false;
        this.form = response.data;
        this.open = true;
        this.title = "修改用户动态";
      });
    },
    /** 提交按钮 */
    submitForm() {
      this.$refs["form"].validate(valid => {
        if (valid) {
          this.buttonLoading = true;
          if (this.form.id != null) {
            updateUserPublish(this.form).then(response => {
              this.msgSuccess("修改成功");
              this.open = false;
              this.getList();
            }).finally(() => {
              this.buttonLoading = false;
            });
          } else {
            addUserPublish(this.form).then(response => {
              this.msgSuccess("新增成功");
              this.open = false;
              this.getList();
            }).finally(() => {
              this.buttonLoading = false;
            });
          }
        }
      });
    },
    /** 删除按钮操作 */
    handleDelete(row) {
      const ids = row.id || this.ids;
      this.$confirm('是否确认删除用户动态编号为"' + ids + '"的数据项?', "警告", {
          confirmButtonText: "确定",
          cancelButtonText: "取消",
          type: "warning"
        }).then(() => {
          this.loading = true;
          return delUserPublish(ids);
        }).then(() => {
          this.loading = false;
          this.getList();
          this.msgSuccess("删除成功");
        }).catch(() => {});
    },
    /** 导出按钮操作 */
    handleExport() {
      const queryParams = this.queryParams;
      this.$confirm('是否确认导出所有用户动态数据项?', "警告", {
          confirmButtonText: "确定",
          cancelButtonText: "取消",
          type: "warning"
        }).then(() => {
          this.exportLoading = true;
          return exportUserPublish(queryParams);
        }).then(response => {
          this.download(response.msg);
          this.exportLoading = false;
        }).catch(() => {});
    }
  }
};
</script>
