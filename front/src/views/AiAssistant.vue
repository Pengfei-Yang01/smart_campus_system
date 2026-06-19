<template>
  <AppLayout title="AI 助手" subtitle="该模块按当前开发要求暂不接入大模型">
    <section class="panel">
      <el-empty description="AI 问答模块暂不实现。活动、报名、组织和积分数据已在其他页面完整开放查询。" />
      <el-alert title="说明" type="info" :closable="false" show-icon>
        <p>数据库保留 ai_qa_record 表，后续可接入大模型接口并保存问答记录。</p>
      </el-alert>
    </section>
  </AppLayout>
</template>

<script setup>
import AppLayout from '../components/AppLayout.vue'
</script>
