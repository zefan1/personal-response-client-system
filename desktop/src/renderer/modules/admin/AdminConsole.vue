<template>
  <section class="ops-admin-shell">
    <aside class="ops-admin-sidebar">
      <div class="ops-admin-brand">
        <strong>运营后台</strong>
        <span>{{ sessionLabel }}</span>
      </div>
      <nav class="ops-admin-nav-groups" aria-label="运营后台模块">
        <section v-for="group in navGroups" :key="group.key" class="ops-admin-nav-group">
          <button
            class="ops-admin-group-button"
            :class="{ active: activeSection.groupKey === group.key }"
            type="button"
            @click="selectSection(group.defaultKey)"
          >
            <span>{{ group.title }}</span>
            <small>{{ group.subtitle }}</small>
          </button>
          <div class="ops-admin-subnav">
            <button
              v-for="section in group.pages"
              :key="section.key"
              class="ops-admin-subnav-button"
              :class="{ active: activeSectionKey === section.key }"
              type="button"
              @click="selectSection(section.key)"
            >
              <span>{{ section.module }}</span>
              <small>{{ section.title }}</small>
            </button>
          </div>
        </section>
      </nav>
      <div class="ops-admin-sidebar-actions">
        <button class="secondary small" type="button" @click="$emit('logout')">退出</button>
      </div>
    </aside>

    <main class="ops-admin-main">
      <header class="ops-admin-toolbar">
        <div>
          <p class="ops-admin-kicker">{{ activeSection.group }}</p>
          <h1>{{ activeSection.title }}</h1>
          <span>{{ activeSection.description }}</span>
        </div>
        <div class="ops-admin-toolbar-actions">
          <span v-if="!tagManagementOnly" :class="['runtime-mode-pill', runtimeModePillClass]" :title="runtimeModeDescription">{{ runtimeModeLabel }}</span>
          <button class="secondary" type="button" :disabled="loading" @click="refreshActiveSection">刷新</button>
          <button class="primary" type="button" @click="startPrimaryAction">{{ activeSection.primaryAction }}</button>
        </div>
      </header>

      <p v-if="notice" class="admin-message" :class="{ error: noticeKind === 'error' }">{{ notice }}</p>

      <section class="ops-admin-dashboard">
        <article v-for="metric in activeMetrics" :key="metric.label" class="ops-metric-card">
          <span>{{ metric.label }}</span>
          <strong>{{ metric.value }}</strong>
          <small>{{ metric.help }}</small>
        </article>
      </section>

      <section v-if="activeSection.groupKey === 'config-center'" class="ops-admin-layout">
        <article v-if="activeSection.key === 'skill-scenes'" class="ops-panel wide">
          <div class="ops-panel-head">
            <div>
              <h2>Skill 场景绑定</h2>
              <p>按业务场景和线索类型维护 AI 路由，停用高风险项前会二次确认。</p>
            </div>
            <button class="primary small" type="button" @click="openForm('skill')">新增绑定</button>
          </div>
          <div class="ops-filter-bar three">
            <select v-model="skillSceneFilter" @change="loadSkillAi">
              <option value="">全部场景</option>
              <option v-for="scene in SKILL_SCENE_OPTIONS" :key="scene.value" :value="scene.value">{{ scene.label }}</option>
            </select>
            <select v-model="skillLeadTypeFilter" @change="loadSkillAi">
              <option value="">全部线索类型</option>
              <option v-for="option in LEAD_TYPE_OPTIONS" :key="option.value" :value="option.value">{{ option.label }}</option>
            </select>
            <select v-model.number="skillAnalyticsDays" @change="loadSkillAnalytics">
              <option :value="7">近 7 天</option>
              <option :value="14">近 14 天</option>
              <option :value="30">近 30 天</option>
            </select>
          </div>
          <div class="ops-table">
            <div class="ops-table-row head">
              <span>场景</span>
              <span>线索类型</span>
              <span>技能名称</span>
              <span>优先级</span>
              <span>状态</span>
              <span>操作</span>
            </div>
            <div v-for="item in skillBindings" :key="item.id" class="ops-table-row">
              <span>{{ sceneLabel(item.scene) }}</span>
              <span>{{ leadTypeLabel(item.leadType) }}</span>
              <span>{{ item.skillName || item.skillId }}</span>
              <span>{{ item.priority }}</span>
              <span><b :class="item.enabled ? 'ok-text' : 'warn-text'">{{ item.enabled ? '当前可用' : '已停用' }}</b></span>
              <span class="ops-row-actions">
                <button class="secondary small" type="button" @click="openForm('skill', item)">编辑</button>
                <button class="secondary small" type="button" @click="runSkillTest(item)">测试</button>
                <button class="secondary small" type="button" @click="confirmToggleSkill(item)">{{ item.enabled ? '停用' : '启用' }}</button>
                <button class="secondary small danger" type="button" @click="confirmDeleteSkill(item)">删除</button>
              </span>
            </div>
            <p v-if="!skillBindings.length" class="ops-empty">尚未配置任何 Skill 绑定。</p>
          </div>
        </article>

        <article v-if="activeSection.key === 'skill-scenes'" class="ops-panel wide">
          <div class="ops-panel-head">
            <div>
              <h2>在线测试与调用监控</h2>
              <p>输入一条真实业务话术，点击任意绑定的“测试”即可验证返回质量。</p>
            </div>
            <button class="secondary small" type="button" @click="loadSkillAnalytics">刷新监控</button>
          </div>
          <div class="ops-form-grid">
            <label>
              测试消息
              <textarea v-model="skillTestMessage" rows="4" placeholder="输入客户最近一句话，验证对应 Skill 的回复效果"></textarea>
            </label>
            <div class="ops-detail-box">
              <strong>调用监控</strong>
              <p>{{ summarizeObject(skillAnalytics?.summary ?? skillAnalytics) }}</p>
              <small>{{ skillAnalytics ? `统计窗口：近 ${skillAnalyticsDays} 天` : '尚未加载调用统计' }}</small>
            </div>
          </div>
          <div v-if="Object.keys(skillTestResults).length" class="ops-card-grid">
            <article v-for="(result, key) in skillTestResults" :key="key" class="ops-content-card">
              <strong>{{ result.skillName || key }}</strong>
              <span>{{ result.responseTimeMs ? `${result.responseTimeMs}ms` : '已完成' }}</span>
              <p>{{ summarizeSkillTest(result) }}</p>
            </article>
          </div>
          <p v-else class="ops-empty">还没有测试结果。先在上方列表选择一条绑定测试。</p>
        </article>

        <article v-if="activeSection.key === 'configuration-center'" class="ops-panel">
          <div class="ops-panel-head">
            <div>
              <h2>Skill 环境</h2>
              <p>切换后影响全公司 AI 回复生成。</p>
            </div>
            <button class="secondary small" type="button" @click="openForm('skillEnv')">新增环境</button>
          </div>
          <div v-for="env in skillEnvironments" :key="env.id" class="ops-env-card">
            <div>
              <strong>{{ env.envName }}</strong>
              <span>{{ env.baseUrl }}</span>
              <small>Key 后四位：{{ env.apiKeyLast4 || '未返回' }}</small>
            </div>
            <div class="ops-row-actions">
              <button class="secondary small" type="button" :disabled="isActiveEnvironment(env)" @click="confirmActivateEnvironment('skill', env)">
                {{ isActiveEnvironment(env) ? '当前使用' : '启用' }}
              </button>
              <button class="secondary small" type="button" @click="openForm('skillEnv', env)">编辑</button>
              <button class="secondary small danger" type="button" :disabled="!canDeleteEnvironment('skill', env)" @click="confirmDeleteEnvironment('skill', env)">删除</button>
            </div>
          </div>
          <p v-if="!skillEnvironments.length" class="ops-empty">暂无 Skill 环境，请先新增。</p>
        </article>

        <article v-if="activeSection.key === 'configuration-center'" class="ops-panel">
          <div class="ops-panel-head">
            <div>
              <h2>识图环境</h2>
              <p>保存后可测试连接，测试不计入生产统计。</p>
            </div>
            <button class="secondary small" type="button" @click="openForm('imageEnv')">新增环境</button>
          </div>
          <div v-for="env in imageEnvironments" :key="env.id" class="ops-env-card">
            <div>
              <strong>{{ env.envName }}</strong>
              <span>{{ env.baseUrl }}</span>
              <small>{{ imageTestLabel(env) }}</small>
            </div>
            <div class="ops-row-actions">
              <button class="secondary small" type="button" :disabled="isActiveEnvironment(env)" @click="confirmActivateEnvironment('image', env)">
                {{ isActiveEnvironment(env) ? '当前使用' : '启用' }}
              </button>
              <button class="secondary small" type="button" @click="openForm('imageEnv', env)">编辑</button>
              <button class="secondary small" type="button" @click="testImageEnvironment(env)">测试连接</button>
              <button class="secondary small danger" type="button" :disabled="!canDeleteEnvironment('image', env)" @click="confirmDeleteEnvironment('image', env)">删除</button>
            </div>
          </div>
          <p v-if="!imageEnvironments.length" class="ops-empty">暂无识图环境，请先新增。</p>
        </article>

        <article v-if="activeSection.key === 'configuration-center'" class="ops-panel wide">
          <div class="ops-panel-head">
            <div>
              <h2>配置顺序</h2>
              <p>先配置 Skill、识图、LLM 环境并测试连接，再按场景开启回复生成、档案提取、跟进建议等开关；真实 Key 未配置前建议保持生产开关关闭。</p>
            </div>
          </div>
          <div class="ops-chip-list">
            <span class="ops-chip">1. 环境与 API Key</span>
            <span class="ops-chip">2. 测试连接</span>
            <span class="ops-chip">3. 场景路由</span>
            <span class="ops-chip">4. 开启业务能力</span>
          </div>
        </article>

        <article v-if="activeSection.key === 'configuration-center'" class="ops-panel">
          <div class="ops-panel-head">
            <div>
              <h2>LLM 思考环境</h2>
              <p>用于后续接入独立推理、总结和回复优化，可配置多个供应商轮流测试。</p>
            </div>
            <button class="secondary small" type="button" @click="openForm('llmEnv')">新增环境</button>
          </div>
          <div v-for="env in llmEnvironments" :key="env.id" class="ops-env-card">
            <div>
              <strong>{{ env.envName }}</strong>
              <span>{{ env.baseUrl }}</span>
              <small>{{ llmEnvironmentLabel(env) }}</small>
            </div>
            <div class="ops-row-actions">
              <button class="secondary small" type="button" :disabled="isActiveEnvironment(env)" @click="confirmActivateEnvironment('llm', env)">
                {{ isActiveEnvironment(env) ? '当前使用' : '启用' }}
              </button>
              <button class="secondary small" type="button" @click="openForm('llmEnv', env)">编辑</button>
              <button class="secondary small" type="button" @click="testLlmEnvironment(env)">测试连接</button>
              <button class="secondary small danger" type="button" :disabled="!canDeleteEnvironment('llm', env)" @click="confirmDeleteEnvironment('llm', env)">删除</button>
            </div>
          </div>
          <p v-if="!llmEnvironments.length" class="ops-empty">暂无 LLM 环境，请先新增。</p>
        </article>

        <article v-if="activeSection.key === 'configuration-center'" class="ops-panel wide">
          <div class="ops-panel-head">
            <div>
              <h2>LLM 回复生成</h2>
              <p>控制回复建议是否优先走 LLM，并保留 Skill 回落，未配置真实模型前建议保持关闭。</p>
            </div>
            <button class="primary small" type="button" :disabled="loading" @click="saveLlmReplySettings">保存回复生成</button>
          </div>
          <div class="ops-form-grid">
            <label>
              启用 LLM 回复生成
              <input v-model="llmReplyDraft.enabled" type="checkbox" />
            </label>
            <label>
              失败时回落 Skill
              <input v-model="llmReplyDraft.fallbackToSkill" type="checkbox" />
            </label>
            <label>
              温度覆盖
              <input v-model="llmReplyDraft.temperature" type="number" min="0" max="2" step="0.1" placeholder="留空使用环境默认值" />
            </label>
            <label>
              最大 Tokens
              <input v-model.number="llmReplyDraft.maxTokens" type="number" min="1" max="32000" />
            </label>
            <label class="ops-form-span-2">
              系统 Prompt
              <textarea v-model="llmReplyDraft.systemPrompt" rows="8" placeholder="填写回复建议 LLM 的系统提示词"></textarea>
            </label>
          </div>
          <div class="ops-detail-box">
            <strong>当前策略</strong>
            <p>{{ llmReplyDraft.enabled ? 'LLM 优先生成回复建议' : '继续使用 Skill 生成回复建议' }}；{{ llmReplyDraft.fallbackToSkill ? 'LLM 异常时自动回落 Skill' : 'LLM 异常时直接走降级提示' }}。</p>
          </div>
        </article>

        <article v-if="activeSection.key === 'configuration-center'" class="ops-panel wide">
          <div class="ops-panel-head">
            <div>
              <h2>LLM 档案提取</h2>
              <p>控制发送确认后的资料更新建议是否优先走 LLM，默认关闭并保留 Skill 回落。</p>
            </div>
            <button class="primary small" type="button" :disabled="loading" @click="saveLlmProfileSettings">保存档案提取</button>
          </div>
          <div class="ops-form-grid">
            <label>
              启用 LLM 档案提取
              <input v-model="llmProfileDraft.enabled" type="checkbox" />
            </label>
            <label>
              失败时回落 Skill
              <input v-model="llmProfileDraft.fallbackToSkill" type="checkbox" />
            </label>
            <label>
              温度覆盖
              <input v-model="llmProfileDraft.temperature" type="number" min="0" max="2" step="0.1" placeholder="留空使用环境默认值" />
            </label>
            <label>
              最大 Tokens
              <input v-model.number="llmProfileDraft.maxTokens" type="number" min="1" max="32000" />
            </label>
            <label class="ops-form-span-2">
              系统 Prompt
              <textarea v-model="llmProfileDraft.systemPrompt" rows="7" placeholder="填写档案提取 LLM 的系统提示词"></textarea>
            </label>
          </div>
          <div class="ops-detail-box">
            <strong>当前策略</strong>
            <p>{{ llmProfileDraft.enabled ? '发送确认后优先用 LLM 提取资料更新' : '继续使用 Skill 提取资料更新' }}；{{ llmProfileDraft.fallbackToSkill ? 'LLM 异常时自动回落 Skill' : 'LLM 异常时不写入建议' }}。</p>
          </div>
        </article>

        <article v-if="activeSection.key === 'configuration-center'" class="ops-panel wide">
          <div class="ops-panel-head">
            <div>
              <h2>LLM 跟进建议</h2>
              <p>当回复流程没有返回下次跟进建议时，用 LLM 补充跟进时间和方向。</p>
            </div>
            <button class="primary small" type="button" :disabled="loading" @click="saveLlmFollowupSettings">保存跟进建议</button>
          </div>
          <div class="ops-form-grid">
            <label>
              启用 LLM 跟进建议
              <input v-model="llmFollowupDraft.enabled" type="checkbox" />
            </label>
            <label>
              温度覆盖
              <input v-model="llmFollowupDraft.temperature" type="number" min="0" max="2" step="0.1" placeholder="留空使用环境默认值" />
            </label>
            <label>
              最大 Tokens
              <input v-model.number="llmFollowupDraft.maxTokens" type="number" min="1" max="32000" />
            </label>
            <label class="ops-form-span-2">
              系统 Prompt
              <textarea v-model="llmFollowupDraft.systemPrompt" rows="6" placeholder="填写跟进建议 LLM 的系统提示词"></textarea>
            </label>
          </div>
          <div class="ops-detail-box">
            <strong>当前策略</strong>
            <p>{{ llmFollowupDraft.enabled ? '缺少跟进建议时由 LLM 补充' : '继续使用当前回复方向作为跟进方向' }}。</p>
          </div>
        </article>

        <article v-if="activeSection.key === 'configuration-center'" class="ops-panel wide">
          <div class="ops-panel-head">
            <div>
              <h2>LLM 异常识别</h2>
              <p>发送确认后异步识别客户不满或流失风险，命中后推送到侧边栏提醒中心。</p>
            </div>
            <button class="primary small" type="button" :disabled="loading" @click="saveLlmAbnormalSettings">保存异常识别</button>
          </div>
          <div class="ops-form-grid">
            <label>
              启用 LLM 异常识别
              <input v-model="llmAbnormalDraft.enabled" type="checkbox" />
            </label>
            <label>
              温度覆盖
              <input v-model="llmAbnormalDraft.temperature" type="number" min="0" max="2" step="0.1" placeholder="留空使用环境默认值" />
            </label>
            <label>
              最大 Tokens
              <input v-model.number="llmAbnormalDraft.maxTokens" type="number" min="1" max="32000" />
            </label>
            <label class="ops-form-span-2">
              系统 Prompt
              <textarea v-model="llmAbnormalDraft.systemPrompt" rows="6" placeholder="填写异常识别 LLM 的系统提示词"></textarea>
            </label>
          </div>
          <div class="ops-detail-box">
            <strong>当前策略</strong>
            <p>{{ llmAbnormalDraft.enabled ? '发送确认后异步识别客户不满和流失风险' : '不启用 LLM 异常识别' }}。</p>
          </div>
        </article>

        <article v-if="activeSection.key === 'configuration-center'" class="ops-panel wide">
          <div class="ops-panel-head">
            <div>
              <h2>LLM 总结补位</h2>
              <p>当发送确认没有会话摘要时，用 LLM 生成短跟进备注，供客户档案和表格写入使用。</p>
            </div>
            <button class="primary small" type="button" :disabled="loading" @click="saveLlmSummarySettings">保存总结补位</button>
          </div>
          <div class="ops-form-grid">
            <label>
              启用 LLM 总结补位
              <input v-model="llmSummaryDraft.enabled" type="checkbox" />
            </label>
            <label>
              温度覆盖
              <input v-model="llmSummaryDraft.temperature" type="number" min="0" max="2" step="0.1" placeholder="留空使用环境默认值" />
            </label>
            <label>
              最大 Tokens
              <input v-model.number="llmSummaryDraft.maxTokens" type="number" min="1" max="32000" />
            </label>
            <label class="ops-form-span-2">
              系统 Prompt
              <textarea v-model="llmSummaryDraft.systemPrompt" rows="6" placeholder="填写总结补位 LLM 的系统提示词"></textarea>
            </label>
          </div>
          <div class="ops-detail-box">
            <strong>当前策略</strong>
            <p>{{ llmSummaryDraft.enabled ? '缺少摘要时由 LLM 生成跟进备注' : '缺少摘要时继续使用已发送文本作为备注' }}。</p>
          </div>
        </article>

        <article v-if="activeSection.key === 'configuration-center'" class="ops-panel wide">
          <div class="ops-panel-head">
            <div>
              <h2>LLM 场景路由</h2>
              <p>按业务场景和线索类型选择 LLM 环境，可用多个模型做并行测试和灰度切换。</p>
            </div>
            <button class="primary small" type="button" :disabled="!llmEnvironments.length" @click="openForm('llmRoute')">新增路由</button>
          </div>
          <div class="ops-table">
            <div class="ops-table-row head llm-route">
              <span>场景</span>
              <span>线索类型</span>
              <span>环境</span>
              <span>模型</span>
              <span>优先级</span>
              <span>状态</span>
              <span>操作</span>
            </div>
            <div v-for="route in llmRoutes" :key="route.id" class="ops-table-row llm-route">
              <span>{{ llmSceneLabel(route.scene) }}</span>
              <span>{{ route.leadType ? leadTypeLabel(route.leadType) : '通用' }}</span>
              <span>{{ route.environmentName || llmEnvironmentName(route.environmentId) }}</span>
              <span>{{ route.model || '-' }}</span>
              <span>{{ route.priority ?? 0 }}</span>
              <span><b :class="route.enabled !== false ? 'ok-text' : 'warn-text'">{{ route.enabled !== false ? '当前可用' : '已停用' }}</b></span>
              <span class="ops-row-actions">
                <button class="secondary small" type="button" @click="openForm('llmRoute', route)">编辑</button>
                <button class="secondary small" type="button" @click="confirmToggleLlmRoute(route)">{{ route.enabled !== false ? '停用' : '启用' }}</button>
                <button class="secondary small danger" type="button" @click="confirmDeleteLlmRoute(route)">删除</button>
              </span>
            </div>
            <p v-if="!llmRoutes.length" class="ops-empty">尚未配置 LLM 场景路由；未命中路由时会使用当前启用的 LLM 环境。</p>
          </div>
        </article>

        <article v-if="activeSection.key === 'configuration-center'" class="ops-panel wide">
          <div class="ops-panel-head">
            <div>
              <h2>LLM 调用统计</h2>
              <p>查看 LLM 按场景、线索类型和模型的调用量、成功率与平均响应时间。</p>
            </div>
            <button class="secondary small" type="button" @click="loadLlmAnalytics">刷新统计</button>
          </div>
          <div class="ops-filter-bar">
            <select v-model.number="llmAnalyticsDays" @change="loadLlmAnalytics">
              <option :value="7">近 7 天</option>
              <option :value="14">近 14 天</option>
              <option :value="30">近 30 天</option>
            </select>
            <span class="ops-inline-info">统计窗口：近 {{ llmAnalyticsDays }} 天</span>
          </div>
          <div class="ops-health-grid">
            <div class="ops-health-card">
              <span>调用次数</span>
              <strong>{{ llmCallSummary.totalCalls ?? 0 }}</strong>
              <small>含成功与失败调用</small>
            </div>
            <div class="ops-health-card">
              <span>成功率</span>
              <strong>{{ percentLabel(llmCallSummary.successRate) }}</strong>
              <small>按成功调用占比计算</small>
            </div>
            <div class="ops-health-card">
              <span>平均响应</span>
              <strong>{{ llmCallSummary.avgResponseTime ?? 0 }}ms</strong>
              <small>近 {{ llmAnalyticsDays }} 天平均值</small>
            </div>
          </div>
          <div v-if="llmCallDetails.length" class="ops-mini-table">
            <div class="ops-mini-row head">
              <span>场景</span>
              <span>线索类型</span>
              <span>模型</span>
              <span>调用</span>
              <span>成功</span>
              <span>失败</span>
              <span>响应ms</span>
            </div>
            <div v-for="detail in llmCallDetails" :key="`${detail.scene}-${detail.leadType}-${detail.environmentId}-${detail.model}`" class="ops-mini-row">
              <span>{{ llmSceneLabel(detail.scene) }}</span>
              <span>{{ detail.leadType ? leadTypeLabel(detail.leadType) : '通用' }}</span>
              <span>{{ detail.model || llmEnvironmentName(detail.environmentId) }}</span>
              <span>{{ detail.totalCalls ?? 0 }}</span>
              <span>{{ detail.successCount ?? 0 }}</span>
              <span>{{ detail.failCount ?? 0 }}</span>
              <span>{{ detail.avgResponseTime ?? 0 }}</span>
            </div>
          </div>
          <p v-else class="ops-empty">暂无 LLM 调用记录。开启 LLM 回复生成或通过测试接口调用后会出现数据。</p>
        </article>

        <article v-if="activeSection.key === 'configuration-center'" class="ops-panel wide">
          <div class="ops-panel-head">
            <div>
              <h2>Skill 运行参数</h2>
              <p>控制 AI 调用超时、熔断和失败率告警，保存后立即影响生产路由。</p>
            </div>
            <button class="primary small" type="button" :disabled="loading" @click="saveSkillRuntimeSettings">保存 Skill 参数</button>
          </div>
          <div class="ops-form-grid">
            <label>
              Skill 调用超时（毫秒）
              <input v-model.number="skillRuntimeDraft.timeoutMs" type="number" min="5000" max="15000" />
            </label>
            <label>
              熔断窗口（秒）
              <input v-model.number="skillRuntimeDraft.circuitBreakerWindowS" type="number" min="10" max="300" />
            </label>
            <label>
              熔断失败率
              <input v-model.number="skillRuntimeDraft.circuitBreakerFailureRate" type="number" min="0.05" max="1" step="0.01" />
            </label>
            <label>
              熔断最小调用数
              <input v-model.number="skillRuntimeDraft.circuitBreakerMinCalls" type="number" min="1" max="100" />
            </label>
            <label>
              熔断持续时间（秒）
              <input v-model.number="skillRuntimeDraft.circuitBreakerOpenS" type="number" min="10" max="600" />
            </label>
            <label>
              健康告警失败率
              <input v-model.number="skillRuntimeDraft.alertFailureRate" type="number" min="0.01" max="1" step="0.01" />
            </label>
            <label>
              告警持续时间（分钟）
              <input v-model.number="skillRuntimeDraft.alertFailureDurationMinutes" type="number" min="1" max="120" />
            </label>
            <label>
              档案提取超时（毫秒）
              <input v-model.number="skillRuntimeDraft.profileExtractTimeoutMs" type="number" min="5000" max="12000" />
            </label>
          </div>
        </article>

        <article v-if="activeSection.key === 'configuration-center'" class="ops-panel wide">
          <div class="ops-panel-head">
            <div>
              <h2>识图运行参数</h2>
              <p>控制截图识别模型、图片限制和连续失败告警。</p>
            </div>
            <button class="primary small" type="button" :disabled="loading" @click="saveImageRuntimeSettings">保存识图参数</button>
          </div>
          <div class="ops-form-grid">
            <label>
              识图模型
              <input v-model="imageRuntimeDraft.model" type="text" placeholder="qwen3-vl-plus" />
            </label>
            <label>
              识图超时（毫秒）
              <input v-model.number="imageRuntimeDraft.timeoutMs" type="number" min="1000" max="60000" />
            </label>
            <label>
              最大图片体积（字节）
              <input v-model.number="imageRuntimeDraft.maxSizeBytes" type="number" min="1048576" max="20971520" />
            </label>
            <label>
              最大长边像素
              <input v-model.number="imageRuntimeDraft.maxDimensionPx" type="number" min="640" max="4096" />
            </label>
            <label>
              JPEG 压缩质量
              <input v-model.number="imageRuntimeDraft.compressQuality" type="number" min="60" max="95" />
            </label>
            <label>
              连续失败告警次数
              <input v-model.number="imageRuntimeDraft.consecutiveFailuresAlert" type="number" min="1" max="10" />
            </label>
            <label>
              截图确认提示停留（秒）
              <input v-model.number="imageRuntimeDraft.clipboardScreenshotConfirmPromptS" type="number" min="0" max="60" />
              <small>0 表示不自动忽略，持续等待手动处理；自动忽略请输入 3-60 秒，建议 10 秒。</small>
            </label>
          </div>
        </article>

        <article v-if="activeSection.key === 'configuration-center'" class="ops-panel wide">
          <div class="ops-panel-head">
            <div>
              <h2>企微表格网关</h2>
              <p>配置真实企微/智能表格读写网关，影响客户同步和保存到表格。</p>
            </div>
            <button class="primary small" type="button" :disabled="loading" @click="saveTableRuntimeSettings">保存表格参数</button>
          </div>
          <div class="ops-form-grid">
            <label>
              网关 Base URL
              <input v-model="tableRuntimeDraft.apiBaseUrl" type="text" placeholder="https://table-gateway.example.com" />
            </label>
            <label>
              网关 API Key
              <input v-model="tableRuntimeDraft.apiKey" type="password" autocomplete="new-password" aria-label="企微表格网关 API Key" placeholder="留空表示沿用当前 Key" />
              <small>当前 Key：{{ configSecretStatus('table.api_key') }}</small>
            </label>
            <label>
              写入超时（毫秒）
              <input v-model.number="tableRuntimeDraft.writeTimeoutMs" type="number" min="5000" max="20000" />
            </label>
            <label>
              最大重试次数
              <input v-model.number="tableRuntimeDraft.retryMaxCount" type="number" min="3" max="10" />
            </label>
            <label>
              重试间隔（秒）
              <input v-model.number="tableRuntimeDraft.retryIntervalS" type="number" min="30" max="300" />
            </label>
            <label>
              失败告警小时数
              <input v-model.number="tableRuntimeDraft.alertFailureHours" type="number" min="1" max="24" />
            </label>
            <label>
              告警通知对象
              <select v-model="tableRuntimeDraft.alertNotifyTarget">
                <option value="ADMIN">管理员</option>
                <option value="LEADER">组长</option>
                <option value="BOTH">管理员和组长</option>
              </select>
            </label>
            <label>
              队列提醒阈值
              <input v-model.number="tableRuntimeDraft.queueWarnThreshold" type="number" min="50" max="500" />
            </label>
            <label>
              队列告警阈值
              <input v-model.number="tableRuntimeDraft.queueAlertThreshold" type="number" min="500" max="5000" />
            </label>
          </div>
        </article>

        <article v-if="activeSection.key === 'configuration-center'" class="ops-panel wide">
          <div class="ops-panel-head">
            <div>
              <h2>数据同步策略</h2>
              <p>控制企微表格同步频率、缓存 TTL、手动同步和导入限制。</p>
            </div>
            <button class="primary small" type="button" :disabled="loading" @click="saveDatasourceRuntimeSettings">保存同步策略</button>
          </div>
          <div class="ops-form-grid">
            <label>
              自动同步 Cron
              <input v-model="datasourceRuntimeDraft.syncCron" type="text" placeholder="0 */30 * * * *" />
            </label>
            <label>
              客户缓存 TTL（秒）
              <input v-model.number="datasourceRuntimeDraft.ttlSeconds" type="number" min="60" max="86400" />
            </label>
            <label>
              同步 API 超时（毫秒）
              <input v-model.number="datasourceRuntimeDraft.syncTimeoutMs" type="number" min="5000" max="60000" />
            </label>
            <label>
              映射版本保留数
              <input v-model.number="datasourceRuntimeDraft.mappingVersionMax" type="number" min="20" max="200" />
            </label>
            <label>
              CSV 单次导入行数
              <input v-model.number="datasourceRuntimeDraft.importMaxRows" type="number" min="1000" max="10000" />
            </label>
            <label>
              手动同步超时（秒）
              <input v-model.number="datasourceRuntimeDraft.manualSyncTimeoutS" type="number" min="30" max="120" />
            </label>
            <label>
              同步状态刷新（秒）
              <input v-model.number="datasourceRuntimeDraft.syncStatusRefreshS" type="number" min="15" max="120" />
            </label>
          </div>
        </article>

        <article v-if="activeSection.key === 'configuration-center'" class="ops-panel wide">
          <div class="ops-panel-head">
            <div>
              <h2>Prompt 与规则</h2>
              <p>红线逐条编辑，前缀规则一行一个，保存后立即生效。</p>
            </div>
            <button class="primary small" type="button" :disabled="loading" @click="savePromptSettings">保存配置</button>
          </div>
          <div class="ops-filter-bar">
            <select v-model="selectedPromptType" @change="loadPromptVersions">
              <option value="format">输出格式模板</option>
              <option value="red-lines">企业红线</option>
              <option value="image">识图提示词</option>
            </select>
            <button class="secondary small" type="button" @click="loadPromptVersions">查看版本历史</button>
          </div>
          <div class="ops-form-grid">
            <label>
              输出格式模板
              <textarea v-model="promptDraft.format" rows="7" placeholder="填写 Skill 输出格式要求，可使用 {{字段名}} 占位符"></textarea>
            </label>
            <label>
              企业红线
              <textarea v-model="promptDraft.redLinesText" rows="7" placeholder="每行一条红线"></textarea>
            </label>
            <label>
              昵称前缀去除规则
              <textarea v-model="promptDraft.tagRemovalRulesText" rows="5" placeholder="每行一个前缀，如 L1-"></textarea>
            </label>
            <label>
              降级回复
              <textarea v-model="promptDraft.fallbackReply" rows="5" placeholder="AI 服务不可用时展示给同事的回复"></textarea>
            </label>
            <label>
              识图提示词
              <textarea v-model="promptDraft.imageRecognitionPrompt" rows="5" placeholder="告诉识图模型需要提取昵称、手机号、消息和时间"></textarea>
            </label>
            <label>
              换一组次数上限
              <input v-model.number="promptDraft.regenerateMaxCount" type="number" min="0" max="10" placeholder="0 表示不限制" />
            </label>
          </div>
          <div v-if="promptVersions.length" class="ops-card-grid">
            <article v-for="version in promptVersions" :key="version.version" class="ops-content-card">
              <strong>版本 {{ version.version }}{{ version.current ? ' · 当前' : '' }}</strong>
              <span>{{ version.operator || '-' }} · {{ formatDate(version.createdAt) }}</span>
              <p>{{ textSnippet(version.content) }}</p>
              <button class="secondary small" type="button" :disabled="version.current" @click="confirmRestorePrompt(version)">恢复此版本</button>
            </article>
          </div>
        </article>
      </section>

      <section v-else-if="activeSection.groupKey === 'data-content'" class="ops-admin-layout">
        <article v-if="activeSection.key === 'data-integration'" class="ops-panel wide">
          <div class="ops-panel-head">
            <div>
              <h2>客户查询</h2>
              <p>查询已同步到数据库的客户，可按姓名/昵称、手机号、门店、项目、来源或管家搜索。</p>
            </div>
          </div>
          <div class="ops-filter-bar customer-search-filter">
            <input v-model="customerSearchKeyword" placeholder="例如：1111、王女士、万江店" @keyup.enter="resetCustomerSearchPageAndLoad" />
            <button class="primary small" type="button" :disabled="loading" @click="resetCustomerSearchPageAndLoad">查询客户</button>
          </div>
          <div class="ops-table">
            <div class="ops-table-row head customer-search">
              <span>客户</span>
              <span>手机号</span>
              <span>来源</span>
              <span>线索类型</span>
              <span>门店 / 项目</span>
              <span>阶段 / 意向</span>
              <span>分配管家</span>
              <span>操作</span>
            </div>
            <div v-for="customer in customerSearchItems" :key="customer.id || customer.phone" class="ops-table-row customer-search">
              <span>{{ customer.nickname || '未填写昵称' }}</span>
              <span>{{ customer.phone || '-' }}</span>
              <span>{{ customer.sourceChannel || customer.sourceTable || '-' }}</span>
              <span>{{ leadTypeLabel(customer.leadType) }}</span>
              <span>{{ customer.intendedStore || customer.appointmentStore || '-' }} / {{ customer.intendedProject || customer.appointmentItem || '-' }}</span>
              <span>{{ translateValue(customer.customerStage) }} / {{ translateValue(customer.intentLevel) }}</span>
              <span>{{ customer.assignedKeeper || '-' }}</span>
              <span class="ops-row-actions">
                <button class="secondary small" type="button" @click="toggleAdminCustomerDetail(customer)">{{ selectedAdminCustomer?.phone === customer.phone ? '收起' : '查看档案' }}</button>
              </span>
            </div>
            <p v-if="!customerSearchItems.length" class="ops-empty">没有找到符合条件的客户。</p>
          </div>
          <div v-if="selectedAdminCustomer" class="ops-detail-box customer-search-detail">
            <strong>{{ selectedAdminCustomer.nickname || '未填写昵称' }} · {{ selectedAdminCustomer.phone }}</strong>
            <p>客户阶段：{{ translateValue(selectedAdminCustomer.customerStage) }} · 意向等级：{{ translateValue(selectedAdminCustomer.intentLevel) }} · 最近跟进：{{ formatDate(selectedAdminCustomer.lastFollowupAt) }} · 下次跟进：{{ formatDate(selectedAdminCustomer.nextFollowupAt) }}</p>
            <p>预约：{{ formatDate(selectedAdminCustomer.appointmentDate) }} · {{ selectedAdminCustomer.appointmentStore || '-' }} · {{ selectedAdminCustomer.appointmentItem || '-' }} · 到店：{{ selectedAdminCustomer.arrived || '-' }}</p>
            <p>数据来源：{{ selectedAdminCustomer.sourceTable || selectedAdminCustomer.sourceChannel || '-' }} · 最后更新：{{ formatDate(selectedAdminCustomer.updatedAt) }}</p>
          </div>
          <div class="ops-pagination">
            <strong>当前筛选：{{ customerSearchPageInfo.total }} 个客户</strong>
            <p>第 {{ customerSearchPageInfo.page }} / {{ customerSearchTotalPages }} 页，每页 {{ customerSearchPageInfo.size }} 条</p>
            <div class="ops-row-actions">
              <button class="secondary small" type="button" :disabled="customerSearchPageInfo.page <= 1" @click="changeCustomerSearchPage(-1)">上一页</button>
              <button class="secondary small" type="button" :disabled="customerSearchPageInfo.page >= customerSearchTotalPages" @click="changeCustomerSearchPage(1)">下一页</button>
            </div>
          </div>
        </article>

        <article v-if="activeSection.key === 'data-integration'" class="ops-panel wide">
          <div class="ops-panel-head">
            <div>
              <h2>客户数据源</h2>
              <p>配置企微表格、同步状态、字段映射和手动导入。</p>
            </div>
            <button class="primary small" type="button" @click="openForm('datasource')">添加数据源</button>
          </div>
          <div class="ops-table">
            <div class="ops-table-row head">
              <span>名称</span>
              <span>来源表</span>
              <span>同步状态</span>
              <span>映射数</span>
              <span>状态</span>
              <span>操作</span>
            </div>
            <div v-for="item in datasources" :key="item.id" class="ops-table-row">
              <span>{{ item.name }}</span>
              <span>{{ item.sourceTable || item.sheetId }}</span>
              <span>{{ syncStatusFor(item.id) }}</span>
              <span>{{ mappingCountFor(item.id) }}</span>
              <span><b :class="item.enabled === false ? 'warn-text' : 'ok-text'">{{ item.enabled === false ? '已停用' : '启用中' }}</b></span>
              <span class="ops-row-actions">
                <button class="secondary small" type="button" @click="selectDatasource(item)">字段映射</button>
                <button class="secondary small" type="button" @click="openForm('datasource', item)">编辑</button>
                <button class="secondary small" type="button" @click="replaceDatasource(item)">换表</button>
                <button class="secondary small" type="button" @click="triggerDatasourceSync(item)">立即同步</button>
                <button class="secondary small" type="button" @click="toggleDatasource(item)">{{ item.enabled === false ? '启用' : '停用' }}</button>
                <button class="secondary small danger" type="button" @click="confirmDeleteDatasource(item)">删除</button>
              </span>
            </div>
            <p v-if="!datasources.length" class="ops-empty">暂无数据源，添加第一张企微表格开始同步。</p>
          </div>
        </article>

        <article v-if="activeSection.key === 'data-integration'" class="ops-panel wide">
          <div class="ops-panel-head">
            <div>
              <h2>字段映射</h2>
              <p>{{ selectedDatasource ? `当前数据源：${selectedDatasource.name}` : '选择数据源后编辑字段映射' }}</p>
            </div>
            <div class="ops-row-actions">
              <button class="secondary small" type="button" :disabled="!selectedDatasource" @click="loadDatasourceColumns">识别列名</button>
              <button class="secondary small" type="button" :disabled="!selectedDatasource" @click="compareMappings">对比最新版本</button>
              <button class="secondary small" type="button" :disabled="!selectedDatasource" @click="loadMappingVersions">版本历史</button>
              <button class="primary small" type="button" :disabled="!selectedDatasource" @click="saveMappings">保存映射</button>
            </div>
          </div>
          <div v-if="selectedDatasource" class="ops-mapping-grid">
            <label v-for="field in customerFields" :key="field.key">
              <span class="ops-field-title">
                {{ field.label || field.key }}
                <label class="ops-inline-toggle">
                  <input v-model="mappingEnabledDraft[field.key]" type="checkbox" />
                  启用
                </label>
              </span>
              <input v-model="mappingDraft[field.key]" :list="`columns-${field.key}`" :placeholder="field.key === 'phone' ? '必须映射手机号列' : '填写表格列名'" />
              <datalist :id="`columns-${field.key}`">
                <option v-for="column in datasourceColumns" :key="`${field.key}-${columnName(column)}`" :value="columnName(column)" />
              </datalist>
            </label>
          </div>
          <div v-if="datasourceColumnStatus" class="ops-detail-box">
            <strong>列名识别：{{ datasourceColumnStatusLabel }}</strong>
            <p>{{ datasourceColumnStatusDetail }}</p>
            <div v-if="datasourceColumns.length" class="ops-chip-list">
              <span v-for="column in datasourceColumns.slice(0, 12)" :key="`column-${columnName(column)}`" class="ops-chip" :class="{ muted: !columnMapped(column) }">
                {{ columnName(column) }}{{ columnTarget(column) ? ` -> ${customerFieldLabel(columnTarget(column))}` : '' }}
              </span>
            </div>
          </div>
          <div v-if="mappingCompare" class="ops-detail-box">
            <strong>映射差异</strong>
            <p>{{ summarizeObject(mappingCompare.summary) }}</p>
            <div class="ops-diff-grid">
              <div v-for="group in mappingDiffGroups" :key="group.key" class="ops-diff-group">
                <strong>{{ group.label }}（{{ group.items.length }}）</strong>
                <span v-for="item in group.items.slice(0, 6)" :key="`${group.key}-${mappingDiffKey(item)}`">{{ mappingDiffText(item) }}</span>
                <small v-if="group.items.length > 6">还有 {{ group.items.length - 6 }} 条未展示</small>
              </div>
            </div>
          </div>
          <div v-if="mappingVersions.length" class="ops-card-grid">
            <article v-for="version in mappingVersions" :key="version.version" class="ops-content-card">
              <strong>映射版本 {{ version.version }}</strong>
              <span>{{ version.operator || '-' }} · {{ formatDate(version.createdAt) }}</span>
              <p>{{ version.changeNote || version.note || `包含 ${version.mappingCount ?? '-'} 条映射` }}</p>
              <button class="secondary small" type="button" @click="confirmRestoreMapping(version)">恢复此版本</button>
            </article>
          </div>
          <p v-if="!selectedDatasource" class="ops-empty">从上方数据源列表选择一项。</p>
        </article>

        <article v-if="activeSection.key === 'data-integration'" class="ops-panel">
          <div class="ops-panel-head">
            <div>
              <h2>CSV 导入</h2>
              <p>先预览前几行，确认包含手机号列后再导入。</p>
            </div>
          </div>
          <input type="file" accept=".csv,text/csv" @change="previewCsv" />
          <div v-if="csvPreview.length" class="ops-preview-list">
            <strong>预览前 {{ csvPreview.length }} 行</strong>
            <pre>{{ csvPreview.join('\n') }}</pre>
            <button class="primary small" type="button" @click="importCsv">确认导入</button>
          </div>
          <div v-if="csvImportResult" class="ops-detail-box">
            <strong>导入结果</strong>
            <p>{{ csvImportSummary }}</p>
            <div v-if="csvImportErrors.length" class="ops-error-list">
              <span v-for="error in csvImportErrors.slice(0, 8)" :key="`${error.row}-${error.reason}`">第 {{ error.row }} 行：{{ error.reason }}</span>
              <small v-if="csvImportErrors.length > 8">还有 {{ csvImportErrors.length - 8 }} 条错误未展示</small>
            </div>
          </div>
          <div v-if="importLogs.length" class="ops-preview-list">
            <strong>最近导入记录</strong>
            <span v-for="log in importLogs.slice(0, 4)" :key="log.id || log.createdAt">
              {{ formatDate(log.createdAt) }} · {{ log.fileName || 'CSV 文件' }} · {{ importLogSummary(log) }}
              <small v-if="log.errorDetail">错误：{{ textSnippet(log.errorDetail) }}</small>
            </span>
          </div>
          <p v-if="!csvPreview.length && !importLogs.length" class="ops-empty">拖拽或选择 CSV 文件。</p>
        </article>

        <article v-if="activeSection.key === 'quick-search-content'" class="ops-panel wide">
          <div class="ops-panel-head">
            <div>
              <h2>速搜内容</h2>
              <p>管理话术模板、知识片段、门店定位、图片素材和小程序引导。</p>
            </div>
            <button class="primary small" type="button" @click="openForm('quickSearch')">新增内容</button>
          </div>
          <div class="ops-filter-bar three">
            <input v-model="quickSearchKeyword" placeholder="搜索标题、快线码或正文" @change="resetQuickSearchPageAndLoad" />
            <select v-model="quickSearchType" @change="resetQuickSearchPageAndLoad">
              <option value="">全部类型</option>
              <option value="TEMPLATE">话术模板</option>
              <option value="KNOWLEDGE">知识片段</option>
              <option value="LOCATION">门店定位</option>
              <option value="IMAGE">图片素材</option>
              <option value="MINI_PROGRAM">小程序引导</option>
            </select>
            <select v-model="quickSearchEnabledFilter" @change="resetQuickSearchPageAndLoad">
              <option value="">全部状态</option>
              <option value="1">启用</option>
              <option value="0">停用</option>
            </select>
          </div>
          <div class="ops-row-actions">
            <button class="secondary small" type="button" :disabled="!quickSearchSelectedIds.length" @click="batchToggleQuickSearch(false)">批量停用</button>
            <button class="secondary small" type="button" :disabled="!quickSearchSelectedIds.length" @click="batchToggleQuickSearch(true)">批量启用</button>
            <button class="secondary small danger" type="button" :disabled="!quickSearchSelectedIds.length" @click="batchDeleteQuickSearch">批量删除</button>
          </div>
          <div class="ops-card-grid">
            <article v-for="item in quickSearchItems" :key="item.id" class="ops-content-card">
              <label class="ops-checkline">
                <input v-model="quickSearchSelectedIds" type="checkbox" :value="item.id" />
                选中
              </label>
              <div>
                <strong>{{ item.title }}</strong>
                <span>{{ contentTypeLabel(item.contentType) }} · {{ item.shortcutCode }}</span>
                <p>{{ item.content || item.imageUrl || '无内容' }}</p>
              </div>
              <div v-if="item.contentType === 'IMAGE' && item.imageUrl" class="ops-image-preview">
                <img :src="item.imageUrl" alt="图片素材预览" />
              </div>
              <div class="ops-row-actions">
                <button class="secondary small" type="button" @click="openForm('quickSearch', item)">编辑</button>
                <label class="secondary small file-button">
                  上传图片
                  <input type="file" accept="image/*" @change="uploadQuickSearchImage($event, item)" />
                </label>
                <button v-if="item.imageUrl" class="secondary small" type="button" @click="clearQuickSearchImage(item)">清除图片</button>
                <button class="secondary small" type="button" @click="toggleQuickSearchItem(item)">{{ item.enabled === false ? '启用' : '停用' }}</button>
                <button class="secondary small danger" type="button" @click="confirmDeleteQuickSearchItem(item)">删除</button>
              </div>
            </article>
          </div>
          <p v-if="!quickSearchItems.length" class="ops-empty">暂无匹配内容。</p>
          <div class="ops-pagination">
            <strong>当前筛选：{{ quickSearchPageInfo.total }} 条速搜内容</strong>
            <p>第 {{ quickSearchPageInfo.page }} / {{ quickSearchTotalPages }} 页，每页 {{ quickSearchPageInfo.size }} 条</p>
            <div class="ops-row-actions">
              <button class="secondary small" type="button" :disabled="quickSearchPageInfo.page <= 1" @click="changeQuickSearchPage(-1)">上一页</button>
              <button class="secondary small" type="button" :disabled="quickSearchPageInfo.page >= quickSearchTotalPages" @click="changeQuickSearchPage(1)">下一页</button>
            </div>
          </div>
        </article>
      </section>

      <section v-else-if="activeSection.groupKey === 'org-rules-tags'" class="ops-admin-layout">
        <article v-if="activeSection.key === 'account-permissions'" class="ops-panel wide">
          <div class="ops-panel-head">
            <div>
              <h2>账号与权限</h2>
              <p>管理员可为组长或管家单独授予客户标签管理权限。</p>
            </div>
            <button class="primary small" type="button" @click="openForm('account')">新增账号</button>
          </div>
          <div class="ops-filter-bar three">
            <input v-model="accountKeyword" placeholder="搜索姓名或手机号" @change="resetAccountPageAndLoad" />
            <select v-model="accountRoleFilter" @change="resetAccountPageAndLoad">
              <option value="">全部角色</option>
              <option value="ADMIN">管理员</option>
              <option value="LEADER">组长</option>
              <option value="KEEPER">管家</option>
            </select>
            <select v-model="accountEnabledFilter" @change="resetAccountPageAndLoad">
              <option value="">全部状态</option>
              <option value="1">启用中</option>
              <option value="0">已停用</option>
            </select>
          </div>
          <div class="ops-table">
            <div class="ops-table-row head accounts">
              <span>姓名</span>
              <span>手机号</span>
              <span>角色</span>
              <span>直属组长</span>
              <span>最近登录</span>
              <span>状态</span>
              <span>操作</span>
            </div>
            <div v-for="account in accounts" :key="account.id" class="ops-table-row accounts">
              <span>{{ account.displayName || account.username }}</span>
              <span>{{ account.phone || account.username }}</span>
              <span>{{ roleLabel(account.role) }}</span>
              <span>{{ account.leaderName || leaderName(account.leaderId) }}</span>
              <span>{{ formatDate(account.lastLoginAt) }}</span>
              <span><b :class="account.isEnabled === false ? 'warn-text' : 'ok-text'">{{ account.isEnabled === false ? '已停用' : '启用中' }}</b></span>
              <span class="ops-row-actions">
                <button class="secondary small" type="button" @click="openForm('account', account)">编辑</button>
                <button class="secondary small" type="button" @click="resetAccountPassword(account)">重置密码</button>
                <button class="secondary small" type="button" @click="confirmToggleAccount(account)">{{ account.isEnabled === false ? '启用' : '停用' }}</button>
                <button class="secondary small danger" type="button" @click="confirmDeleteAccount(account)">删除</button>
              </span>
            </div>
            <p v-if="!accounts.length" class="ops-empty">暂无账号。</p>
          </div>
          <div class="ops-pagination">
            <strong>当前筛选：{{ accountPageInfo.total }} 个账号</strong>
            <p>第 {{ accountPageInfo.page }} / {{ accountTotalPages }} 页，每页 {{ accountPageInfo.size }} 条</p>
            <div class="ops-row-actions">
              <button class="secondary small" type="button" :disabled="accountPageInfo.page <= 1" @click="changeAccountPage(-1)">上一页</button>
              <button class="secondary small" type="button" :disabled="accountPageInfo.page >= accountTotalPages" @click="changeAccountPage(1)">下一页</button>
            </div>
          </div>
        </article>

        <article v-if="activeSection.key === 'followup-rules'" class="ops-panel wide">
          <div class="ops-panel-head">
            <div>
              <h2>跟进规则</h2>
              <p>条件构建器会生成业务语言预览，内置规则不能删除。</p>
            </div>
            <button class="primary small" type="button" @click="openForm('rule')">新增规则</button>
          </div>
          <div class="ops-filter-bar">
            <input v-model="ruleKeyword" placeholder="搜索规则名称" @change="resetRulePageAndLoad" />
            <select v-model="ruleActionType" @change="resetRulePageAndLoad">
              <option value="">全部动作</option>
              <option value="ALERT">提醒/告警</option>
              <option value="TAG_CHANGE">标签建议</option>
              <option value="NOTIFY_LEADER">通知组长</option>
            </select>
            <select v-model="ruleEnabledFilter" @change="resetRulePageAndLoad">
              <option value="">全部状态</option>
              <option value="1">启用</option>
              <option value="0">停用</option>
            </select>
          </div>
          <div class="ops-row-actions">
            <button class="secondary small" type="button" :disabled="!ruleSelectedIds.length" @click="batchToggleRules(true)">批量启用</button>
            <button class="secondary small" type="button" :disabled="!ruleSelectedIds.length" @click="batchToggleRules(false)">批量停用</button>
            <button class="secondary small danger" type="button" :disabled="!deletableSelectedRules.length" @click="batchDeleteRules">批量删除</button>
          </div>
          <div class="ops-card-grid">
            <article v-for="rule in rules" :key="rule.id" class="ops-rule-card">
              <label class="ops-checkline">
                <input v-model="ruleSelectedIds" type="checkbox" :value="rule.id" />
                选中
              </label>
              <div>
                <strong>{{ rule.name }}</strong>
                <span>{{ actionTypeLabel(rule.actionType) }} · 优先级 {{ rule.priority }}</span>
                <p>{{ rulePreview(rule) }}</p>
              </div>
              <div class="ops-row-actions">
                <button class="secondary small" type="button" @click="openForm('rule', rule)">编辑</button>
                <button class="secondary small" type="button" @click="confirmToggleRule(rule)">{{ rule.enabled === false ? '启用' : '停用' }}</button>
                <button class="secondary small danger" type="button" :disabled="isBuiltinRule(rule)" @click="confirmDeleteRule(rule)">删除</button>
              </div>
            </article>
          </div>
          <p v-if="!rules.length" class="ops-empty">暂无匹配规则。</p>
          <div class="ops-pagination">
            <strong>当前筛选：{{ rulePageInfo.total }} 条规则</strong>
            <p>第 {{ rulePageInfo.page }} / {{ ruleTotalPages }} 页，每页 {{ rulePageInfo.size }} 条</p>
            <div class="ops-row-actions">
              <button class="secondary small" type="button" :disabled="rulePageInfo.page <= 1" @click="changeRulePage(-1)">上一页</button>
              <button class="secondary small" type="button" :disabled="rulePageInfo.page >= ruleTotalPages" @click="changeRulePage(1)">下一页</button>
            </div>
          </div>
        </article>

        <article v-if="activeSection.key === 'customer-tags'" class="ops-panel wide">
          <div class="ops-panel-head">
            <div>
              <h2>标签与分层</h2>
              <p>分类和标签值使用独立分页管理，系统编号由后端生成并永久保留。</p>
            </div>
            <div class="ops-row-actions">
              <button class="secondary small" type="button" @click="exportCurrentTags">导出 CSV</button>
              <button class="primary small" type="button" @click="openForm(tagView === 'categories' ? 'tagCategory' : 'tagValue')">
                {{ tagView === 'categories' ? '新增分类' : '新增标签值' }}
              </button>
            </div>
          </div>

          <div class="ops-segmented" role="tablist" aria-label="标签管理视图">
            <button type="button" :class="{ active: tagView === 'categories' }" @click="switchTagView('categories')">标签分类</button>
            <button type="button" :class="{ active: tagView === 'values' }" @click="switchTagView('values')">标签值</button>
          </div>

          <template v-if="tagView === 'categories'">
            <div class="ops-filter-bar tag-filters">
              <input v-model="tagCategoryKeyword" placeholder="搜索分类名称、系统编号或用途" @change="resetTagCategoryPageAndLoad" />
              <select v-model="tagCategoryEnabledFilter" @change="resetTagCategoryPageAndLoad">
                <option value="">全部启用状态</option>
                <option value="true">启用</option>
                <option value="false">停用</option>
              </select>
              <select v-model="tagCategoryMergedFilter" @change="resetTagCategoryPageAndLoad">
                <option value="">全部合并状态</option>
                <option value="false">有效分类</option>
                <option value="true">已合并</option>
              </select>
              <select v-model="tagCategorySortBy" @change="resetTagCategoryPageAndLoad">
                <option value="sortOrder">按显示顺序</option>
                <option value="categoryName">按分类名称</option>
                <option value="updatedAt">按更新时间</option>
                <option value="createdAt">按创建时间</option>
              </select>
              <select v-model="tagCategorySortDirection" @change="resetTagCategoryPageAndLoad">
                <option value="ASC">升序</option>
                <option value="DESC">降序</option>
              </select>
            </div>
            <div class="ops-table">
              <div class="ops-table-row head tag-category-row">
                <span>分类</span>
                <span>管理方式</span>
                <span>状态</span>
                <span>影响范围</span>
                <span>更新时间</span>
                <span>操作</span>
              </div>
              <div v-for="category in tagCategories" :key="category.id" class="ops-table-row tag-category-row">
                <span class="ops-table-primary">
                  <strong>{{ category.categoryName }}</strong>
                  <small>{{ category.categoryKey }}</small>
                  <small>{{ category.purpose || '未填写用途' }}</small>
                </span>
                <span>{{ tagSelectionModeLabel(category.selectionMode) }} · {{ category.values?.length ?? 0 }} 个标签值</span>
                <span>
                  <b :class="category.isEnabled ? 'ok-text' : 'warn-text'">{{ category.isEnabled ? '启用' : '停用' }}</b>
                  <small v-if="category.mergedIntoId">已合并至 #{{ category.mergedIntoId }}</small>
                </span>
                <span>{{ tagImpactText(category.impact) }}</span>
                <span>{{ formatDate(category.updatedAt) }}</span>
                <span class="ops-row-actions tag-actions">
                  <button class="secondary small" type="button" @click="openTagDetail('category', category)">详情</button>
                  <button class="secondary small" type="button" :disabled="Boolean(category.mergedIntoId)" @click="editTagEntity('category', category)">编辑</button>
                  <button class="secondary small" type="button" :disabled="Boolean(category.mergedIntoId)" @click="toggleTagCategory(category)">{{ category.isEnabled ? '停用' : '启用' }}</button>
                  <button class="secondary small" type="button" :disabled="Boolean(category.mergedIntoId)" @click="openTagMerge('category', category)">合并</button>
                  <button class="secondary small danger" type="button" :disabled="category.isBuiltin || Boolean(category.mergedIntoId)" @click="confirmDeleteTagCategory(category)">删除</button>
                </span>
              </div>
              <p v-if="!tagCategories.length" class="ops-empty">暂无匹配分类。</p>
            </div>
            <div class="ops-pagination">
              <strong>当前筛选：{{ tagCategoryPageInfo.total }} 个分类</strong>
              <p>第 {{ tagCategoryPageInfo.page }} / {{ tagCategoryTotalPages }} 页，每页 {{ tagCategoryPageInfo.size }} 条</p>
              <div class="ops-row-actions">
                <button class="secondary small" type="button" :disabled="tagCategoryPageInfo.page <= 1" @click="changeTagCategoryPage(-1)">上一页</button>
                <button class="secondary small" type="button" :disabled="tagCategoryPageInfo.page >= tagCategoryTotalPages" @click="changeTagCategoryPage(1)">下一页</button>
              </div>
            </div>
          </template>

          <template v-else>
            <div class="ops-filter-bar tag-filters tag-value-filters">
              <input v-model="tagValueKeyword" placeholder="搜索标签名称、系统编号或含义" @change="resetTagValuePageAndLoad" />
              <select v-model="tagValueCategoryFilter" @change="resetTagValuePageAndLoad">
                <option value="">全部分类</option>
                <option v-for="category in tagCategoryOptionsCache" :key="category.id" :value="String(category.id)">{{ category.categoryName }}</option>
              </select>
              <select v-model="tagValueEnabledFilter" @change="resetTagValuePageAndLoad">
                <option value="">全部启用状态</option>
                <option value="true">启用</option>
                <option value="false">停用</option>
              </select>
              <select v-model="tagValueMergedFilter" @change="resetTagValuePageAndLoad">
                <option value="">全部合并状态</option>
                <option value="false">有效标签</option>
                <option value="true">已合并</option>
              </select>
              <select v-model="tagValueSortBy" @change="resetTagValuePageAndLoad">
                <option value="sortOrder">按显示顺序</option>
                <option value="displayName">按标签名称</option>
                <option value="updatedAt">按更新时间</option>
                <option value="createdAt">按创建时间</option>
              </select>
              <select v-model="tagValueSortDirection" @change="resetTagValuePageAndLoad">
                <option value="ASC">升序</option>
                <option value="DESC">降序</option>
              </select>
            </div>
            <div class="ops-table">
              <div class="ops-table-row head tag-value-row">
                <span>标签值</span>
                <span>所属分类</span>
                <span>选择范围</span>
                <span>状态</span>
                <span>影响范围</span>
                <span>操作</span>
              </div>
              <div v-for="tag in tagValues" :key="tag.id" class="ops-table-row tag-value-row">
                <span class="ops-table-primary">
                  <strong>{{ tag.displayName }}</strong>
                  <small>{{ tag.tagValue }}</small>
                  <small>{{ tag.meaning || '未填写标签含义' }}</small>
                </span>
                <span>{{ tagCategoryLabelForValue(tag) }}</span>
                <span>{{ tagSelectableText(tag) }}</span>
                <span>
                  <b :class="tag.isEnabled ? 'ok-text' : 'warn-text'">{{ tag.isEnabled ? '启用' : '停用' }}</b>
                  <small v-if="tag.mergedIntoId">已合并至 #{{ tag.mergedIntoId }}</small>
                </span>
                <span>{{ tagImpactText(tag.impact) }}</span>
                <span class="ops-row-actions tag-actions">
                  <button class="secondary small" type="button" @click="openTagDetail('value', tag)">详情</button>
                  <button class="secondary small" type="button" :disabled="Boolean(tag.mergedIntoId)" @click="editTagEntity('value', tag)">编辑</button>
                  <button class="secondary small" type="button" :disabled="Boolean(tag.mergedIntoId)" @click="toggleTagValue(tag)">{{ tag.isEnabled ? '停用' : '启用' }}</button>
                  <button class="secondary small" type="button" :disabled="Boolean(tag.mergedIntoId)" @click="openTagMerge('value', tag)">合并</button>
                  <button class="secondary small danger" type="button" :disabled="Boolean(tag.mergedIntoId)" @click="confirmDeleteTagValue(tag)">删除</button>
                </span>
              </div>
              <p v-if="!tagValues.length" class="ops-empty">暂无匹配标签值。</p>
            </div>
            <div class="ops-pagination">
              <strong>当前筛选：{{ tagValuePageInfo.total }} 个标签值</strong>
              <p>第 {{ tagValuePageInfo.page }} / {{ tagValueTotalPages }} 页，每页 {{ tagValuePageInfo.size }} 条</p>
              <div class="ops-row-actions">
                <button class="secondary small" type="button" :disabled="tagValuePageInfo.page <= 1" @click="changeTagValuePage(-1)">上一页</button>
                <button class="secondary small" type="button" :disabled="tagValuePageInfo.page >= tagValueTotalPages" @click="changeTagValuePage(1)">下一页</button>
              </div>
            </div>
          </template>
        </article>
      </section>

      <section v-else-if="activeSection.groupKey === 'insight-ops'" class="ops-admin-layout">
        <article v-if="activeSection.key === 'analytics-dashboard'" class="ops-panel wide">
          <div class="ops-panel-head">
            <div>
              <h2>运营分析看板</h2>
              <p>主管在一页看清 AI 使用、漏斗、同事效能、来源、阶段、风险和内容排行。</p>
            </div>
          </div>
          <div class="ops-filter-bar three">
            <select v-model="skillAnalyticsDays" @change="loadAnalyticsDashboard()">
              <option :value="7">近 7 天</option>
              <option :value="14">近 14 天</option>
              <option :value="30">近 30 天</option>
            </select>
            <select v-model="skillLeadTypeFilter" @change="loadAnalyticsDashboard()">
              <option value="">全部线索类型</option>
              <option v-for="option in LEAD_TYPE_OPTIONS" :key="option.value" :value="option.value">{{ option.label }}</option>
            </select>
            <select v-model="analyticsCallerFilter" @change="loadAnalyticsDashboard()">
              <option value="">全部同事</option>
              <option v-for="account in analyticsCallerOptions" :key="account.phone || account.username || account.id" :value="account.phone || account.username">
                {{ account.displayName || account.phone || account.username }}
              </option>
            </select>
          </div>
          <div class="ops-row-actions">
            <button class="secondary small" type="button" @click="downloadAnalyticsCsv">导出当前看板</button>
          </div>
          <div class="ops-analytics-grid">
            <div v-for="block in analyticsBlocks" :key="block.title" class="ops-analytics-block">
              <h3>{{ block.title }}</h3>
              <p>{{ block.summary }}</p>
            </div>
          </div>
          <div v-if="analyticsFailedSections.length" class="ops-detail-box warning">
            <strong>部分分析区块刷新失败</strong>
            <p>{{ analyticsFailureSummary }}</p>
            <button class="secondary small" type="button" @click="loadAnalyticsDashboard()">重试分析区块</button>
          </div>
          <div class="ops-insight-grid">
            <article v-for="section in analyticsDetailSections" :key="section.key" class="ops-detail-box">
              <strong>{{ section.title }}</strong>
              <p>{{ section.description }}</p>
              <p v-if="section.status.error" class="ops-empty compact error">刷新失败：{{ section.status.error }}</p>
              <div v-if="section.rows.length" class="ops-mini-table">
                <div class="ops-mini-row head">
                  <span v-for="column in section.columns" :key="column.key">{{ column.label }}</span>
                </div>
                <div v-for="(row, rowIndex) in section.rows.slice(0, 8)" :key="`${section.key}-${rowIndex}`" class="ops-mini-row">
                  <span v-for="column in section.columns" :key="column.key">{{ analyticsCell(row, column.key) }}</span>
                </div>
              </div>
              <small v-if="section.rows.length > 8">还有 {{ section.rows.length - 8 }} 行可在导出 CSV 中查看</small>
              <p v-if="!section.rows.length && !section.status.error" class="ops-empty compact">{{ section.status.loading ? '正在刷新' : '暂无数据' }}</p>
            </article>
          </div>
        </article>

        <article v-if="activeSection.key === 'version-management'" class="ops-panel wide">
          <div class="ops-panel-head">
            <div>
              <h2>桌面版本</h2>
              <p>发布、灰度、撤回都需要保留可追溯记录。</p>
            </div>
            <button class="primary small" type="button" @click="openForm('version')">新增版本</button>
          </div>
          <div class="ops-filter-bar">
            <select v-model="versionStatusFilter" @change="resetVersionPageAndLoad">
              <option value="">全部状态</option>
              <option value="DRAFT">草稿</option>
              <option value="PUBLISHED">已发布</option>
              <option value="REVOKED">已撤回</option>
            </select>
            <select v-model="versionPlatformFilter" @change="resetVersionPageAndLoad">
              <option value="">全部平台</option>
              <option value="WINDOWS">Windows</option>
              <option value="MAC">macOS</option>
            </select>
          </div>
          <div class="ops-detail-box">
            <strong>安装包上传</strong>
            <p>新增版本时可先上传安装包，系统会自动回填下载地址和文件大小；也可以继续使用已有下载地址。</p>
            <button class="secondary small" type="button" @click="openForm('version')">上传或新增版本</button>
          </div>
          <div class="ops-table">
            <div class="ops-table-row head version">
              <span>版本</span>
              <span>平台</span>
              <span>策略</span>
              <span>状态</span>
              <span>时间</span>
              <span>操作</span>
            </div>
            <div v-for="version in versions" :key="version.id" class="ops-table-row version">
              <span>{{ version.version }}</span>
              <span>{{ version.platform }}</span>
              <span>{{ updateStrategyLabel(version.updateStrategy) }}</span>
              <span>
                <b :class="versionStatusClass(version)">{{ versionStatusLabel(version.status) }}</b>
                <small v-if="version.revokeReason" class="ops-cell-note">撤回原因：{{ version.revokeReason }}</small>
                <small v-if="version.alternativeVersion" class="ops-cell-note">替代版本：{{ version.alternativeVersion }}</small>
              </span>
              <span>
                <small class="ops-cell-note">发布：{{ formatDate(version.publishedAt) }}</small>
                <small class="ops-cell-note">撤回：{{ formatDate(version.revokedAt) }}</small>
              </span>
              <span class="ops-row-actions">
                <button class="secondary small" type="button" :disabled="!canEditVersion(version)" :title="versionActionReason(version, 'edit')" @click="openForm('version', version)">编辑</button>
                <button class="secondary small" type="button" :disabled="!canPublishVersion(version)" :title="versionActionReason(version, 'publish')" @click="confirmPublishVersion(version)">发布</button>
                <button class="secondary small danger" type="button" :disabled="!canRevokeVersion(version)" :title="versionActionReason(version, 'revoke')" @click="openForm('revokeVersion', version)">撤回</button>
                <button class="secondary small danger" type="button" :disabled="!canDeleteVersion(version)" :title="versionActionReason(version, 'delete')" @click="confirmDeleteVersion(version)">删除</button>
              </span>
            </div>
          </div>
          <p v-if="!versions.length" class="ops-empty">暂无匹配版本。</p>
          <div class="ops-pagination">
            <strong>当前筛选：{{ versionPageInfo.total }} 个版本</strong>
            <p>第 {{ versionPageInfo.page }} / {{ versionTotalPages }} 页，每页 {{ versionPageInfo.size }} 条</p>
            <div class="ops-row-actions">
              <button class="secondary small" type="button" :disabled="versionPageInfo.page <= 1" @click="changeVersionPage(-1)">上一页</button>
              <button class="secondary small" type="button" :disabled="versionPageInfo.page >= versionTotalPages" @click="changeVersionPage(1)">下一页</button>
            </div>
          </div>
        </article>

        <article v-if="activeSection.key === 'system-notices'" class="ops-panel wide">
          <div class="ops-panel-head">
            <div>
              <h2>系统公告</h2>
              <p>只发布工具能力变化和运行状态通知。</p>
            </div>
            <button class="primary small" type="button" @click="openForm('notice')">新增公告</button>
          </div>
          <div class="ops-filter-bar three">
            <select v-model="noticeStatusFilter" @change="resetNoticePageAndLoad">
              <option value="">全部状态</option>
              <option value="PUBLISHED">生效中</option>
              <option value="SCHEDULED">待发布</option>
              <option value="STOPPED">已停止</option>
            </select>
            <select v-model="noticeLevelFilter" @change="resetNoticePageAndLoad">
              <option value="">全部级别</option>
              <option value="INFO">普通</option>
              <option value="WARN">提醒</option>
              <option value="ERROR">故障</option>
            </select>
            <select v-model="noticeSourceFilter" @change="resetNoticePageAndLoad">
              <option value="">全部来源</option>
              <option value="MANUAL">人工公告</option>
              <option value="AUTO">系统自动</option>
            </select>
          </div>
          <div v-for="item in notices" :key="item.id" class="ops-notice-row">
            <strong>{{ item.title }}</strong>
            <span>{{ noticeLevelLabel(item.level) }} · {{ noticeStatusLabel(item) }} · {{ noticeSourceLabel(item.source) }}</span>
            <small>发布时间：{{ formatDate(item.publishAt) }} · 过期时间：{{ formatDate(item.expireAt) }}</small>
            <p>{{ item.content }}</p>
            <div class="ops-row-actions">
              <button class="secondary small" type="button" :disabled="!canEditNotice(item)" :title="noticeActionReason(item, 'edit')" @click="openForm('notice', item)">编辑</button>
              <button class="secondary small" type="button" :disabled="!canStopNotice(item)" :title="noticeActionReason(item, 'stop')" @click="confirmStopNotice(item)">停止</button>
              <button class="secondary small danger" type="button" :disabled="!canDeleteNotice(item)" :title="noticeActionReason(item, 'delete')" @click="confirmDeleteNotice(item)">删除</button>
            </div>
          </div>
          <p v-if="!notices.length" class="ops-empty">暂无公告。</p>
          <div class="ops-pagination">
            <strong>当前筛选：{{ noticePageInfo.total }} 条公告</strong>
            <p>第 {{ noticePageInfo.page }} / {{ noticeTotalPages }} 页，每页 {{ noticePageInfo.size }} 条</p>
            <div class="ops-row-actions">
              <button class="secondary small" type="button" :disabled="noticePageInfo.page <= 1" @click="changeNoticePage(-1)">上一页</button>
              <button class="secondary small" type="button" :disabled="noticePageInfo.page >= noticeTotalPages" @click="changeNoticePage(1)">下一页</button>
            </div>
          </div>
        </article>

        <article v-if="activeSection.key === 'audit-logs'" class="ops-panel wide">
          <div class="ops-panel-head">
            <div>
              <h2>审计日志</h2>
              <p>查询、筛选、导出，不允许修改日志。</p>
            </div>
            <button class="secondary small" type="button" @click="exportAuditLogs">导出 CSV</button>
          </div>
          <div class="ops-filter-bar three">
            <input v-model="auditKeyword" placeholder="搜索操作人或对象" @change="resetAuditPageAndLoad" />
            <select v-model="auditTargetType" @change="loadAuditLogs">
              <option value="">全部对象</option>
              <option v-for="target in auditTargetTypes" :key="target.type" :value="target.type">{{ target.label }}</option>
            </select>
            <input v-model="auditTargetId" placeholder="对象 ID" @change="resetAuditPageAndLoad" />
          </div>
          <div class="audit-action-groups">
            <section v-for="group in auditActionGroups" :key="group.group" class="audit-action-group">
              <strong>{{ group.group }}</strong>
              <div class="audit-action-picker">
                <label
                  v-for="action in group.actions"
                  :key="action.action"
                  class="audit-action-chip"
                  :class="{ active: auditActionsSelected.includes(action.action) }"
                >
                  <input
                    v-model="auditActionsSelected"
                    type="checkbox"
                    :value="action.action"
                    @change="resetAuditPageAndLoad"
                  />
                  <span>{{ action.label }}</span>
                </label>
              </div>
            </section>
          </div>
          <div class="ops-filter-bar three">
            <input v-model="auditStartDate" type="date" @change="loadAuditLogs" />
            <input v-model="auditEndDate" type="date" @change="loadAuditLogs" />
            <button class="secondary small" type="button" @click="loadAuditLogs">查询</button>
            <button class="secondary small" type="button" @click="clearAuditActions">清空动作</button>
          </div>
          <div class="ops-detail-box">
            <strong>当前筛选：{{ auditPageInfo.total }} 条</strong>
            <p>第 {{ auditPageInfo.page }} / {{ auditTotalPages }} 页，动作：{{ selectedAuditActionsText }}，对象：{{ auditTargetType || '全部' }} {{ auditTargetId || '' }}，日志保留 {{ auditPageInfo.retentionDays || '-' }} 天，最早记录：{{ formatDate(auditPageInfo.earliestCreatedAt) }}</p>
            <div class="ops-row-actions">
              <button class="secondary small" type="button" :disabled="auditPageInfo.page <= 1" @click="changeAuditPage(-1)">上一页</button>
              <button class="secondary small" type="button" :disabled="auditPageInfo.page >= auditTotalPages" @click="changeAuditPage(1)">下一页</button>
            </div>
          </div>
          <div v-if="auditExportJob" class="ops-detail-box">
            <strong>导出任务：{{ auditExportJob.status || '处理中' }}</strong>
            <p>{{ auditExportJob.message || summarizeObject(auditExportJob) }}</p>
            <div class="ops-row-actions">
              <button class="secondary small" type="button" @click="refreshAuditExportStatus">刷新导出状态</button>
              <button v-if="auditDownloadUrl" class="primary small" type="button" @click="downloadAuditExport">下载 CSV</button>
            </div>
          </div>
          <div v-for="log in filteredAuditLogs" :key="log.id" class="ops-notice-row">
            <strong>{{ log.actionLabel || actionLabel(log.action) }}</strong>
            <span>{{ log.actionGroup || '-' }} · {{ log.targetTypeLabel || targetTypeLabel(log.targetType) }} {{ log.targetId || '' }}</span>
            <small>{{ log.operator || '-' }} · {{ formatDate(log.createdAt) }}</small>
            <p>{{ log.detailSummary || log.detail || log.target || '无详情' }}</p>
            <button class="secondary small" type="button" @click="toggleAuditDetail(log)">查看详情</button>
            <pre v-if="expandedAuditId === log.id" class="ops-raw-detail">{{ formatDetail(log.detailParsed ?? log.detailJson ?? log.detail ?? log) }}</pre>
          </div>
          <p v-if="!filteredAuditLogs.length" class="ops-empty">暂无审计记录。</p>
        </article>

        <article v-if="activeSection.key === 'system-health'" class="ops-panel wide">
          <div class="ops-panel-head">
            <div>
              <h2>系统健康</h2>
              <p>展示故障和恢复情况，连续失败后转手动刷新。</p>
            </div>
            <button class="secondary small" type="button" @click="loadHealth(true)">手动刷新</button>
          </div>
          <div class="ops-filter-bar three">
            <span class="ops-inline-info">最近刷新：{{ healthLastRefreshAt || '尚未刷新' }} · 自动刷新 {{ healthRefreshIntervalS }} 秒</span>
            <select v-model="healthAlertStatusFilter">
              <option value="">全部告警</option>
              <option value="OPEN">未恢复</option>
              <option value="RECOVERED">已恢复</option>
            </select>
            <select v-model="healthAlertLevelFilter">
              <option value="">全部级别</option>
              <option value="ERROR">故障</option>
              <option value="WARN">提醒</option>
              <option value="INFO">普通</option>
            </select>
          </div>
          <div class="ops-health-grid">
            <div v-for="item in healthCards" :key="item.label" class="ops-health-card">
              <span>{{ item.label }}</span>
              <strong :class="item.ok ? 'ok-text' : 'warn-text'">{{ item.status }}</strong>
              <small>{{ item.help }}</small>
            </div>
          </div>
          <div v-if="filteredHealthAlerts.length" class="ops-card-grid">
            <article v-for="alert in filteredHealthAlerts" :key="alert.id || alert.createdAt" class="ops-content-card">
              <strong>{{ healthAlertTitle(alert) }}</strong>
              <span>{{ healthAlertStatusLabel(alert) }} · {{ alert.level || alert.alertLevel || '-' }}</span>
              <small>发生：{{ formatDate(alert.occurredAt || alert.createdAt || alert.lastSeenAt) }} · 恢复：{{ formatDate(alert.resolvedAt) }} · 持续 {{ healthAlertDuration(alert) }}</small>
              <p>{{ alert.message || alert.detail || '暂无详情' }}</p>
              <button class="secondary small" type="button" @click="toggleHealthAlert(alert)">展开详情</button>
              <pre v-if="expandedHealthAlertId === (alert.id || alert.createdAt)" class="ops-raw-detail">{{ formatDetail(alert.detailJson ?? alert.detail ?? alert) }}</pre>
            </article>
          </div>
          <p v-else class="ops-empty">暂无匹配健康告警。</p>
        </article>
      </section>
    </main>

    <div v-if="activeForm" class="ops-drawer-backdrop" @click.self="closeForm">
      <form class="ops-drawer" @submit.prevent="submitActiveForm">
        <header>
          <div>
            <h2>{{ activeFormTitle }}</h2>
            <p>{{ activeFormDescription }}</p>
          </div>
          <button class="icon-close-button" type="button" aria-label="关闭表单" title="关闭表单" @click="closeForm">
            <span aria-hidden="true">×</span>
          </button>
        </header>
        <div class="ops-form-grid one">
          <div v-if="activeForm === 'version'" class="ops-upload-box">
            <strong>安装包</strong>
            <p>选择安装包后会上传到后端并回填下载地址；提交版本前可以继续修改版本号、策略和更新说明。</p>
            <label class="secondary small file-button">
              选择安装包
              <input type="file" @change="uploadVersionPackage" />
            </label>
            <span v-if="versionUploadState.message" :class="{ 'warn-text': versionUploadState.kind === 'error', 'ok-text': versionUploadState.kind === 'success' }">
              {{ versionUploadState.message }}
            </span>
          </div>
          <label v-for="field in activeFormFields" :key="field.key" :class="{ 'ops-form-span-2': field.type === 'textarea' }">
            <span class="ops-label-title">{{ field.label }}</span>
            <div v-if="activeForm === 'quickSearch' && field.key === 'content'" class="ops-variable-bar">
              <button
                v-for="variable in quickSearchVariables"
                :key="variable.value"
                class="secondary tiny"
                type="button"
                @click="insertQuickSearchVariable(variable.value)"
              >
                {{ variable.label }}
              </button>
            </div>
            <select v-if="field.type === 'select'" v-model="formDraft[field.key]" :disabled="field.disabled" @change="onFormFieldChange(field.key)">
              <option v-for="option in field.options ?? []" :key="String(option.value)" :value="option.value">{{ option.label }}</option>
            </select>
            <textarea v-else-if="field.type === 'textarea'" v-model="formDraft[field.key]" :placeholder="field.placeholder" :disabled="field.disabled" rows="5"></textarea>
            <input v-else-if="field.type === 'checkbox'" v-model="formDraft[field.key]" type="checkbox" :disabled="field.disabled" />
            <input v-else-if="field.type === 'number'" v-model.number="formDraft[field.key]" type="number" :placeholder="field.placeholder" :disabled="field.disabled" :min="field.min" :max="field.max" :step="field.step" />
            <input v-else v-model="formDraft[field.key]" :type="field.type" :placeholder="field.placeholder" :disabled="field.disabled" />
            <small v-if="field.help">{{ field.help }}</small>
          </label>
        </div>
        <p v-if="formError" class="admin-message error">{{ formError }}</p>
        <footer>
          <button class="secondary" type="button" @click="closeForm">取消</button>
          <button class="primary" type="submit" :disabled="loading">保存</button>
        </footer>
      </form>
    </div>

    <div v-if="tagDetailKind" class="ops-drawer-backdrop" @click.self="closeTagDetail">
      <aside class="ops-drawer ops-tag-detail-drawer" aria-label="标签详情">
        <header>
          <div>
            <h2>{{ tagDetailKind === 'category' ? '分类详情' : '标签值详情' }}</h2>
            <p>系统编号、配置和影响统计均来自当前后端数据。</p>
          </div>
          <button class="icon-close-button" type="button" aria-label="关闭详情" title="关闭详情" @click="closeTagDetail"><span aria-hidden="true">×</span></button>
        </header>
        <p v-if="tagDetailLoading" class="ops-empty">正在加载详情。</p>
        <div v-else-if="tagDetail" class="ops-tag-detail-grid">
          <template v-if="tagDetailKind === 'category'">
            <div><span>分类名称</span><strong>{{ tagDetail.categoryName }}</strong></div>
            <div><span>系统编号</span><strong>{{ tagDetail.categoryKey }}</strong></div>
            <div><span>用途</span><strong>{{ tagDetail.purpose || '未填写' }}</strong></div>
            <div><span>绑定字段</span><strong>{{ tagDetail.boundField ? customerFieldLabel(tagDetail.boundField) : '未绑定' }}</strong></div>
            <div><span>选择模式</span><strong>{{ tagSelectionModeLabel(tagDetail.selectionMode) }}</strong></div>
            <div><span>自动更新</span><strong>{{ tagAutoUpdateModeLabel(tagDetail.autoUpdateMode) }}</strong></div>
            <div><span>系统判断</span><strong>{{ booleanLabel(tagDetail.systemInferenceEnabled) }}</strong></div>
            <div><span>员工手动</span><strong>{{ booleanLabel(tagDetail.manualEditEnabled) }}</strong></div>
            <div><span>最低把握程度</span><strong>{{ tagDetail.minConfidence ?? '-' }}</strong></div>
            <div><span>最低有效消息</span><strong>{{ tagDetail.minEvidenceMessages ?? 0 }}</strong></div>
            <div><span>更新冷却</span><strong>{{ tagDetail.cooldownHours ?? 0 }} 小时</strong></div>
            <div><span>不确定策略</span><strong>{{ tagUncertainPolicyLabel(tagDetail.uncertainPolicy) }}</strong></div>
            <div><span>业务用途</span><strong>{{ tagUsageText(tagDetail) }}</strong></div>
            <div><span>状态</span><strong>{{ tagStatusText(tagDetail) }}</strong></div>
          </template>
          <template v-else>
            <div><span>标签名称</span><strong>{{ tagDetail.displayName }}</strong></div>
            <div><span>系统编号</span><strong>{{ tagDetail.tagValue }}</strong></div>
            <div><span>所属分类</span><strong>{{ tagCategoryLabelForValue(tagDetail) }}</strong></div>
            <div><span>选择范围</span><strong>{{ tagSelectableText(tagDetail) }}</strong></div>
            <div class="span-2"><span>标签含义</span><strong>{{ tagDetail.meaning || '未填写' }}</strong></div>
            <div class="span-2"><span>适用条件</span><strong>{{ tagDetail.applicableWhen || '未填写' }}</strong></div>
            <div class="span-2"><span>禁止条件</span><strong>{{ tagDetail.notApplicableWhen || '未填写' }}</strong></div>
            <div class="span-2"><span>正确例子</span><strong>{{ tagDetail.positiveExamples || '未填写' }}</strong></div>
            <div class="span-2"><span>错误例子</span><strong>{{ tagDetail.negativeExamples || '未填写' }}</strong></div>
            <div class="span-2"><span>同义表达</span><strong>{{ tagSynonymsText(tagDetail.synonyms) }}</strong></div>
            <div><span>状态</span><strong>{{ tagStatusText(tagDetail) }}</strong></div>
          </template>
          <div class="span-2"><span>影响范围</span><strong>{{ tagImpactText(tagDetail.impact) }}</strong></div>
          <div><span>版本</span><strong>{{ tagDetail.version }}</strong></div>
          <div><span>更新时间</span><strong>{{ formatDate(tagDetail.updatedAt) }}</strong></div>
        </div>
        <footer>
          <button class="secondary" type="button" @click="closeTagDetail">关闭</button>
          <button v-if="tagDetail && !tagDetail.mergedIntoId" class="primary" type="button" @click="editTagEntity(tagDetailKind, tagDetail)">编辑</button>
        </footer>
      </aside>
    </div>

    <div v-if="tagMerge.kind" class="ops-drawer-backdrop" @click.self="closeTagMerge">
      <aside class="ops-drawer ops-tag-merge-drawer" aria-label="标签合并">
        <header>
          <div>
            <h2>{{ tagMerge.kind === 'category' ? '合并标签分类' : '合并标签值' }}</h2>
            <p>源项会保留历史编号，客户、规则和历史引用会迁移到目标项。</p>
          </div>
          <button class="icon-close-button" type="button" aria-label="关闭合并" title="关闭合并" @click="closeTagMerge"><span aria-hidden="true">×</span></button>
        </header>
        <div class="ops-detail-box">
          <strong>源项：{{ tagMergeSourceName }}</strong>
          <p>{{ tagImpactText(tagMerge.source?.impact) }}</p>
        </div>
        <label class="ops-tag-merge-target">
          <span class="ops-label-title">合并目标</span>
          <select v-model="tagMerge.targetId" :disabled="tagMerge.loading" @change="tagMerge.preview = null">
            <option value="">请选择有效目标</option>
            <option v-for="target in tagMerge.targets" :key="target.id" :value="String(target.id)">{{ tagMergeTargetLabel(target) }}</option>
          </select>
        </label>
        <button class="secondary" type="button" :disabled="!tagMerge.targetId || tagMerge.loading" @click="previewTagMerge">生成合并预览</button>
        <div v-if="tagMerge.preview" class="ops-detail-box warning">
          <strong>{{ tagMerge.preview.sourceName }} → {{ tagMerge.preview.targetName }}</strong>
          <p>{{ tagImpactText(tagMerge.preview.impact) }}</p>
          <p v-if="tagMerge.preview.valueCount">涉及 {{ tagMerge.preview.valueCount }} 个标签值，{{ tagMerge.preview.codeConflictCount }} 个编号冲突。</p>
          <ul v-if="tagMerge.preview.warnings?.length" class="ops-warning-list">
            <li v-for="warning in tagMerge.preview.warnings" :key="warning">{{ warning }}</li>
          </ul>
        </div>
        <p v-if="tagMerge.error" class="admin-message error">{{ tagMerge.error }}</p>
        <footer>
          <button class="secondary" type="button" @click="closeTagMerge">取消</button>
          <button class="primary" type="button" :disabled="!tagMerge.preview || tagMerge.loading" @click="executeTagMerge">确认合并</button>
        </footer>
      </aside>
    </div>
  </section>
