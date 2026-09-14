<template>
  <div class="app-container">
    <el-form
      :model="queryParams"
      ref="queryForm"
      :inline="true"
      v-show="showSearch"
      label-width="68px"
    >
      <el-form-item label="标题" prop="title">
        <el-input
          v-model="queryParams.title"
          placeholder="请输入标题"
          clearable
          size="small"
          @keyup.enter.native="handleQuery"
        />
      </el-form-item>
      <el-form-item label="介绍" prop="Introduction">
        <el-input
          v-model="queryParams.Introduction"
          placeholder="请输入介绍"
          clearable
          size="small"
          @keyup.enter.native="handleQuery"
        />
      </el-form-item>
      <el-form-item>
        <el-button
          type="primary"
          icon="el-icon-search"
          size="mini"
          @click="handleQuery"
          >搜索</el-button
        >
        <el-button icon="el-icon-refresh" size="mini" @click="resetQuery"
          >重置</el-button
        >
      </el-form-item>
    </el-form>

    <el-row :gutter="10" class="mb8">
      <el-col :span="1.5">
        <el-button
          type="primary"
          plain
          icon="el-icon-plus"
          size="mini"
          @click="handleAdd"
          v-hasPermi="['life:essay:add']"
          >新增</el-button
        >
      </el-col>
      <el-col :span="1.5">
        <el-button
          type="success"
          plain
          icon="el-icon-edit"
          size="mini"
          :disabled="single"
          @click="handleUpdate"
          v-hasPermi="['life:essay:edit']"
          >修改</el-button
        >
      </el-col>
      <el-col :span="1.5">
        <el-button
          type="danger"
          plain
          icon="el-icon-delete"
          size="mini"
          :disabled="multiple"
          @click="handleDelete"
          v-hasPermi="['life:essay:remove']"
          >删除</el-button
        >
      </el-col>
      <right-toolbar
        :showSearch.sync="showSearch"
        @queryTable="getList"
      ></right-toolbar>
    </el-row>

    <el-table
      v-loading="loading"
      :data="essayList"
      @selection-change="handleSelectionChange"
    >
      <el-table-column type="selection" width="55" align="center" />
      <el-table-column
        label="标题"
        align="center"
        prop="title"
        show-overflow-tooltip
      />
      <!-- <el-table-column label="内容" align="center" prop="content" /> -->
      <el-table-column label="介绍" align="center" prop="Introduction" />
      <el-table-column label="封面" align="center" prop="titleUrl">
        <template slot-scope="scope">
          <el-image
            style="width: 120px; height: 60px"
            :src="scope.row.titleUrl"
            :preview-src-list="[scope.row.titleUrl]"
          ></el-image>
        </template>
      </el-table-column>
      <el-table-column label="排序" align="center" prop="orderNum" />
      <el-table-column label="状态" align="center" prop="status">
        <template slot-scope="scope">
          <el-switch
            v-model="scope.row.status"
            active-value="1"
            inactive-value="0"
            @change="handleStatusChange(scope.row)"
          >
          </el-switch>
        </template>
      </el-table-column>
      <el-table-column label="作者" align="center" prop="authorNickName" />
      <el-table-column label="标签" align="center" prop="label" />
      <el-table-column
        label="创建时间"
        align="center"
        prop="insertTime"
        width="180"
      >
        <template slot-scope="scope">
          <span>{{
            parseTime(scope.row.insertTime, "{y}-{m}-{d} {h}:{i}:{s}")
          }}</span>
        </template>
      </el-table-column>
      <el-table-column
        label="操作"
        align="center"
        class-name="small-padding fixed-width"
      >
        <template slot-scope="scope">
          <el-button
            size="mini"
            type="text"
            icon="el-icon-edit"
            @click="handleUpdate(scope.row)"
            v-hasPermi="['life:essay:edit']"
            >修改</el-button
          >
          <el-button
            size="mini"
            type="text"
            icon="el-icon-delete"
            @click="handleDelete(scope.row)"
            v-hasPermi="['life:essay:remove']"
            >删除</el-button>
        </template>
      </el-table-column>
    </el-table>

    <pagination
      v-show="total > 0"
      :total="total"
      :page.sync="queryParams.pageNum"
      :limit.sync="queryParams.pageSize"
      @pagination="getList"
    />

    <!-- 添加或修改文章对话框 -->
    <el-dialog :title="title" :visible.sync="open" width="800px" append-to-body>
      <el-form ref="form" :model="form" :rules="rules" label-width="80px">
        <el-form-item label="标题" prop="title">
          <el-input v-model="form.title" placeholder="请输入标题" />
        </el-form-item>
        <el-form-item label="介绍" prop="Introduction">
          <el-input v-model="form.Introduction" placeholder="请输入介绍" />
        </el-form-item>
        <el-row>
          <el-col :span="12">
            <el-form-item label="排序" prop="orderNum">
              <el-input v-model="form.orderNum" placeholder="请输入排序" />
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="状态">
              <el-radio-group v-model="form.status">
                <el-radio label="1">启用</el-radio>
                <el-radio label="0">停用</el-radio>
              </el-radio-group>
            </el-form-item>
          </el-col>
        </el-row>
        <el-form-item label="重置未读" v-if="form.id != null">
          <el-checkbox v-model="form.updateNotRead">重置</el-checkbox>
        </el-form-item>
        <el-form-item label="封面" prop="titleUrl">
          <imageLocalUpload v-model="form.titleUrl" :limit=1 />
        </el-form-item>
        <el-form-item label="标签" prop="label">
          <el-checkbox-group v-model="form.labels">
            <el-checkbox v-for="item in labelList" :label="item.id" :key="item.id">{{item.name}}</el-checkbox>
          </el-checkbox-group>
        </el-form-item>
        <el-form-item label="内容" prop="content">
          <editorLocal v-model="form.content" type="url" :min-height="192" :fileSize="100" />
        </el-form-item>
      </el-form>
      <div slot="footer" class="dialog-footer">
        <el-button :loading="buttonLoading" type="primary" @click="submitForm"
          >确 定</el-button
        >
        <el-button @click="cancel">取 消</el-button>
      </div>
    </el-dialog>
  </div>
