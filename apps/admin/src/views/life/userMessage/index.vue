<template>
  <div class="app-container">
    <el-form :model="queryParams" ref="queryForm" :inline="true" v-show="showSearch" label-width="68px">
      <el-form-item label="消息所属用户" prop="userId">
        <el-input
          v-model="queryParams.userId"
          placeholder="请输入消息所属用户"
          clearable
          size="small"
          @keyup.enter.native="handleQuery"
        />
      </el-form-item>
      <el-form-item label="消息类型：0点赞1回复" prop="msgType">
        <el-select v-model="queryParams.msgType" placeholder="请选择消息类型：0点赞1回复" clearable size="small">
          <el-option label="请选择字典生成" value="" />
        </el-select>
      </el-form-item>
      <el-form-item label="消息来源：0动态1评论" prop="msgFrom">
        <el-input
          v-model="queryParams.msgFrom"
          placeholder="请输入消息来源：0动态1评论"
          clearable
          size="small"
          @keyup.enter.native="handleQuery"
        />
      </el-form-item>
      <el-form-item label="来源用户" prop="fromUserId">
        <el-input
          v-model="queryParams.fromUserId"
          placeholder="请输入来源用户"
          clearable
          size="small"
          @keyup.enter.native="handleQuery"
        />
      </el-form-item>
      <el-form-item label="来源用户昵称" prop="fromNickName">
        <el-input
          v-model="queryParams.fromNickName"
          placeholder="请输入来源用户昵称"
          clearable
          size="small"
          @keyup.enter.native="handleQuery"
        />
      </el-form-item>
      <el-form-item label="是否已读" prop="readed">
        <el-input
          v-model="queryParams.readed"
          placeholder="请输入是否已读"
          clearable
          size="small"
          @keyup.enter.native="handleQuery"
        />
      </el-form-item>
      <el-form-item label="是否已读" prop="insertTime">
        <el-date-picker clearable size="small"
          v-model="queryParams.insertTime"
          type="date"
          value-format="yyyy-MM-dd"
          placeholder="选择是否已读">
        </el-date-picker>
      </el-form-item>
      <el-form-item label="是否已读" prop="insertUser">
        <el-input
          v-model="queryParams.insertUser"
          placeholder="请输入是否已读"
          clearable
          size="small"
          @keyup.enter.native="handleQuery"
        />
      </el-form-item>
      <el-form-item label="是否已读" prop="updateUser">
        <el-input
          v-model="queryParams.updateUser"
          placeholder="请输入是否已读"
          clearable
          size="small"
          @keyup.enter.native="handleQuery"
        />
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
          v-hasPermi="['life:userMessage:add']"
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
          v-hasPermi="['life:userMessage:edit']"
        >修改</el-button>
      </el-col>
      <el-col :span="1.5">
        <el-button
          type="danger"
          plain
          icon="el-icon-delete"
          size="mini"
          :disabled="multiple"
          @click="handleDelete"
          v-hasPermi="['life:userMessage:remove']"
        >删除</el-button>
      </el-col>
      <el-col :span="1.5">
        <el-button
          type="warning"
          plain
          icon="el-icon-download"
          size="mini"
		  :loading="exportLoading"
          @click="handleExport"
          v-hasPermi="['life:userMessage:export']"
        >导出</el-button>
      </el-col>
      <right-toolbar :showSearch.sync="showSearch" @queryTable="getList"></right-toolbar>
    </el-row>

    <el-table v-loading="loading" :data="userMessageList" @selection-change="handleSelectionChange">
      <el-table-column type="selection" width="55" align="center" />
      <el-table-column label="是否已读" align="center" prop="id" v-if="false"/>
      <el-table-column label="消息所属用户" align="center" prop="userId" />
      <el-table-column label="消息类型：0点赞1回复" align="center" prop="msgType" />
      <el-table-column label="消息来源：0动态1评论" align="center" prop="msgFrom" />
      <el-table-column label="来源用户" align="center" prop="fromUserId" />
      <el-table-column label="来源用户昵称" align="center" prop="fromNickName" />
      <el-table-column label="来源用户头像" align="center" prop="fromHead" />
      <el-table-column label="是否已读" align="center" prop="readed" />
      <el-table-column label="是否已读" align="center" prop="insertTime" width="180">
        <template slot-scope="scope">
          <span>{{ parseTime(scope.row.insertTime, '{y}-{m}-{d}') }}</span>
        </template>
      </el-table-column>
      <el-table-column label="是否已读" align="center" prop="insertUser" />
      <el-table-column label="是否已读" align="center" prop="updateUser" />
      <el-table-column label="操作" align="center" class-name="small-padding fixed-width">
        <template slot-scope="scope">
          <el-button
            size="mini"
            type="text"
            icon="el-icon-edit"
            @click="handleUpdate(scope.row)"
            v-hasPermi="['life:userMessage:edit']"
          >修改</el-button>
          <el-button
            size="mini"
            type="text"
            icon="el-icon-delete"
            @click="handleDelete(scope.row)"
            v-hasPermi="['life:userMessage:remove']"
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

    <!-- 添加或修改用户消息对话框 -->
    <el-dialog :title="title" :visible.sync="open" width="500px" append-to-body>
      <el-form ref="form" :model="form" :rules="rules" label-width="80px">
        <el-form-item label="消息所属用户" prop="userId">
          <el-input v-model="form.userId" placeholder="请输入消息所属用户" />
        </el-form-item>
        <el-form-item label="消息类型：0点赞1回复" prop="msgType">
          <el-select v-model="form.msgType" placeholder="请选择消息类型：0点赞1回复">
            <el-option label="请选择字典生成" value="" />
          </el-select>
        </el-form-item>
        <el-form-item label="消息来源：0动态1评论" prop="msgFrom">
          <el-input v-model="form.msgFrom" placeholder="请输入消息来源：0动态1评论" />
        </el-form-item>
        <el-form-item label="来源用户" prop="fromUserId">
          <el-input v-model="form.fromUserId" placeholder="请输入来源用户" />
        </el-form-item>
        <el-form-item label="来源用户昵称" prop="fromNickName">
          <el-input v-model="form.fromNickName" placeholder="请输入来源用户昵称" />
        </el-form-item>
        <el-form-item label="来源用户头像" prop="fromHead">
          <el-input v-model="form.fromHead" type="textarea" placeholder="请输入内容" />
        </el-form-item>
        <el-form-item label="来源用户头像" prop="deleted">
          <el-input v-model="form.deleted" placeholder="请输入来源用户头像" />
        </el-form-item>
        <el-form-item label="是否已读" prop="readed">
          <el-input v-model="form.readed" placeholder="请输入是否已读" />
        </el-form-item>
        <el-form-item label="是否已读" prop="insertTime">
          <el-date-picker clearable size="small"
            v-model="form.insertTime"
            type="datetime"
            value-format="yyyy-MM-dd HH:mm:ss"
            placeholder="选择是否已读">
          </el-date-picker>
        </el-form-item>
        <el-form-item label="是否已读" prop="insertUser">
          <el-input v-model="form.insertUser" placeholder="请输入是否已读" />
        </el-form-item>
        <el-form-item label="是否已读" prop="updateTime">
          <el-date-picker clearable size="small"
            v-model="form.updateTime"
            type="datetime"
            value-format="yyyy-MM-dd HH:mm:ss"
            placeholder="选择是否已读">
          </el-date-picker>
        </el-form-item>
        <el-form-item label="是否已读" prop="updateUser">
          <el-input v-model="form.updateUser" placeholder="请输入是否已读" />
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
import { listUserMessage, getUserMessage, delUserMessage, addUserMessage, updateUserMessage, exportUserMessage } from "@/api/life/userMessage";