</template>

<script setup lang="ts">
import { computed, onBeforeUnmount, onMounted, reactive, ref } from 'vue';
import {
  deleteJson as requestDeleteJson,
  getBlob as requestGetBlob,
  getJson as requestGetJson,
  postForm as requestPostForm,
  postJson as requestPostJson,
  putJson as requestPutJson,
  type ApiResponse
} from '../../shared/apiClient';
import { loadDesktopConfig } from '../../shared/config';
import { eventBus } from '../../shared/eventBus';
import { QUICK_SEARCH_TEMPLATE_VARIABLES } from '../quick-search/templateVariables';

type SectionGroupKey = 'config-center' | 'data-content' | 'org-rules-tags' | 'insight-ops';
type SectionKey =
  | 'skill-scenes'
  | 'configuration-center'
  | 'data-integration'
  | 'quick-search-content'
  | 'account-permissions'
  | 'followup-rules'
  | 'customer-tags'
  | 'analytics-dashboard'
  | 'version-management'
  | 'system-notices'
  | 'audit-logs'
  | 'system-health';
type NoticeKind = 'info' | 'error';
type AnyRecord = Record<string, any>;
type FormOptionValue = string | number | boolean;
type FormKind =
  | 'skill'
  | 'skillEnv'
  | 'imageEnv'
  | 'llmEnv'
  | 'llmRoute'
  | 'datasource'
  | 'quickSearch'
  | 'account'
  | 'rule'
  | 'tagCategory'
  | 'tagValue'
  | 'version'
  | 'revokeVersion'
  | 'notice';

