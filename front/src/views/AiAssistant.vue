<template>
<!-- 智能问答占位页。当前需求允许该模块暂不实现，
       但保留可见导航入口，方便后续扩展。 -->
  <AppLayout title="AI 助手" subtitle="该模块按当前开发要求暂不接入大模型">
    <section class="panel">
      <el-empty description="AI 问答模块暂不实现。活动、报名、组织、事务、通知、评价和积分数据已在其他页面开放查询。" />
      <el-alert title="说明" type="info" :closable="false" show-icon>
        <p>数据库保留 ai_qa_record 表，后续可接入大模型接口并保存问答记录。</p>
      </el-alert>
    </section>
  </AppLayout>
</template>

<script setup>
// 当前暂无交互逻辑，主布局提供登录后的页面外壳。
import AppLayout from '../components/AppLayout.vue'
</script>