export default {
  name: "UserMessage",
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
      // 用户消息表格数据
      userMessageList: [],
      // 弹出层标题
      title: "",
      // 是否显示弹出层
      open: false,
      // 查询参数
      queryParams: {
        pageNum: 1,
        pageSize: 10,
        userId: undefined,
        msgType: undefined,
        msgFrom: undefined,
        fromUserId: undefined,
        fromNickName: undefined,
        fromHead: undefined,
        readed: undefined,
        insertTime: undefined,
        insertUser: undefined,
        updateUser: undefined
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
    /** 查询用户消息列表 */
    getList() {
      this.loading = true;
      listUserMessage(this.queryParams).then(response => {
        this.userMessageList = response.rows;
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
        msgType: undefined,
        msgFrom: undefined,
        fromUserId: undefined,
        fromNickName: undefined,
        fromHead: undefined,
        deleted: undefined,
        readed: undefined,
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
      this.title = "添加用户消息";
    },
    /** 修改按钮操作 */
    handleUpdate(row) {
      this.loading = true;
      this.reset();
      const id = row.id || this.ids
      getUserMessage(id).then(response => {
        this.loading = false;
        this.form = response.data;
        this.open = true;
        this.title = "修改用户消息";
      });
    },
    /** 提交按钮 */
    submitForm() {
      this.$refs["form"].validate(valid => {
        if (valid) {
          this.buttonLoading = true;
          if (this.form.id != null) {
            updateUserMessage(this.form).then(response => {
              this.msgSuccess("修改成功");
              this.open = false;
              this.getList();
            }).finally(() => {
              this.buttonLoading = false;
            });
          } else {
            addUserMessage(this.form).then(response => {
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
      this.$confirm('是否确认删除用户消息编号为"' + ids + '"的数据项?', "警告", {
          confirmButtonText: "确定",
          cancelButtonText: "取消",
          type: "warning"
        }).then(() => {
          this.loading = true;
          return delUserMessage(ids);
        }).then(() => {
          this.loading = false;
          this.getList();
          this.msgSuccess("删除成功");
        }).catch(() => {});
    },
    /** 导出按钮操作 */
    handleExport() {
      const queryParams = this.queryParams;
      this.$confirm('是否确认导出所有用户消息数据项?', "警告", {
          confirmButtonText: "确定",
          cancelButtonText: "取消",
          type: "warning"
        }).then(() => {
          this.exportLoading = true;
          return exportUserMessage(queryParams);
        }).then(response => {
          this.download(response.msg);
          this.exportLoading = false;
        }).catch(() => {});
    }
  }
};
</script>