type FormField = {
  key: string;
  label: string;
  type: 'text' | 'number' | 'password' | 'textarea' | 'select' | 'checkbox';
  placeholder?: string;
  options?: Array<{ label: string; value: FormOptionValue }>;
  disabled?: boolean;
  help?: string;
  min?: number;
  max?: number;
  step?: number | string;
};

const props = defineProps<{
  accountName: string;
  tagManagementOnly?: boolean;
}>();

const tagManagementOnly = computed(() => props.tagManagementOnly === true);

defineEmits<{
  logout: [];
}>();

const sessionLabel = computed(() => (props.accountName ? `当前账号：${props.accountName}` : '已登录'));
type AdminSection = {
  key: SectionKey;
  groupKey: SectionGroupKey;
  group: string;
  module: string;
  title: string;
  subtitle: string;
  description: string;
  primaryAction: string;
};
type AnalyticsKey = 'overview' | 'funnels' | 'staff' | 'sources' | 'stages' | 'health' | 'lifecycle' | 'risks' | 'contentRanking';
type AdminNavGroup = {
  key: SectionGroupKey;
  title: string;
  subtitle: string;
  defaultKey: SectionKey;
  pages: AdminSection[];
};

const sections: AdminSection[] = [
  { key: 'skill-scenes', groupKey: 'config-center', group: '运营 A', module: 'A', title: 'Skill 场景管理', subtitle: '场景绑定、测试、调用监控', description: '按业务场景和线索类型维护 AI 路由，测试真实话术效果并观察调用质量。', primaryAction: '新增 Skill 绑定' },
  { key: 'configuration-center', groupKey: 'config-center', group: '运营 B', module: 'B', title: '配置中心', subtitle: 'AI、LLM、识图、Prompt', description: '集中管理 Skill 环境、LLM 思考环境、识图环境、Prompt 模板、企业红线和降级回复。', primaryAction: '新增 Skill 环境' },
  { key: 'data-integration', groupKey: 'data-content', group: '运营 C', module: 'C', title: '客户数据对接', subtitle: '数据源、字段映射、同步、导入', description: '管理企微表格数据源、字段映射版本、同步状态和 CSV 导入。', primaryAction: '添加数据源' },
  { key: 'quick-search-content', groupKey: 'data-content', group: '运营 D', module: 'D', title: '速搜内容管理', subtitle: '模板、知识、图片、小程序', description: '维护桌面端速搜可用的话术、知识片段、门店定位、图片素材和小程序引导。', primaryAction: '新增内容' },
  { key: 'account-permissions', groupKey: 'org-rules-tags', group: '运营 E', module: 'E', title: '账号与权限', subtitle: '账号、角色、组长关系', description: '管理 ADMIN、LEADER、KEEPER 的账号权限和直属组长关系。', primaryAction: '新增账号' },
  { key: 'followup-rules', groupKey: 'org-rules-tags', group: '运营 F', module: 'F', title: '跟进规则引擎配置', subtitle: '条件、动作、启停', description: '配置跟进提醒、标签建议和通知组长的业务规则。', primaryAction: '新增规则' },
  { key: 'customer-tags', groupKey: 'org-rules-tags', group: '运营 G', module: 'G', title: '客户标签与分层', subtitle: '标签分类、标签值、合并', description: '维护 AI 和运营共用的客户标签分类、标签值和历史合并关系。', primaryAction: '新增分类' },
  { key: 'analytics-dashboard', groupKey: 'insight-ops', group: '运营 H', module: 'H', title: '运营分析看板', subtitle: '使用、漏斗、来源、风险', description: '查看 AI 使用、转化漏斗、同事效能、客户来源、阶段、风险和内容排行。', primaryAction: '导出当前看板' },
  { key: 'version-management', groupKey: 'insight-ops', group: '运营 I', module: 'I', title: '版本管理', subtitle: '桌面版本、发布、撤回', description: '管理桌面端版本、发布策略、灰度和撤回记录。', primaryAction: '新增版本' },
  { key: 'system-notices', groupKey: 'insight-ops', group: '运营 J', module: 'J', title: '系统公告', subtitle: '公告、排期、停止', description: '发布工具能力变化、维护窗口和故障通知。', primaryAction: '新增公告' },
  { key: 'audit-logs', groupKey: 'insight-ops', group: '运营 K', module: 'K', title: '操作审计日志', subtitle: '查询、详情、导出', description: '查询、筛选和导出关键后台操作，审计日志不可修改。', primaryAction: '导出 CSV' },
  { key: 'system-health', groupKey: 'insight-ops', group: '运营 L', module: 'L', title: '系统健康监控', subtitle: 'DB、缓存、AI、识图、表格', description: '监控数据库、缓存、Skill、识图和表格通道状态，查看故障与恢复情况。', primaryAction: '手动刷新' }
];

const SKILL_SCENE_OPTIONS = [
  { label: '聊天识别', value: 'CHAT_RECOGNIZE' },
  { label: '开场白', value: 'OPENING' },
  { label: '主动回复', value: 'ACTIVE_REPLY' },
  { label: '换一组', value: 'REGENERATE' },
  { label: '档案提取', value: 'PROFILE_EXTRACT' }
];

const LLM_SCENE_LABELS: Record<string, string> = {
  REPLY_GENERATION: '回复生成',
  PROFILE_EXTRACTION: '档案提取',
  FOLLOWUP_SUGGESTION: '跟进建议',
  ABNORMAL_DETECTION: '异常检测',
  SUMMARY: '总结分析'
};

const LEAD_TYPE_OPTIONS = [
  { label: '全部客资', value: 'GENERAL' },
  { label: '团购客资', value: 'TUAN_GOU' },
  { label: '线索客资', value: 'XIAN_SUO' },
  { label: '待确认', value: 'PENDING' }
];

const CUSTOMER_FIELD_LABELS: Record<string, string> = {
  phone: '手机号',
  nickname: '客户昵称',
  leadType: '线索类型',
  assignedKeeper: '负责管家',
  intendedStore: '意向门店',
  intendedProject: '意向项目',
  purchasedProject: '已购项目',
  intentLevel: '意向等级',
  customerStage: '客户阶段',
  personalityType: '性格类型',
  bodyConcerns: '身体关注',
  postpartumMonths: '产后月份',
  parity: '胎次',
  deliveryMethod: '分娩方式',
  breastfeeding: '哺乳情况',
  worries: '客户顾虑',
  nextFollowupAt: '下次跟进时间',
  nextFollowupDir: '下次跟进方向',
  appointmentDate: '预约日期',
  appointmentStore: '预约门店'
};

const TRANSLATED_VALUE_LABELS: Record<string, string> = {
  GENERAL: '全部客资',
  TUAN_GOU: '团购客资',
  XIAN_SUO: '线索客资',
  PENDING: '待确认',
  HIGH: '高',
  MEDIUM: '中',
  LOW: '低',
  UNKNOWN: '未填写',
  ACTIVE_REPLY: '主动回复',
  CHAT_RECOGNIZE: '聊天识别',
  PROFILE_EXTRACT: '档案提取',
  REGENERATE: '换一组',
  OPENING: '开场白'
};

const ANALYTICS_KEY_LABELS: Record<string, string> = {
  totalCalls: '调用次数',
  successCount: '成功',
  failCount: '失败',
  successRate: '成功率',
  adoptionRate: '采纳率',
  adoptionCount: '采纳次数',
  avgResponseTime: '平均响应',
  avgResponseTimeMs: '平均响应',
  activeCallerCount: '活跃同事',
  totalCustomers: '客户数',
  keeperCount: '管家数',
  overdueCount: '逾期客户',
  silentCount: '沉默客户',
  total: '总数',
  currentCount: '当前映射',
  baselineCount: '对比版本',
  added: '新增',
  removed: '移除',
  changed: '变更',
  unchanged: '未变化'
};

