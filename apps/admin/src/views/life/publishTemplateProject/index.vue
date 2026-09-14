<template>
  <div class="app-container">
    <el-form :model="queryParams" ref="queryForm" :inline="true" v-show="showSearch" label-width="68px">
      <el-form-item label="模板名称" prop="templateId">
        <el-select v-model="queryParams.templateId" placeholder="请选择模板名称" clearable size="small">
          <el-option 
            v-for="item in templateList" 
            :key="item.id" 
            :label="item.templateName" 
            :value="item.id" 
            />
        </el-select>
      </el-form-item>
      <el-form-item label="项目名称" prop="projectName">
        <el-input
          v-model="queryParams.projectName"
          placeholder="请输入项目名称"
          clearable
          size="small"
          @keyup.enter.native="handleQuery"
        />
      </el-form-item>
      <el-form-item label="是否必填" prop="required">
        <el-select v-model="queryParams.required" placeholder="是否必填" clearable size="small">
          <el-option label="是" value="1" />
          <el-option label="否" value="0" />
        </el-select>
      </el-form-item>
      <el-form-item label="项目状态" prop="status">
        <el-select v-model="queryParams.status" placeholder="请选择项目状态" clearable size="small">
          <el-option label="可用" value="1" />
          <el-option label="禁用" value="-1" />
        </el-select>
      </el-form-item>
      <el-form-item>
        <el-button type="primary" icon="el-icon-search" size="mini" @click="handleQuery">搜索</el-button>
        <el-button icon="el-icon-refresh" size="mini" @click="resetQuery">重置</el-button>
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
          v-hasPermi="['life:publishTemplateProject:add']"
        >新增</el-button>
      </el-col>
      <el-col :span="1.5">
        <el-button
          type="success"
          plain
          icon="el-icon-edit"
          size="mini"
          :disabled="single"
          @click="handleUpdate"
          v-hasPermi="['life:publishTemplateProject:edit']"
        >修改</el-button>
      </el-col>
      <right-toolbar :showSearch.sync="showSearch" @queryTable="getList"></right-toolbar>
    </el-row>

    <el-table v-loading="loading" :data="publishTemplateProjectList">
      <el-table-column label="模板名称" align="center" prop="templateName" />
      <el-table-column label="项目名称" align="center" prop="projectName" />
      <el-table-column label="提示语" align="center" prop="placeholder" />
      <el-table-column label="字数上限" align="center" prop="maxNum" />
      <el-table-column label="是否必填" align="center" prop="required" >
        <template slot-scope="scope">
          {{scope.row.required == 0 ? '否' : '是'}}
        </template>
      </el-table-column>
      <el-table-column label="项目状态" align="center" prop="status">
        <template slot-scope="scope">
          {{scope.row.status == -1 ? '禁用' : '可用'}}
        </template>
      </el-table-column>
      <el-table-column label="创建时间" align="center" prop="insertTime" width="180">
        <template slot-scope="scope">
          <span>{{ parseTime(scope.row.insertTime, '{y}-{m}-{d} {h}:{i}:{s}') }}</span>
        </template>
      </el-table-column>
      <el-table-column label="操作" align="center" class-name="small-padding fixed-width">
        <template slot-scope="scope">
          <el-button
            size="mini"
            type="text"
            icon="el-icon-edit"
            @click="handleUpdate(scope.row)"
            v-hasPermi="['life:publishTemplateProject:edit']"
          >修改</el-button>
          <el-button
            size="mini"
            type="text"
            icon="el-icon-delete"
            @click="handleDelete(scope.row)"
            v-hasPermi="['life:publishTemplateProject:remove']"
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

    <!-- 添加或修改状态模板项目对话框 -->
    <el-dialog :title="title" :visible.sync="open" width="500px" append-to-body>
      <el-form ref="form" :model="form" :rules="rules" label-width="80px">
        <el-form-item label="模板名称" prop="templateId">
          <el-select v-model="form.templateId" placeholder="请选择模板名称">
            <el-option 
              v-for="item in templateList" 
              :key="item.id" 
              :label="item.templateName" 
              :value="item.id" 
              />
          </el-select>
        </el-form-item>
        <el-form-item label="项目名称" prop="projectName">
          <el-input v-model="form.projectName" placeholder="请输入项目名称" />
        </el-form-item>
        <el-form-item label="提示语" prop="placeholder">
          <el-input v-model="form.placeholder" placeholder="请输入提示语" />
        </el-form-item>
        <el-form-item label="字数上限" prop="maxNum">
          <el-input v-model="form.maxNum" placeholder="请输入字数上限" />
        </el-form-item>
        <el-form-item label="是否必填" prop="required">
          <el-radio-group v-model="form.required">
            <el-radio label="1">是</el-radio>
            <el-radio label="0">否</el-radio>
          </el-radio-group>
        </el-form-item>
        <el-form-item label="项目状态">
          <el-radio-group v-model="form.status">
            <el-radio label="1">启用</el-radio>
            <el-radio label="-1">禁用</el-radio>
          </el-radio-group>
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
import { listPublishTemplateProject, getPublishTemplateProject, delPublishTemplateProject, addPublishTemplateProject, updatePublishTemplateProject, exportPublishTemplateProject } from "@/api/life/publishTemplateProject";
import { getTemplateList } from "@/api/life/publishTemplate";