</template>

<script>
import { listEssay, getEssay, delEssay, addEssay, updateEssay, changeStatus } from "@/api/life/essay";
import { isInteger } from "@/api/tool/validate";
import { getLabelList } from "@/api/life/label";

export default {
  name: "Essay",
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
      // 文章表格数据
      essayList: [],
      // labelList
      labelList: [],
      // 弹出层标题
      title: "",
      // 是否显示弹出层
      open: false,
      // 查询参数
      queryParams: {
        pageNum: 1,
        pageSize: 10,
        title: undefined,
        content: undefined,
        Introduction: undefined,
        status: undefined,
        orderNum: undefined,
        insertTime: undefined,
        author: undefined,
        titleUrl: undefined,
        updateUser: undefined,
        isVideo: undefined,
      },
      // 表单参数
      form: {},
      // 表单校验
      rules: {
        title: [
          { required: true, message: "文章标题不能为空", trigger: "blur" }
        ],
        Introduction: [
          { required: true, message: "文章介绍不能为空", trigger: "blur" }
        ],
        orderNum: [
          { required: true, message: "文章排序不能为空", trigger: "blur" },
          { validator: isInteger, trigger: 'blur'}
        ],
        titleUrl: [
          { required: true, message: "文章封面不能为空", trigger: "blur" }
        ],
        content: [
          { required: true, message: "文章内容不能为空", trigger: "blur" }
        ],
      },
    };
  },
  created() {
    this.getList();
  },
  methods: {
    /** 查询文章列表 */
    getList() {
      this.loading = true;
      listEssay(this.queryParams).then((response) => {
        this.essayList = response.rows;
        for (var i = 0; i < this.essayList.length; i++) {
          this.essayList[i].status = this.essayList[i].status + "";
          console.log(this.essayList[i].titleUrl)
        }
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
        title: undefined,
        content: undefined,
        Introduction: undefined,
        status: "1",
        orderNum: undefined,
        insertTime: undefined,
        author: undefined,
        titleUrl: undefined,
        updateTime: undefined,
        updateUser: undefined,
        isVideo: undefined,
        labels: [],
        updateNotRead: false,
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
      this.ids = selection.map((item) => item.id);
      this.single = selection.length !== 1;
      this.multiple = !selection.length;
    },
    /** 新增按钮操作 */
    handleAdd() {
      this.reset();
      getLabelList().then((response) =>{
        this.labelList = response
      });
      this.open = true;
      this.title = "添加文章";
    },
    /** 修改按钮操作 */
    handleUpdate(row) {
      this.loading = true;
      this.reset();
      const id = row.id || this.ids;
      getEssay(id).then((response) => {
        this.loading = false;
        this.form = response.data;
        this.form.status = this.form.status + '';
        for(var i=0; i<this.form.labels.length; i++){
          this.form.labels[i] = this.form.labels[i] + '';
        }
        this.open = true;
        this.title = "修改文章";
      });
      getLabelList().then((response) =>{
        this.labelList = response;
      });
    },
    /** 提交按钮 */
    submitForm() {
      this.$refs["form"].validate((valid) => {
        if (valid) {
          this.buttonLoading = true;
          if (this.form.id != null) {
            updateEssay(this.form)
              .then((response) => {
                this.msgSuccess("修改成功");
                this.open = false;
                this.getList();
              })
              .finally(() => {
                this.buttonLoading = false;
              });
          } else {
            addEssay(this.form)
              .then((response) => {
                this.msgSuccess("新增成功");
                this.open = false;
                this.getList();
              })
              .finally(() => {
                this.buttonLoading = false;
              });
          }
        }
      });
    },
    /** 删除按钮操作 */
    handleDelete(row) {
      const ids = row.id || this.ids;
      this.$confirm('是否确认删除文章编号为"' + ids + '"的数据项?', "警告", {
        confirmButtonText: "确定",
        cancelButtonText: "取消",
        type: "warning",
      })
        .then(() => {
          this.loading = true;
          return delEssay(ids);
        })
        .then(() => {
          this.loading = false;
          this.getList();
          this.msgSuccess("删除成功");
        })
        .catch(() => {});
    },
    // 文章状态修改
    handleStatusChange(row) {
      let text = row.status === "0" ? "停用" : "启用";
      this.$confirm('确认要"' + text + '""' + row.title + '"文章吗?', "警告", {
        confirmButtonText: "确定",
        cancelButtonText: "取消",
        type: "warning",
      })
        .then(function () {
          return changeStatus(row.id, row.status);
        })
        .then(() => {
          this.msgSuccess(text + "成功");
        })
        .catch(function () {
          row.status = row.status === "0" ? "1" : "0";
        });
    },
    
  },
};
</script>