const QUICK_SEARCH_CONTENT_META: Record<string, { contentLabel: string; contentPlaceholder: string; imageLabel: string; imagePlaceholder: string; help: string }> = {
  TEMPLATE: {
    contentLabel: '话术正文',
    contentPlaceholder: '写同事可以直接发送给客户的话术，可插入客户档案变量',
    imageLabel: '图片地址（可选）',
    imagePlaceholder: '有配图时填写或上传图片',
    help: '适合开场、跟进、邀约等可直接复制发送的话术。'
  },
  KNOWLEDGE: {
    contentLabel: '知识内容',
    contentPlaceholder: '写产品、项目、门店或政策说明，可插入客户档案变量',
    imageLabel: '参考链接（可选）',
    imagePlaceholder: '可填写知识来源链接',
    help: '适合内部查询，不一定直接发给客户。'
  },
  LOCATION: {
    contentLabel: '门店定位与到店说明',
    contentPlaceholder: '填写门店地址、路线、停车、营业时间等信息',
    imageLabel: '地图/定位链接',
    imagePlaceholder: '填写地图链接或定位链接',
    help: '适合门店位置、路线和预约到店提醒。'
  },
  IMAGE: {
    contentLabel: '图片说明/配套话术',
    contentPlaceholder: '说明图片适用场景，或写发送图片时配套的一句话',
    imageLabel: '图片地址',
    imagePlaceholder: '上传图片后会自动回填，也可以粘贴图片 URL',
    help: '适合海报、案例图、项目图等素材。'
  },
  MINI_PROGRAM: {
    contentLabel: '小程序引导话术',
    contentPlaceholder: '写引导客户打开小程序或领取权益的话术',
    imageLabel: '小程序路径/链接',
    imagePlaceholder: '填写小程序 path、二维码图片或跳转链接',
    help: '适合预约、领券、查看套餐等小程序入口。'
  }
};

const allNavGroups: AdminNavGroup[] = [
  { key: 'config-center', title: '配置中心', subtitle: 'Skill、AI、识图、Prompt', defaultKey: 'skill-scenes', pages: sections.filter((section) => section.groupKey === 'config-center') },
  { key: 'data-content', title: '数据源与内容', subtitle: '数据对接、速搜内容', defaultKey: 'data-integration', pages: sections.filter((section) => section.groupKey === 'data-content') },
  { key: 'org-rules-tags', title: '组织与规则', subtitle: '账号、规则、标签', defaultKey: 'account-permissions', pages: sections.filter((section) => section.groupKey === 'org-rules-tags') },
  { key: 'insight-ops', title: '分析与系统', subtitle: '看板、版本、公告、审计、健康', defaultKey: 'analytics-dashboard', pages: sections.filter((section) => section.groupKey === 'insight-ops') }
];

const navGroups = computed<AdminNavGroup[]>(() => tagManagementOnly.value
  ? [{
      key: 'org-rules-tags',
      title: '客户标签',
      subtitle: '分类与标签值',
      defaultKey: 'customer-tags',
      pages: sections.filter((section) => section.key === 'customer-tags')
    }]
  : allNavGroups);

const activeSectionKey = ref<SectionKey>(props.tagManagementOnly ? 'customer-tags' : 'skill-scenes');
const loading = ref(false);
const notice = ref('');
const noticeKind = ref<NoticeKind>('info');
const activeForm = ref<FormKind | null>(null);
const editingItem = ref<AnyRecord | null>(null);
const formDraft = reactive<AnyRecord>({});
const formError = ref('');
const versionUploadState = reactive<{ kind: 'idle' | 'loading' | 'success' | 'error'; message: string }>({ kind: 'idle', message: '' });
const selectedDatasource = ref<AnyRecord | null>(null);
const mappingDraft = reactive<Record<string, string>>({});
const mappingEnabledDraft = reactive<Record<string, boolean>>({});
const skillSceneFilter = ref('');
const skillLeadTypeFilter = ref('');
const skillAnalyticsDays = ref(7);
const skillTestMessage = ref('请基于当前客户状态生成一条跟进回复');
const selectedPromptType = ref('format');
const datasourceColumns = ref<Array<AnyRecord | string>>([]);
const datasourceColumnStatus = ref<AnyRecord | null>(null);
const mappingCompare = ref<AnyRecord | null>(null);
const customerSearchKeyword = ref('');
const quickSearchKeyword = ref('');
const quickSearchType = ref('');
const quickSearchEnabledFilter = ref('');
const quickSearchSelectedIds = ref<Array<string | number>>([]);
const ruleKeyword = ref('');
const ruleActionType = ref('');
const ruleEnabledFilter = ref('');
const ruleSelectedIds = ref<Array<string | number>>([]);
const csvPreview = ref<string[]>([]);
const csvFile = ref<File | null>(null);
const csvImportResult = ref<AnyRecord | null>(null);
const accountKeyword = ref('');
const accountRoleFilter = ref('');
const accountEnabledFilter = ref('');
const tagView = ref<'categories' | 'values'>('categories');
const tagCategoryKeyword = ref('');
const tagCategoryEnabledFilter = ref('');
const tagCategoryMergedFilter = ref('false');
const tagCategorySortBy = ref('sortOrder');
const tagCategorySortDirection = ref<'ASC' | 'DESC'>('ASC');
const tagValueKeyword = ref('');
const tagValueCategoryFilter = ref('');
const tagValueEnabledFilter = ref('');
const tagValueMergedFilter = ref('false');
const tagValueSortBy = ref('sortOrder');
const tagValueSortDirection = ref<'ASC' | 'DESC'>('ASC');
const tagDetailKind = ref<'category' | 'value' | null>(null);
const tagDetail = ref<AnyRecord | null>(null);
const tagDetailLoading = ref(false);
const tagMerge = reactive<{
  kind: 'category' | 'value' | null;
  source: AnyRecord | null;
  targetId: string;
  targets: AnyRecord[];
  preview: AnyRecord | null;
  loading: boolean;
  error: string;
}>({ kind: null, source: null, targetId: '', targets: [], preview: null, loading: false, error: '' });
const versionStatusFilter = ref('');
const versionPlatformFilter = ref('');
const analyticsCallerFilter = ref('');
const noticeStatusFilter = ref('');
const noticeLevelFilter = ref('');
const noticeSourceFilter = ref('');
const auditKeyword = ref('');
const auditActionsSelected = ref<string[]>([]);
const auditTargetType = ref('');
const auditTargetId = ref('');
const auditStartDate = ref('');
const auditEndDate = ref('');
const expandedAuditId = ref<string | number | null>(null);
const auditExportJob = ref<AnyRecord | null>(null);
const auditExportPollTimer = ref<number | null>(null);
const expandedHealthAlertId = ref<string | number | null>(null);
const healthAlertStatusFilter = ref('');
const healthAlertLevelFilter = ref('');
const healthLastRefreshAt = ref('');
const healthConsecutiveFailures = ref(0);
const healthRefreshIntervalS = ref(30);

const skillBindings = ref<AnyRecord[]>([]);
const availableSkills = ref<AnyRecord[]>([]);
const skillAnalytics = ref<AnyRecord | null>(null);
const skillTestResults = reactive<Record<string, AnyRecord>>({});
const skillEnvironments = ref<AnyRecord[]>([]);
const imageEnvironments = ref<AnyRecord[]>([]);
const llmEnvironments = ref<AnyRecord[]>([]);
const llmRoutes = ref<AnyRecord[]>([]);
const llmRouteScenes = ref<string[]>([]);
const llmAnalytics = ref<AnyRecord | null>(null);
const configs = ref<AnyRecord[]>([]);
const datasources = ref<AnyRecord[]>([]);
const syncStatuses = ref<AnyRecord[]>([]);
const customerFields = ref<AnyRecord[]>([]);
const mappings = ref<AnyRecord[]>([]);
const mappingVersions = ref<AnyRecord[]>([]);
const importLogs = ref<AnyRecord[]>([]);
const customerSearchItems = ref<AnyRecord[]>([]);
const selectedAdminCustomer = ref<AnyRecord | null>(null);
const quickSearchItems = ref<AnyRecord[]>([]);
const accounts = ref<AnyRecord[]>([]);
const leaderAccounts = ref<AnyRecord[]>([]);
const analyticsAccounts = ref<AnyRecord[]>([]);
const rules = ref<AnyRecord[]>([]);
const tagCategories = ref<AnyRecord[]>([]);
const tagCategoryOptionsCache = ref<AnyRecord[]>([]);
const tagValues = ref<AnyRecord[]>([]);
const analytics = reactive<Record<string, AnyRecord>>({});
const analyticsStatus = reactive<Record<AnalyticsKey, { loading: boolean; error: string }>>({
  overview: { loading: false, error: '' },
  funnels: { loading: false, error: '' },
  staff: { loading: false, error: '' },
  sources: { loading: false, error: '' },
  stages: { loading: false, error: '' },
  health: { loading: false, error: '' },
  lifecycle: { loading: false, error: '' },
  risks: { loading: false, error: '' },
  contentRanking: { loading: false, error: '' }
});
const analyticsLabels: Record<AnalyticsKey, string> = {
  overview: '使用量概览',
  funnels: '转化漏斗',
  staff: '同事效能',
  sources: '客户来源',
  stages: '阶段分布',
  health: '健康趋势',
  lifecycle: '生命周期',
  risks: '风险客户',
  contentRanking: '内容排行'
};
const versions = ref<AnyRecord[]>([]);
const notices = ref<AnyRecord[]>([]);
const auditLogs = ref<AnyRecord[]>([]);
const auditActions = ref<AnyRecord[]>([]);
const auditTargetTypes = ref<AnyRecord[]>([]);
const auditPageInfo = reactive({
  total: 0,
  page: 1,
  size: 20,
  totalPages: 1,
  retentionDays: 0,
  earliestCreatedAt: ''
});
const accountPageInfo = reactive({
  total: 0,
  page: 1,
  size: 20,
  totalPages: 1
});
const customerSearchPageInfo = reactive({
  total: 0,
  page: 1,
  size: 20,
  totalPages: 1
});
const rulePageInfo = reactive({
  total: 0,
  page: 1,
  size: 20,
  totalPages: 1
});
const tagCategoryPageInfo = reactive({
  total: 0,
  page: 1,
  size: 20,
  totalPages: 1
});
const tagValuePageInfo = reactive({
  total: 0,
  page: 1,
  size: 20,
  totalPages: 1
});
const quickSearchPageInfo = reactive({
  total: 0,
  page: 1,
  size: 20,
  totalPages: 1
});
const versionPageInfo = reactive({
  total: 0,
  page: 1,
  size: 20,
  totalPages: 1
});
const noticePageInfo = reactive({
  total: 0,
  page: 1,
  size: 20,
  totalPages: 1
});
const health = ref<AnyRecord | null>(null);
const promptVersions = ref<AnyRecord[]>([]);
const promptDraft = reactive({
  format: '',
  redLinesText: '',
  tagRemovalRulesText: '',
  fallbackReply: '',
  imageRecognitionPrompt: '',
  regenerateMaxCount: 0
});
const skillRuntimeDraft = reactive({
  timeoutMs: 10000,
  circuitBreakerWindowS: 30,
  circuitBreakerFailureRate: 0.5,
  circuitBreakerMinCalls: 5,
  circuitBreakerOpenS: 30,
  alertFailureRate: 0.3,
  alertFailureDurationMinutes: 15,
  profileExtractTimeoutMs: 8000
});
const imageRuntimeDraft = reactive({
  model: 'qwen3-vl-plus',
  timeoutMs: 5000,
  maxSizeBytes: 5242880,
  maxDimensionPx: 1920,
  compressQuality: 85,
  consecutiveFailuresAlert: 3,
  clipboardScreenshotConfirmPromptS: 10
});
const llmAnalyticsDays = ref(7);
const llmReplyDraft = reactive({
  enabled: false,
  fallbackToSkill: true,
  temperature: '',
  maxTokens: 900,
  systemPrompt: ''
});
const llmProfileDraft = reactive({
  enabled: false,
  fallbackToSkill: true,
  temperature: '',
  maxTokens: 700,
  systemPrompt: ''
});
const llmFollowupDraft = reactive({
  enabled: false,
  temperature: '',
  maxTokens: 500,
  systemPrompt: ''
});
const llmAbnormalDraft = reactive({
  enabled: false,
  temperature: '',
  maxTokens: 500,
  systemPrompt: ''
});
const llmSummaryDraft = reactive({
  enabled: false,
  temperature: '',
  maxTokens: 500,
  systemPrompt: ''
});
const tableRuntimeDraft = reactive({
  apiBaseUrl: '',
  apiKey: '',
  writeTimeoutMs: 10000,
  retryMaxCount: 5,
  retryIntervalS: 60,
  alertFailureHours: 1,
  alertNotifyTarget: 'ADMIN',
  queueWarnThreshold: 100,
  queueAlertThreshold: 1000
});
const datasourceRuntimeDraft = reactive({
  syncCron: '0 */30 * * * *',
  ttlSeconds: 900,
  syncTimeoutMs: 10000,
  mappingVersionMax: 50,
  importMaxRows: 5000,
  manualSyncTimeoutS: 60,
  syncStatusRefreshS: 30
});

let healthTimer: number | null = null;
let analyticsTimer: number | null = null;
let disposeTagRefresh: (() => void) | null = null;

const activeSection = computed(() => sections.find((section) => section.key === activeSectionKey.value)
  ?? sections.find((section) => section.key === 'customer-tags')
  ?? sections[0]);
const activeMetrics = computed(() => {
  const metricsBySection: Record<SectionKey, Array<{ label: string; value: string | number; help: string }>> = {
    'skill-scenes': [
      { label: '场景绑定', value: skillBindings.value.length, help: '覆盖开场白、主动回复和换一组' },
      { label: '启用中', value: skillBindings.value.filter((item) => item.enabled !== false).length, help: '停用项不会参与生产路由' },
      { label: '测试结果', value: Object.keys(skillTestResults).length, help: '本页可直接验证话术质量' }
    ],
    'configuration-center': [
      { label: 'Skill 环境', value: skillEnvironments.value.length, help: activeEnvironmentName(skillEnvironments.value) },
      { label: '识图环境', value: imageEnvironments.value.length, help: activeEnvironmentName(imageEnvironments.value) },
      { label: 'LLM 路由', value: llmRoutes.value.length, help: llmReplyDraft.enabled ? '回复生成已启用 LLM' : activeEnvironmentName(llmEnvironments.value) }
    ],
    'data-integration': [
      { label: '数据源', value: datasources.value.length, help: '企微表格与 CSV 导入' },
      { label: '同步异常', value: syncStatuses.value.filter((item) => String(item.syncStatus || item.status || '').includes('FAIL')).length, help: '失败项需人工检查' },
      { label: '映射版本', value: mappingVersions.value.length, help: selectedDatasource.value ? selectedDatasource.value.name : '选择数据源后查看' }
    ],
    'quick-search-content': [
      { label: '速搜内容', value: quickSearchPageInfo.total, help: '推送到桌面端快线模板' },
      { label: '当前页', value: quickSearchItems.value.length, help: '支持按标题、快线码、正文筛选' },
      { label: '已选中', value: quickSearchSelectedIds.value.length, help: '可批量启用、停用或删除' }
    ],
    'account-permissions': [
      { label: '账号', value: accountPageInfo.total, help: 'ADMIN/LEADER/KEEPER' },
      { label: '启用组长', value: leaderAccounts.value.length, help: '用于绑定管家直属组长' },
      { label: '当前页停用', value: accounts.value.filter((item) => item.isEnabled === false).length, help: '停用后不可登录' }
    ],
    'followup-rules': [
      { label: '规则', value: rulePageInfo.total, help: '提醒、标签建议、通知组长' },
      { label: '启用中', value: rules.value.filter((item) => item.enabled !== false).length, help: '内置规则优先停用而非删除' },
      { label: '当前页', value: rules.value.length, help: '按规则名称、动作和状态筛选' }
    ],
    'customer-tags': [
      { label: '标签分类', value: tagCategoryPageInfo.total, help: 'AI 和运营共用分类' },
      { label: '标签值', value: tagValuePageInfo.total, help: '可用于客户分层和规则' },
      { label: '当前视图', value: tagView.value === 'categories' ? '分类' : '标签值', help: '支持详情、合并和导出' }
    ],
    'analytics-dashboard': [
      { label: '看板区块', value: analyticsBlocks.value.length, help: '使用、漏斗、效能、来源、风险' },
      { label: '统计窗口', value: `${skillAnalyticsDays.value} 天`, help: '同一窗口联动所有分析 API' },
      { label: '运行模式', value: runtimeModeLabel.value, help: runtimeModeDescription.value }
    ],
    'version-management': [
      { label: '版本', value: versionPageInfo.total, help: 'Windows/macOS 发布记录' },
      { label: '已发布', value: versions.value.filter((item) => item.status === 'PUBLISHED').length, help: '发布后桌面端可收到升级信息' },
      { label: '当前页', value: versions.value.length, help: '草稿、发布、撤回分状态管理' }
    ],
    'system-notices': [
      { label: '公告', value: noticePageInfo.total, help: '工具能力和故障通知' },
      { label: '生效中', value: notices.value.filter((item) => noticeStatus(item) === 'PUBLISHED').length, help: '会展示给桌面端同事' },
      { label: '当前页', value: notices.value.length, help: '按状态和级别筛选' }
    ],
    'audit-logs': [
      { label: '审计记录', value: filteredAuditLogs.value.length, help: '当前筛选结果' },
      { label: '动作类型', value: auditActions.value.length, help: '来自后端动作字典' },
      { label: '导出任务', value: auditExportJob.value?.status ?? '未创建', help: '导出后可刷新任务状态' }
    ],
    'system-health': [
      { label: '健康', value: healthStatusText.value, help: '系统、AI、表格、缓存状态' },
      { label: '告警', value: filteredHealthAlerts.value.length, help: '按恢复状态筛选' },
      { label: '最近刷新', value: healthLastRefreshAt.value || '未刷新', help: '连续失败会暂停自动刷新' }
    ]
  };
  return metricsBySection[activeSectionKey.value] ?? [];
});
const filteredRules = computed(() => rules.value.filter((rule) => {
  const matchesKeyword = !ruleKeyword.value.trim() || String(rule.name ?? '').includes(ruleKeyword.value.trim());
  const matchesType = !ruleActionType.value || rule.actionType === ruleActionType.value;
  return matchesKeyword && matchesType;
}));
const deletableSelectedRules = computed(() => rules.value.filter((rule) => ruleSelectedIds.value.includes(rule.id) && !isBuiltinRule(rule)));
const tagCategoryTotalPages = computed(() => Math.max(1, tagCategoryPageInfo.totalPages));
const tagValueTotalPages = computed(() => Math.max(1, tagValuePageInfo.totalPages));
const tagMergeSourceName = computed(() => tagMerge.kind === 'category'
  ? String(tagMerge.source?.categoryName ?? '')
  : String(tagMerge.source?.displayName ?? ''));
const filteredAuditLogs = computed(() => auditLogs.value.filter((log) => {
  const keyword = auditKeyword.value.trim();
  const matchesKeyword = !keyword || [log.operator, log.action, log.detail, log.target].some((value) => String(value ?? '').includes(keyword));
  const matchesAction = !auditActionsSelected.value.length || auditActionsSelected.value.includes(String(log.action ?? ''));
  return matchesKeyword && matchesAction;
}));
const selectedAuditActionsText = computed(() => auditActionsSelected.value.length
  ? auditActionsSelected.value.map((action) => actionLabel(action)).join('、')
  : '全部动作');
const analyticsFailedSections = computed(() => (Object.entries(analyticsStatus) as Array<[AnalyticsKey, { loading: boolean; error: string }]>)
  .filter(([, status]) => status.error)
  .map(([key, status]) => ({ key, label: analyticsLabels[key], error: status.error })));
const analyticsFailureSummary = computed(() => analyticsFailedSections.value
  .map((item) => `${item.label}：${item.error}`)
  .join('；'));
const analyticsBlocks = computed(() => [
  { title: '使用量概览', summary: summarizeObject(analytics.overview?.summary ?? analytics.overview) },
  { title: '转化漏斗', summary: summarizeObject(analytics.funnels) },
  { title: '同事效能', summary: `${listFrom(analytics.staff, 'staff').length} 位同事有统计数据` },
  { title: '客户来源', summary: `${listFrom(analytics.sources, 'sources').length} 个来源渠道` },
  { title: '阶段分布', summary: `${listFrom(analytics.stages, 'stages').length} 个客户阶段` },
  { title: '健康趋势', summary: summarizeObject(analytics.health?.summary ?? analytics.health) },
  { title: '生命周期', summary: summarizeObject(analytics.lifecycle?.summary ?? analytics.lifecycle) },
  { title: '风险与内容', summary: `${listFrom(analytics.risks, 'customers').length} 个风险客户，${listFrom(analytics.contentRanking, 'items').length} 条内容排行` }
]);
const analyticsCallerOptions = computed(() => analyticsAccounts.value.filter((account) => ['KEEPER', 'LEADER', 'ADMIN'].includes(String(account.role ?? ''))));
const llmCallSummary = computed(() => (llmAnalytics.value?.summary ?? {}) as AnyRecord);
const llmCallDetails = computed(() => listFrom(llmAnalytics.value ?? {}, 'details'));
const analyticsDetailSections = computed(() => [
  {
    key: 'trend',
    title: '使用趋势',
    description: '按天查看调用量、采纳量和响应时间。',
    columns: [
      { key: 'date', label: '日期' },
      { key: 'totalCalls', label: '调用' },
      { key: 'adoptionCount', label: '采纳' },
      { key: 'adoptionRate', label: '采纳率' },
      { key: 'avgResponseTimeMs', label: '响应ms' }
    ],
    rows: listFrom(analytics.overview, 'dailyTrend'),
    status: analyticsStatus.overview
  },
  {
    key: 'staff',
    title: '同事效能',
    description: '按 caller 汇总客户数、调用量、逾期和沉默客户。',
    columns: [
      { key: 'caller', label: '同事' },
      { key: 'totalCustomers', label: '客户' },
      { key: 'totalCalls', label: '调用' },
      { key: 'adoptionRate', label: '采纳率' },
      { key: 'overdueCount', label: '逾期' },
      { key: 'silentCount', label: '沉默' }
    ],
    rows: listFrom(analytics.staff),
    status: analyticsStatus.staff
  },
  {
    key: 'sources',
    title: '客户来源',
    description: '来源渠道和到店率快照。',
    columns: [
      { key: 'sourceChannel', label: '来源' },
      { key: 'total', label: '总数' },
      { key: 'tuanGouCount', label: '团购' },
      { key: 'xianSuoCount', label: '线索' },
      { key: 'arrivalRate', label: '到店率' }
    ],
    rows: listFrom(analytics.sources),
    status: analyticsStatus.sources
  },
  {
    key: 'stages',
    title: '阶段分布',
    description: '当前客户阶段分布。',
    columns: [
      { key: 'customerStage', label: '阶段' },
      { key: 'total', label: '总数' },
      { key: 'tuanGouCount', label: '团购' },
      { key: 'xianSuoCount', label: '线索' }
    ],
    rows: listFrom(analytics.stages),
    status: analyticsStatus.stages
  },
  {
    key: 'lifecycle',
    title: '生命周期估算',
    description: '基于现有客户更新时间估算，适合趋势参考。',
    columns: [
      { key: 'leadType', label: '线索类型' },
      { key: 'allocationToFirstContact', label: '分配到首触' },
      { key: 'allocationToArrival', label: '分配到到店' },
      { key: 'estimateSource', label: '估算来源' }
    ],
    rows: listFrom(analytics.lifecycle),
    status: analyticsStatus.lifecycle
  },
  {
    key: 'risks',
    title: '风险客户',
    description: '逾期、沉默和待跟进客户。',
    columns: [
      { key: 'phone', label: '客户' },
      { key: 'nickname', label: '昵称' },
      { key: 'leadType', label: '线索' },
      { key: 'customerStage', label: '阶段' },
      { key: 'assignedKeeper', label: '管家' },
      { key: 'lastFollowupAt', label: '最近跟进' }
    ],
    rows: listFrom(analytics.risks, 'customers'),
    status: analyticsStatus.risks
  },
  {
    key: 'content',
    title: '内容排行',
    description: '基于审计/使用记录的内容动作排行。',
    columns: [
      { key: 'action', label: '动作' },
      { key: 'targetType', label: '对象' },
      { key: 'targetId', label: '对象 ID' },
      { key: 'useCount', label: '次数' },
      { key: 'sampleDetail', label: '样例' }
    ],
    rows: listFrom(analytics.contentRanking),
    status: analyticsStatus.contentRanking
  }
]);
const healthCards = computed(() => {
  const data = health.value ?? {};
  const components = (data.components ?? {}) as AnyRecord;
  const cards = [
    { key: 'db', label: '数据库', help: '影响全部业务数据' },
    { key: 'redis', label: '缓存', help: '影响会话和实时状态' },
    { key: 'skill', label: 'Skill 通道', help: '影响回复生成' },
    { key: 'imageRecognition', label: '识图通道', help: '影响截图识别' },
    { key: 'wecomTable', label: '表格通道', help: '影响保存到表格' }
  ];
  return cards.map((card) => {
    const component = components[card.key] ?? {};
    const status = component.status || data[`${card.key}Status`] || 'UNKNOWN';
    return { label: card.label, status, ok: isOkStatus(status), help: component.duration ? `${card.help} · ${component.duration}` : card.help };
  });
});
const healthStatusText = computed(() => healthCards.value.some((item) => !item.ok) ? '需关注' : '正常');
const runtimeMode = computed(() => (health.value?.runtimeMode ?? {}) as AnyRecord);
const runtimeModeLabel = computed(() => String(runtimeMode.value.label ?? '运行模式未连接'));
const runtimeModeDescription = computed(() => String(runtimeMode.value.description ?? '正在等待后端返回运行模式。'));
const runtimeModePillClass = computed(() => runtimeMode.value.mockExternals === true ? 'mock' : runtimeMode.value.mockExternals === false ? 'real' : 'unknown');
const auditDownloadUrl = computed(() => String(auditExportJob.value?.downloadUrl ?? ''));
const accountTotalPages = computed(() => Math.max(1, Number(accountPageInfo.totalPages || Math.ceil(accountPageInfo.total / Math.max(1, accountPageInfo.size)) || 1)));
const customerSearchTotalPages = computed(() => Math.max(1, Number(customerSearchPageInfo.totalPages || Math.ceil(customerSearchPageInfo.total / Math.max(1, customerSearchPageInfo.size)) || 1)));
const ruleTotalPages = computed(() => Math.max(1, Number(rulePageInfo.totalPages || Math.ceil(rulePageInfo.total / Math.max(1, rulePageInfo.size)) || 1)));
const quickSearchTotalPages = computed(() => Math.max(1, Number(quickSearchPageInfo.totalPages || Math.ceil(quickSearchPageInfo.total / Math.max(1, quickSearchPageInfo.size)) || 1)));
const versionTotalPages = computed(() => Math.max(1, Number(versionPageInfo.totalPages || Math.ceil(versionPageInfo.total / Math.max(1, versionPageInfo.size)) || 1)));
const noticeTotalPages = computed(() => Math.max(1, Number(noticePageInfo.totalPages || Math.ceil(noticePageInfo.total / Math.max(1, noticePageInfo.size)) || 1)));
const auditTotalPages = computed(() => Math.max(1, Number(auditPageInfo.totalPages || Math.ceil(auditPageInfo.total / Math.max(1, auditPageInfo.size)) || 1)));
const filteredHealthAlerts = computed(() => listFrom(health.value ?? {}, 'recentAlerts').filter((alert) => {
  const matchesStatus = !healthAlertStatusFilter.value || String(alert.status ?? 'OPEN').toUpperCase() === healthAlertStatusFilter.value;
  const matchesLevel = !healthAlertLevelFilter.value || String(alert.level ?? alert.alertLevel ?? '').toUpperCase() === healthAlertLevelFilter.value;
  return matchesStatus && matchesLevel;
}));
const activeFormTitle = computed(() => formMeta(activeForm.value).title);
const activeFormDescription = computed(() => formMeta(activeForm.value).description);
const activeFormFields = computed(() => formMeta(activeForm.value).fields);
const quickSearchVariables = QUICK_SEARCH_TEMPLATE_VARIABLES.map((variable) => ({
  label: variable.label,
  value: variable.placeholder
}));
const auditActionGroups = computed(() => {
  const groups = new Map<string, AnyRecord[]>();
  auditActions.value.forEach((action) => {
    const group = String(action.group || '其他操作');
    groups.set(group, [...(groups.get(group) ?? []), action]);
  });
  return [...groups.entries()].map(([group, actions]) => ({ group, actions }));
});
const datasourceColumnStatusLabel = computed(() => {
  if (!datasourceColumnStatus.value) return '尚未识别';
  const status = String(datasourceColumnStatus.value.fetchStatus ?? '').toUpperCase();
  if (status === 'OK') return datasourceColumnStatus.value.fallback ? '已从现有映射补全' : '已获取真实表格列名';
  if (status === 'UNAVAILABLE') return '真实表格暂不可用';
  return '等待识别';
});
const datasourceColumnStatusDetail = computed(() => {
  const status = datasourceColumnStatus.value;
  if (!status) return '点击“识别列名”后会显示取样来源和失败原因。';
  if (status.fetchError) return `取样失败：${status.fetchError}。系统已保留现有映射列名作为兜底。`;
  if (status.fallback) return `当前列名来自 ${status.source || '现有映射'}，未能直接从企微表格取样。`;
  return `来源：${status.source || 'SHEET_SAMPLE'}，共识别 ${datasourceColumns.value.length} 个列名。`;
});
const mappingDiffGroups = computed(() => {
  const diff = (mappingCompare.value?.diff ?? {}) as AnyRecord;
  return [
    { key: 'added', label: '新增映射', items: listFrom(diff, 'added') },
    { key: 'removed', label: '移除映射', items: listFrom(diff, 'removed') },
    { key: 'changed', label: '变更映射', items: listFrom(diff, 'changed') },
    { key: 'unchanged', label: '未变化', items: listFrom(diff, 'unchanged') }
  ];
});
const csvImportErrors = computed(() => listFrom(csvImportResult.value ?? {}, 'errors'));
const csvImportSummary = computed(() => {
  const result = csvImportResult.value;
  if (!result) return '尚未导入';
  return `总行数 ${result.totalRows ?? 0}，新增 ${result.created ?? 0}，更新 ${result.updated ?? 0}，跳过 ${result.skipped ?? 0}`;
});

onMounted(() => {
  disposeTagRefresh = eventBus.on<{ configKey?: string; configKeys?: string[] }>('CONFIG_REFRESH', (payload) => {
    const keys = [payload?.configKey, ...(payload?.configKeys ?? [])].filter(Boolean);
    if (activeSectionKey.value === 'customer-tags' && keys.includes('tag_config')) {
      void loadOrgRulesTags();
    }
  });
  if (tagManagementOnly.value) {
    void refreshActiveSection();
    return;
  }
  void loadRuntimeModeStatus();
  void refreshActiveSection();
  scheduleHealthRefresh();
  scheduleAnalyticsRefresh();
});

onBeforeUnmount(() => {
  disposeTagRefresh?.();
  disposeTagRefresh = null;
  stopHealthRefresh();
  stopAnalyticsRefresh();
  stopAuditExportPolling();
});

function selectSection(section: SectionKey) {
  if (tagManagementOnly.value && section !== 'customer-tags') return;
  activeSectionKey.value = section;
  void refreshActiveSection();
}

async function refreshActiveSection() {
  if (activeSection.value.groupKey === 'config-center') await loadSkillAi();
  if (activeSection.value.groupKey === 'data-content') await loadDataContent();
  if (activeSection.value.groupKey === 'org-rules-tags') await loadOrgRulesTags();
  if (activeSection.value.groupKey === 'insight-ops') await loadInsightOps();
}

function startPrimaryAction() {
  if (activeSectionKey.value === 'skill-scenes') openForm('skill');
  if (activeSectionKey.value === 'configuration-center') openForm('skillEnv');
  if (activeSectionKey.value === 'data-integration') openForm('datasource');
  if (activeSectionKey.value === 'quick-search-content') openForm('quickSearch');
  if (activeSectionKey.value === 'account-permissions') openForm('account');
  if (activeSectionKey.value === 'followup-rules') openForm('rule');
  if (activeSectionKey.value === 'customer-tags') openForm(tagView.value === 'categories' ? 'tagCategory' : 'tagValue');
  if (activeSectionKey.value === 'analytics-dashboard') downloadAnalyticsCsv();
  if (activeSectionKey.value === 'version-management') openForm('version');
  if (activeSectionKey.value === 'system-notices') openForm('notice');
  if (activeSectionKey.value === 'audit-logs') void exportAuditLogs();
  if (activeSectionKey.value === 'system-health') void loadHealth(true);
}

