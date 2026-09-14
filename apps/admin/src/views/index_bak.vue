<template>
  <div class="app-container home">
    <h2 style="text-align:center;">系统数据一览
      <el-button icon="el-icon-refresh" circle title="刷新数据" style="float:right;" @click="refreshIndex"></el-button>
    </h2>
    <el-row :gutter="20">
        <el-col :sm="8" :lg="4">
          <div class="title">注册账户</div>
          <div class="content">{{summaryData.totalAccount}}</div>
        </el-col>
        <el-col :sm="8" :lg="4">
          <div class="title">当日充值</div>
          <div class="content">{{summaryData.todayRecharge}}</div>
        </el-col>
        <el-col :sm="8" :lg="4">
          <div class="title">总金币</div>
          <div class="content">{{summaryData.totalCoin}}</div>
        </el-col>
        <el-col :sm="8" :lg="4">
          <div class="title">总金豆</div>
          <div class="content">{{summaryData.totalPoint}}</div>
        </el-col>
        <el-col :sm="8" :lg="4">
          <div class="title">上架盒子</div>
          <div class="content">{{summaryData.totalBox}}</div>
        </el-col>
        <el-col :sm="8" :lg="4">
          <div class="title">上架商品</div>
          <div class="content">{{summaryData.totalGoods}}</div>
        </el-col>
        <el-col :sm="8" :lg="4">
          <div class="title">开箱商品/消费金币</div>
          <div class="content">{{summaryData.openOrder}}/{{summaryData.openCoin}}</div>
        </el-col>
        <el-col :sm="8" :lg="4">
          <div class="title">待发货/总订单</div>
          <div class="content">{{summaryData.preDeliverOrder}}/{{summaryData.totalDeliverOrder}}</div>
        </el-col>
    </el-row>
    <el-divider />
    <el-row :gutter="20">
      <el-col :xs="24" :sm="24" :lg="12">
        <!-- <chart ref="chart1" :options="accountOptions" class="charts" :auto-resize="true"></chart> -->
        <div id="chart1" :style="{width: '100%', height: '400px'}"></div>
      </el-col>
      <el-col :xs="24" :sm="24" :lg="12">
        <!-- <chart ref="chart2" :options="rechargeOptions" class="charts" :auto-resize="true"></chart> -->
        <div id="chart2" :style="{width: '100%', height: '400px'}"></div>
      </el-col>
    </el-row>
    <el-row :gutter="20">
      <el-col :xs="24" :sm="24" :lg="12">
        <!-- <chart ref="chart3" :options="openOptions" class="charts" :auto-resize="true"></chart> -->
        <div id="chart3" :style="{width: '100%', height: '400px'}"></div>
      </el-col>
     
    </el-row>
  </div>
</template>

<script>
// import {summary,rechargeChart,accountChart,openChart}  from "@/api/life/index";
import echarts from 'echarts'
// import resize from './mixins/resize'

export default {
  name: "index",
  //  mixins: [resize],
  props: {
    className: {
      type: String,
      default: 'chart'
    },
    width: {
      type: String,
      default: '100%'
    },
    height: {
      type: String,
      default: '300px'
    }
  },
  data() {
    return {
      loading:false,
      summaryData:{},
      chart1:{},
      chart2:{},
      chart3:{}
    };
  },
  mounted(){
    this.initChart();
  },
  created(){
    this.loading = true;
    this.summary();
    this.rechargeChart();
    this.accountChart();
    this.openChart();
  },
  methods: {
    initChart() {
      this.chart1 = echarts.init(document.getElementById('chart1'));
      this.chart2 = echarts.init(document.getElementById('chart2'));
      this.chart3 = echarts.init(document.getElementById('chart3'));
    },
    refreshIndex(){
      this.loading = true;
      this.summary();
    },
    summary:function(){
      summary().then(response=>{
        this.summaryData = response.data;
        this.loading = false;
      });
    },
    rechargeChart(){
      rechargeChart().then(res=>{
        this.chart1.setOption({
          title:{
              text:'每日充值'
          },
          xAxis: {
              type: 'category',
              data: res.data.x
          },
          tooltip: {
              trigger: 'axis'
          },
          legend: {
              data: ['充值金额']
          },
          yAxis: {
              type: 'value'
          },
          series: [
              {
                name:'微信充值',
                data: res.data.y1,
                type: 'line',
                areaStyle: {},
                smooth: true
              },
              {
                name:'支付宝充值',
                data: res.data.y2,
                type: 'line',
                areaStyle: {},
                smooth: true
              }
          ],
          animationDuration: 2000
        })
      });
    },
    accountChart(){
      accountChart().then(res=>{
          this.chart2.setOption({
            title:{
                text:'注册账户'
            },
            xAxis: {
                type: 'category',
                data: res.data.x
            },
            tooltip: {
                trigger: 'axis'
            },
            legend: {
                data: ['新注册账户']
            },
            yAxis: {
                type: 'value'
            },
            series: [
                {
                  name:'手机注册',
                  data: res.data.y1,
                  type: 'line',
                  areaStyle: {},
                  smooth: true
                },
                {
                  name:'微信注册',
                  data: res.data.y2,
                  type: 'line',
                  areaStyle: {},
                  smooth: true
                }
            ],
            animationDuration: 2000
        })
      });
    },
    openChart(){
      openChart().then(res=>{
          this.chart3.setOption({
            title:{
                text:'每日开箱'
            },
            xAxis: {
                type: 'category',
                data: res.data.x
            },
            tooltip: {
                trigger: 'axis'
            },
            legend: {
                data: ['开箱数据']
            },
            yAxis: {
                type: 'value'
            },
            series: [
                {
                  name:'开箱商品',
                  data: res.data.y1,
                  type: 'bar',
                  areaStyle: {},
                  smooth: true
                },
                {
                  name:'开箱金币',
                  data: res.data.y2,
                  type: 'bar',
                  areaStyle: {},
                  smooth: true
                }
            ],
            animationDuration: 2000
        })
      });
    }
  },
};
</script>

<style scoped lang="scss">
.home {
  .charts{
    width:100%;
  }
  hr {
    margin-top: 20px;
    margin-bottom: 20px;
    border: 0;
    border-top: 1px solid #eee;
  }
  .title{
    text-align: center;
    line-height: 24px;
    margin-top: 14px;
  }
  .content{
    text-align: center;
    font-size: 16px;
    line-height: 24px;
    font-weight: bolder;
    color:#409EFF;
  }
}
</style>