export default {
  name: "PublishTemplateProject",
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
      // 状态模板项目表格数据
      publishTemplateProjectList: [],
      // 弹出层标题
      title: "",
      // 是否显示弹出层
      open: false,
      // 查询参数
      queryParams: {
        pageNum: 1,
        pageSize: 10,
        templateId: undefined,
        projectName: undefined,
        placeholder: undefined,
        maxNum: undefined,
        required: undefined,
        status: undefined,
        insertTime: undefined,
        insertUser: undefined,
        updateUser: undefined
      },
      // 表单参数
      form: {},
      templateList: [],
      // 表单校验
      rules: {
      }
    };
  },
  created() {
    this.getList();
    this.getTemplates();
  },
  methods: {
    /** 查询状态模板项目列表 */
    getList() {
      this.loading = true;
      listPublishTemplateProject(this.queryParams).then(response => {
        this.publishTemplateProjectList = response.rows;
        this.total = response.total;
        this.loading = false;
      });
    },
    getTemplates(){
      getTemplateList().then(response =>{
        this.templateList = response.data;
      })
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
        templateId: undefined,
        projectName: undefined,
        placeholder: undefined,
        maxNum: undefined,
        required: "0",
        status: "1",
        insertTime: undefined,
        insertUser: undefined,
        updateTime: undefined,
        updateUser: undefined
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
      this.title = "添加状态模板项目";
    },
    /** 修改按钮操作 */
    handleUpdate(row) {
      this.loading = true;
      this.reset();
      const id = row.id || this.ids
      getPublishTemplateProject(id).then(response => {
        this.loading = false;
        this.form = response.data;
        this.open = true;
        this.title = "修改状态模板项目";
      });
    },
    /** 提交按钮 */
    submitForm() {
      this.$refs["form"].validate(valid => {
        if (valid) {
          this.buttonLoading = true;
          if (this.form.id != null) {
            updatePublishTemplateProject(this.form).then(response => {
              this.msgSuccess("修改成功");
              this.open = false;
              this.getList();
            }).finally(() => {
              this.buttonLoading = false;
            });
          } else {
            addPublishTemplateProject(this.form).then(response => {
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
      this.$confirm('是否确认删除状态模板项目编号为"' + ids + '"的数据项?', "警告", {
          confirmButtonText: "确定",
          cancelButtonText: "取消",
          type: "warning"
        }).then(() => {
          this.loading = true;
          return delPublishTemplateProject(ids);
        }).then(() => {
          this.loading = false;
          this.getList();
          this.msgSuccess("删除成功");
        }).catch(() => {});
    },
    /** 导出按钮操作 */
    handleExport() {
      const queryParams = this.queryParams;
      this.$confirm('是否确认导出所有状态模板项目数据项?', "警告", {
          confirmButtonText: "确定",
          cancelButtonText: "取消",
          type: "warning"
        }).then(() => {
          this.exportLoading = true;
          return exportPublishTemplateProject(queryParams);
        }).then(response => {
          this.download(response.msg);
          this.exportLoading = false;
        }).catch(() => {});
    }
  }
};
</script>