async function loadSkillAi() {
  await runWithNotice(async () => {
    const [skillList, availableSkillList, skillCallAnalytics, skillEnvList, imageEnvList, llmEnvList, llmRouteList, llmSceneList, llmCallAnalytics, configList] = await Promise.all([
      getJson<unknown>(withQuery('/admin/api/v1/skills', { scene: skillSceneFilter.value, leadType: skillLeadTypeFilter.value })),
      getJson<unknown>('/admin/api/v1/skills/available'),
      getJson<unknown>(withQuery('/admin/api/v1/analytics/skill-calls', { days: skillAnalyticsDays.value, scene: skillSceneFilter.value, leadType: skillLeadTypeFilter.value })),
      getJson<unknown>('/admin/api/v1/skill-environments'),
      getJson<unknown>('/admin/api/v1/image-environments'),
      getJson<unknown>('/admin/api/v1/llm-environments'),
      getJson<unknown>('/admin/api/v1/llm-routes'),
      getJson<unknown>('/admin/api/v1/llm-routes/scenes'),
      getJson<unknown>(withQuery('/admin/api/v1/analytics/llm-calls', { days: llmAnalyticsDays.value })),
      getJson<unknown>('/admin/api/v1/configs')
    ]);
    skillBindings.value = listFromResponse(skillList);
    availableSkills.value = listFromResponse(availableSkillList);
    skillAnalytics.value = recordFromResponse(skillCallAnalytics);
    skillEnvironments.value = listFromResponse(skillEnvList);
    imageEnvironments.value = listFromResponse(imageEnvList);
    llmEnvironments.value = listFromResponse(llmEnvList);
    llmRoutes.value = listFromResponse(llmRouteList);
    llmRouteScenes.value = listFromResponse(llmSceneList).map((item) => String(item.value ?? item.name ?? item.scene ?? item)).filter(Boolean);
    llmAnalytics.value = recordFromResponse(llmCallAnalytics);
    configs.value = configEntries(configList);
    hydratePromptDraft();
  }, '配置中心已刷新');
}

async function loadSkillAnalytics() {
  await runWithNotice(async () => {
    skillAnalytics.value = recordFromResponse(await getJson<unknown>(withQuery('/admin/api/v1/analytics/skill-calls', {
      days: skillAnalyticsDays.value,
      scene: skillSceneFilter.value,
      leadType: skillLeadTypeFilter.value
    })));
  }, 'Skill 调用监控已刷新');
}

async function loadLlmAnalytics() {
  await runWithNotice(async () => {
    llmAnalytics.value = recordFromResponse(await getJson<unknown>(withQuery('/admin/api/v1/analytics/llm-calls', {
      days: llmAnalyticsDays.value
    })));
  }, 'LLM 调用统计已刷新');
}

async function loadDataContent() {
  await runWithNotice(async () => {
    const [dsList, fieldList, syncList, importList, customerList, quickList] = await Promise.all([
      getJson<unknown>('/admin/api/v1/datasources'),
      getJson<unknown>('/admin/api/v1/customer-fields'),
      getJson<unknown>('/admin/api/v1/datasources/sync-status'),
      getJson<unknown>('/admin/api/v1/datasources/import-logs'),
      getJson<unknown>(customerSearchListPath()),
      getJson<unknown>(quickSearchListPath())
    ]);
    datasources.value = listFromResponse(dsList);
    customerFields.value = normalizeCustomerFields(fieldList);
    syncStatuses.value = listFromResponse(syncList);
    importLogs.value = listFromResponse(importList);
    applyCustomerSearchList(customerList);
    applyQuickSearchList(quickList);
    if (!selectedDatasource.value && datasources.value.length) {
      selectDatasource(datasources.value[0]);
    }
  }, '数据与内容已刷新');
}

async function loadOrgRulesTags() {
  await runWithNotice(async () => {
    if (activeSectionKey.value === 'account-permissions') {
      applyAccountList(await getJson<unknown>(accountListPath()));
      await loadLeaderAccounts();
      return;
    }
    if (activeSectionKey.value === 'followup-rules') {
      applyRuleList(await getJson<unknown>(ruleListPath()));
      return;
    }
    await loadTags();
  }, '组织与规则已刷新');
}

async function loadTags() {
  const [categoryList, valueList] = await Promise.all([
    getJson<unknown>(tagCategoryListPath()),
    getJson<unknown>(tagValueListPath())
  ]);
  applyTagCategoryList(categoryList);
  applyTagValueList(valueList);
  await refreshTagCategoryOptionsCache();
}

async function loadInsightOps() {
  await runWithNotice(async () => {
    const [accountList, versionList, noticeList, auditList, actionList, healthPayload] = await Promise.all([
      getJson<unknown>(withQuery('/admin/api/v1/accounts', { page: 1, page_size: 50 })),
      getJson<unknown>(versionListPath()),
      getJson<unknown>(noticeListPath()),
      getJson<unknown>(auditLogPath()),
      getJson<unknown>('/admin/api/v1/audit-logs/actions'),
      getJson<unknown>('/admin/api/v1/health')
    ]);
    analyticsAccounts.value = listFromResponse(accountList);
    applyVersionList(versionList);
    applyNoticeList(noticeList);
    applyAuditList(auditList);
    applyAuditDictionary(actionList);
    health.value = dataFromResponse(healthPayload) as AnyRecord;
    applyHealthMetadata();
    healthLastRefreshAt.value = formatDate((health.value as AnyRecord)?.timestamp ?? new Date().toISOString());
    healthConsecutiveFailures.value = 0;
    await loadAnalyticsDashboard({ silent: true });
  }, '分析与系统运营已刷新');
}

async function loadAnalyticsDashboard(options: { silent?: boolean } = {}) {
  const analyticsQuery = { days: skillAnalyticsDays.value, leadType: analyticsLeadType(), caller: analyticsCallerFilter.value };
  const contentRankingQuery = { days: skillAnalyticsDays.value, caller: analyticsCallerFilter.value };
  const endpoints: Array<{ key: AnalyticsKey; path: string; query: Record<string, unknown> }> = [
    { key: 'overview', path: '/admin/api/v1/analytics/overview', query: analyticsQuery },
    { key: 'funnels', path: '/admin/api/v1/analytics/funnels', query: analyticsQuery },
    { key: 'staff', path: '/admin/api/v1/analytics/staff', query: analyticsQuery },
    { key: 'sources', path: '/admin/api/v1/analytics/sources', query: analyticsQuery },
    { key: 'stages', path: '/admin/api/v1/analytics/stages', query: analyticsQuery },
    { key: 'health', path: '/admin/api/v1/analytics/health', query: analyticsQuery },
    { key: 'lifecycle', path: '/admin/api/v1/analytics/lifecycle', query: analyticsQuery },
    { key: 'risks', path: '/admin/api/v1/analytics/risks', query: analyticsQuery },
    { key: 'contentRanking', path: '/admin/api/v1/analytics/content-ranking', query: contentRankingQuery }
  ];
  const run = async () => {
    endpoints.forEach(({ key }) => {
      analyticsStatus[key].loading = true;
      analyticsStatus[key].error = '';
    });
    const results = await Promise.allSettled(endpoints.map(({ path, query }) => getJson<unknown>(withQuery(path, query))));
    results.forEach((result, index) => {
      const key = endpoints[index].key;
      analyticsStatus[key].loading = false;
      if (result.status === 'fulfilled') {
        analytics[key] = recordFromResponse(result.value);
      } else {
        analyticsStatus[key].error = errorText(result.reason);
      }
    });
  };
  if (options.silent) {
    await run();
    return;
  }
  await runWithNotice(run, analyticsFailedSections.value.length ? '分析看板已部分刷新' : '分析看板已刷新');
}

async function loadRuntimeModeStatus() {
  try {
    health.value = dataFromResponse(await getJson<unknown>('/admin/api/v1/health')) as AnyRecord;
    applyHealthMetadata();
    healthLastRefreshAt.value = formatDate((health.value as AnyRecord)?.timestamp ?? new Date().toISOString());
    healthConsecutiveFailures.value = 0;
  } catch {
    // The active page refresh will surface health errors when the user enters system monitoring.
  }
}

async function loadHealth(force = false) {
  if (!force && healthConsecutiveFailures.value >= 3) {
    noticeKind.value = 'error';
    notice.value = '健康监控连续刷新失败，已暂停自动刷新，请手动重试。';
    return;
  }
  if (force) healthConsecutiveFailures.value = 0;
  await runWithNotice(async () => {
    try {
      health.value = dataFromResponse(await getJson<unknown>('/admin/api/v1/health')) as AnyRecord;
      applyHealthMetadata();
      healthLastRefreshAt.value = formatDate((health.value as AnyRecord)?.timestamp ?? new Date().toISOString());
      healthConsecutiveFailures.value = 0;
    } catch (error) {
      healthConsecutiveFailures.value += 1;
      throw error;
    }
  }, '健康状态已刷新');
}

async function loadAuditLogs() {
  await runWithNotice(async () => {
    applyAuditList(await getJson<unknown>(auditLogPath()));
  }, '审计日志已刷新');
}

async function resetAuditPageAndLoad() {
  auditPageInfo.page = 1;
  await loadAuditLogs();
}

function clearAuditActions() {
  if (!auditActionsSelected.value.length) return;
  auditActionsSelected.value = [];
  void resetAuditPageAndLoad();
}

async function changeAuditPage(delta: number) {
  const nextPage = Math.min(auditTotalPages.value, Math.max(1, auditPageInfo.page + delta));
  if (nextPage === auditPageInfo.page) return;
  auditPageInfo.page = nextPage;
  await loadAuditLogs();
}

function applyAuditList(response: ApiResponse<unknown>) {
  const data = recordFromResponse(response);
  auditLogs.value = listFrom(data);
  auditPageInfo.total = Number(data.total ?? auditLogs.value.length);
  auditPageInfo.page = Number(data.page ?? auditPageInfo.page ?? 1);
  auditPageInfo.size = Number(data.size ?? data.pageSize ?? auditPageInfo.size ?? 20);
  auditPageInfo.totalPages = Number(data.totalPages ?? (Math.ceil(auditPageInfo.total / Math.max(1, auditPageInfo.size)) || 1));
  auditPageInfo.retentionDays = Number(data.retentionDays ?? 0);
  auditPageInfo.earliestCreatedAt = String(data.earliestCreatedAt ?? '');
}

async function loadLeaderAccounts() {
  const response = await getJson<unknown>(withQuery('/admin/api/v1/accounts', {
    role: 'LEADER',
    is_enabled: 1,
    page: 1,
    page_size: 50
  }));
  leaderAccounts.value = listFromResponse(response);
}

async function resetAccountPageAndLoad() {
  accountPageInfo.page = 1;
  await loadOrgRulesTags();
}

async function resetCustomerSearchPageAndLoad() {
  customerSearchPageInfo.page = 1;
  selectedAdminCustomer.value = null;
  await loadDataContent();
}

async function changeCustomerSearchPage(delta: number) {
  const nextPage = Math.min(customerSearchTotalPages.value, Math.max(1, customerSearchPageInfo.page + delta));
  if (nextPage === customerSearchPageInfo.page) return;
  customerSearchPageInfo.page = nextPage;
  selectedAdminCustomer.value = null;
  await loadDataContent();
}

function toggleAdminCustomerDetail(customer: AnyRecord) {
  selectedAdminCustomer.value = selectedAdminCustomer.value?.phone === customer.phone ? null : customer;
}

async function changeAccountPage(delta: number) {
  const nextPage = Math.min(accountTotalPages.value, Math.max(1, accountPageInfo.page + delta));
  if (nextPage === accountPageInfo.page) return;
  accountPageInfo.page = nextPage;
  await loadOrgRulesTags();
}

async function resetRulePageAndLoad() {
  rulePageInfo.page = 1;
  await loadOrgRulesTags();
}

async function changeRulePage(delta: number) {
  const nextPage = Math.min(ruleTotalPages.value, Math.max(1, rulePageInfo.page + delta));
  if (nextPage === rulePageInfo.page) return;
  rulePageInfo.page = nextPage;
  await loadOrgRulesTags();
}

async function resetTagCategoryPageAndLoad() {
  tagCategoryPageInfo.page = 1;
  await loadOrgRulesTags();
}

async function changeTagCategoryPage(delta: number) {
  const nextPage = Math.min(tagCategoryTotalPages.value, Math.max(1, tagCategoryPageInfo.page + delta));
  if (nextPage === tagCategoryPageInfo.page) return;
  tagCategoryPageInfo.page = nextPage;
  await loadOrgRulesTags();
}

async function resetTagValuePageAndLoad() {
  tagValuePageInfo.page = 1;
  await loadOrgRulesTags();
}

async function changeTagValuePage(delta: number) {
  const nextPage = Math.min(tagValueTotalPages.value, Math.max(1, tagValuePageInfo.page + delta));
  if (nextPage === tagValuePageInfo.page) return;
  tagValuePageInfo.page = nextPage;
  await loadOrgRulesTags();
}

async function switchTagView(view: 'categories' | 'values') {
  tagView.value = view;
  await loadOrgRulesTags();
}

async function resetQuickSearchPageAndLoad() {
  quickSearchPageInfo.page = 1;
  await loadDataContent();
}

async function changeQuickSearchPage(delta: number) {
  const nextPage = Math.min(quickSearchTotalPages.value, Math.max(1, quickSearchPageInfo.page + delta));
  if (nextPage === quickSearchPageInfo.page) return;
  quickSearchPageInfo.page = nextPage;
  await loadDataContent();
}

async function resetVersionPageAndLoad() {
  versionPageInfo.page = 1;
  await loadInsightOps();
}

async function changeVersionPage(delta: number) {
  const nextPage = Math.min(versionTotalPages.value, Math.max(1, versionPageInfo.page + delta));
  if (nextPage === versionPageInfo.page) return;
  versionPageInfo.page = nextPage;
  await loadInsightOps();
}

async function resetNoticePageAndLoad() {
  noticePageInfo.page = 1;
  await loadInsightOps();
}

async function changeNoticePage(delta: number) {
  const nextPage = Math.min(noticeTotalPages.value, Math.max(1, noticePageInfo.page + delta));
  if (nextPage === noticePageInfo.page) return;
  noticePageInfo.page = nextPage;
  await loadInsightOps();
}

function applyAccountList(response: ApiResponse<unknown>) {
  const data = recordFromResponse(response);
  accounts.value = listFrom(data);
  accountPageInfo.total = Number(data.total ?? accounts.value.length);
  accountPageInfo.page = Number(data.page ?? accountPageInfo.page ?? 1);
  accountPageInfo.size = Number(data.pageSize ?? data.size ?? accountPageInfo.size ?? 20);
  accountPageInfo.totalPages = Number(data.totalPages ?? (Math.ceil(accountPageInfo.total / Math.max(1, accountPageInfo.size)) || 1));
}

function applyCustomerSearchList(response: ApiResponse<unknown>) {
  const data = recordFromResponse(response);
  customerSearchItems.value = listFrom(data);
  customerSearchPageInfo.total = Number(data.total ?? customerSearchItems.value.length);
  customerSearchPageInfo.page = Number(data.page ?? customerSearchPageInfo.page ?? 1);
  customerSearchPageInfo.size = Number(data.size ?? data.pageSize ?? customerSearchPageInfo.size ?? 20);
  customerSearchPageInfo.totalPages = Number(data.totalPages ?? (Math.ceil(customerSearchPageInfo.total / Math.max(1, customerSearchPageInfo.size)) || 1));
}

function applyRuleList(response: ApiResponse<unknown>) {
  const data = recordFromResponse(response);
  rules.value = listFrom(data);
  ruleSelectedIds.value = ruleSelectedIds.value.filter((id) => rules.value.some((rule) => rule.id === id));
  rulePageInfo.total = Number(data.total ?? rules.value.length);
  rulePageInfo.page = Number(data.page ?? rulePageInfo.page ?? 1);
  rulePageInfo.size = Number(data.size ?? data.pageSize ?? rulePageInfo.size ?? 20);
  rulePageInfo.totalPages = Number(data.totalPages ?? (Math.ceil(rulePageInfo.total / Math.max(1, rulePageInfo.size)) || 1));
}

function applyTagCategoryList(response: ApiResponse<unknown>) {
  const data = recordFromResponse(response);
  tagCategories.value = listFrom(data);
  tagCategoryPageInfo.total = Number(data.total ?? tagCategories.value.length);
  tagCategoryPageInfo.page = Number(data.page ?? tagCategoryPageInfo.page ?? 1);
  tagCategoryPageInfo.size = Number(data.size ?? tagCategoryPageInfo.size ?? 20);
  tagCategoryPageInfo.totalPages = Number(data.totalPages ?? (Math.ceil(tagCategoryPageInfo.total / Math.max(1, tagCategoryPageInfo.size)) || 1));
}

function applyTagValueList(response: ApiResponse<unknown>) {
  const data = recordFromResponse(response);
  tagValues.value = listFrom(data);
  tagValuePageInfo.total = Number(data.total ?? tagValues.value.length);
  tagValuePageInfo.page = Number(data.page ?? tagValuePageInfo.page ?? 1);
  tagValuePageInfo.size = Number(data.size ?? tagValuePageInfo.size ?? 20);
  tagValuePageInfo.totalPages = Number(data.totalPages ?? (Math.ceil(tagValuePageInfo.total / Math.max(1, tagValuePageInfo.size)) || 1));
}

function applyQuickSearchList(response: ApiResponse<unknown>) {
  const data = recordFromResponse(response);
  quickSearchItems.value = listFrom(data);
  quickSearchSelectedIds.value = quickSearchSelectedIds.value.filter((id) => quickSearchItems.value.some((item) => item.id === id));
  quickSearchPageInfo.total = Number(data.total ?? quickSearchItems.value.length);
  quickSearchPageInfo.page = Number(data.page ?? quickSearchPageInfo.page ?? 1);
  quickSearchPageInfo.size = Number(data.size ?? data.pageSize ?? quickSearchPageInfo.size ?? 20);
  quickSearchPageInfo.totalPages = Number(data.totalPages ?? (Math.ceil(quickSearchPageInfo.total / Math.max(1, quickSearchPageInfo.size)) || 1));
}

function applyVersionList(response: ApiResponse<unknown>) {
  const data = recordFromResponse(response);
  versions.value = listFrom(data);
  versionPageInfo.total = Number(data.total ?? versions.value.length);
  versionPageInfo.page = Number(data.page ?? versionPageInfo.page ?? 1);
  versionPageInfo.size = Number(data.size ?? data.pageSize ?? versionPageInfo.size ?? 20);
  versionPageInfo.totalPages = Number(data.totalPages ?? (Math.ceil(versionPageInfo.total / Math.max(1, versionPageInfo.size)) || 1));
}

function applyNoticeList(response: ApiResponse<unknown>) {
  const data = recordFromResponse(response);
  notices.value = listFrom(data);
  noticePageInfo.total = Number(data.total ?? notices.value.length);
  noticePageInfo.page = Number(data.page ?? noticePageInfo.page ?? 1);
  noticePageInfo.size = Number(data.size ?? data.pageSize ?? noticePageInfo.size ?? 20);
  noticePageInfo.totalPages = Number(data.totalPages ?? (Math.ceil(noticePageInfo.total / Math.max(1, noticePageInfo.size)) || 1));
}

function applyAuditDictionary(response: ApiResponse<unknown>) {
  const data = recordFromResponse(response);
  const actions = listFrom(data, 'actions');
  auditActions.value = actions.map((item) => typeof item === 'string' ? { action: item, label: item, group: '其他' } : {
    action: String(item.action ?? item.name ?? ''),
    label: String(item.label ?? item.action ?? item.name ?? ''),
    group: String(item.group ?? '其他')
  }).filter((item) => item.action);
  auditTargetTypes.value = listFrom(data, 'targetTypes').map((item) => typeof item === 'string' ? { type: item, label: item } : {
    type: String(item.type ?? item.value ?? ''),
    label: String(item.label ?? item.type ?? item.value ?? '')
  }).filter((item) => item.type);
}

function applyHealthMetadata() {
  const seconds = Number(health.value?.refreshIntervalS);
  if (Number.isFinite(seconds) && seconds >= 15 && seconds <= 120 && seconds !== healthRefreshIntervalS.value) {
    healthRefreshIntervalS.value = seconds;
    scheduleHealthRefresh();
  }
}

function scheduleHealthRefresh() {
  stopHealthRefresh();
  healthTimer = window.setInterval(() => {
    if (activeSectionKey.value === 'system-health' && !document.hidden) {
      void loadHealth();
    }
  }, healthRefreshIntervalS.value * 1000);
}

function stopHealthRefresh() {
  if (healthTimer !== null) {
    window.clearInterval(healthTimer);
    healthTimer = null;
  }
}

function scheduleAnalyticsRefresh() {
  stopAnalyticsRefresh();
  analyticsTimer = window.setInterval(() => {
    if (activeSectionKey.value === 'analytics-dashboard' && !document.hidden) {
      void loadAnalyticsDashboard({ silent: true });
    }
  }, 5 * 60 * 1000);
}

function stopAnalyticsRefresh() {
  if (analyticsTimer !== null) {
    window.clearInterval(analyticsTimer);
    analyticsTimer = null;
  }
}

function openForm(kind: FormKind, item?: AnyRecord) {
  if (kind === 'account' && !leaderAccounts.value.length) {
    void loadLeaderAccounts();
  }
  activeForm.value = kind;
  editingItem.value = item ?? null;
  formError.value = '';
  if (kind === 'version') {
    versionUploadState.kind = 'idle';
    versionUploadState.message = item?.downloadUrl ? '已加载当前版本安装包地址。' : '';
  }
  Object.keys(formDraft).forEach((key) => delete formDraft[key]);
  Object.assign(formDraft, initialDraft(kind, item));
}

function closeForm() {
  activeForm.value = null;
  editingItem.value = null;
  formError.value = '';
  versionUploadState.kind = 'idle';
  versionUploadState.message = '';
}

async function submitActiveForm() {
  if (!activeForm.value) return;
  const kind = activeForm.value;
  formError.value = '';
  try {
    await submitForm(kind);
    closeForm();
    await refreshActiveSection();
  } catch (error) {
    formError.value = humanizeError(error);
  }
}

function onFormFieldChange(key: string) {
  if (key === 'role' && formDraft.role === 'ADMIN') {
    formDraft.tagManagementPermission = true;
  }
}

async function submitForm(kind: FormKind) {
  loading.value = true;
  try {
    if (kind === 'skill') {
      const payload = pickDraft(['skillId', 'skillName', 'scene', 'leadType', 'priority']);
      if (editingItem.value?.id) await putJson(`/admin/api/v1/skills/${editingItem.value.id}`, payload);
      else await postJson('/admin/api/v1/skills', payload);
    } else if (kind === 'skillEnv') {
      await submitEnvironment('skill');
    } else if (kind === 'imageEnv') {
      await submitEnvironment('image');
    } else if (kind === 'llmEnv') {
      await submitEnvironment('llm');
    } else if (kind === 'llmRoute') {
      await submitLlmRoute();
    } else if (kind === 'datasource') {
      const payload = pickDraft(['name', 'sheetId', 'sourceTable', 'description']);
      if (editingItem.value?.id) await putJson(`/admin/api/v1/datasources/${editingItem.value.id}`, payload);
      else await postJson('/admin/api/v1/datasources', payload);
    } else if (kind === 'quickSearch') {
      const payload = pickDraft(['contentType', 'leadType', 'title', 'shortcutCode', 'content', 'imageUrl', 'sortOrder', 'enabled']);
      if (editingItem.value?.id) await putJson(`/admin/api/v1/quick-search/items/${editingItem.value.id}`, payload);
      else {
        await postJson('/admin/api/v1/quick-search/items', payload);
        quickSearchPageInfo.page = 1;
      }
    } else if (kind === 'account') {
      if (editingItem.value?.id) await putJson(`/admin/api/v1/accounts/${editingItem.value.id}`, accountUpdatePayload());
      else {
        await postJson('/admin/api/v1/accounts', accountCreatePayload());
        accountPageInfo.page = 1;
      }
    } else if (kind === 'rule') {
      const payload = buildRulePayload();
      if (editingItem.value?.id) await putJson(`/admin/api/v1/rules/${editingItem.value.id}`, payload);
      else await postJson('/admin/api/v1/rules', payload);
    } else if (kind === 'tagCategory') {
      const payload = tagCategoryPayload();
      if (editingItem.value?.id) await putJson(`/admin/api/v1/tags/categories/${editingItem.value.id}`, payload);
      else {
        await postJson('/admin/api/v1/tags/categories', payload);
        tagCategoryPageInfo.page = 1;
      }
    } else if (kind === 'tagValue') {
      const payload = tagValueFormPayload();
      if (editingItem.value?.id) await putJson(`/admin/api/v1/tags/values/${editingItem.value.id}`, payload);
      else {
        await postJson('/admin/api/v1/tags/values', payload);
        tagValuePageInfo.page = 1;
      }
    } else if (kind === 'version') {
      const payload = pickDraft(['version', 'platform', 'downloadUrl', 'changelog', 'updateStrategy', 'gradualPercent', 'fileSize']);
      if (editingItem.value?.id) await putJson(`/admin/api/v1/versions/${editingItem.value.id}`, payload);
      else {
        await postJson('/admin/api/v1/versions', payload);
        versionPageInfo.page = 1;
      }
    } else if (kind === 'revokeVersion') {
      if (!editingItem.value?.id) throw new Error('请选择要撤回的版本');
      await putJson(`/admin/api/v1/versions/${editingItem.value.id}/revoke`, pickDraft(['reason', 'alternativeVersion']));
      if (versionStatusFilter.value === 'PUBLISHED' && versions.value.length <= 1 && versionPageInfo.page > 1) {
        versionPageInfo.page -= 1;
      }
    } else if (kind === 'notice') {
      if (editingItem.value?.id) await putJson(`/admin/api/v1/notices/${editingItem.value.id}`, noticeUpdatePayload());
      else {
        await postJson('/admin/api/v1/notices', noticeCreatePayload());
        noticePageInfo.page = 1;
      }
    }
    noticeKind.value = 'info';
    notice.value = '保存成功';
  } finally {
    loading.value = false;
  }
}

async function uploadVersionPackage(event: Event) {
  const input = event.target as HTMLInputElement;
  const file = input.files?.[0];
  input.value = '';
  if (!file) return;
  versionUploadState.kind = 'loading';
  versionUploadState.message = '正在上传安装包...';
  try {
    const body = new FormData();
    body.append('file', file);
    body.append('platform', String(formDraft.platform || 'WINDOWS'));
    const uploaded = recordFromResponse(await postForm<unknown>(
      '/admin/api/v1/versions/upload',
      body,
      Math.max(loadDesktopConfig().requestTotalTimeoutMs, 120000)
    ));
    if (!uploaded.downloadUrl) {
      throw new Error('安装包上传成功但后端未返回下载地址');
    }
    formDraft.downloadUrl = uploaded.downloadUrl;
    formDraft.fileSize = uploaded.fileSize ?? file.size;
    versionUploadState.kind = 'success';
    versionUploadState.message = `上传完成：${formatFileSize(formDraft.fileSize)}`;
  } catch (error) {
    versionUploadState.kind = 'error';
    versionUploadState.message = humanizeError(error);
  }
}

async function submitEnvironment(kind: 'skill' | 'image' | 'llm') {
  const prefix = environmentPrefix(kind);
  const payload = kind === 'llm'
    ? pickDraft(['envName', 'baseUrl', 'apiKey', 'model', 'protocol', 'timeoutMs', 'temperature', 'maxTokens'])
    : pickDraft(['envName', 'baseUrl', 'apiKey']);
  if (editingItem.value?.id) await putJson(`/admin/api/v1/${prefix}/${editingItem.value.id}`, payload);
  else await postJson(`/admin/api/v1/${prefix}`, payload);
}

async function submitLlmRoute() {
  const payload = {
    scene: formDraft.scene,
    leadType: formDraft.leadType ?? '',
    environmentId: Number(formDraft.environmentId),
    priority: Number(formDraft.priority ?? 0),
    enabled: formDraft.enabled !== false
  };
  if (editingItem.value?.id) await putJson(`/admin/api/v1/llm-routes/${editingItem.value.id}`, payload);
  else await postJson('/admin/api/v1/llm-routes', payload);
}

function pickDraft(keys: string[]) {
  return keys.reduce<AnyRecord>((payload, key) => {
    if (formDraft[key] !== undefined && formDraft[key] !== '') payload[key] = formDraft[key];
    return payload;
  }, {});
}

function accountCreatePayload() {
  return normalizeAccountPayload(pickDraft(['phone', 'password', 'displayName', 'role', 'leaderId', 'tagManagementPermission']));
}

function accountUpdatePayload() {
  return normalizeAccountPayload(pickDraft(['displayName', 'role', 'leaderId', 'isEnabled', 'tagManagementPermission']));
}

function normalizeAccountPayload(payload: AnyRecord) {
  const next = { ...payload };
  next.permissions = next.role === 'ADMIN' || next.tagManagementPermission === true ? ['TAG_MANAGEMENT'] : [];
  delete next.tagManagementPermission;
  if (next.role !== 'KEEPER') {
    next.leaderId = null;
  } else if (next.leaderId !== undefined && next.leaderId !== null && next.leaderId !== '') {
    next.leaderId = Number(next.leaderId);
  } else {
    throw new Error('管家账号必须选择直属组长');
  }
  if (next.isEnabled !== undefined) {
    next.isEnabled = next.isEnabled !== false;
  }
  return next;
}

function tagCategoryPayload() {
  const payload = pickDraft([
    'categoryName',
    'purpose',
    'selectionMode',
    'systemInferenceEnabled',
    'manualEditEnabled',
    'autoUpdateMode',
    'minConfidence',
    'minEvidenceMessages',
    'cooldownHours',
    'uncertainPolicy',
    'useForReply',
    'useForFilter',
    'useForStatistics',
    'useForFollowupRules',
    'isEnabled',
    'sortOrder',
    'version'
  ]);
  if (formDraft.purpose !== undefined) payload.purpose = formDraft.purpose;
  return payload;
}

function tagValueFormPayload() {
  const payload = pickDraft([
    'categoryId',
    'displayName',
    'meaning',
    'applicableWhen',
    'notApplicableWhen',
    'positiveExamples',
    'negativeExamples',
    'systemSelectable',
    'manualSelectable',
    'isEnabled',
    'sortOrder',
    'version'
  ]);
  for (const key of ['meaning', 'applicableWhen', 'notApplicableWhen', 'positiveExamples', 'negativeExamples']) {
    if (formDraft[key] !== undefined) payload[key] = formDraft[key];
  }
  payload.categoryId = Number(payload.categoryId);
  payload.synonyms = linesFrom(String(formDraft.synonymsText ?? '')).map((value) => value.replace(/，/g, ',')).flatMap((value) => value.split(',')).map((value) => value.trim()).filter(Boolean);
  return payload;
}

function environmentPrefix(kind: 'skill' | 'image' | 'llm') {
  if (kind === 'skill') return 'skill-environments';
  if (kind === 'image') return 'image-environments';
  return 'llm-environments';
}

function noticeCreatePayload() {
  return pickDraft(['title', 'content', 'level', 'publishType', 'publishAt', 'expireDays']);
}

function noticeUpdatePayload() {
  return pickDraft(['title', 'content', 'level', 'publishAt', 'expireDays']);
}

function initialDraft(kind: FormKind, item?: AnyRecord): AnyRecord {
  const suffix = Date.now().toString().slice(-6);
  if (kind === 'skill') return { skillId: item?.skillId ?? '', skillName: item?.skillName ?? '', scene: item?.scene ?? 'OPENING', leadType: item?.leadType ?? 'GENERAL', priority: item?.priority ?? 90 };
  if (kind === 'skillEnv' || kind === 'imageEnv') return { envName: item?.envName ?? '', baseUrl: item?.baseUrl ?? '', apiKey: '' };
  if (kind === 'llmEnv') return {
    envName: item?.envName ?? '',
    baseUrl: item?.baseUrl ?? '',
    apiKey: '',
    model: item?.model ?? '',
    protocol: item?.protocol ?? 'OPENAI_COMPATIBLE',
    timeoutMs: item?.timeoutMs ?? 10000,
    temperature: item?.temperature ?? 0.2,
    maxTokens: item?.maxTokens ?? 1024
  };
  if (kind === 'llmRoute') return {
    scene: item?.scene ?? (llmRouteScenes.value[0] ?? 'REPLY_GENERATION'),
    leadType: item?.leadType ?? '',
    environmentId: item?.environmentId ?? llmEnvironments.value[0]?.id ?? '',
    priority: item?.priority ?? 0,
    enabled: item?.enabled ?? true
  };
  if (kind === 'datasource') return { name: item?.name ?? '', sheetId: item?.sheetId ?? '', sourceTable: item?.sourceTable ?? '', description: item?.description ?? '' };
  if (kind === 'quickSearch') return { contentType: item?.contentType ?? 'TEMPLATE', leadType: item?.leadType ?? 'GENERAL', title: item?.title ?? '', shortcutCode: item?.shortcutCode ?? '', content: item?.content ?? '', imageUrl: item?.imageUrl ?? '', sortOrder: item?.sortOrder ?? 99, enabled: item?.enabled ?? true };
  if (kind === 'account') return {
    phone: item?.phone ?? item?.username ?? '',
    password: '',
    displayName: item?.displayName ?? '',
    role: item?.role ?? 'KEEPER',
    leaderId: item?.leaderId ?? '',
    isEnabled: item?.isEnabled ?? true,
    tagManagementPermission: item?.role === 'ADMIN' || Array.isArray(item?.permissions) && item.permissions.includes('TAG_MANAGEMENT')
  };
  if (kind === 'rule') return ruleDraft(item);
  if (kind === 'tagCategory') return {
    categoryKey: item?.categoryKey ?? '',
    categoryName: item?.categoryName ?? '',
    purpose: item?.purpose ?? '',
    boundField: item?.boundField ?? '',
    selectionMode: item?.selectionMode ?? 'SINGLE',
    systemInferenceEnabled: item?.systemInferenceEnabled ?? false,
    manualEditEnabled: item?.manualEditEnabled ?? true,
    autoUpdateMode: item?.autoUpdateMode ?? 'RECORD_ONLY',
    minConfidence: item?.minConfidence ?? 0.85,
    minEvidenceMessages: item?.minEvidenceMessages ?? 1,
    cooldownHours: item?.cooldownHours ?? 0,
    uncertainPolicy: item?.uncertainPolicy ?? 'KEEP_CURRENT',
    useForReply: item?.useForReply ?? true,
    useForFilter: item?.useForFilter ?? true,
    useForStatistics: item?.useForStatistics ?? true,
    useForFollowupRules: item?.useForFollowupRules ?? true,
    isEnabled: item?.isEnabled ?? true,
    sortOrder: item?.sortOrder ?? 99,
    version: item?.version
  };
  if (kind === 'tagValue') return {
    categoryId: item?.categoryId ?? tagCategoryOptionsCache.value[0]?.id ?? '',
    tagValue: item?.tagValue ?? '',
    displayName: item?.displayName ?? '',
    meaning: item?.meaning ?? '',
    applicableWhen: item?.applicableWhen ?? '',
    notApplicableWhen: item?.notApplicableWhen ?? '',
    positiveExamples: item?.positiveExamples ?? '',
    negativeExamples: item?.negativeExamples ?? '',
    synonymsText: tagSynonymsText(item?.synonyms, ''),
    systemSelectable: item?.systemSelectable ?? true,
    manualSelectable: item?.manualSelectable ?? true,
    sortOrder: item?.sortOrder ?? 99,
    isEnabled: item?.isEnabled ?? true,
    version: item?.version
  };
  if (kind === 'version') return { version: item?.version ?? `1.0.${suffix}`, platform: item?.platform ?? 'WINDOWS', downloadUrl: item?.downloadUrl ?? '', changelog: item?.changelog ?? '', updateStrategy: item?.updateStrategy ?? 'OPTIONAL', gradualPercent: item?.gradualPercent ?? null, fileSize: item?.fileSize ?? 0 };
  if (kind === 'revokeVersion') return { reason: '', alternativeVersion: '' };
  if (kind === 'notice') return { title: item?.title ?? '', content: item?.content ?? '', level: item?.level ?? 'INFO', publishType: item?.publishType ?? 'IMMEDIATE', publishAt: item?.publishAt ?? '', expireDays: item?.expireDays ?? 1 };
  return {};
}

function quickSearchContentMeta() {
  const type = String(formDraft.contentType || 'TEMPLATE');
  return QUICK_SEARCH_CONTENT_META[type] ?? QUICK_SEARCH_CONTENT_META.TEMPLATE;
}

function insertQuickSearchVariable(variable: string) {
  const current = String(formDraft.content ?? '');
  const spacer = !current || current.endsWith(' ') || current.endsWith('\n') ? '' : ' ';
  formDraft.content = `${current}${spacer}${variable}`;
}

function formMeta(kind: FormKind | null): { title: string; description: string; fields: FormField[] } {
  const commonOptions = {
    scene: SKILL_SCENE_OPTIONS,
    leadType: LEAD_TYPE_OPTIONS
  };
  if (kind === 'skill') return { title: 'Skill 场景绑定', description: '选择场景、线索类型和技能，保存后立即用于对应业务场景。', fields: [
    { key: 'scene', label: '场景', type: 'select', options: commonOptions.scene },
    { key: 'leadType', label: '线索类型', type: 'select', options: commonOptions.leadType, help: '选“全部客资”时作为兜底绑定；具体线索类型优先于全部客资。' },
    availableSkills.value.length
      ? { key: 'skillId', label: '技能', type: 'select', options: availableSkills.value.map((skill) => ({ label: skill.skillName || skill.skillId, value: skill.skillId })) }
      : { key: 'skillId', label: '技能标识', type: 'text', placeholder: '选择或填写已登记的技能标识' },
    { key: 'skillName', label: '显示名称', type: 'text' },
    { key: 'priority', label: '优先级（数字越小越先尝试）', type: 'number', min: 0, max: 999, step: 1, help: '建议主用 10，备用 20，测试 90。相同场景和线索类型会先用数字最小的启用项。' }
  ] };
  if (kind === 'skillEnv' || kind === 'imageEnv') return { title: kind === 'skillEnv' ? 'Skill 环境' : '识图环境', description: 'API Key 保存后只展示脱敏信息。', fields: [
    { key: 'envName', label: '环境名称', type: 'text', placeholder: '生产环境 / 备份环境' },
    { key: 'baseUrl', label: '服务地址', type: 'text', placeholder: 'https://api.example.com' },
    { key: 'apiKey', label: 'API Key', type: 'password' }
  ] };
  if (kind === 'llmEnv') return { title: 'LLM 思考环境', description: '每个环境可绑定一个模型和一组推理参数，API Key 保存后只展示脱敏信息。', fields: [
    { key: 'envName', label: '环境名称', type: 'text', placeholder: 'OpenAI 主模型 / 本地模型网关' },
    { key: 'baseUrl', label: '服务地址', type: 'text', placeholder: 'https://api.example.com' },
    { key: 'apiKey', label: 'API Key', type: 'password' },
    { key: 'model', label: '模型名称', type: 'text', placeholder: 'gpt-4.1-mini / qwen-plus' },
    { key: 'protocol', label: '协议', type: 'select', options: [{ label: 'OpenAI Compatible', value: 'OPENAI_COMPATIBLE' }] },
    { key: 'timeoutMs', label: '超时（毫秒）', type: 'number', min: 1000, max: 60000, step: 1000 },
    { key: 'temperature', label: '温度', type: 'number', min: 0, max: 2, step: 0.1 },
    { key: 'maxTokens', label: '最大 Tokens', type: 'number', min: 1, max: 32000, step: 1 }
  ] };
  if (kind === 'llmRoute') return { title: 'LLM 场景路由', description: '为特定业务场景指定 LLM 环境；线索类型为空时作为通用兜底路由。', fields: [
    { key: 'scene', label: '场景', type: 'select', options: llmSceneOptions() },
    { key: 'leadType', label: '线索类型', type: 'select', options: [{ label: '通用', value: '' }, ...commonOptions.leadType.filter((option) => option.value !== 'GENERAL')] },
    { key: 'environmentId', label: 'LLM 环境', type: 'select', options: llmEnvironments.value.map((env) => ({ label: `${env.envName || `#${env.id}`} · ${env.model || '未填写模型'}`, value: env.id })) },
    { key: 'priority', label: '优先级（数字越小越先尝试）', type: 'number', min: 0, max: 999, step: 1, help: '同一场景可配置多个 LLM，数字小的先调用，失败后再尝试后面的路由。' },
    { key: 'enabled', label: '启用', type: 'checkbox' }
  ] };
  if (kind === 'datasource') return { title: '数据源', description: '配置企微智能表格来源。', fields: [
    { key: 'name', label: '数据源名称', type: 'text' },
    { key: 'sheetId', label: '企微表格标识', type: 'text' },
    { key: 'sourceTable', label: '来源表标识', type: 'text' },
    { key: 'description', label: '说明', type: 'textarea' }
  ] };
  if (kind === 'quickSearch') {
    const meta = quickSearchContentMeta();
    return { title: '速搜内容', description: meta.help, fields: [
    { key: 'contentType', label: '内容类型', type: 'select', options: [{ label: '话术模板', value: 'TEMPLATE' }, { label: '知识片段', value: 'KNOWLEDGE' }, { label: '门店定位', value: 'LOCATION' }, { label: '图片素材', value: 'IMAGE' }, { label: '小程序引导', value: 'MINI_PROGRAM' }] },
    { key: 'leadType', label: '线索类型', type: 'select', options: commonOptions.leadType },
    { key: 'title', label: '标题', type: 'text', placeholder: '如：产后修复开场白' },
    { key: 'shortcutCode', label: '快线码', type: 'text', placeholder: '2-20 位字母或数字，如 hi01' },
    { key: 'content', label: meta.contentLabel, type: 'textarea', placeholder: meta.contentPlaceholder, help: '变量会在侧边栏按客户档案替换，例如 {{客户昵称}}、{{意向门店}}。旧模板中的英文变量仍可继续使用。' },
    { key: 'imageUrl', label: meta.imageLabel, type: 'text', placeholder: meta.imagePlaceholder },
    { key: 'sortOrder', label: '排序（数字越小越靠前）', type: 'number', min: 0, max: 999, step: 1 },
    { key: 'enabled', label: '启用', type: 'checkbox' }
  ] };
  }
  if (kind === 'account') {
    const creating = !editingItem.value?.id;
    return { title: creating ? '新增账号' : '编辑账号', description: creating ? '手机号和初始密码只在创建时设置；后续改密码请使用列表里的重置密码。' : '手机号不可在编辑中修改；改密码请使用列表里的重置密码。', fields: [
    ...(creating ? [
      { key: 'phone', label: '手机号', type: 'text' as const },
      { key: 'password', label: '初始密码', type: 'password' as const }
    ] : []),
    { key: 'displayName', label: '姓名', type: 'text' },
    { key: 'role', label: '角色', type: 'select', options: [{ label: '管理员', value: 'ADMIN' }, { label: '组长', value: 'LEADER' }, { label: '管家', value: 'KEEPER' }] },
    { key: 'leaderId', label: '直属组长', type: 'select', options: leaderOptions(), disabled: formDraft.role !== 'KEEPER', help: formDraft.role === 'KEEPER' ? '管家必须绑定直属组长，用于客户数据隔离和组长通知。' : '只有管家角色需要选择直属组长。' },
    { key: 'tagManagementPermission', label: '客户标签管理权限', type: 'checkbox', disabled: formDraft.role === 'ADMIN', help: formDraft.role === 'ADMIN' ? '管理员默认拥有此权限。' : '允许该账号进入后台，但只能管理客户标签与分层。' },
    { key: 'isEnabled', label: '启用', type: 'checkbox' }
  ] };
  }
  if (kind === 'rule') return { title: '跟进规则', description: '用业务字段构建条件，系统自动生成规则配置。', fields: [
    { key: 'name', label: '规则名称', type: 'text' },
    { key: 'leadType', label: '线索类型', type: 'select', options: commonOptions.leadType, help: '选择全部客资时，不限制客户线索类型。' },
    { key: 'thresholdHours', label: '超过多少小时未跟进', type: 'number', min: 1, max: 720, step: 1 },
    { key: 'actionType', label: '动作', type: 'select', options: [{ label: '提醒/告警', value: 'ALERT' }, { label: '标签建议', value: 'TAG_CHANGE' }, { label: '通知组长', value: 'NOTIFY_LEADER' }] },
    { key: 'alertLevel', label: '提醒级别', type: 'select', options: [{ label: '普通', value: 'NORMAL' }, { label: '提醒', value: 'WARN' }, { label: '紧急', value: 'HIGH' }] },
    { key: 'reminderType', label: '提醒类型', type: 'select', options: [{ label: '逾期跟进', value: 'OVERDUE' }, { label: '标签建议', value: 'TAG_SUGGESTION' }] },
    { key: 'tagName', label: '建议标签', type: 'text', placeholder: '如：高意向待跟进' },
    { key: 'priority', label: '优先级（数字越大越先执行）', type: 'number', min: 1, max: 100, step: 1, help: '跟进规则当前按数字从大到小执行。建议紧急提醒 90，普通提醒 60，观察类 30。' },
    { key: 'enabled', label: '启用', type: 'checkbox' }
  ] };
  if (kind === 'tagCategory') return { title: editingItem.value?.id ? '编辑标签分类' : '新增标签分类', description: '系统编号由后端自动生成，分类配置保存后立即进入统一标签字典。', fields: [
    ...(editingItem.value?.id ? [{ key: 'categoryKey', label: '系统编号', type: 'text' as const, disabled: true, help: '系统编号只用于识别和兼容历史数据，不能人工修改。' }] : []),
    { key: 'categoryName', label: '分类名称', type: 'text', placeholder: '如：意向度 / 客户阶段 / 体型关注' },
    { key: 'purpose', label: '分类用途', type: 'textarea', placeholder: '说明该分类用于什么业务判断' },
    { key: 'selectionMode', label: '选择模式', type: 'select', options: [{ label: '单选', value: 'SINGLE' }, { label: '多选', value: 'MULTI' }] },
    { key: 'systemInferenceEnabled', label: '允许系统判断', type: 'checkbox' },
    { key: 'manualEditEnabled', label: '允许员工手动修改', type: 'checkbox' },
    { key: 'autoUpdateMode', label: '自动更新模式', type: 'select', options: [{ label: '只新增', value: 'ADD_ONLY' }, { label: '替换当前值', value: 'REPLACE' }, { label: '只记录建议', value: 'RECORD_ONLY' }] },
    { key: 'minConfidence', label: '最低把握程度', type: 'number', min: 0, max: 1, step: 0.01 },
    { key: 'minEvidenceMessages', label: '最低有效消息数', type: 'number', min: 0, max: 1000, step: 1 },
    { key: 'cooldownHours', label: '自动更新冷却时间（小时）', type: 'number', min: 0, max: 87600, step: 1 },
    { key: 'uncertainPolicy', label: '不确定时处理', type: 'select', options: [{ label: '保留当前值', value: 'KEEP_CURRENT' }, { label: '设为待确认', value: 'SET_PENDING' }] },
    { key: 'useForReply', label: '用于回复生成', type: 'checkbox' },
    { key: 'useForFilter', label: '用于客户筛选', type: 'checkbox' },
    { key: 'useForStatistics', label: '用于运营统计', type: 'checkbox' },
    { key: 'useForFollowupRules', label: '用于跟进规则', type: 'checkbox' },
    { key: 'sortOrder', label: '显示顺序', type: 'number', min: 0, max: 999999, step: 1 },
    { key: 'isEnabled', label: '启用', type: 'checkbox' }
  ] };
  if (kind === 'tagValue') return { title: editingItem.value?.id ? '编辑标签值' : '新增标签值', description: '系统编号由后端根据中文名称生成，运营人员只维护业务含义和使用边界。', fields: [
    { key: 'categoryId', label: '分类', type: 'select', options: tagCategoryOptions(), disabled: Boolean(editingItem.value?.id) },
    ...(editingItem.value?.id ? [{ key: 'tagValue', label: '系统编号', type: 'text' as const, disabled: true, help: '系统编号只读，用于规则和历史数据兼容。' }] : []),
    { key: 'displayName', label: '标签名称', type: 'text' },
    { key: 'meaning', label: '标签含义', type: 'textarea' },
    { key: 'applicableWhen', label: '适用条件', type: 'textarea' },
    { key: 'notApplicableWhen', label: '禁止条件', type: 'textarea' },
    { key: 'positiveExamples', label: '正确例子', type: 'textarea' },
    { key: 'negativeExamples', label: '错误例子', type: 'textarea' },
    { key: 'synonymsText', label: '同义表达', type: 'textarea', placeholder: '每行一个同义表达，最多 50 个' },
    { key: 'systemSelectable', label: '系统可选择', type: 'checkbox' },
    { key: 'manualSelectable', label: '员工可选择', type: 'checkbox' },
    { key: 'sortOrder', label: '显示顺序', type: 'number', min: 0, max: 999999, step: 1 },
    { key: 'isEnabled', label: '启用', type: 'checkbox' }
  ] };
  if (kind === 'version') return { title: '桌面版本', description: '发布前确认平台、策略和下载地址。', fields: [
    { key: 'version', label: '版本号', type: 'text' },
    { key: 'platform', label: '平台', type: 'select', options: [{ label: 'Windows', value: 'WINDOWS' }, { label: 'macOS', value: 'MAC' }] },
    { key: 'updateStrategy', label: '更新策略', type: 'select', options: [{ label: '可选升级', value: 'OPTIONAL' }, { label: '强制升级', value: 'FORCED' }, { label: '灰度发布', value: 'GRADUAL' }] },
    { key: 'gradualPercent', label: '灰度比例', type: 'number' },
    { key: 'downloadUrl', label: '下载地址', type: 'text' },
    { key: 'fileSize', label: '文件大小', type: 'number' },
    { key: 'changelog', label: '更新说明', type: 'textarea' }
  ] };
  if (kind === 'revokeVersion') return { title: '撤回版本', description: '撤回原因会展示给受影响同事。', fields: [
    { key: 'reason', label: '撤回原因', type: 'textarea' },
    { key: 'alternativeVersion', label: '替代版本', type: 'text' }
  ] };
  if (kind === 'notice') return { title: '系统公告', description: '公告只用于工具能力变化和运行状态。', fields: [
    { key: 'title', label: '标题', type: 'text' },
    { key: 'content', label: '内容', type: 'textarea' },
    { key: 'level', label: '级别', type: 'select', options: [{ label: '普通', value: 'INFO' }, { label: '提醒', value: 'WARN' }, { label: '故障', value: 'ERROR' }] },
    { key: 'publishType', label: '发布方式', type: 'select', options: [{ label: '立即发布', value: 'IMMEDIATE' }, { label: '定时发布', value: 'SCHEDULED' }] },
    { key: 'publishAt', label: '定时时间', type: 'text' },
    { key: 'expireDays', label: '有效天数', type: 'number' }
  ] };
  return { title: '', description: '', fields: [] };
}

async function runWithNotice(task: () => Promise<void>, success: string) {
  loading.value = true;
  notice.value = '';
  try {
    await task();
    noticeKind.value = 'info';
    notice.value = success;
  } catch (error) {
    noticeKind.value = 'error';
    notice.value = humanizeError(error);
  } finally {
    loading.value = false;
  }
}

function errorText(error: unknown) {
  return humanizeError(error);
}

function humanizeError(error: unknown): string {
  if (error instanceof Error) return humanizeMessage(error.message);
  return humanizeMessage(String(error || '请求失败'));
}

function humanizeMessage(message: string): string {
  const text = String(message || '').trim();
  const lower = text.toLowerCase();
  const exact: Record<string, string> = {
    'keeper must specify leaderId': '管家账号必须选择直属组长',
    'password length must be at least 6': '密码至少需要 6 位',
    'username or password is invalid': '手机号或密码不正确',
    'permission denied': '当前账号没有权限执行此操作',
    'account disabled': '账号已停用，请联系管理员',
    'request parameter invalid': '请求参数不正确，请检查后重试',
    'system internal error': '系统内部异常，请稍后重试',
    'Failed to fetch': '网络连接失败，请确认本地后端服务已启动'
  };
  if (exact[text]) return exact[text];
  if (lower.includes('failed to fetch')) return '网络连接失败，请确认本地后端服务已启动';
  if (lower.includes('timeout') || lower.includes('timed out')) return '请求超时，请稍后重试';
  if (lower.includes('leader has') && lower.includes('enabled keepers')) return '该组长名下还有启用中的管家，请先调整直属关系';
  if (lower.includes('phone format')) return '手机号格式不正确';
  if (lower.includes('phone already')) return '该手机号已注册';
  if (lower.includes('displayname')) return '姓名长度需要 2-20 个字符';
  if (lower.includes('not found')) return '数据不存在或已被删除';
  if (/^[A-Z0-9_-]+$/.test(text)) return '请求失败，请检查配置后重试';
  return text || '请求失败，请稍后重试';
}

function assertApiSuccess<T>(response: ApiResponse<T>): T {
  if (!response.success) {
    throw new Error(humanizeMessage(response.message || response.errorCode || '请求失败'));
  }
  return response.data as T;
}

async function getJson<T>(path: string, timeoutMs?: number, signal?: AbortSignal): Promise<ApiResponse<T>> {
  const response = signal !== undefined
    ? await requestGetJson<T>(path, timeoutMs, signal)
    : timeoutMs !== undefined
      ? await requestGetJson<T>(path, timeoutMs)
      : await requestGetJson<T>(path);
  assertApiSuccess(response);
  return response;
}

async function postJson<T>(path: string, body: unknown, timeoutMs?: number, signal?: AbortSignal): Promise<ApiResponse<T>> {
  const response = signal !== undefined
    ? await requestPostJson<T>(path, body, timeoutMs, signal)
    : timeoutMs !== undefined
      ? await requestPostJson<T>(path, body, timeoutMs)
      : await requestPostJson<T>(path, body);
  assertApiSuccess(response);
  return response;
}

async function putJson<T>(path: string, body: unknown, timeoutMs?: number, signal?: AbortSignal): Promise<ApiResponse<T>> {
  const response = signal !== undefined
    ? await requestPutJson<T>(path, body, timeoutMs, signal)
    : timeoutMs !== undefined
      ? await requestPutJson<T>(path, body, timeoutMs)
      : await requestPutJson<T>(path, body);
  assertApiSuccess(response);
  return response;
}

async function deleteJson<T>(path: string, timeoutMs?: number, signal?: AbortSignal): Promise<ApiResponse<T>> {
  const response = signal !== undefined
    ? await requestDeleteJson<T>(path, timeoutMs, signal)
    : timeoutMs !== undefined
      ? await requestDeleteJson<T>(path, timeoutMs)
      : await requestDeleteJson<T>(path);
  assertApiSuccess(response);
  return response;
}

async function postForm<T>(path: string, body: FormData, timeoutMs?: number, signal?: AbortSignal): Promise<ApiResponse<T>> {
  const response = signal !== undefined
    ? await requestPostForm<T>(path, body, timeoutMs, signal)
    : timeoutMs !== undefined
      ? await requestPostForm<T>(path, body, timeoutMs)
      : await requestPostForm<T>(path, body);
  assertApiSuccess(response);
  return response;
}

function hydratePromptDraft() {
  promptDraft.format = configValue('skill.system_prompt_format') || configValue('skill.system_prompt_template');
  promptDraft.redLinesText = parseTextList(configValue('skill.system_prompt_red_lines')).join('\n');
  promptDraft.tagRemovalRulesText = parseTextList(configValue('match.tag_removal_rules')).join('\n');
  promptDraft.fallbackReply = configValue('skill.fallback_reply');
  promptDraft.imageRecognitionPrompt = configValue('image.recognition_prompt');
  promptDraft.regenerateMaxCount = Number(configValue('skill.regenerate_max_count') || 0);
  hydrateRuntimeDrafts();
}

async function savePromptSettings() {
  await runWithNotice(async () => {
    await Promise.all([
      putJson('/admin/api/v1/configs/skill.system_prompt_format', { value: promptDraft.format }),
      putJson('/admin/api/v1/configs/skill.system_prompt_red_lines', { value: JSON.stringify(linesFrom(promptDraft.redLinesText)) }),
      putJson('/admin/api/v1/configs/match.tag_removal_rules', { value: JSON.stringify(linesFrom(promptDraft.tagRemovalRulesText)) }),
      putJson('/admin/api/v1/configs/skill.fallback_reply', { value: promptDraft.fallbackReply }),
      putJson('/admin/api/v1/configs/image.recognition_prompt', { value: promptDraft.imageRecognitionPrompt }),
      putJson('/admin/api/v1/configs/skill.regenerate_max_count', { value: String(promptDraft.regenerateMaxCount || 0) })
    ]);
  }, 'Prompt 配置已保存');
}

function hydrateRuntimeDrafts() {
  skillRuntimeDraft.timeoutMs = intConfigValue('skill.timeout_ms', 10000);
  skillRuntimeDraft.circuitBreakerWindowS = intConfigValue('skill.circuit_breaker_window_s', 30);
  skillRuntimeDraft.circuitBreakerFailureRate = numberConfigValue('skill.circuit_breaker_failure_rate', 0.5);
  skillRuntimeDraft.circuitBreakerMinCalls = intConfigValue('skill.circuit_breaker_min_calls', 5);
  skillRuntimeDraft.circuitBreakerOpenS = intConfigValue('skill.circuit_breaker_open_s', 30);
  skillRuntimeDraft.alertFailureRate = numberConfigValue('skill.alert_failure_rate', 0.3);
  skillRuntimeDraft.alertFailureDurationMinutes = intConfigValue('skill.alert_failure_duration_minutes', 15);
  skillRuntimeDraft.profileExtractTimeoutMs = intConfigValue('profile.extract_timeout_ms', 8000);

  imageRuntimeDraft.model = configValue('image.model') || 'qwen3-vl-plus';
  imageRuntimeDraft.timeoutMs = intConfigValue('image.timeout_ms', 5000);
  imageRuntimeDraft.maxSizeBytes = intConfigValue('image.max_size_bytes', 5242880);
  imageRuntimeDraft.maxDimensionPx = intConfigValue('image.max_dimension_px', 1920);
  imageRuntimeDraft.compressQuality = intConfigValue('image.compress_quality', 85);
  imageRuntimeDraft.consecutiveFailuresAlert = intConfigValue('image.consecutive_failures_alert', 3);
  imageRuntimeDraft.clipboardScreenshotConfirmPromptS = intConfigValue('desktop.clipboard_screenshot_confirm_prompt_s', 10);

  llmReplyDraft.enabled = boolConfigValue('llm.reply_generation.enabled', false);
  llmReplyDraft.fallbackToSkill = boolConfigValue('llm.reply_generation.fallback_to_skill', true);
  llmReplyDraft.temperature = configValue('llm.reply_generation.temperature');
  llmReplyDraft.maxTokens = intConfigValue('llm.reply_generation.max_tokens', 900);
  llmReplyDraft.systemPrompt = configValue('llm.reply_generation.system_prompt');
  llmProfileDraft.enabled = boolConfigValue('llm.profile_extraction.enabled', false);
  llmProfileDraft.fallbackToSkill = boolConfigValue('llm.profile_extraction.fallback_to_skill', true);
  llmProfileDraft.temperature = configValue('llm.profile_extraction.temperature');
  llmProfileDraft.maxTokens = intConfigValue('llm.profile_extraction.max_tokens', 700);
  llmProfileDraft.systemPrompt = configValue('llm.profile_extraction.system_prompt');
  llmFollowupDraft.enabled = boolConfigValue('llm.followup_suggestion.enabled', false);
  llmFollowupDraft.temperature = configValue('llm.followup_suggestion.temperature');
  llmFollowupDraft.maxTokens = intConfigValue('llm.followup_suggestion.max_tokens', 500);
  llmFollowupDraft.systemPrompt = configValue('llm.followup_suggestion.system_prompt');
  llmAbnormalDraft.enabled = boolConfigValue('llm.abnormal_detection.enabled', false);
  llmAbnormalDraft.temperature = configValue('llm.abnormal_detection.temperature');
  llmAbnormalDraft.maxTokens = intConfigValue('llm.abnormal_detection.max_tokens', 500);
  llmAbnormalDraft.systemPrompt = configValue('llm.abnormal_detection.system_prompt');
  llmSummaryDraft.enabled = boolConfigValue('llm.summary.enabled', false);
  llmSummaryDraft.temperature = configValue('llm.summary.temperature');
  llmSummaryDraft.maxTokens = intConfigValue('llm.summary.max_tokens', 500);
  llmSummaryDraft.systemPrompt = configValue('llm.summary.system_prompt');

  tableRuntimeDraft.apiBaseUrl = configValue('table.api_base_url');
  tableRuntimeDraft.apiKey = '';
  tableRuntimeDraft.writeTimeoutMs = intConfigValue('table.write_timeout_ms', 10000);
  tableRuntimeDraft.retryMaxCount = intConfigValue('table.retry_max_count', 5);
  tableRuntimeDraft.retryIntervalS = intConfigValue('table.retry_interval_s', 60);
  tableRuntimeDraft.alertFailureHours = intConfigValue('table.alert_failure_hours', 1);
  tableRuntimeDraft.alertNotifyTarget = configValue('table.alert_notify_target') || 'ADMIN';
  tableRuntimeDraft.queueWarnThreshold = intConfigValue('table.queue_warn_threshold', 100);
  tableRuntimeDraft.queueAlertThreshold = intConfigValue('table.queue_alert_threshold', 1000);

  datasourceRuntimeDraft.syncCron = configValue('cache.sync_cron') || '0 */30 * * * *';
  datasourceRuntimeDraft.ttlSeconds = intConfigValue('cache.ttl_seconds', 900);
  datasourceRuntimeDraft.syncTimeoutMs = intConfigValue('cache.sync_timeout_ms', 10000);
  datasourceRuntimeDraft.mappingVersionMax = intConfigValue('datasource.mapping_version_max', 50);
  datasourceRuntimeDraft.importMaxRows = intConfigValue('datasource.import_max_rows', 5000);
  datasourceRuntimeDraft.manualSyncTimeoutS = intConfigValue('datasource.manual_sync_timeout_s', 60);
  datasourceRuntimeDraft.syncStatusRefreshS = intConfigValue('datasource.sync_status_refresh_s', 30);
}

async function saveSkillRuntimeSettings() {
  await saveConfigGroup([
    ['skill.timeout_ms', skillRuntimeDraft.timeoutMs],
    ['skill.circuit_breaker_window_s', skillRuntimeDraft.circuitBreakerWindowS],
    ['skill.circuit_breaker_failure_rate', skillRuntimeDraft.circuitBreakerFailureRate],
    ['skill.circuit_breaker_min_calls', skillRuntimeDraft.circuitBreakerMinCalls],
    ['skill.circuit_breaker_open_s', skillRuntimeDraft.circuitBreakerOpenS],
    ['skill.alert_failure_rate', skillRuntimeDraft.alertFailureRate],
    ['skill.alert_failure_duration_minutes', skillRuntimeDraft.alertFailureDurationMinutes],
    ['profile.extract_timeout_ms', skillRuntimeDraft.profileExtractTimeoutMs]
  ], 'Skill 运行参数已保存');
}

async function saveImageRuntimeSettings() {
  if (!isValidClipboardScreenshotConfirmPromptSeconds(imageRuntimeDraft.clipboardScreenshotConfirmPromptS)) {
    noticeKind.value = 'error';
    notice.value = '截图确认提示停留必须为 0 或 3-60 秒。0 表示不自动忽略。';
    return;
  }
  await saveConfigGroup([
    ['image.model', imageRuntimeDraft.model],
    ['image.timeout_ms', imageRuntimeDraft.timeoutMs],
    ['image.max_size_bytes', imageRuntimeDraft.maxSizeBytes],
    ['image.max_dimension_px', imageRuntimeDraft.maxDimensionPx],
    ['image.compress_quality', imageRuntimeDraft.compressQuality],
    ['image.consecutive_failures_alert', imageRuntimeDraft.consecutiveFailuresAlert],
    ['desktop.clipboard_screenshot_confirm_prompt_s', imageRuntimeDraft.clipboardScreenshotConfirmPromptS]
  ], '识图运行参数已保存');
}

async function saveLlmReplySettings() {
  await saveConfigGroup([
    ['llm.reply_generation.enabled', llmReplyDraft.enabled ? 'true' : 'false'],
    ['llm.reply_generation.fallback_to_skill', llmReplyDraft.fallbackToSkill ? 'true' : 'false'],
    ['llm.reply_generation.temperature', llmReplyDraft.temperature],
    ['llm.reply_generation.max_tokens', llmReplyDraft.maxTokens],
    ['llm.reply_generation.system_prompt', llmReplyDraft.systemPrompt]
  ], 'LLM 回复生成配置已保存');
}

async function saveLlmProfileSettings() {
  await saveConfigGroup([
    ['llm.profile_extraction.enabled', llmProfileDraft.enabled ? 'true' : 'false'],
    ['llm.profile_extraction.fallback_to_skill', llmProfileDraft.fallbackToSkill ? 'true' : 'false'],
    ['llm.profile_extraction.temperature', llmProfileDraft.temperature],
    ['llm.profile_extraction.max_tokens', llmProfileDraft.maxTokens],
    ['llm.profile_extraction.system_prompt', llmProfileDraft.systemPrompt]
  ], 'LLM 档案提取配置已保存');
}

async function saveLlmFollowupSettings() {
  await saveConfigGroup([
    ['llm.followup_suggestion.enabled', llmFollowupDraft.enabled ? 'true' : 'false'],
    ['llm.followup_suggestion.temperature', llmFollowupDraft.temperature],
    ['llm.followup_suggestion.max_tokens', llmFollowupDraft.maxTokens],
    ['llm.followup_suggestion.system_prompt', llmFollowupDraft.systemPrompt]
  ], 'LLM 跟进建议配置已保存');
}

async function saveLlmAbnormalSettings() {
  await saveConfigGroup([
    ['llm.abnormal_detection.enabled', llmAbnormalDraft.enabled ? 'true' : 'false'],
    ['llm.abnormal_detection.temperature', llmAbnormalDraft.temperature],
    ['llm.abnormal_detection.max_tokens', llmAbnormalDraft.maxTokens],
    ['llm.abnormal_detection.system_prompt', llmAbnormalDraft.systemPrompt]
  ], 'LLM 异常识别配置已保存');
}

async function saveLlmSummarySettings() {
  await saveConfigGroup([
    ['llm.summary.enabled', llmSummaryDraft.enabled ? 'true' : 'false'],
    ['llm.summary.temperature', llmSummaryDraft.temperature],
    ['llm.summary.max_tokens', llmSummaryDraft.maxTokens],
    ['llm.summary.system_prompt', llmSummaryDraft.systemPrompt]
  ], 'LLM 总结补位配置已保存');
}

function isValidClipboardScreenshotConfirmPromptSeconds(value: unknown): boolean {
  const parsed = Number(value);
  if (!Number.isFinite(parsed)) {
    return false;
  }
  const integer = Math.trunc(parsed);
  return parsed === integer && (integer === 0 || (integer >= 3 && integer <= 60));
}

async function saveTableRuntimeSettings() {
  const entries: Array<[string, string | number]> = [
    ['table.api_base_url', tableRuntimeDraft.apiBaseUrl],
    ['table.write_timeout_ms', tableRuntimeDraft.writeTimeoutMs],
    ['table.retry_max_count', tableRuntimeDraft.retryMaxCount],
    ['table.retry_interval_s', tableRuntimeDraft.retryIntervalS],
    ['table.alert_failure_hours', tableRuntimeDraft.alertFailureHours],
    ['table.alert_notify_target', tableRuntimeDraft.alertNotifyTarget],
    ['table.queue_warn_threshold', tableRuntimeDraft.queueWarnThreshold],
    ['table.queue_alert_threshold', tableRuntimeDraft.queueAlertThreshold]
  ];
  if (tableRuntimeDraft.apiKey.trim()) {
    entries.push(['table.api_key', tableRuntimeDraft.apiKey.trim()]);
  }
  await saveConfigGroup(entries, '企微表格网关参数已保存');
  tableRuntimeDraft.apiKey = '';
}

async function saveDatasourceRuntimeSettings() {
  await saveConfigGroup([
    ['cache.sync_cron', datasourceRuntimeDraft.syncCron],
    ['cache.ttl_seconds', datasourceRuntimeDraft.ttlSeconds],
    ['cache.sync_timeout_ms', datasourceRuntimeDraft.syncTimeoutMs],
    ['datasource.mapping_version_max', datasourceRuntimeDraft.mappingVersionMax],
    ['datasource.import_max_rows', datasourceRuntimeDraft.importMaxRows],
    ['datasource.manual_sync_timeout_s', datasourceRuntimeDraft.manualSyncTimeoutS],
    ['datasource.sync_status_refresh_s', datasourceRuntimeDraft.syncStatusRefreshS]
  ], '数据同步策略已保存');
}

async function saveConfigGroup(entries: Array<[string, string | number]>, success: string) {
  await runWithNotice(async () => {
    await Promise.all(entries.map(([key, value]) => putJson(`/admin/api/v1/configs/${key}`, { value: String(value ?? '') })));
    const configList = await getJson<unknown>('/admin/api/v1/configs');
    configs.value = configEntries(configList);
    hydrateRuntimeDrafts();
  }, success);
}

async function loadPromptVersions() {
  await runWithNotice(async () => {
    const payload = recordFromResponse(await getJson<unknown>(`/admin/api/v1/skill-prompt/${selectedPromptType.value}/versions`));
    promptVersions.value = listFrom(payload, 'versions');
  }, 'Prompt 版本历史已加载');
}

function confirmRestorePrompt(version: AnyRecord) {
  if (window.confirm(`确认恢复到 Prompt 版本 ${version.version}？恢复会立即影响线上配置。`)) {
    void runWithNotice(async () => {
      await postJson(`/admin/api/v1/skill-prompt/${selectedPromptType.value}/restore`, { version: version.version, operator: props.accountName || 'admin' });
      await Promise.all([loadSkillAi(), loadPromptVersions()]);
    }, 'Prompt 版本已恢复');
  }
}

async function runSkillTest(item: AnyRecord) {
  await runWithNotice(async () => {
    const response = recordFromResponse(await postJson<unknown>(`/admin/api/v1/skills/${item.id}/test`, { testMessage: skillTestMessage.value }));
    skillTestResults[String(item.id)] = { ...response, skillName: item.skillName || item.skillId };
  }, 'Skill 测试完成');
}

function confirmToggleSkill(item: AnyRecord) {
  const message = item.enabled ? `确认停用「${item.skillName || item.skillId}」？停用后该场景可能断开。` : `确认启用「${item.skillName || item.skillId}」？`;
  if (window.confirm(message)) {
    void runWithNotice(async () => {
      await putJson(`/admin/api/v1/skills/${item.id}/toggle`, { enabled: !item.enabled });
      await loadSkillAi();
    }, item.enabled ? '已停用' : '已启用');
  }
}

function confirmDeleteSkill(item: AnyRecord) {
  if (window.confirm(`确认删除「${item.skillName || item.skillId}」？删除后不可恢复。`)) {
    void runWithNotice(async () => {
      await deleteJson(`/admin/api/v1/skills/${item.id}`);
      await loadSkillAi();
    }, 'Skill 绑定已删除');
  }
}

function confirmToggleLlmRoute(route: AnyRecord) {
  const enabled = route.enabled !== false;
  const message = enabled ? `确认停用「${llmRouteTitle(route)}」？` : `确认启用「${llmRouteTitle(route)}」？`;
  if (window.confirm(message)) {
    void runWithNotice(async () => {
      await putJson(`/admin/api/v1/llm-routes/${route.id}/toggle`, { enabled: !enabled });
      await loadSkillAi();
    }, enabled ? 'LLM 路由已停用' : 'LLM 路由已启用');
  }
}

function confirmDeleteLlmRoute(route: AnyRecord) {
  if (window.confirm(`确认删除「${llmRouteTitle(route)}」？删除后该场景会回退到默认 LLM 环境。`)) {
    void runWithNotice(async () => {
      await deleteJson(`/admin/api/v1/llm-routes/${route.id}`);
      await loadSkillAi();
    }, 'LLM 路由已删除');
  }
}

function confirmActivateEnvironment(kind: 'skill' | 'image' | 'llm', env: AnyRecord) {
  if (window.confirm(`将切换至「${env.envName}」，此修改会影响线上能力，确认切换？`)) {
    void runWithNotice(async () => {
      await putJson(`/admin/api/v1/${environmentPrefix(kind)}/${env.id}/activate`, {});
      await loadSkillAi();
    }, '环境已切换');
  }
}

function confirmDeleteEnvironment(kind: 'skill' | 'image' | 'llm', env: AnyRecord) {
  if (!canDeleteEnvironment(kind, env)) {
    noticeKind.value = 'error';
    notice.value = isActiveEnvironment(env)
      ? '当前启用环境不能删除，请先切换到其他环境。'
      : '至少保留一个环境作为线上或备用配置。';
    return;
  }
  if (window.confirm(`确认删除「${env.envName}」？删除后不能继续作为备用环境。`)) {
    void runWithNotice(async () => {
      await deleteJson(`/admin/api/v1/${environmentPrefix(kind)}/${env.id}`);
      await loadSkillAi();
    }, '环境已删除');
  }
}

async function testImageEnvironment(env: AnyRecord) {
  await runWithNotice(async () => {
    await postJson(`/admin/api/v1/image-environments/${env.id}/test`, {});
    await loadSkillAi();
  }, '测试完成');
}

async function testLlmEnvironment(env: AnyRecord) {
  await runWithNotice(async () => {
    await postJson(`/admin/api/v1/llm-environments/${env.id}/test`, {});
    await loadSkillAi();
  }, 'LLM 测试完成');
}

async function selectDatasource(item: AnyRecord) {
  selectedDatasource.value = item;
  mappingVersions.value = [];
  datasourceColumns.value = [];
  datasourceColumnStatus.value = null;
  mappingCompare.value = null;
  Object.keys(mappingDraft).forEach((key) => delete mappingDraft[key]);
  Object.keys(mappingEnabledDraft).forEach((key) => delete mappingEnabledDraft[key]);
  await runWithNotice(async () => {
    const payload = await getJson<unknown>(`/admin/api/v1/datasources/${item.id}/mappings`);
    mappings.value = listFromResponse(payload);
    for (const mapping of mappings.value) {
      const target = mapping.targetField ?? mapping.fieldName;
      const source = mapping.sourceField ?? mapping.sourceColumn;
      if (target) mappingDraft[target] = source ?? '';
      if (target) mappingEnabledDraft[target] = mapping.enabled !== false;
    }
    for (const field of customerFields.value) {
      if (field.key && mappingEnabledDraft[field.key] === undefined) mappingEnabledDraft[field.key] = true;
    }
  }, '字段映射已加载');
}

async function loadDatasourceColumns() {
  const datasource = selectedDatasource.value;
  if (!datasource) return;
  await runWithNotice(async () => {
    const payload = recordFromResponse(await getJson<unknown>(`/admin/api/v1/datasources/${datasource.id}/columns`));
    datasourceColumnStatus.value = payload;
    datasourceColumns.value = listFrom(payload, 'columns');
    if (!datasourceColumns.value.length) {
      throw new Error(payload.fetchError || '没有识别到可用列名，请检查数据源连接或先保存字段映射');
    }
  }, '列名已识别');
}

async function compareMappings() {
  const datasource = selectedDatasource.value;
  if (!datasource) return;
  await runWithNotice(async () => {
    mappingCompare.value = recordFromResponse(await getJson<unknown>(`/admin/api/v1/datasources/${datasource.id}/mappings/compare`));
  }, '映射差异已更新');
}

function confirmRestoreMapping(version: AnyRecord) {
  const datasource = selectedDatasource.value;
  if (!datasource) return;
  if (window.confirm(`确认恢复「${datasource.name}」到映射版本 ${version.version}？当前映射会生成新版本留痕。`)) {
    void runWithNotice(async () => {
      await postJson(`/admin/api/v1/datasources/${datasource.id}/mappings/restore`, { version: version.version });
      await selectDatasource(datasource);
      await loadMappingVersions();
    }, '字段映射版本已恢复');
  }
}

function replaceDatasource(item: AnyRecord) {
  const sheetId = window.prompt(`为「${item.name}」填写新的企微表格标识`, item.sheetId || '');
  if (!sheetId || sheetId === item.sheetId) return;
  void runWithNotice(async () => {
    await putJson(`/admin/api/v1/datasources/${item.id}/replace`, { sheetId });
    await loadDataContent();
  }, '数据源已换表，原字段映射已保留');
}

function confirmDeleteDatasource(item: AnyRecord) {
  if (window.confirm(`确认删除数据源「${item.name}」？删除后该来源将停止同步。`)) {
    void runWithNotice(async () => {
      await deleteJson(`/admin/api/v1/datasources/${item.id}`);
      if (selectedDatasource.value?.id === item.id) selectedDatasource.value = null;
      await loadDataContent();
    }, '数据源已删除');
  }
}

async function saveMappings() {
  const datasource = selectedDatasource.value;
  if (!datasource) return;
  await runWithNotice(async () => {
    const payload = {
      mappings: Object.entries(mappingDraft)
        .filter(([, sourceField]) => sourceField.trim())
        .map(([targetField, sourceField]) => ({ targetField, sourceField, enabled: mappingEnabledDraft[targetField] !== false }))
    };
    await putJson(`/admin/api/v1/datasources/${datasource.id}/mappings`, payload);
    await selectDatasource(datasource);
  }, '字段映射已保存并生成版本');
}

async function loadMappingVersions() {
  const datasource = selectedDatasource.value;
  if (!datasource) return;
  await runWithNotice(async () => {
    mappingVersions.value = listFromResponse(await getJson<unknown>(`/admin/api/v1/datasources/${datasource.id}/mappings/versions`));
  }, `已加载 ${mappingVersions.value.length} 个映射版本`);
}

async function triggerDatasourceSync(item: AnyRecord) {
  await runWithNotice(async () => {
    await postJson(`/admin/api/v1/datasources/${item.id}/sync`, {});
    await loadDataContent();
  }, '已提交同步任务');
}

async function toggleDatasource(item: AnyRecord) {
  await runWithNotice(async () => {
    await putJson(`/admin/api/v1/datasources/${item.id}/toggle`, { enabled: item.enabled === false });
    await loadDataContent();
  }, '数据源状态已更新');
}

async function previewCsv(event: Event) {
  const input = event.target as HTMLInputElement;
  const file = input.files?.[0];
  csvFile.value = file ?? null;
  csvPreview.value = [];
  if (!file) return;
  const text = await file.text();
  csvPreview.value = text.split(/\r?\n/).slice(0, 6);
}

async function importCsv() {
  if (!csvFile.value) return;
  await runWithNotice(async () => {
    const body = new FormData();
    body.append('file', csvFile.value!);
    csvImportResult.value = recordFromResponse(await postForm<unknown>('/admin/api/v1/datasources/import', body));
    await loadDataContent();
  }, 'CSV 导入任务已提交');
}

async function uploadQuickSearchImage(event: Event, item: AnyRecord) {
  const input = event.target as HTMLInputElement;
  const file = input.files?.[0];
  input.value = '';
  if (!file) return;
  await runWithNotice(async () => {
    const body = new FormData();
    body.append('file', file);
    const uploaded = recordFromResponse(await postForm<unknown>('/admin/api/v1/upload/image', body));
    const imageUrl = String(uploaded.imageUrl || uploaded.url || '');
    if (!imageUrl) throw new Error('图片上传成功但未返回可用地址');
    await putJson(`/admin/api/v1/quick-search/items/${item.id}`, { ...quickSearchPayload(item), imageUrl, contentType: 'IMAGE' });
    await loadDataContent();
  }, '图片已上传并绑定到速搜内容');
}

async function clearQuickSearchImage(item: AnyRecord) {
  await runWithNotice(async () => {
    await putJson(`/admin/api/v1/quick-search/items/${item.id}`, { ...quickSearchPayload(item), imageUrl: '' });
    await loadDataContent();
  }, '图片已清除');
}

async function batchToggleQuickSearch(enabled: boolean) {
  if (!quickSearchSelectedIds.value.length) return;
  await runWithNotice(async () => {
    const selected = quickSearchItems.value.filter((item) => quickSearchSelectedIds.value.includes(item.id));
    for (const item of selected) {
      if ((item.enabled !== false) !== enabled) await putJson(`/admin/api/v1/quick-search/items/${item.id}/toggle`, {});
    }
    quickSearchSelectedIds.value = [];
    await loadDataContent();
  }, enabled ? '已批量启用速搜内容' : '已批量停用速搜内容');
}

function batchDeleteQuickSearch() {
  if (!quickSearchSelectedIds.value.length) return;
  const selectedCount = quickSearchSelectedIds.value.length;
  if (window.confirm(`确认删除选中的 ${quickSearchSelectedIds.value.length} 条速搜内容？`)) {
    void runWithNotice(async () => {
      for (const id of quickSearchSelectedIds.value) await deleteJson(`/admin/api/v1/quick-search/items/${id}`);
      quickSearchSelectedIds.value = [];
      if (quickSearchItems.value.length <= selectedCount && quickSearchPageInfo.page > 1) {
        quickSearchPageInfo.page -= 1;
      }
      await loadDataContent();
    }, '已批量删除速搜内容');
  }
}

async function toggleQuickSearchItem(item: AnyRecord) {
  await runWithNotice(async () => {
    await putJson(`/admin/api/v1/quick-search/items/${item.id}/toggle`, {});
    await loadDataContent();
  }, '速搜内容状态已更新');
}

function confirmDeleteQuickSearchItem(item: AnyRecord) {
  if (window.confirm(`确认删除「${item.title}」？删除后桌面端将无法搜索到。`)) {
    void runWithNotice(async () => {
      await deleteJson(`/admin/api/v1/quick-search/items/${item.id}`);
      if (quickSearchItems.value.length <= 1 && quickSearchPageInfo.page > 1) {
        quickSearchPageInfo.page -= 1;
      }
      await loadDataContent();
    }, '速搜内容已删除');
  }
}

async function resetAccountPassword(account: AnyRecord) {
  const newPassword = window.prompt(`为「${account.displayName || account.username}」设置新密码`, 'pass5678');
  if (!newPassword) return;
  await runWithNotice(async () => {
    await putJson(`/admin/api/v1/accounts/${account.id}/reset-password`, { newPassword });
    await refreshAccountsAfterMutation();
  }, '密码已重置，旧登录已失效');
}

function confirmToggleAccount(account: AnyRecord) {
  if (isCurrentAccount(account) && account.isEnabled !== false) {
    noticeKind.value = 'error';
    notice.value = '不能停用当前登录账号，请先切换到其他管理员账号。';
    return;
  }
  if (window.confirm(`确认${account.isEnabled === false ? '启用' : '停用'}「${account.displayName || account.username}」？`)) {
    void toggleAccount(account);
  }
}

async function toggleAccount(account: AnyRecord) {
  await runWithNotice(async () => {
    await putJson(`/admin/api/v1/accounts/${account.id}/toggle`, { isEnabled: account.isEnabled === false });
    await refreshAccountsAfterMutation();
  }, '账号状态已更新');
}

function confirmDeleteAccount(account: AnyRecord) {
  if (isCurrentAccount(account)) {
    noticeKind.value = 'error';
    notice.value = '不能删除当前登录账号。';
    return;
  }
  if (window.confirm(`确认删除账号「${account.displayName || account.username}」？删除后该账号无法登录。`)) {
    void runWithNotice(async () => {
      await deleteJson(`/admin/api/v1/accounts/${account.id}`);
      if (accounts.value.length <= 1 && accountPageInfo.page > 1) {
        accountPageInfo.page -= 1;
      }
      await refreshAccountsAfterMutation();
    }, '账号已删除');
  }
}

async function refreshAccountsAfterMutation() {
  await loadOrgRulesTags();
}

function buildRulePayload() {
  const conditions: AnyRecord[] = [
    { field: 'lastFollowupHours', op: 'GT', value: Number(formDraft.thresholdHours) }
  ];
  if (formDraft.leadType && formDraft.leadType !== 'GENERAL') {
    conditions.unshift({ field: 'leadType', op: 'EQ', value: formDraft.leadType });
  }
  const conditionJson = JSON.stringify({
    operator: 'AND',
    conditions
  });
  const actionConfig = {
    alertLevel: formDraft.alertLevel || (formDraft.actionType === 'ALERT' ? 'WARN' : 'NORMAL'),
    reminderType: formDraft.reminderType || (formDraft.actionType === 'TAG_CHANGE' ? 'TAG_SUGGESTION' : 'OVERDUE'),
    ...(formDraft.actionType === 'TAG_CHANGE' ? { tagName: formDraft.tagName || '系统标签建议' } : {}),
    ...(formDraft.actionType === 'NOTIFY_LEADER' ? { reason: formDraft.reason || '跟进规则触发' } : {})
  };
  return {
    name: formDraft.name,
    conditionJson,
    actionType: formDraft.actionType,
    actionConfig: JSON.stringify(actionConfig),
    priority: formDraft.priority,
    enabled: formDraft.enabled
  };
}

function confirmToggleRule(rule: AnyRecord) {
  if (window.confirm(`确认${rule.enabled === false ? '启用' : '停用'}规则「${rule.name}」？`)) {
    void toggleRule(rule);
  }
}

async function toggleRule(rule: AnyRecord) {
  await runWithNotice(async () => {
    await putJson(`/admin/api/v1/rules/${rule.id}/toggle`, { enabled: rule.enabled === false });
    await loadOrgRulesTags();
  }, '规则状态已更新');
}

async function batchToggleRules(enabled: boolean) {
  const selected = rules.value.filter((rule) => ruleSelectedIds.value.includes(rule.id));
  if (!selected.length) return;
  if (window.confirm(`确认批量${enabled ? '启用' : '停用'} ${selected.length} 条规则？`)) {
    await runWithNotice(async () => {
      await Promise.all(selected.map((rule) => putJson(`/admin/api/v1/rules/${rule.id}/toggle`, { enabled })));
      ruleSelectedIds.value = [];
      await loadOrgRulesTags();
    }, enabled ? '规则已批量启用' : '规则已批量停用');
  }
}

function confirmDeleteRule(rule: AnyRecord) {
  if (isBuiltinRule(rule)) return;
  if (window.confirm(`确认删除「${rule.name}」？建议优先选择停用。`)) {
    void runWithNotice(async () => {
      await deleteJson(`/admin/api/v1/rules/${rule.id}`);
      if (rules.value.length <= 1 && rulePageInfo.page > 1) {
        rulePageInfo.page -= 1;
      }
      await loadOrgRulesTags();
    }, '规则已删除');
  }
}

async function batchDeleteRules() {
  const selected = deletableSelectedRules.value;
  if (!selected.length) return;
  if (window.confirm(`确认删除 ${selected.length} 条自定义规则？内置规则不会被删除，建议优先停用。`)) {
    await runWithNotice(async () => {
      await Promise.all(selected.map((rule) => deleteJson(`/admin/api/v1/rules/${rule.id}`)));
      ruleSelectedIds.value = [];
      if (rules.value.length <= selected.length && rulePageInfo.page > 1) {
        rulePageInfo.page -= 1;
      }
      await loadOrgRulesTags();
    }, '规则已批量删除');
  }
}

async function openTagDetail(kind: 'category' | 'value', item: AnyRecord) {
  tagDetailKind.value = kind;
  tagDetail.value = null;
  tagDetailLoading.value = true;
  try {
    tagDetail.value = recordFromResponse(await getJson<unknown>(tagDetailPath(kind, item.id)));
  } catch (error) {
    closeTagDetail();
    noticeKind.value = 'error';
    notice.value = humanizeError(error);
  } finally {
    tagDetailLoading.value = false;
  }
}

function closeTagDetail() {
  tagDetailKind.value = null;
  tagDetail.value = null;
  tagDetailLoading.value = false;
}

async function editTagEntity(kind: 'category' | 'value', item: AnyRecord) {
  closeTagDetail();
  loading.value = true;
  try {
    const detail = recordFromResponse(await getJson<unknown>(tagDetailPath(kind, item.id)));
    openForm(kind === 'category' ? 'tagCategory' : 'tagValue', detail);
  } catch (error) {
    noticeKind.value = 'error';
    notice.value = humanizeError(error);
  } finally {
    loading.value = false;
  }
}

function tagDetailPath(kind: 'category' | 'value', id: unknown) {
  return `/admin/api/v1/tags/${kind === 'category' ? 'categories' : 'values'}/${id}`;
}

async function toggleTagCategory(category: AnyRecord) {
  await runWithNotice(async () => {
    await putJson(`/admin/api/v1/tags/categories/${category.id}/toggle`, {
      enabled: category.isEnabled === false,
      version: category.version
    });
    await loadOrgRulesTags();
  }, '标签分类状态已更新');
}

async function toggleTagValue(tag: AnyRecord) {
  await runWithNotice(async () => {
    await putJson(`/admin/api/v1/tags/values/${tag.id}/toggle`, {
      enabled: tag.isEnabled === false,
      version: tag.version
    });
    await loadOrgRulesTags();
  }, '标签值状态已更新');
}

function confirmDeleteTagValue(tag: AnyRecord) {
  if (window.confirm(`确认删除标签值「${tag.displayName || tag.tagValue}」？建议优先停用仍在使用的标签。`)) {
    void runWithNotice(async () => {
      await deleteJson(`/admin/api/v1/tags/values/${tag.id}`);
      await loadOrgRulesTags();
    }, '标签值已删除');
  }
}

function confirmDeleteTagCategory(category: AnyRecord) {
  if (isBuiltinTagCategory(category)) return;
  if (window.confirm(`确认删除标签分类「${category.categoryName || category.name}」？分类下标签也将不可用。`)) {
    void runWithNotice(async () => {
      await deleteJson(`/admin/api/v1/tags/categories/${category.id}`);
      await loadOrgRulesTags();
    }, '标签分类已删除');
  }
}

function confirmPublishVersion(version: AnyRecord) {
  if (window.confirm(`确认发布桌面版本 ${version.version}？发布后客户端会收到升级信息。`)) {
    void publishVersion(version);
  }
}

async function publishVersion(version: AnyRecord) {
  await runWithNotice(async () => {
    await putJson(`/admin/api/v1/versions/${version.id}/publish`, {});
    if (versionStatusFilter.value === 'DRAFT' && versions.value.length <= 1 && versionPageInfo.page > 1) {
      versionPageInfo.page -= 1;
    }
    await loadInsightOps();
  }, '版本已发布');
}

async function openTagMerge(kind: 'category' | 'value', item: AnyRecord) {
  tagMerge.kind = kind;
  tagMerge.source = null;
  tagMerge.targetId = '';
  tagMerge.targets = [];
  tagMerge.preview = null;
  tagMerge.error = '';
  tagMerge.loading = true;
  try {
    const source = recordFromResponse(await getJson<unknown>(tagDetailPath(kind, item.id)));
    tagMerge.source = source;
    tagMerge.targets = (kind === 'category'
      ? await fetchAllTagItems('/admin/api/v1/tags/categories', {
          enabled: true,
          merged: false,
          selectionMode: source.selectionMode,
          sortBy: 'sortOrder',
          sortDirection: 'ASC'
        })
      : await fetchAllTagItems('/admin/api/v1/tags/values', {
          categoryId: source.categoryId,
          enabled: true,
          merged: false,
          sortBy: 'sortOrder',
          sortDirection: 'ASC'
        })).filter((target) => String(target.id) !== String(source.id));
  } catch (error) {
    tagMerge.error = humanizeError(error);
  } finally {
    tagMerge.loading = false;
  }
}

function closeTagMerge() {
  tagMerge.kind = null;
  tagMerge.source = null;
  tagMerge.targetId = '';
  tagMerge.targets = [];
  tagMerge.preview = null;
  tagMerge.loading = false;
  tagMerge.error = '';
}

async function previewTagMerge() {
  const payload = tagMergePayload();
  if (!tagMerge.kind || !tagMerge.source || !payload) return;
  tagMerge.loading = true;
  tagMerge.error = '';
  try {
    const path = `${tagDetailPath(tagMerge.kind, tagMerge.source.id)}/merge-preview`;
    tagMerge.preview = recordFromResponse(await postJson<unknown>(path, payload));
  } catch (error) {
    tagMerge.preview = null;
    tagMerge.error = humanizeError(error);
  } finally {
    tagMerge.loading = false;
  }
}

async function executeTagMerge() {
  const payload = tagMergePayload();
  if (!tagMerge.kind || !tagMerge.source || !tagMerge.preview || !payload) return;
  if (!window.confirm(`确认将「${tagMerge.preview.sourceName}」合并到「${tagMerge.preview.targetName}」？此操作会迁移客户、规则和历史引用。`)) return;
  tagMerge.loading = true;
  tagMerge.error = '';
  try {
    const path = `${tagDetailPath(tagMerge.kind, tagMerge.source.id)}/merge`;
    await postJson(path, payload);
    closeTagMerge();
    await loadOrgRulesTags();
    noticeKind.value = 'info';
    notice.value = '标签合并完成';
  } catch (error) {
    tagMerge.error = humanizeError(error);
    tagMerge.loading = false;
  }
}

function tagMergePayload() {
  const target = tagMerge.targets.find((item) => String(item.id) === tagMerge.targetId);
  if (!tagMerge.source || !target) return null;
  return {
    targetId: Number(target.id),
    sourceVersion: Number(tagMerge.source.version),
    targetVersion: Number(target.version)
  };
}

async function exportCurrentTags() {
  await runWithNotice(async () => {
    const categories = tagView.value === 'categories';
    const path = categories
      ? withQuery('/admin/api/v1/tags/categories/export', {
          keyword: tagCategoryKeyword.value,
          enabled: booleanFilter(tagCategoryEnabledFilter.value),
          merged: booleanFilter(tagCategoryMergedFilter.value),
          sortBy: tagCategorySortBy.value,
          sortDirection: tagCategorySortDirection.value
        })
      : withQuery('/admin/api/v1/tags/values/export', {
          categoryId: tagValueCategoryFilter.value,
          keyword: tagValueKeyword.value,
          enabled: booleanFilter(tagValueEnabledFilter.value),
          merged: booleanFilter(tagValueMergedFilter.value),
          sortBy: tagValueSortBy.value,
          sortDirection: tagValueSortDirection.value
        });
    const download = await requestGetBlob(path);
    downloadBlob(download.filename || (categories ? 'tag-categories.csv' : 'tag-values.csv'), download.blob);
  }, '标签 CSV 已开始下载');
}

function confirmDeleteVersion(version: AnyRecord) {
  if (window.confirm(`确认删除桌面版本 ${version.version}？仅应删除未发布草稿。`)) {
    void runWithNotice(async () => {
      await deleteJson(`/admin/api/v1/versions/${version.id}`);
      if (versions.value.length <= 1 && versionPageInfo.page > 1) {
        versionPageInfo.page -= 1;
      }
      await loadInsightOps();
    }, '版本已删除');
  }
}

function confirmStopNotice(item: AnyRecord) {
  if (window.confirm(`确认停止公告「${item.title}」？停止后桌面端不再展示。`)) {
    void stopNotice(item);
  }
}

async function stopNotice(item: AnyRecord) {
  await runWithNotice(async () => {
    await putJson(`/admin/api/v1/notices/${item.id}/stop`, {});
    if (noticeStatusFilter.value && noticeStatusFilter.value !== 'STOPPED' && notices.value.length <= 1 && noticePageInfo.page > 1) {
      noticePageInfo.page -= 1;
    }
    await loadInsightOps();
  }, '公告已停止');
}

function confirmDeleteNotice(item: AnyRecord) {
  if (window.confirm(`确认删除公告「${item.title}」？删除后无法恢复。`)) {
    void runWithNotice(async () => {
      await deleteJson(`/admin/api/v1/notices/${item.id}`);
      if (notices.value.length <= 1 && noticePageInfo.page > 1) {
        noticePageInfo.page -= 1;
      }
      await loadInsightOps();
    }, '公告已删除');
  }
}

async function exportAuditLogs() {
  await runWithNotice(async () => {
    auditExportJob.value = recordFromResponse(await postJson<unknown>('/admin/api/v1/audit-logs/export', auditFilterPayload()));
    startAuditExportPolling();
  }, '审计导出任务已创建');
}

async function refreshAuditExportStatus() {
  const exportId = auditExportJob.value?.exportId;
  if (!exportId) return;
  await runWithNotice(async () => {
    auditExportJob.value = recordFromResponse(await getJson<unknown>(`/admin/api/v1/audit-logs/export/${exportId}`));
    if (isAuditExportFinal(auditExportJob.value)) {
      stopAuditExportPolling();
    }
  }, '导出状态已刷新');
}

async function pollAuditExportStatus() {
  const exportId = auditExportJob.value?.exportId;
  if (!exportId) return;
  try {
    auditExportJob.value = recordFromResponse(await getJson<unknown>(`/admin/api/v1/audit-logs/export/${exportId}`));
    if (isAuditExportFinal(auditExportJob.value)) {
      stopAuditExportPolling();
    }
  } catch {
    stopAuditExportPolling();
  }
}

function startAuditExportPolling() {
  stopAuditExportPolling();
  auditExportPollTimer.value = window.setInterval(() => {
    const status = String(auditExportJob.value?.status ?? '').toUpperCase();
    if (!auditExportJob.value?.exportId || ['DONE', 'COMPLETED', 'FAILED'].includes(status)) {
      stopAuditExportPolling();
      return;
    }
    void pollAuditExportStatus();
  }, 3000);
}

function stopAuditExportPolling() {
  if (auditExportPollTimer.value !== null) {
    window.clearInterval(auditExportPollTimer.value);
    auditExportPollTimer.value = null;
  }
}

function isAuditExportFinal(job: AnyRecord | null) {
  const status = String(job?.status ?? '').toUpperCase();
  return ['DONE', 'COMPLETED', 'FAILED'].includes(status);
}

async function downloadAuditExport() {
  const url = auditDownloadUrl.value;
  if (!url) return;
  await runWithNotice(async () => {
    const config = loadDesktopConfig();
    const response = await fetch(apiUrl(url), {
      headers: {
        ...(config.accessToken ? { Authorization: `Bearer ${config.accessToken}` } : {})
      }
    });
    if (!response.ok) {
      throw new Error(`审计文件下载失败：${response.status}`);
    }
    const blob = await response.blob();
    downloadBlob(`${auditExportJob.value?.exportId ?? 'audit-logs'}.csv`, blob);
  }, '审计 CSV 已开始下载');
}

function toggleAuditDetail(log: AnyRecord) {
  expandedAuditId.value = expandedAuditId.value === log.id ? null : log.id;
}

function toggleHealthAlert(alert: AnyRecord) {
  const id = alert.id || alert.createdAt;
  expandedHealthAlertId.value = expandedHealthAlertId.value === id ? null : id;
}

function downloadAnalyticsCsv() {
  const sections = [
    `模块,摘要\n${analyticsBlocks.value.map((block) => `${csvCell(block.title)},${csvCell(block.summary)}`).join('\n')}`,
    ...analyticsDetailSections.value.map((section) => {
      const header = section.columns.map((column) => csvCell(column.label)).join(',');
      const rows = section.rows.map((row) => section.columns.map((column) => csvCell(analyticsCell(row, column.key))).join(',')).join('\n');
      return `${section.title}\n${header}${rows ? `\n${rows}` : ''}`;
    })
  ];
  downloadTextFile(`analytics-${new Date().toISOString().slice(0, 10)}.csv`, sections.join('\n\n'));
}

function listFromResponse(response: ApiResponse<unknown>): AnyRecord[] {
  return listFrom(dataFromResponse(response));
}

function configEntries(response: ApiResponse<unknown>): AnyRecord[] {
  const data = dataFromResponse(response);
  if (Array.isArray(data)) return data as AnyRecord[];
  if (data && typeof data === 'object') {
    const record = data as AnyRecord;
    const nested = listFrom(record);
    if (nested.length) return nested;
    return Object.entries(record).map(([key, value]) => ({ key, configKey: key, value }));
  }
  return [];
}

function dataFromResponse(response: ApiResponse<unknown>): unknown {
  return assertApiSuccess(response) ?? {};
}

function recordFromResponse(response: ApiResponse<unknown>): AnyRecord {
  const data = dataFromResponse(response);
  return data && !Array.isArray(data) && typeof data === 'object' ? data as AnyRecord : {};
}

function listFrom(value: unknown, preferredKey?: string): AnyRecord[] {
  const data = value as AnyRecord;
  if (Array.isArray(value)) return value as AnyRecord[];
  if (preferredKey && Array.isArray(data?.[preferredKey])) return data[preferredKey];
  for (const key of ['items', 'list', 'records', 'datasources', 'fields', 'mappings', 'logs', 'rules', 'categories', 'versions', 'notices', 'staff', 'sources', 'stages', 'ranking', 'actions', 'columns', 'recentAlerts', 'systemAlerts']) {
    if (Array.isArray(data?.[key])) return data[key];
  }
  return [];
}

function withQuery(path: string, query: Record<string, unknown>) {
  const params = new URLSearchParams();
  for (const [key, value] of Object.entries(query)) {
    if (value === undefined || value === null || value === '') continue;
    params.set(key, String(value));
  }
  const text = params.toString();
  return text ? `${path}?${text}` : path;
}

function apiUrl(pathOrUrl: string) {
  if (/^https?:\/\//i.test(pathOrUrl)) {
    return pathOrUrl;
  }
  return `${loadDesktopConfig().apiBaseUrl}${pathOrUrl.startsWith('/') ? pathOrUrl : `/${pathOrUrl}`}`;
}

function auditLogPath() {
  return withQuery('/admin/api/v1/audit-logs', {
    action: auditActionParam(),
    keyword: auditKeyword.value,
    targetType: auditTargetType.value,
    targetId: auditTargetId.value,
    startDate: auditStartDate.value,
    endDate: auditEndDate.value,
    page: auditPageInfo.page,
    size: auditPageInfo.size
  });
}

function accountListPath() {
  return withQuery('/admin/api/v1/accounts', {
    keyword: accountKeyword.value,
    role: accountRoleFilter.value,
    is_enabled: accountEnabledFilter.value,
    page: accountPageInfo.page,
    page_size: accountPageInfo.size
  });
}

function customerSearchListPath() {
  return withQuery('/admin/api/v1/customers/search', {
    q: customerSearchKeyword.value,
    page: customerSearchPageInfo.page,
    page_size: customerSearchPageInfo.size
  });
}

function ruleListPath() {
  return withQuery('/admin/api/v1/rules', {
    keyword: ruleKeyword.value,
    actionType: ruleActionType.value,
    enabled: ruleEnabledFilter.value === '' ? '' : ruleEnabledFilter.value === '1',
    page: rulePageInfo.page,
    size: rulePageInfo.size
  });
}

function quickSearchListPath() {
  return withQuery('/admin/api/v1/quick-search/items', {
    keyword: quickSearchKeyword.value,
    contentType: quickSearchType.value,
    enabled: quickSearchEnabledFilter.value === '' ? '' : quickSearchEnabledFilter.value === '1',
    page: quickSearchPageInfo.page,
    size: quickSearchPageInfo.size
  });
}

function tagCategoryListPath() {
  return withQuery('/admin/api/v1/tags/categories', {
    keyword: tagCategoryKeyword.value,
    enabled: booleanFilter(tagCategoryEnabledFilter.value),
    merged: booleanFilter(tagCategoryMergedFilter.value),
    page: tagCategoryPageInfo.page,
    size: tagCategoryPageInfo.size,
    sortBy: tagCategorySortBy.value,
    sortDirection: tagCategorySortDirection.value
  });
}

function tagValueListPath() {
  return withQuery('/admin/api/v1/tags/values', {
    categoryId: tagValueCategoryFilter.value,
    keyword: tagValueKeyword.value,
    enabled: booleanFilter(tagValueEnabledFilter.value),
    merged: booleanFilter(tagValueMergedFilter.value),
    page: tagValuePageInfo.page,
    size: tagValuePageInfo.size,
    sortBy: tagValueSortBy.value,
    sortDirection: tagValueSortDirection.value
  });
}

function booleanFilter(value: string): boolean | '' {
  if (value === 'true') return true;
  if (value === 'false') return false;
  return '';
}

async function refreshTagCategoryOptionsCache() {
  tagCategoryOptionsCache.value = await fetchAllTagItems('/admin/api/v1/tags/categories', {
    merged: false,
    sortBy: 'sortOrder',
    sortDirection: 'ASC'
  });
}

async function fetchAllTagItems(path: string, query: Record<string, unknown>): Promise<AnyRecord[]> {
  const items: AnyRecord[] = [];
  let page = 1;
  let totalPages = 1;
  do {
    const response = await getJson<unknown>(withQuery(path, { ...query, page, size: 100 }));
    const data = recordFromResponse(response);
    items.push(...listFrom(data));
    totalPages = Math.max(1, Number(data.totalPages ?? 1));
    page += 1;
  } while (page <= totalPages);
  const seen = new Set<string>();
  return items.filter((item) => {
    const key = String(item.id ?? JSON.stringify(item));
    if (seen.has(key)) return false;
    seen.add(key);
    return true;
  });
}

function versionListPath() {
  return withQuery('/admin/api/v1/versions', {
    status: versionStatusFilter.value,
    platform: versionPlatformFilter.value,
    page: versionPageInfo.page,
    size: versionPageInfo.size
  });
}

function noticeListPath() {
  return withQuery('/admin/api/v1/notices', {
    status: noticeStatusFilter.value,
    level: noticeLevelFilter.value,
    source: noticeSourceFilter.value,
    page: noticePageInfo.page,
    size: noticePageInfo.size
  });
}

function auditFilterPayload() {
  return {
    action: auditActionParam() || null,
    operator: null,
    keyword: auditKeyword.value || null,
    targetType: auditTargetType.value || null,
    targetId: auditTargetId.value || null,
    startDate: auditStartDate.value || null,
    endDate: auditEndDate.value || null
  };
}

function auditActionParam() {
  return auditActionsSelected.value.join(',');
}

function quickSearchPayload(item: AnyRecord) {
  return {
    contentType: item.contentType,
    leadType: item.leadType,
    title: item.title,
    shortcutCode: item.shortcutCode,
    content: item.content ?? '',
    imageUrl: item.imageUrl ?? '',
    sortOrder: item.sortOrder ?? 99,
    enabled: item.enabled !== false
  };
}

function tagCategoryOptions() {
  return tagCategoryOptionsCache.value.map((category) => ({
    label: tagCategoryOptionLabel(category),
    value: Number(category.id)
  })).filter((option) => Number.isFinite(option.value));
}

function tagCategoryFieldText(category: AnyRecord) {
  const field = category.boundField || category.fieldName;
  return field ? `写入客户档案：${customerFieldLabel(field)}` : '未绑定客户档案字段';
}

function tagCategoryOptionLabel(category: AnyRecord) {
  const field = category.boundField || category.fieldName;
  return field ? `${tagCategoryName(category)}（${customerFieldLabel(field)}）` : tagCategoryName(category);
}

function tagCategoryName(category: AnyRecord) {
  const categoryName = String(category.categoryName ?? '').trim();
  if (categoryName) return categoryName;
  const categoryKey = String(category.categoryKey ?? '').trim();
  return categoryKey || (category.id == null ? '' : `分类 #${category.id}`);
}

function tagCategoryLabelForValue(tag: AnyRecord) {
  const category = tagCategoryOptionsCache.value.find((item) => String(item.id) === String(tag.categoryId));
  const loadedCategoryName = category ? tagCategoryName(category) : '';
  if (loadedCategoryName) return loadedCategoryName;
  const categoryKey = String(tag.categoryKey ?? '').trim();
  return categoryKey || `分类 #${tag.categoryId}`;
}

function tagSelectionModeLabel(value: unknown) {
  return String(value ?? '').toUpperCase() === 'MULTI' ? '多选' : '单选';
}

function tagAutoUpdateModeLabel(value: unknown) {
  return ({ ADD_ONLY: '只新增', REPLACE: '替换当前值', RECORD_ONLY: '只记录建议' } as Record<string, string>)[String(value ?? '')] ?? '-';
}

function tagUncertainPolicyLabel(value: unknown) {
  return ({ KEEP_CURRENT: '保留当前值', SET_PENDING: '设为待确认' } as Record<string, string>)[String(value ?? '')] ?? '-';
}

function tagSelectableText(tag: AnyRecord) {
  const scopes = [];
  if (tag.systemSelectable) scopes.push('系统');
  if (tag.manualSelectable) scopes.push('员工');
  return scopes.length ? `${scopes.join('、')}可选` : '均不可选';
}

function tagImpactText(impact: AnyRecord | null | undefined) {
  return `客户 ${Number(impact?.customerCount ?? 0)} · 规则 ${Number(impact?.ruleCount ?? 0)} · 历史 ${Number(impact?.historyCount ?? 0)}`;
}

function tagStatusText(item: AnyRecord) {
  if (item.mergedIntoId) return `已合并至 #${item.mergedIntoId}`;
  return item.isEnabled ? '启用' : '停用';
}

function tagUsageText(category: AnyRecord) {
  const usages = [];
  if (category.useForReply) usages.push('回复生成');
  if (category.useForFilter) usages.push('客户筛选');
  if (category.useForStatistics) usages.push('运营统计');
  if (category.useForFollowupRules) usages.push('跟进规则');
  return usages.join('、') || '未启用业务用途';
}

function tagSynonymsText(value: unknown, fallback = '未填写') {
  return Array.isArray(value) && value.length ? value.join('、') : fallback;
}

function booleanLabel(value: unknown) {
  return value === true ? '是' : '否';
}

function tagMergeTargetLabel(target: AnyRecord) {
  return tagMerge.kind === 'category'
    ? `${target.categoryName}（${target.categoryKey}）`
    : `${target.displayName}（${target.tagValue}）`;
}

function isBuiltinTagCategory(category: AnyRecord) {
  return category.isBuiltin === true || category.builtin === true;
}

function isCurrentAccount(account: AnyRecord) {
  const current = props.accountName?.trim();
  if (!current) return false;
  return [account.username, account.phone, account.displayName].some((value) => String(value ?? '') === current);
}

function leaderOptions() {
  const options = leaderAccounts.value
    .filter((account) => account.role === 'LEADER' && account.isEnabled !== false)
    .map((account) => ({ label: account.displayName || account.phone || account.username || `#${account.id}`, value: account.id }));
  return [{ label: '不指定', value: '' }, ...options];
}

function isActiveEnvironment(env: AnyRecord) {
  return env.isActive === true || env.active === true;
}

function canDeleteEnvironment(kind: 'skill' | 'image' | 'llm', env: AnyRecord) {
  const list = kind === 'skill'
    ? skillEnvironments.value
    : kind === 'image'
      ? imageEnvironments.value
      : llmEnvironments.value;
  return !isActiveEnvironment(env) && list.length > 1;
}

function isBuiltinRule(rule: AnyRecord) {
  return rule.builtin === true || rule.isBuiltin === true;
}

function versionStatus(version: AnyRecord) {
  return String(version.status ?? 'DRAFT').toUpperCase();
}

function canEditVersion(version: AnyRecord) {
  return versionStatus(version) === 'DRAFT';
}

function canPublishVersion(version: AnyRecord) {
  return versionStatus(version) === 'DRAFT';
}

function canRevokeVersion(version: AnyRecord) {
  return versionStatus(version) === 'PUBLISHED';
}

function canDeleteVersion(version: AnyRecord) {
  return versionStatus(version) === 'DRAFT';
}

function versionActionReason(version: AnyRecord, action: 'edit' | 'publish' | 'revoke' | 'delete') {
  const status = versionStatus(version);
  if (action === 'revoke') return status === 'PUBLISHED' ? '撤回已发布版本' : '只有已发布版本可以撤回';
  if (action === 'publish') return status === 'DRAFT' ? '发布草稿版本' : '只有草稿版本可以发布';
  if (action === 'delete') return status === 'DRAFT' ? '删除未发布草稿' : '只有草稿版本可以删除';
  return status === 'DRAFT' ? '编辑草稿版本' : '已发布或已撤回版本不能编辑';
}

function versionStatusLabel(value: unknown) {
  return ({ DRAFT: '草稿', PUBLISHED: '已发布', REVOKED: '已撤回' } as Record<string, string>)[String(value ?? 'DRAFT').toUpperCase()] ?? String(value ?? '草稿');
}

function versionStatusClass(version: AnyRecord) {
  const status = versionStatus(version);
  return status === 'PUBLISHED' ? 'ok-text' : status === 'REVOKED' ? 'warn-text' : '';
}

function isStoppedNotice(item: AnyRecord) {
  return item.isStopped === true || item.stopped === true || item.status === 'STOPPED';
}

function isAutoNotice(item: AnyRecord) {
  return String(item.source ?? '').toUpperCase() === 'AUTO';
}

function noticeStatus(item: AnyRecord) {
  if (isStoppedNotice(item)) return 'STOPPED';
  return String(item.status ?? '').toUpperCase();
}

function canEditNotice(item: AnyRecord) {
  return !isAutoNotice(item) && !isStoppedNotice(item) && noticeStatus(item) === 'SCHEDULED';
}

function canStopNotice(item: AnyRecord) {
  return !isStoppedNotice(item);
}

function canDeleteNotice(item: AnyRecord) {
  return isStoppedNotice(item);
}

function noticeActionReason(item: AnyRecord, action: 'edit' | 'stop' | 'delete') {
  if (isAutoNotice(item) && action === 'edit') return '系统自动公告只读';
  if (action === 'edit') return canEditNotice(item) ? '编辑未发布定时公告' : '只有未停止的定时公告可以编辑';
  if (action === 'stop') return canStopNotice(item) ? '停止后桌面端不再展示' : '公告已停止';
  return canDeleteNotice(item) ? '删除已停止公告' : '只有已停止公告可以删除';
}

function noticeStatusLabel(item: AnyRecord) {
  const status = noticeStatus(item);
  return ({ PUBLISHED: '已发布', SCHEDULED: '待发布', STOPPED: '已停止' } as Record<string, string>)[status] ?? (status || '未知状态');
}

function noticeSourceLabel(value: unknown) {
  return ({ MANUAL: '人工公告', AUTO: '系统自动' } as Record<string, string>)[String(value ?? '').toUpperCase()] ?? String(value ?? '未知来源');
}

function noticeLevelLabel(value: unknown) {
  return ({ INFO: '普通', WARN: '提醒', ERROR: '故障' } as Record<string, string>)[String(value ?? '').toUpperCase()] ?? String(value ?? '-');
}

function ruleDraft(item?: AnyRecord) {
  const parsed = parseRuleCondition(item?.conditionJson);
  const action = parseRuleAction(item?.actionConfig);
  return {
    name: item?.name ?? '',
    leadType: parsed.leadType ?? 'GENERAL',
    actionType: item?.actionType ?? 'ALERT',
    thresholdHours: parsed.thresholdHours ?? 24,
    alertLevel: action.alertLevel ?? 'WARN',
    reminderType: action.reminderType ?? (item?.actionType === 'TAG_CHANGE' ? 'TAG_SUGGESTION' : 'OVERDUE'),
    tagName: action.tagName ?? '',
    reason: action.reason ?? '',
    priority: item?.priority ?? 90,
    enabled: item?.enabled ?? true
  };
}

function parseRuleCondition(value: unknown) {
  const fallback: { leadType?: string; thresholdHours?: number } = {};
  if (!value) return fallback;
  try {
    const parsed = JSON.parse(String(value));
    const conditions = Array.isArray(parsed.conditions) ? parsed.conditions : [];
    const leadType = conditions.find((item: AnyRecord) => item.field === 'leadType')?.value ?? 'GENERAL';
    const threshold = conditions.find((item: AnyRecord) => ['hoursSinceLastFollowup', 'lastFollowupHours'].includes(item.field))?.value;
    return { leadType, thresholdHours: Number(threshold) || undefined };
  } catch {
    return fallback;
  }
}

function parseRuleAction(value: unknown) {
  if (!value) return {};
  try {
    const parsed = JSON.parse(String(value));
    return parsed && typeof parsed === 'object' ? parsed as AnyRecord : {};
  } catch {
    return {};
  }
}

function normalizeCustomerFields(response: ApiResponse<unknown>) {
  const fields = listFromResponse(response);
  return fields.length ? fields.map((field) => ({ key: field.key ?? field.fieldName ?? field.name, label: field.label ?? field.displayName ?? field.name ?? field.key })) : [
    { key: 'phone', label: '手机号' },
    { key: 'nickname', label: '客户昵称' },
    { key: 'leadType', label: '线索类型' },
    { key: 'intentLevel', label: '意向度' },
    { key: 'customerStage', label: '客户阶段' }
  ];
}

function configValue(key: string): string {
  const config = configs.value.find((item) => item.configKey === key || item.key === key);
  return String(config?.value ?? '');
}

function intConfigValue(key: string, fallback: number): number {
  const parsed = Number.parseInt(configValue(key), 10);
  return Number.isFinite(parsed) ? parsed : fallback;
}

function numberConfigValue(key: string, fallback: number): number {
  const parsed = Number.parseFloat(configValue(key));
  return Number.isFinite(parsed) ? parsed : fallback;
}

function boolConfigValue(key: string, fallback: boolean): boolean {
  const raw = configValue(key).trim().toLowerCase();
  if (!raw) return fallback;
  if (['true', '1', 'yes', 'on'].includes(raw)) return true;
  if (['false', '0', 'no', 'off'].includes(raw)) return false;
  return fallback;
}

function configSecretStatus(key: string): string {
  const value = configValue(key);
  return value ? value : '未配置';
}

function parseTextList(value: string): string[] {
  if (!value) return [];
  try {
    const parsed = JSON.parse(value);
    if (Array.isArray(parsed)) return parsed.map(String);
  } catch {
    return value.split(/\r?\n/);
  }
  return [];
}

function linesFrom(value: string) {
  return value.split(/\r?\n/).map((item) => item.trim()).filter(Boolean);
}

function sceneLabel(value: string) {
  return SKILL_SCENE_OPTIONS.find((option) => option.value === value)?.label ?? value ?? '-';
}

function llmSceneLabel(value: unknown) {
  const scene = String(value ?? '');
  return (LLM_SCENE_LABELS[scene] ?? scene) || '-';
}

function llmSceneOptions() {
  const scenes = llmRouteScenes.value.length ? llmRouteScenes.value : Object.keys(LLM_SCENE_LABELS);
  return scenes.map((scene) => ({ label: llmSceneLabel(scene), value: scene }));
}

function leadTypeLabel(value: string) {
  const text = String(value ?? '');
  if (!text) return '全部客资';
  return ({ GENERAL: '全部客资', TUAN_GOU: '团购客资', XIAN_SUO: '线索客资', PENDING: '待确认' } as Record<string, string>)[text] ?? text;
}

function contentTypeLabel(value: string) {
  return ({ TEMPLATE: '话术模板', KNOWLEDGE: '知识片段', LOCATION: '门店定位', IMAGE: '图片素材', MINI_PROGRAM: '小程序引导' } as Record<string, string>)[value] ?? value ?? '-';
}

function roleLabel(value: string) {
  return ({ ADMIN: '管理员', LEADER: '组长', KEEPER: '管家' } as Record<string, string>)[value] ?? value ?? '-';
}

function actionTypeLabel(value: string) {
  return ({ ALERT: '提醒/告警', TAG_CHANGE: '标签建议', TAG_SUGGESTION: '标签建议', NOTIFY_LEADER: '通知组长' } as Record<string, string>)[value] ?? value ?? '-';
}

function actionLabel(value: unknown) {
  const action = String(value ?? '');
  return auditActions.value.find((item) => item.action === action)?.label ?? action;
}

function targetTypeLabel(value: unknown) {
  const type = String(value ?? '');
  return auditTargetTypes.value.find((item) => item.type === type)?.label ?? type;
}

function updateStrategyLabel(value: string) {
  return ({ OPTIONAL: '可选升级', FORCED: '强制升级', FORCE: '强制升级', GRADUAL: '灰度发布' } as Record<string, string>)[value] ?? value ?? '-';
}

function formatFileSize(value: unknown) {
  const bytes = Number(value);
  if (!Number.isFinite(bytes) || bytes <= 0) return '大小未知';
  if (bytes < 1024) return `${bytes} B`;
  if (bytes < 1024 * 1024) return `${(bytes / 1024).toFixed(1)} KB`;
  return `${(bytes / 1024 / 1024).toFixed(1)} MB`;
}

function leaderName(leaderId: unknown) {
  if (!leaderId) return '-';
  const leader = [...accounts.value, ...leaderAccounts.value].find((account) => String(account.id) === String(leaderId));
  return leader?.displayName || leader?.username || `#${leaderId}`;
}

function syncStatusFor(id: unknown) {
  const status = syncStatuses.value.find((item) => String(item.datasourceId ?? item.id) === String(id));
  return status?.syncStatus || status?.status || '待同步';
}

function mappingCountFor(id: unknown) {
  const status = syncStatuses.value.find((item) => String(item.datasourceId ?? item.id) === String(id));
  return status?.mappingCount ?? '-';
}

function columnName(column: AnyRecord | string) {
  return typeof column === 'string' ? column : String(column.name ?? column.columnName ?? '');
}

function columnMapped(column: AnyRecord | string) {
  return typeof column !== 'string' && column.mapped === true;
}

function columnTarget(column: AnyRecord | string) {
  return typeof column === 'string' ? '' : String(column.targetField ?? '');
}

function customerFieldLabel(fieldKey: unknown) {
  const key = String(fieldKey ?? '');
  return customerFields.value.find((field) => field.key === key)?.label ?? CUSTOMER_FIELD_LABELS[key] ?? key;
}

function mappingDiffKey(item: AnyRecord) {
  return item.id ?? item.sourceField ?? item.before?.sourceField ?? item.after?.sourceField ?? JSON.stringify(item);
}

function mappingDiffText(item: AnyRecord) {
  if (item.before || item.after) {
    const before = item.before ?? {};
    const after = item.after ?? {};
    return `${before.sourceField ?? after.sourceField ?? '-'}：${before.targetField ?? '-'} -> ${after.targetField ?? '-'}${after.enabled === false ? '（停用）' : ''}`;
  }
  return `${item.sourceField ?? '-'} -> ${item.targetField ?? '-'}${item.enabled === false ? '（停用）' : ''}`;
}

function importLogSummary(log: AnyRecord) {
  return `总 ${log.totalRows ?? 0}，新增 ${log.created ?? 0}，更新 ${log.updated ?? 0}，跳过 ${log.skipped ?? 0}`;
}

function analyticsLeadType() {
  return skillLeadTypeFilter.value === 'GENERAL' ? '' : skillLeadTypeFilter.value;
}

function analyticsCell(row: AnyRecord, key: string) {
  const value = row?.[key];
  if (key === 'phone') return value ? String(value).replace(/^(\d{3})\d{4}(\d{4})$/, '$1****$2') : '-';
  if (key === 'leadType') return leadTypeLabel(String(value ?? ''));
  if (key === 'customerStage') return translateValue(value);
  if (String(key).endsWith('At') || key === 'date') return formatDate(value);
  if (value === undefined || value === null || value === '') return '-';
  if (typeof value === 'object') return summarizeObject(value);
  return translateValue(value);
}

function imageTestLabel(env: AnyRecord) {
  if (env.lastTestOk === true || env.lastTestOk === 1) return `连接正常 · ${formatDate(env.lastTestAt)}`;
  if (env.lastTestOk === false || env.lastTestOk === 0) return `测试失败 · ${formatDate(env.lastTestAt)}`;
  return '未完成连接测试';
}

function llmEnvironmentLabel(env: AnyRecord) {
  const model = env.model || '未填写模型';
  const protocol = env.protocol || 'OPENAI_COMPATIBLE';
  const timeout = env.timeoutMs ? `${env.timeoutMs}ms` : '默认超时';
  return `${model} · ${protocol} · ${timeout} · ${imageTestLabel(env)}`;
}

function llmEnvironmentName(id: unknown) {
  if (id === undefined || id === null || id === '') return '-';
  const env = llmEnvironments.value.find((item) => String(item.id) === String(id));
  return env?.envName || `#${id}`;
}

function llmRouteTitle(route: AnyRecord) {
  return `${llmSceneLabel(route.scene)} / ${route.leadType ? leadTypeLabel(route.leadType) : '通用'} / ${route.environmentName || llmEnvironmentName(route.environmentId)}`;
}

function percentLabel(value: unknown) {
  const parsed = Number(value);
  if (!Number.isFinite(parsed)) return '0%';
  const percent = parsed <= 1 ? parsed * 100 : parsed;
  return `${Math.round(percent * 10) / 10}%`;
}

function healthAlertTitle(alert: AnyRecord) {
  return alert.title || alert.alertType || alert.type || alert.sourceModule || alert.component || '系统告警';
}

function healthAlertStatusLabel(alert: AnyRecord) {
  const status = String(alert.status ?? 'OPEN').toUpperCase();
  return ({ OPEN: '未恢复', RESOLVED: '已恢复', RECOVERED: '已恢复' } as Record<string, string>)[status] ?? status;
}

function healthAlertDuration(alert: AnyRecord) {
  const start = dateTime(alert.occurredAt || alert.createdAt || alert.lastSeenAt);
  const end = dateTime(alert.resolvedAt) ?? new Date();
  if (!start) return '-';
  const seconds = Math.max(0, Math.floor((end.getTime() - start.getTime()) / 1000));
  if (seconds < 60) return `${seconds} 秒`;
  const minutes = Math.floor(seconds / 60);
  if (minutes < 60) return `${minutes} 分钟`;
  const hours = Math.floor(minutes / 60);
  return `${hours} 小时 ${minutes % 60} 分钟`;
}

function rulePreview(rule: AnyRecord) {
  if (rule.conditionPreview) return rule.conditionPreview;
  const text = String(rule.conditionJson ?? '');
  if (text.includes('leadType')) return '按线索类型和跟进间隔触发';
  return '规则条件由条件构建器生成';
}

function activeEnvironmentName(items: AnyRecord[]) {
  const item = items.find((env) => isActiveEnvironment(env));
  return item ? `当前使用：${item.envName}` : '尚未启用环境';
}

function summarizeObject(value: unknown) {
  if (value === undefined || value === null || value === '') return '暂无数据';
  if (Array.isArray(value)) return value.length ? `${value.length} 条记录` : '暂无数据';
  if (typeof value !== 'object') return translateValue(value);
  const entries = Object.entries(value as AnyRecord)
    .filter(([, item]) => item !== undefined && item !== null && item !== '')
    .slice(0, 4)
    .map(([key, item]) => `${analyticsKeyLabel(key)}：${summarizeValue(item)}`);
  return entries.join(' · ') || '暂无数据';
}

function summarizeValue(value: unknown): string {
  if (value === undefined || value === null || value === '') return '-';
  if (Array.isArray(value)) return `${value.length} 条`;
  if (typeof value === 'object') return summarizeObject(value);
  if (typeof value === 'number' && Number.isFinite(value) && value > 0 && value < 1) return percentLabel(value);
  return translateValue(value);
}

function analyticsKeyLabel(key: string) {
  return ANALYTICS_KEY_LABELS[key] ?? customerFieldLabel(key);
}

function translateValue(value: unknown): string {
  const text = String(value ?? '');
  if (!text) return '-';
  return TRANSLATED_VALUE_LABELS[text] ?? text;
}

function summarizeSkillTest(value: AnyRecord) {
  const suggestions = listFrom(value, 'suggestions');
  if (suggestions.length) return suggestions.map((item) => item.text || item.content || item.reply || summarizeObject(item)).join('；');
  if (value.rawResponse) return summarizeObject(value.rawResponse);
  return summarizeObject(value);
}

function textSnippet(value: unknown) {
  const text = String(value ?? '');
  return text.length > 140 ? `${text.slice(0, 140)}...` : text || '暂无内容';
}

function formatDetail(value: unknown) {
  if (value === undefined || value === null || value === '') return '暂无详情';
  if (typeof value === 'string') {
    try {
      return JSON.stringify(JSON.parse(value), null, 2);
    } catch {
      return value;
    }
  }
  return JSON.stringify(value, null, 2);
}

function csvCell(value: unknown) {
  return `"${String(value ?? '').replace(/"/g, '""')}"`;
}

function downloadTextFile(filename: string, content: string) {
  const blob = new Blob([`\ufeff${content}`], { type: 'text/csv;charset=utf-8' });
  downloadBlob(filename, blob);
}

function downloadBlob(filename: string, blob: Blob) {
  const url = URL.createObjectURL(blob);
  const link = document.createElement('a');
  link.href = url;
  link.download = filename;
  link.click();
  URL.revokeObjectURL(url);
}

function isOkStatus(value: unknown) {
  const text = String(value ?? '').toUpperCase();
  return ['OK', 'UP', 'HEALTHY', 'NORMAL', 'RUNNING'].some((item) => text.includes(item));
}

function formatDate(value: unknown) {
  if (!value) return '-';
  const date = dateTime(value);
  return date ? date.toLocaleString('zh-CN') : String(value);
}

function dateTime(value: unknown) {
  if (!value) return null;
  const date = new Date(String(value));
  return Number.isNaN(date.getTime()) ? null : date;
}
</script>
