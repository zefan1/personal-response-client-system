<template>
  <section class="ops-admin-shell">
    <aside class="ops-admin-sidebar">
      <div class="ops-admin-brand">
        <strong>运营后台</strong>
        <span>{{ sessionLabel }}</span>
      </div>
      <button
        v-for="section in sections"
        :key="section.key"
        class="ops-admin-nav"
        :class="{ active: activeSectionKey === section.key }"
        type="button"
        @click="selectSection(section.key)"
      >
        <span>{{ section.title }}</span>
        <small>{{ section.subtitle }}</small>
      </button>
      <div class="ops-admin-sidebar-actions">
        <button class="secondary small" type="button" @click="$emit('switch-desktop')">桌面工作台</button>
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

      <section v-if="activeSection.key === 'skill-ai'" class="ops-admin-layout">
        <article class="ops-panel wide">
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
              <option value="OPENING">开场白</option>
              <option value="ACTIVE_REPLY">主动回复</option>
              <option value="REGENERATE">换一组</option>
            </select>
            <select v-model="skillLeadTypeFilter" @change="loadSkillAi">
              <option value="">全部线索类型</option>
              <option value="GENERAL">通用</option>
              <option value="TUAN_GOU">团购客资</option>
              <option value="XIAN_SUO">线索客资</option>
              <option value="PENDING">待确认</option>
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

        <article class="ops-panel wide">
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

        <article class="ops-panel">
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
              <button class="secondary small danger" type="button" :disabled="isActiveEnvironment(env)" @click="confirmDeleteEnvironment('skill', env)">删除</button>
            </div>
          </div>
          <p v-if="!skillEnvironments.length" class="ops-empty">暂无 Skill 环境，请先新增。</p>
        </article>

        <article class="ops-panel">
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
              <button class="secondary small danger" type="button" :disabled="isActiveEnvironment(env)" @click="confirmDeleteEnvironment('image', env)">删除</button>
            </div>
          </div>
          <p v-if="!imageEnvironments.length" class="ops-empty">暂无识图环境，请先新增。</p>
        </article>

        <article class="ops-panel wide">
          <div class="ops-panel-head">
            <div>
              <h2>Prompt 与规则</h2>
              <p>红线逐条编辑，前缀规则一行一个，保存后立即生效。</p>
            </div>
            <button class="primary small" type="button" @click="savePromptSettings">保存配置</button>
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

      <section v-else-if="activeSection.key === 'data-content'" class="ops-admin-layout">
        <article class="ops-panel wide">
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

        <article class="ops-panel wide">
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
              {{ field.label || field.key }}
              <input v-model="mappingDraft[field.key]" :list="`columns-${field.key}`" :placeholder="field.key === 'phone' ? '必须映射手机号列' : '填写表格列名'" />
              <datalist :id="`columns-${field.key}`">
                <option v-for="column in datasourceColumns" :key="`${field.key}-${columnName(column)}`" :value="columnName(column)" />
              </datalist>
            </label>
          </div>
          <div v-if="mappingCompare" class="ops-detail-box">
            <strong>映射差异</strong>
            <p>{{ summarizeObject(mappingCompare.summary) }}</p>
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

        <article class="ops-panel">
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
            <p>{{ summarizeObject(csvImportResult) }}</p>
          </div>
          <div v-if="importLogs.length" class="ops-preview-list">
            <strong>最近导入记录</strong>
            <span v-for="log in importLogs.slice(0, 4)" :key="log.id || log.createdAt">{{ formatDate(log.createdAt) }} · {{ log.status || log.result || '已记录' }}</span>
          </div>
          <p v-if="!csvPreview.length && !importLogs.length" class="ops-empty">拖拽或选择 CSV 文件。</p>
        </article>

        <article class="ops-panel wide">
          <div class="ops-panel-head">
            <div>
              <h2>速搜内容</h2>
              <p>管理话术模板、知识片段、门店定位、图片素材和小程序引导。</p>
            </div>
            <button class="primary small" type="button" @click="openForm('quickSearch')">新增内容</button>
          </div>
          <div class="ops-filter-bar">
            <input v-model="quickSearchKeyword" placeholder="搜索标题、快线码或正文" />
            <select v-model="quickSearchType">
              <option value="">全部类型</option>
              <option value="TEMPLATE">话术模板</option>
              <option value="KNOWLEDGE">知识片段</option>
              <option value="LOCATION">门店定位</option>
              <option value="IMAGE">图片素材</option>
              <option value="MINI_PROGRAM">小程序引导</option>
            </select>
          </div>
          <div class="ops-row-actions">
            <button class="secondary small" type="button" :disabled="!quickSearchSelectedIds.length" @click="batchToggleQuickSearch(false)">批量停用</button>
            <button class="secondary small" type="button" :disabled="!quickSearchSelectedIds.length" @click="batchToggleQuickSearch(true)">批量启用</button>
            <button class="secondary small danger" type="button" :disabled="!quickSearchSelectedIds.length" @click="batchDeleteQuickSearch">批量删除</button>
          </div>
          <div class="ops-card-grid">
            <article v-for="item in filteredQuickSearchItems" :key="item.id" class="ops-content-card">
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
          <p v-if="!filteredQuickSearchItems.length" class="ops-empty">暂无匹配内容。</p>
        </article>
      </section>

      <section v-else-if="activeSection.key === 'org-rules-tags'" class="ops-admin-layout">
        <article class="ops-panel wide">
          <div class="ops-panel-head">
            <div>
              <h2>账号与权限</h2>
              <p>ADMIN/LEADER 可进后台，KEEPER 仅使用桌面端。</p>
            </div>
            <button class="primary small" type="button" @click="openForm('account')">新增账号</button>
          </div>
          <div class="ops-filter-bar three">
            <input v-model="accountKeyword" placeholder="搜索姓名或手机号" @change="loadOrgRulesTags" />
            <select v-model="accountRoleFilter" @change="loadOrgRulesTags">
              <option value="">全部角色</option>
              <option value="ADMIN">管理员</option>
              <option value="LEADER">组长</option>
              <option value="KEEPER">管家</option>
            </select>
            <select v-model="accountEnabledFilter" @change="loadOrgRulesTags">
              <option value="">全部状态</option>
              <option value="1">启用中</option>
              <option value="0">已停用</option>
            </select>
          </div>
          <div class="ops-table">
            <div class="ops-table-row head">
              <span>姓名</span>
              <span>手机号</span>
              <span>角色</span>
              <span>直属组长</span>
              <span>状态</span>
              <span>操作</span>
            </div>
            <div v-for="account in accounts" :key="account.id" class="ops-table-row">
              <span>{{ account.displayName || account.username }}</span>
              <span>{{ account.phone || account.username }}</span>
              <span>{{ roleLabel(account.role) }}</span>
              <span>{{ leaderName(account.leaderId) }}</span>
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
        </article>

        <article class="ops-panel wide">
          <div class="ops-panel-head">
            <div>
              <h2>跟进规则</h2>
              <p>条件构建器会生成业务语言预览，内置规则不能删除。</p>
            </div>
            <button class="primary small" type="button" @click="openForm('rule')">新增规则</button>
          </div>
          <div class="ops-filter-bar">
            <input v-model="ruleKeyword" placeholder="搜索规则名称" />
            <select v-model="ruleActionType">
              <option value="">全部动作</option>
              <option value="ALERT">提醒/告警</option>
              <option value="TAG_SUGGESTION">标签建议</option>
              <option value="NOTIFY_LEADER">通知组长</option>
            </select>
          </div>
          <div class="ops-card-grid">
            <article v-for="rule in filteredRules" :key="rule.id" class="ops-rule-card">
              <div>
                <strong>{{ rule.name }}</strong>
                <span>{{ actionTypeLabel(rule.actionType) }} · 优先级 {{ rule.priority }}</span>
                <p>{{ rulePreview(rule) }}</p>
              </div>
              <div class="ops-row-actions">
                <button class="secondary small" type="button" @click="openForm('rule', rule)">编辑</button>
                <button class="secondary small" type="button" @click="confirmToggleRule(rule)">{{ rule.enabled === false ? '启用' : '停用' }}</button>
                <button class="secondary small danger" type="button" :disabled="rule.isBuiltin" @click="confirmDeleteRule(rule)">删除</button>
              </div>
            </article>
          </div>
          <p v-if="!filteredRules.length" class="ops-empty">暂无匹配规则。</p>
        </article>

        <article class="ops-panel wide">
          <div class="ops-panel-head">
            <div>
              <h2>标签与分层</h2>
              <p>展示名可改，代码值稳定；有客户占用时建议禁用。</p>
            </div>
            <button class="primary small" type="button" @click="openForm('tagCategory')">新增分类</button>
          </div>
          <div class="ops-filter-bar">
            <input v-model="tagKeyword" placeholder="搜索分类或标签" />
            <button class="secondary small" type="button" :disabled="!tagKeyword" @click="tagKeyword = ''">清空筛选</button>
          </div>
          <div class="ops-card-grid">
            <article v-for="category in filteredTagCategories" :key="category.id" class="ops-tag-card">
              <div>
                <strong>{{ category.categoryName || category.name }}</strong>
                <span>{{ category.boundField || category.fieldName || '未绑定字段' }}</span>
              </div>
              <div class="ops-tag-list">
                <span v-for="tag in category.values || category.tags || []" :key="tag.id || tag.tagValue" class="ops-tag-value">
                  <button class="status-pill" type="button" @click="openForm('tagValue', tagDraft(category, tag))">
                    {{ tag.displayName || tag.tagValue }}
                  </button>
                  <button class="secondary small" type="button" @click="toggleTagValue(category, tag)">{{ tag.isEnabled === false ? '启用' : '停用' }}</button>
                  <button class="secondary small danger" type="button" @click="confirmDeleteTagValue(category, tag)">删除</button>
                </span>
              </div>
              <div class="ops-row-actions">
                <button class="secondary small" type="button" @click="openForm('tagValue', category)">新增标签值</button>
                <button class="secondary small" type="button" @click="openForm('tagCategory', category)">编辑分类</button>
                <button class="secondary small danger" type="button" :disabled="category.isBuiltin" @click="confirmDeleteTagCategory(category)">删除分类</button>
              </div>
            </article>
          </div>
          <p v-if="!filteredTagCategories.length" class="ops-empty">暂无标签分类。</p>
        </article>
      </section>

      <section v-else class="ops-admin-layout">
        <article class="ops-panel wide">
          <div class="ops-panel-head">
            <div>
              <h2>运营分析看板</h2>
              <p>主管在一页看清 AI 使用、漏斗、同事效能、来源、阶段、风险和内容排行。</p>
            </div>
          </div>
          <div class="ops-filter-bar three">
            <select v-model="skillAnalyticsDays" @change="loadInsightOps">
              <option :value="7">近 7 天</option>
              <option :value="14">近 14 天</option>
              <option :value="30">近 30 天</option>
            </select>
            <select v-model="skillLeadTypeFilter" @change="loadInsightOps">
              <option value="">全部线索类型</option>
              <option value="GENERAL">通用</option>
              <option value="TUAN_GOU">团购客资</option>
              <option value="XIAN_SUO">线索客资</option>
              <option value="PENDING">待确认</option>
            </select>
            <button class="secondary small" type="button" @click="downloadAnalyticsCsv">导出当前看板</button>
          </div>
          <div class="ops-analytics-grid">
            <div v-for="block in analyticsBlocks" :key="block.title" class="ops-analytics-block">
              <h3>{{ block.title }}</h3>
              <p>{{ block.summary }}</p>
            </div>
          </div>
        </article>

        <article class="ops-panel wide">
          <div class="ops-panel-head">
            <div>
              <h2>桌面版本</h2>
              <p>发布、灰度、撤回都需要保留可追溯记录。</p>
            </div>
            <button class="primary small" type="button" @click="openForm('version')">新增版本</button>
          </div>
          <div class="ops-filter-bar">
            <select v-model="versionStatusFilter" @change="loadInsightOps">
              <option value="">全部状态</option>
              <option value="DRAFT">草稿</option>
              <option value="PUBLISHED">已发布</option>
              <option value="REVOKED">已撤回</option>
            </select>
            <select v-model="versionPlatformFilter" @change="loadInsightOps">
              <option value="">全部平台</option>
              <option value="WINDOWS">Windows</option>
              <option value="MAC">macOS</option>
            </select>
          </div>
          <div class="ops-table">
            <div class="ops-table-row head">
              <span>版本</span>
              <span>平台</span>
              <span>策略</span>
              <span>状态</span>
              <span>操作</span>
            </div>
            <div v-for="version in versions" :key="version.id" class="ops-table-row compact">
              <span>{{ version.version }}</span>
              <span>{{ version.platform }}</span>
              <span>{{ updateStrategyLabel(version.updateStrategy) }}</span>
              <span>{{ version.status || 'DRAFT' }}</span>
              <span class="ops-row-actions">
                <button class="secondary small" type="button" @click="openForm('version', version)">编辑</button>
                <button class="secondary small" type="button" @click="confirmPublishVersion(version)">发布</button>
                <button class="secondary small danger" type="button" @click="openForm('revokeVersion', version)">撤回</button>
                <button class="secondary small danger" type="button" @click="confirmDeleteVersion(version)">删除</button>
              </span>
            </div>
          </div>
        </article>

        <article class="ops-panel">
          <div class="ops-panel-head">
            <div>
              <h2>系统公告</h2>
              <p>只发布工具能力变化和运行状态通知。</p>
            </div>
            <button class="primary small" type="button" @click="openForm('notice')">新增公告</button>
          </div>
          <div class="ops-filter-bar">
            <select v-model="noticeStatusFilter" @change="loadInsightOps">
              <option value="">全部状态</option>
              <option value="ACTIVE">生效中</option>
              <option value="SCHEDULED">待发布</option>
              <option value="STOPPED">已停止</option>
            </select>
            <select v-model="noticeLevelFilter" @change="loadInsightOps">
              <option value="">全部级别</option>
              <option value="INFO">普通</option>
              <option value="WARN">提醒</option>
              <option value="ERROR">故障</option>
            </select>
          </div>
          <div v-for="item in notices" :key="item.id" class="ops-notice-row">
            <strong>{{ item.title }}</strong>
            <span>{{ item.level }} · {{ item.status || item.publishType || '有效' }}</span>
            <p>{{ item.content }}</p>
            <div class="ops-row-actions">
              <button class="secondary small" type="button" @click="openForm('notice', item)">编辑</button>
              <button class="secondary small" type="button" @click="confirmStopNotice(item)">停止</button>
              <button class="secondary small danger" type="button" @click="confirmDeleteNotice(item)">删除</button>
            </div>
          </div>
          <p v-if="!notices.length" class="ops-empty">暂无公告。</p>
        </article>

        <article class="ops-panel">
          <div class="ops-panel-head">
            <div>
              <h2>审计日志</h2>
              <p>查询、筛选、导出，不允许修改日志。</p>
            </div>
            <button class="secondary small" type="button" @click="exportAuditLogs">导出 CSV</button>
          </div>
          <div class="ops-filter-bar">
            <input v-model="auditKeyword" placeholder="搜索操作人或对象" />
            <select v-model="auditAction">
              <option value="">全部动作</option>
              <option v-for="action in auditActions" :key="action" :value="action">{{ action }}</option>
            </select>
          </div>
          <div class="ops-filter-bar three">
            <input v-model="auditTargetType" placeholder="对象类型" @change="loadAuditLogs" />
            <input v-model="auditStartDate" type="date" @change="loadAuditLogs" />
            <input v-model="auditEndDate" type="date" @change="loadAuditLogs" />
          </div>
          <div v-if="auditExportJob" class="ops-detail-box">
            <strong>导出任务：{{ auditExportJob.status || '处理中' }}</strong>
            <p>{{ auditExportJob.message || summarizeObject(auditExportJob) }}</p>
            <button class="secondary small" type="button" @click="refreshAuditExportStatus">刷新导出状态</button>
          </div>
          <div v-for="log in filteredAuditLogs" :key="log.id" class="ops-notice-row">
            <strong>{{ log.action }}</strong>
            <span>{{ log.operator || '-' }} · {{ formatDate(log.createdAt) }}</span>
            <p>{{ log.detail || log.target || '无详情' }}</p>
            <button class="secondary small" type="button" @click="toggleAuditDetail(log)">查看详情</button>
            <pre v-if="expandedAuditId === log.id" class="ops-raw-detail">{{ formatDetail(log.detailJson ?? log.detail ?? log) }}</pre>
          </div>
          <p v-if="!filteredAuditLogs.length" class="ops-empty">暂无审计记录。</p>
        </article>

        <article class="ops-panel wide">
          <div class="ops-panel-head">
            <div>
              <h2>系统健康</h2>
              <p>展示故障和恢复情况，连续失败后转手动刷新。</p>
            </div>
            <button class="secondary small" type="button" @click="loadHealth(true)">手动刷新</button>
          </div>
          <div class="ops-filter-bar">
            <span class="ops-inline-info">最近刷新：{{ healthLastRefreshAt || '尚未刷新' }}</span>
            <select v-model="healthAlertStatusFilter">
              <option value="">全部告警</option>
              <option value="OPEN">未恢复</option>
              <option value="RECOVERED">已恢复</option>
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
              <strong>{{ alert.title || alert.type || alert.component || '系统告警' }}</strong>
              <span>{{ alert.status || 'OPEN' }} · {{ formatDate(alert.createdAt || alert.lastSeenAt) }}</span>
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
          <button class="secondary small" type="button" @click="closeForm">关闭</button>
        </header>
        <div class="ops-form-grid one">
          <label v-for="field in activeFormFields" :key="field.key">
            {{ field.label }}
            <select v-if="field.type === 'select'" v-model="formDraft[field.key]">
              <option v-for="option in field.options ?? []" :key="String(option.value)" :value="option.value">{{ option.label }}</option>
            </select>
            <textarea v-else-if="field.type === 'textarea'" v-model="formDraft[field.key]" :placeholder="field.placeholder" rows="5"></textarea>
            <input v-else-if="field.type === 'checkbox'" v-model="formDraft[field.key]" type="checkbox" />
            <input v-else-if="field.type === 'number'" v-model.number="formDraft[field.key]" type="number" :placeholder="field.placeholder" />
            <input v-else v-model="formDraft[field.key]" :type="field.type" :placeholder="field.placeholder" />
          </label>
        </div>
        <p v-if="formError" class="admin-message error">{{ formError }}</p>
        <footer>
          <button class="secondary" type="button" @click="closeForm">取消</button>
          <button class="primary" type="submit" :disabled="loading">保存</button>
        </footer>
      </form>
    </div>
  </section>
</template>

<script setup lang="ts">
import { computed, onBeforeUnmount, onMounted, reactive, ref } from 'vue';
import { deleteJson, getJson, postForm, postJson, putJson, type ApiResponse } from '../../shared/apiClient';

type SectionKey = 'skill-ai' | 'data-content' | 'org-rules-tags' | 'insight-ops';
type NoticeKind = 'info' | 'error';
type AnyRecord = Record<string, any>;
type FormOptionValue = string | number | boolean;
type FormKind =
  | 'skill'
  | 'skillEnv'
  | 'imageEnv'
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
};

const props = defineProps<{
  accountName: string;
}>();

defineEmits<{
  logout: [];
  'switch-desktop': [];
}>();

const sessionLabel = computed(() => (props.accountName ? `当前账号：${props.accountName}` : '已登录'));
const sections: Array<{ key: SectionKey; group: string; title: string; subtitle: string; description: string; primaryAction: string }> = [
  { key: 'skill-ai', group: '运营 A/B', title: 'AI 与 Skill 配置', subtitle: '场景、环境、Prompt', description: '管理 AI 路由、外部环境、连接测试、Prompt 模板和企业红线。', primaryAction: '新增 Skill 绑定' },
  { key: 'data-content', group: '运营 C/D', title: '数据源与内容', subtitle: '表格、映射、速搜', description: '管理客户数据同步、字段映射版本、CSV 导入和桌面速搜内容。', primaryAction: '添加数据源' },
  { key: 'org-rules-tags', group: '运营 E/F/G', title: '组织、规则与标签', subtitle: '账号、跟进、分层', description: '管理账号权限、跟进规则条件和客户标签体系。', primaryAction: '新增账号' },
  { key: 'insight-ops', group: '运营 H/I/J/K/L', title: '分析与系统运营', subtitle: '看板、版本、公告、审计、健康', description: '查看运营表现，管理版本公告，追溯操作并监控系统健康。', primaryAction: '新增公告' }
];

const activeSectionKey = ref<SectionKey>('skill-ai');
const loading = ref(false);
const notice = ref('');
const noticeKind = ref<NoticeKind>('info');
const activeForm = ref<FormKind | null>(null);
const editingItem = ref<AnyRecord | null>(null);
const formDraft = reactive<AnyRecord>({});
const formError = ref('');
const selectedDatasource = ref<AnyRecord | null>(null);
const mappingDraft = reactive<Record<string, string>>({});
const skillSceneFilter = ref('');
const skillLeadTypeFilter = ref('');
const skillAnalyticsDays = ref(7);
const skillTestMessage = ref('请基于当前客户状态生成一条跟进回复');
const selectedPromptType = ref('format');
const datasourceColumns = ref<Array<AnyRecord | string>>([]);
const mappingCompare = ref<AnyRecord | null>(null);
const quickSearchKeyword = ref('');
const quickSearchType = ref('');
const quickSearchSelectedIds = ref<Array<string | number>>([]);
const ruleKeyword = ref('');
const ruleActionType = ref('');
const csvPreview = ref<string[]>([]);
const csvFile = ref<File | null>(null);
const csvImportResult = ref<AnyRecord | null>(null);
const accountKeyword = ref('');
const accountRoleFilter = ref('');
const accountEnabledFilter = ref('');
const tagKeyword = ref('');
const versionStatusFilter = ref('');
const versionPlatformFilter = ref('');
const noticeStatusFilter = ref('');
const noticeLevelFilter = ref('');
const auditKeyword = ref('');
const auditAction = ref('');
const auditTargetType = ref('');
const auditStartDate = ref('');
const auditEndDate = ref('');
const expandedAuditId = ref<string | number | null>(null);
const auditExportJob = ref<AnyRecord | null>(null);
const expandedHealthAlertId = ref<string | number | null>(null);
const healthAlertStatusFilter = ref('');
const healthLastRefreshAt = ref('');
const healthConsecutiveFailures = ref(0);

const skillBindings = ref<AnyRecord[]>([]);
const availableSkills = ref<AnyRecord[]>([]);
const skillAnalytics = ref<AnyRecord | null>(null);
const skillTestResults = reactive<Record<string, AnyRecord>>({});
const skillEnvironments = ref<AnyRecord[]>([]);
const imageEnvironments = ref<AnyRecord[]>([]);
const configs = ref<AnyRecord[]>([]);
const datasources = ref<AnyRecord[]>([]);
const syncStatuses = ref<AnyRecord[]>([]);
const customerFields = ref<AnyRecord[]>([]);
const mappings = ref<AnyRecord[]>([]);
const mappingVersions = ref<AnyRecord[]>([]);
const importLogs = ref<AnyRecord[]>([]);
const quickSearchItems = ref<AnyRecord[]>([]);
const accounts = ref<AnyRecord[]>([]);
const rules = ref<AnyRecord[]>([]);
const tagCategories = ref<AnyRecord[]>([]);
const analytics = reactive<Record<string, AnyRecord>>({});
const versions = ref<AnyRecord[]>([]);
const notices = ref<AnyRecord[]>([]);
const auditLogs = ref<AnyRecord[]>([]);
const auditActions = ref<string[]>([]);
const health = ref<AnyRecord | null>(null);
const promptVersions = ref<AnyRecord[]>([]);
const promptDraft = reactive({
  format: '',
  redLinesText: '',
  tagRemovalRulesText: '',
  fallbackReply: ''
});

let healthTimer: number | null = null;

const activeSection = computed(() => sections.find((section) => section.key === activeSectionKey.value) ?? sections[0]);
const activeMetrics = computed(() => {
  if (activeSectionKey.value === 'skill-ai') {
    return [
      { label: '场景绑定', value: skillBindings.value.length, help: '覆盖开场白、主动回复和换一组' },
      { label: 'Skill 环境', value: skillEnvironments.value.length, help: activeEnvironmentName(skillEnvironments.value) },
      { label: '识图环境', value: imageEnvironments.value.length, help: activeEnvironmentName(imageEnvironments.value) }
    ];
  }
  if (activeSectionKey.value === 'data-content') {
    return [
      { label: '数据源', value: datasources.value.length, help: '企微表格与 CSV 导入' },
      { label: '同步异常', value: syncStatuses.value.filter((item) => String(item.syncStatus || item.status || '').includes('FAIL')).length, help: '失败项需人工检查' },
      { label: '速搜内容', value: quickSearchItems.value.length, help: '推送到桌面端快线模板' }
    ];
  }
  if (activeSectionKey.value === 'org-rules-tags') {
    return [
      { label: '账号', value: accounts.value.length, help: 'ADMIN/LEADER/KEEPER' },
      { label: '规则', value: rules.value.length, help: '提醒、标签建议、通知组长' },
      { label: '标签分类', value: tagCategories.value.length, help: 'AI 和运营共用标签库' }
    ];
  }
  return [
    { label: '公告', value: notices.value.length, help: '工具能力和故障通知' },
    { label: '版本', value: versions.value.length, help: 'Windows/macOS 发布记录' },
    { label: '健康', value: healthStatusText.value, help: '系统、AI、表格、缓存状态' }
  ];
});
const filteredQuickSearchItems = computed(() => quickSearchItems.value.filter((item) => {
  const keyword = quickSearchKeyword.value.trim().toLowerCase();
  const matchesKeyword = !keyword || [item.title, item.shortcutCode, item.content].some((value) => String(value ?? '').toLowerCase().includes(keyword));
  const matchesType = !quickSearchType.value || item.contentType === quickSearchType.value;
  return matchesKeyword && matchesType;
}));
const filteredRules = computed(() => rules.value.filter((rule) => {
  const matchesKeyword = !ruleKeyword.value.trim() || String(rule.name ?? '').includes(ruleKeyword.value.trim());
  const matchesType = !ruleActionType.value || rule.actionType === ruleActionType.value;
  return matchesKeyword && matchesType;
}));
const filteredTagCategories = computed(() => tagCategories.value.filter((category) => {
  const keyword = tagKeyword.value.trim().toLowerCase();
  if (!keyword) return true;
  const tags = category.values || category.tags || [];
  return [category.categoryName, category.name, category.boundField, category.fieldName]
    .some((value) => String(value ?? '').toLowerCase().includes(keyword))
    || tags.some((tag: AnyRecord) => [tag.displayName, tag.tagValue].some((value) => String(value ?? '').toLowerCase().includes(keyword)));
}));
const filteredAuditLogs = computed(() => auditLogs.value.filter((log) => {
  const keyword = auditKeyword.value.trim();
  const matchesKeyword = !keyword || [log.operator, log.action, log.detail, log.target].some((value) => String(value ?? '').includes(keyword));
  const matchesAction = !auditAction.value || log.action === auditAction.value;
  return matchesKeyword && matchesAction;
}));
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
const filteredHealthAlerts = computed(() => listFrom(health.value ?? {}, 'recentAlerts').filter((alert) => {
  if (!healthAlertStatusFilter.value) return true;
  return String(alert.status ?? 'OPEN').toUpperCase() === healthAlertStatusFilter.value;
}));
const activeFormTitle = computed(() => formMeta(activeForm.value).title);
const activeFormDescription = computed(() => formMeta(activeForm.value).description);
const activeFormFields = computed(() => formMeta(activeForm.value).fields);

onMounted(() => {
  void refreshActiveSection();
  healthTimer = window.setInterval(() => {
    if (activeSectionKey.value === 'insight-ops' && !document.hidden) {
      void loadHealth();
    }
  }, 30000);
});

onBeforeUnmount(() => {
  if (healthTimer !== null) {
    window.clearInterval(healthTimer);
  }
});

function selectSection(section: SectionKey) {
  activeSectionKey.value = section;
  void refreshActiveSection();
}

async function refreshActiveSection() {
  if (activeSectionKey.value === 'skill-ai') await loadSkillAi();
  if (activeSectionKey.value === 'data-content') await loadDataContent();
  if (activeSectionKey.value === 'org-rules-tags') await loadOrgRulesTags();
  if (activeSectionKey.value === 'insight-ops') await loadInsightOps();
}

function startPrimaryAction() {
  if (activeSectionKey.value === 'skill-ai') openForm('skill');
  if (activeSectionKey.value === 'data-content') openForm('datasource');
  if (activeSectionKey.value === 'org-rules-tags') openForm('account');
  if (activeSectionKey.value === 'insight-ops') openForm('notice');
}

async function loadSkillAi() {
  await runWithNotice(async () => {
    const [skillList, availableSkillList, skillCallAnalytics, skillEnvList, imageEnvList, configList] = await Promise.all([
      getJson<unknown>(withQuery('/admin/api/v1/skills', { scene: skillSceneFilter.value, leadType: skillLeadTypeFilter.value })),
      getJson<unknown>('/admin/api/v1/skills/available'),
      getJson<unknown>(withQuery('/admin/api/v1/analytics/skill-calls', { days: skillAnalyticsDays.value, scene: skillSceneFilter.value, leadType: skillLeadTypeFilter.value })),
      getJson<unknown>('/admin/api/v1/skill-environments'),
      getJson<unknown>('/admin/api/v1/image-environments'),
      getJson<unknown>('/admin/api/v1/configs')
    ]);
    skillBindings.value = listFromResponse(skillList);
    availableSkills.value = listFromResponse(availableSkillList);
    skillAnalytics.value = recordFromResponse(skillCallAnalytics);
    skillEnvironments.value = listFromResponse(skillEnvList);
    imageEnvironments.value = listFromResponse(imageEnvList);
    configs.value = configEntries(configList);
    hydratePromptDraft();
  }, 'AI 配置已刷新');
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

async function loadDataContent() {
  await runWithNotice(async () => {
    const [dsList, fieldList, syncList, importList, quickList] = await Promise.all([
      getJson<unknown>('/admin/api/v1/datasources'),
      getJson<unknown>('/admin/api/v1/customer-fields'),
      getJson<unknown>('/admin/api/v1/datasources/sync-status'),
      getJson<unknown>('/admin/api/v1/datasources/import-logs'),
      getJson<unknown>('/admin/api/v1/quick-search/items')
    ]);
    datasources.value = listFromResponse(dsList);
    customerFields.value = normalizeCustomerFields(fieldList);
    syncStatuses.value = listFromResponse(syncList);
    importLogs.value = listFromResponse(importList);
    quickSearchItems.value = listFromResponse(quickList);
    if (!selectedDatasource.value && datasources.value.length) {
      selectDatasource(datasources.value[0]);
    }
  }, '数据与内容已刷新');
}

async function loadOrgRulesTags() {
  await runWithNotice(async () => {
    const [accountList, ruleList, tagList] = await Promise.all([
      getJson<unknown>(withQuery('/admin/api/v1/accounts', {
        keyword: accountKeyword.value,
        role: accountRoleFilter.value,
        is_enabled: accountEnabledFilter.value,
        page_size: 100
      })),
      getJson<unknown>(withQuery('/admin/api/v1/rules', { keyword: ruleKeyword.value, actionType: ruleActionType.value, page: 1, size: 100 })),
      getJson<unknown>('/admin/api/v1/tags/categories')
    ]);
    accounts.value = listFromResponse(accountList);
    rules.value = listFromResponse(ruleList);
    tagCategories.value = listFromResponse(tagList);
  }, '组织与规则已刷新');
}

async function loadInsightOps() {
  await runWithNotice(async () => {
    const analyticsQuery = { days: skillAnalyticsDays.value, leadType: skillLeadTypeFilter.value };
    const [overview, funnels, staff, sources, stages, analyticsHealth, lifecycle, risks, contentRanking, versionList, noticeList, auditList, actionList, healthPayload] = await Promise.all([
      getJson<unknown>(withQuery('/admin/api/v1/analytics/overview', analyticsQuery)),
      getJson<unknown>(withQuery('/admin/api/v1/analytics/funnels', analyticsQuery)),
      getJson<unknown>(withQuery('/admin/api/v1/analytics/staff', analyticsQuery)),
      getJson<unknown>(withQuery('/admin/api/v1/analytics/sources', analyticsQuery)),
      getJson<unknown>(withQuery('/admin/api/v1/analytics/stages', analyticsQuery)),
      getJson<unknown>(withQuery('/admin/api/v1/analytics/health', analyticsQuery)),
      getJson<unknown>(withQuery('/admin/api/v1/analytics/lifecycle', analyticsQuery)),
      getJson<unknown>(withQuery('/admin/api/v1/analytics/risks', analyticsQuery)),
      getJson<unknown>(withQuery('/admin/api/v1/analytics/content-ranking', analyticsQuery)),
      getJson<unknown>(withQuery('/admin/api/v1/versions', { status: versionStatusFilter.value, platform: versionPlatformFilter.value, page: 1, size: 100 })),
      getJson<unknown>(withQuery('/admin/api/v1/notices', { status: noticeStatusFilter.value, level: noticeLevelFilter.value, page: 1, size: 100 })),
      getJson<unknown>(auditLogPath()),
      getJson<unknown>('/admin/api/v1/audit-logs/actions'),
      getJson<unknown>('/admin/api/v1/health')
    ]);
    analytics.overview = recordFromResponse(overview);
    analytics.funnels = recordFromResponse(funnels);
    analytics.staff = recordFromResponse(staff);
    analytics.sources = recordFromResponse(sources);
    analytics.stages = recordFromResponse(stages);
    analytics.health = recordFromResponse(analyticsHealth);
    analytics.lifecycle = recordFromResponse(lifecycle);
    analytics.risks = recordFromResponse(risks);
    analytics.contentRanking = recordFromResponse(contentRanking);
    versions.value = listFromResponse(versionList);
    notices.value = listFromResponse(noticeList);
    auditLogs.value = listFromResponse(auditList);
    auditActions.value = listFromResponse(actionList).map((item) => String(item.action ?? item.name ?? item));
    health.value = dataFromResponse(healthPayload) as AnyRecord;
    healthLastRefreshAt.value = formatDate((health.value as AnyRecord)?.timestamp ?? new Date().toISOString());
    healthConsecutiveFailures.value = 0;
  }, '分析与系统运营已刷新');
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
    auditLogs.value = listFromResponse(await getJson<unknown>(auditLogPath()));
  }, '审计日志已刷新');
}

function openForm(kind: FormKind, item?: AnyRecord) {
  activeForm.value = kind;
  editingItem.value = item ?? null;
  formError.value = '';
  Object.keys(formDraft).forEach((key) => delete formDraft[key]);
  Object.assign(formDraft, initialDraft(kind, item));
}

function closeForm() {
  activeForm.value = null;
  editingItem.value = null;
  formError.value = '';
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
    formError.value = error instanceof Error ? error.message : String(error);
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
    } else if (kind === 'datasource') {
      const payload = pickDraft(['name', 'sheetId', 'sourceTable', 'description']);
      if (editingItem.value?.id) await putJson(`/admin/api/v1/datasources/${editingItem.value.id}`, payload);
      else await postJson('/admin/api/v1/datasources', payload);
    } else if (kind === 'quickSearch') {
      const payload = pickDraft(['contentType', 'leadType', 'title', 'shortcutCode', 'content', 'imageUrl', 'sortOrder', 'enabled']);
      if (editingItem.value?.id) await putJson(`/admin/api/v1/quick-search/items/${editingItem.value.id}`, payload);
      else await postJson('/admin/api/v1/quick-search/items', payload);
    } else if (kind === 'account') {
      const payload = pickDraft(['phone', 'password', 'displayName', 'role', 'leaderId']);
      if (editingItem.value?.id) await putJson(`/admin/api/v1/accounts/${editingItem.value.id}`, payload);
      else await postJson('/admin/api/v1/accounts', payload);
    } else if (kind === 'rule') {
      const payload = buildRulePayload();
      if (editingItem.value?.id) await putJson(`/admin/api/v1/rules/${editingItem.value.id}`, payload);
      else await postJson('/admin/api/v1/rules', payload);
    } else if (kind === 'tagCategory') {
      const payload = pickDraft(['categoryName', 'categoryKey', 'boundField', 'isEnabled']);
      if (editingItem.value?.id) await putJson(`/admin/api/v1/tags/categories/${editingItem.value.id}`, payload);
      else await postJson('/admin/api/v1/tags/categories', payload);
    } else if (kind === 'tagValue') {
      const payload = pickDraft(['categoryId', 'tagValue', 'displayName', 'sortOrder', 'isEnabled']);
      if (editingItem.value?.id) await putJson(`/admin/api/v1/tags/values/${editingItem.value.id}`, payload);
      else await postJson('/admin/api/v1/tags/values', payload);
    } else if (kind === 'version') {
      const payload = pickDraft(['version', 'platform', 'downloadUrl', 'changelog', 'updateStrategy', 'gradualPercent', 'fileSize']);
      if (editingItem.value?.id) await putJson(`/admin/api/v1/versions/${editingItem.value.id}`, payload);
      else await postJson('/admin/api/v1/versions', payload);
    } else if (kind === 'revokeVersion') {
      if (!editingItem.value?.id) throw new Error('请选择要撤回的版本');
      await putJson(`/admin/api/v1/versions/${editingItem.value.id}/revoke`, pickDraft(['reason', 'alternativeVersion']));
    } else if (kind === 'notice') {
      const payload = pickDraft(['title', 'content', 'level', 'publishType', 'publishAt', 'expireDays']);
      if (editingItem.value?.id) await putJson(`/admin/api/v1/notices/${editingItem.value.id}`, payload);
      else await postJson('/admin/api/v1/notices', payload);
    }
    noticeKind.value = 'info';
    notice.value = '保存成功';
  } finally {
    loading.value = false;
  }
}

async function submitEnvironment(kind: 'skill' | 'image') {
  const prefix = kind === 'skill' ? 'skill-environments' : 'image-environments';
  const payload = pickDraft(['envName', 'baseUrl', 'apiKey']);
  if (editingItem.value?.id) await putJson(`/admin/api/v1/${prefix}/${editingItem.value.id}`, payload);
  else await postJson(`/admin/api/v1/${prefix}`, payload);
}

function pickDraft(keys: string[]) {
  return keys.reduce<AnyRecord>((payload, key) => {
    if (formDraft[key] !== undefined && formDraft[key] !== '') payload[key] = formDraft[key];
    return payload;
  }, {});
}

function initialDraft(kind: FormKind, item?: AnyRecord): AnyRecord {
  const suffix = Date.now().toString().slice(-6);
  if (kind === 'skill') return { skillId: item?.skillId ?? '', skillName: item?.skillName ?? '', scene: item?.scene ?? 'OPENING', leadType: item?.leadType ?? 'PENDING', priority: item?.priority ?? 90 };
  if (kind === 'skillEnv' || kind === 'imageEnv') return { envName: item?.envName ?? '', baseUrl: item?.baseUrl ?? '', apiKey: '' };
  if (kind === 'datasource') return { name: item?.name ?? '', sheetId: item?.sheetId ?? '', sourceTable: item?.sourceTable ?? '', description: item?.description ?? '' };
  if (kind === 'quickSearch') return { contentType: item?.contentType ?? 'TEMPLATE', leadType: item?.leadType ?? 'GENERAL', title: item?.title ?? '', shortcutCode: item?.shortcutCode ?? '', content: item?.content ?? '', imageUrl: item?.imageUrl ?? '', sortOrder: item?.sortOrder ?? 99, enabled: item?.enabled ?? true };
  if (kind === 'account') return { phone: item?.phone ?? item?.username ?? '', password: '', displayName: item?.displayName ?? '', role: item?.role ?? 'KEEPER', leaderId: item?.leaderId ?? null };
  if (kind === 'rule') return ruleDraft(item);
  if (kind === 'tagCategory') return { categoryName: item?.categoryName ?? item?.name ?? '', categoryKey: item?.categoryKey ?? `custom_${suffix}`, boundField: item?.boundField ?? '', isEnabled: item?.isEnabled ?? true };
  if (kind === 'tagValue') return { categoryId: item?.categoryId ?? item?.id ?? '', tagValue: item?.tagValue ?? `TAG_${suffix}`, displayName: item?.displayName ?? '', sortOrder: item?.sortOrder ?? 99, isEnabled: item?.isEnabled ?? true };
  if (kind === 'version') return { version: item?.version ?? `1.0.${suffix}`, platform: item?.platform ?? 'WINDOWS', downloadUrl: item?.downloadUrl ?? '', changelog: item?.changelog ?? '', updateStrategy: item?.updateStrategy ?? 'OPTIONAL', gradualPercent: item?.gradualPercent ?? null, fileSize: item?.fileSize ?? 0 };
  if (kind === 'revokeVersion') return { reason: '', alternativeVersion: '' };
  if (kind === 'notice') return { title: item?.title ?? '', content: item?.content ?? '', level: item?.level ?? 'INFO', publishType: item?.publishType ?? 'IMMEDIATE', publishAt: item?.publishAt ?? '', expireDays: item?.expireDays ?? 1 };
  return {};
}

function formMeta(kind: FormKind | null): { title: string; description: string; fields: FormField[] } {
  const commonOptions = {
    scene: [{ label: '开场白', value: 'OPENING' }, { label: '主动回复', value: 'ACTIVE_REPLY' }, { label: '换一组', value: 'REGENERATE' }],
    leadType: [{ label: '通用', value: 'GENERAL' }, { label: '团购客资', value: 'TUAN_GOU' }, { label: '线索客资', value: 'XIAN_SUO' }, { label: '待确认', value: 'PENDING' }]
  };
  if (kind === 'skill') return { title: 'Skill 场景绑定', description: '选择场景、线索类型和技能，保存后立即用于对应业务场景。', fields: [
    { key: 'scene', label: '场景', type: 'select', options: commonOptions.scene },
    { key: 'leadType', label: '线索类型', type: 'select', options: commonOptions.leadType },
    availableSkills.value.length
      ? { key: 'skillId', label: '技能', type: 'select', options: availableSkills.value.map((skill) => ({ label: skill.skillName || skill.skillId, value: skill.skillId })) }
      : { key: 'skillId', label: '技能标识', type: 'text', placeholder: '选择或填写已登记的技能标识' },
    { key: 'skillName', label: '显示名称', type: 'text' },
    { key: 'priority', label: '优先级', type: 'number' }
  ] };
  if (kind === 'skillEnv' || kind === 'imageEnv') return { title: kind === 'skillEnv' ? 'Skill 环境' : '识图环境', description: 'API Key 保存后只展示脱敏信息。', fields: [
    { key: 'envName', label: '环境名称', type: 'text', placeholder: '生产环境 / 备份环境' },
    { key: 'baseUrl', label: '服务地址', type: 'text', placeholder: 'https://api.example.com' },
    { key: 'apiKey', label: 'API Key', type: 'password' }
  ] };
  if (kind === 'datasource') return { title: '数据源', description: '配置企微智能表格来源。', fields: [
    { key: 'name', label: '数据源名称', type: 'text' },
    { key: 'sheetId', label: '企微表格标识', type: 'text' },
    { key: 'sourceTable', label: '来源表标识', type: 'text' },
    { key: 'description', label: '说明', type: 'textarea' }
  ] };
  if (kind === 'quickSearch') return { title: '速搜内容', description: '同事在桌面端看到的内容会同步预览。', fields: [
    { key: 'contentType', label: '内容类型', type: 'select', options: [{ label: '话术模板', value: 'TEMPLATE' }, { label: '知识片段', value: 'KNOWLEDGE' }, { label: '门店定位', value: 'LOCATION' }, { label: '图片素材', value: 'IMAGE' }, { label: '小程序引导', value: 'MINI_PROGRAM' }] },
    { key: 'leadType', label: '线索类型', type: 'select', options: commonOptions.leadType },
    { key: 'title', label: '标题', type: 'text' },
    { key: 'shortcutCode', label: '快线码', type: 'text' },
    { key: 'content', label: '正文', type: 'textarea' },
    { key: 'imageUrl', label: '图片地址', type: 'text' },
    { key: 'sortOrder', label: '排序', type: 'number' },
    { key: 'enabled', label: '启用', type: 'checkbox' }
  ] };
  if (kind === 'account') return { title: '账号', description: '角色决定后台和桌面端权限。', fields: [
    { key: 'phone', label: '手机号', type: 'text' },
    { key: 'password', label: '初始密码', type: 'password' },
    { key: 'displayName', label: '姓名', type: 'text' },
    { key: 'role', label: '角色', type: 'select', options: [{ label: '管理员', value: 'ADMIN' }, { label: '组长', value: 'LEADER' }, { label: '管家', value: 'KEEPER' }] },
    { key: 'leaderId', label: '直属组长', type: 'number' }
  ] };
  if (kind === 'rule') return { title: '跟进规则', description: '用业务字段构建条件，系统自动生成规则配置。', fields: [
    { key: 'name', label: '规则名称', type: 'text' },
    { key: 'leadType', label: '线索类型', type: 'select', options: commonOptions.leadType.filter((item) => item.value !== 'GENERAL') },
    { key: 'thresholdHours', label: '超过多少小时未跟进', type: 'number' },
    { key: 'actionType', label: '动作', type: 'select', options: [{ label: '提醒/告警', value: 'ALERT' }, { label: '标签建议', value: 'TAG_SUGGESTION' }, { label: '通知组长', value: 'NOTIFY_LEADER' }] },
    { key: 'priority', label: '优先级', type: 'number' },
    { key: 'enabled', label: '启用', type: 'checkbox' }
  ] };
  if (kind === 'tagCategory') return { title: '标签分类', description: '分类绑定客户档案字段。', fields: [
    { key: 'categoryName', label: '分类名称', type: 'text' },
    { key: 'categoryKey', label: '代码值', type: 'text' },
    { key: 'boundField', label: '绑定字段', type: 'text' },
    { key: 'isEnabled', label: '启用', type: 'checkbox' }
  ] };
  if (kind === 'tagValue') return { title: '标签值', description: '展示名可调整，代码值用于系统判断。', fields: [
    { key: 'categoryId', label: '分类', type: 'number' },
    { key: 'tagValue', label: '代码值', type: 'text' },
    { key: 'displayName', label: '展示名', type: 'text' },
    { key: 'sortOrder', label: '排序', type: 'number' },
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
    notice.value = error instanceof Error ? error.message : String(error);
  } finally {
    loading.value = false;
  }
}

function hydratePromptDraft() {
  promptDraft.format = configValue('skill.system_prompt_format') || configValue('skill.system_prompt_template');
  promptDraft.redLinesText = parseTextList(configValue('skill.system_prompt_red_lines')).join('\n');
  promptDraft.tagRemovalRulesText = parseTextList(configValue('match.tag_removal_rules')).join('\n');
  promptDraft.fallbackReply = configValue('skill.fallback_reply');
}

async function savePromptSettings() {
  await runWithNotice(async () => {
    await Promise.all([
      putJson('/admin/api/v1/configs/skill.system_prompt_format', { value: promptDraft.format }),
      putJson('/admin/api/v1/configs/skill.system_prompt_red_lines', { value: JSON.stringify(linesFrom(promptDraft.redLinesText)) }),
      putJson('/admin/api/v1/configs/match.tag_removal_rules', { value: JSON.stringify(linesFrom(promptDraft.tagRemovalRulesText)) }),
      putJson('/admin/api/v1/configs/skill.fallback_reply', { value: promptDraft.fallbackReply })
    ]);
  }, 'Prompt 配置已保存');
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

function confirmActivateEnvironment(kind: 'skill' | 'image', env: AnyRecord) {
  if (window.confirm(`将切换至「${env.envName}」，此修改会影响线上能力，确认切换？`)) {
    void runWithNotice(async () => {
      await putJson(`/admin/api/v1/${kind === 'skill' ? 'skill-environments' : 'image-environments'}/${env.id}/activate`, {});
      await loadSkillAi();
    }, '环境已切换');
  }
}

function confirmDeleteEnvironment(kind: 'skill' | 'image', env: AnyRecord) {
  if (isActiveEnvironment(env)) return;
  if (window.confirm(`确认删除「${env.envName}」？删除后不能继续作为备用环境。`)) {
    void runWithNotice(async () => {
      await deleteJson(`/admin/api/v1/${kind === 'skill' ? 'skill-environments' : 'image-environments'}/${env.id}`);
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

async function selectDatasource(item: AnyRecord) {
  selectedDatasource.value = item;
  mappingVersions.value = [];
  datasourceColumns.value = [];
  mappingCompare.value = null;
  Object.keys(mappingDraft).forEach((key) => delete mappingDraft[key]);
  await runWithNotice(async () => {
    const payload = await getJson<unknown>(`/admin/api/v1/datasources/${item.id}/mappings`);
    mappings.value = listFromResponse(payload);
    for (const mapping of mappings.value) {
      const target = mapping.targetField ?? mapping.fieldName;
      const source = mapping.sourceField ?? mapping.sourceColumn;
      if (target) mappingDraft[target] = source ?? '';
    }
  }, '字段映射已加载');
}

async function loadDatasourceColumns() {
  const datasource = selectedDatasource.value;
  if (!datasource) return;
  await runWithNotice(async () => {
    const payload = recordFromResponse(await getJson<unknown>(`/admin/api/v1/datasources/${datasource.id}/columns`));
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
        .map(([targetField, sourceField]) => ({ targetField, sourceField, enabled: true }))
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
  if (window.confirm(`确认删除选中的 ${quickSearchSelectedIds.value.length} 条速搜内容？`)) {
    void runWithNotice(async () => {
      for (const id of quickSearchSelectedIds.value) await deleteJson(`/admin/api/v1/quick-search/items/${id}`);
      quickSearchSelectedIds.value = [];
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
      await loadDataContent();
    }, '速搜内容已删除');
  }
}

async function resetAccountPassword(account: AnyRecord) {
  const newPassword = window.prompt(`为「${account.displayName || account.username}」设置新密码`, 'pass5678');
  if (!newPassword) return;
  await runWithNotice(async () => {
    await putJson(`/admin/api/v1/accounts/${account.id}/reset-password`, { newPassword });
  }, '密码已重置');
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
    await loadOrgRulesTags();
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
      await loadOrgRulesTags();
    }, '账号已删除');
  }
}

function buildRulePayload() {
  const conditionJson = JSON.stringify({
    conditions: [{ field: 'leadType', operator: 'EQ', value: formDraft.leadType }, { field: 'hoursSinceLastFollowup', operator: 'GT', value: formDraft.thresholdHours }]
  });
  return {
    name: formDraft.name,
    conditionJson,
    actionType: formDraft.actionType,
    actionConfig: JSON.stringify({ level: formDraft.actionType === 'ALERT' ? 'WARN' : 'INFO' }),
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

function confirmDeleteRule(rule: AnyRecord) {
  if (rule.isBuiltin) return;
  if (window.confirm(`确认删除「${rule.name}」？建议优先选择停用。`)) {
    void runWithNotice(async () => {
      await deleteJson(`/admin/api/v1/rules/${rule.id}`);
      await loadOrgRulesTags();
    }, '规则已删除');
  }
}

function tagDraft(category: AnyRecord, tag: AnyRecord) {
  return { ...tag, categoryId: category.id ?? tag.categoryId };
}

async function toggleTagValue(category: AnyRecord, tag: AnyRecord) {
  await runWithNotice(async () => {
    await putJson(`/admin/api/v1/tags/values/${tag.id}`, { ...tagValuePayload(category, tag), isEnabled: tag.isEnabled === false });
    await loadOrgRulesTags();
  }, '标签值状态已更新');
}

function confirmDeleteTagValue(category: AnyRecord, tag: AnyRecord) {
  if (window.confirm(`确认删除标签值「${tag.displayName || tag.tagValue}」？建议优先停用仍在使用的标签。`)) {
    void runWithNotice(async () => {
      await deleteJson(`/admin/api/v1/tags/values/${tag.id}`);
      await loadOrgRulesTags();
    }, '标签值已删除');
  }
}

function confirmDeleteTagCategory(category: AnyRecord) {
  if (category.isBuiltin) return;
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
    await loadInsightOps();
  }, '版本已发布');
}

function confirmDeleteVersion(version: AnyRecord) {
  if (window.confirm(`确认删除桌面版本 ${version.version}？仅应删除未发布草稿。`)) {
    void runWithNotice(async () => {
      await deleteJson(`/admin/api/v1/versions/${version.id}`);
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
    await loadInsightOps();
  }, '公告已停止');
}

function confirmDeleteNotice(item: AnyRecord) {
  if (window.confirm(`确认删除公告「${item.title}」？删除后无法恢复。`)) {
    void runWithNotice(async () => {
      await deleteJson(`/admin/api/v1/notices/${item.id}`);
      await loadInsightOps();
    }, '公告已删除');
  }
}

async function exportAuditLogs() {
  await runWithNotice(async () => {
    auditExportJob.value = recordFromResponse(await postJson<unknown>('/admin/api/v1/audit-logs/export', auditFilterPayload()));
  }, '审计导出任务已创建');
}

async function refreshAuditExportStatus() {
  const exportId = auditExportJob.value?.exportId;
  if (!exportId) return;
  await runWithNotice(async () => {
    auditExportJob.value = recordFromResponse(await getJson<unknown>(`/admin/api/v1/audit-logs/export/${exportId}`));
  }, '导出状态已刷新');
}

function toggleAuditDetail(log: AnyRecord) {
  expandedAuditId.value = expandedAuditId.value === log.id ? null : log.id;
}

function toggleHealthAlert(alert: AnyRecord) {
  const id = alert.id || alert.createdAt;
  expandedHealthAlertId.value = expandedHealthAlertId.value === id ? null : id;
}

function downloadAnalyticsCsv() {
  const rows = analyticsBlocks.value.map((block) => `${csvCell(block.title)},${csvCell(block.summary)}`).join('\n');
  downloadTextFile(`analytics-${new Date().toISOString().slice(0, 10)}.csv`, `模块,摘要\n${rows}`);
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
  return response.data ?? {};
}

function recordFromResponse(response: ApiResponse<unknown>): AnyRecord {
  const data = dataFromResponse(response);
  return data && !Array.isArray(data) && typeof data === 'object' ? data as AnyRecord : {};
}

function listFrom(value: unknown, preferredKey?: string): AnyRecord[] {
  const data = value as AnyRecord;
  if (Array.isArray(value)) return value as AnyRecord[];
  if (preferredKey && Array.isArray(data?.[preferredKey])) return data[preferredKey];
  for (const key of ['items', 'list', 'records', 'datasources', 'fields', 'logs', 'rules', 'categories', 'versions', 'notices', 'staff', 'sources', 'stages', 'ranking', 'actions', 'columns', 'recentAlerts', 'systemAlerts']) {
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

function auditLogPath() {
  return withQuery('/admin/api/v1/audit-logs', {
    action: auditAction.value,
    operator: auditKeyword.value,
    keyword: auditKeyword.value,
    targetType: auditTargetType.value,
    startDate: auditStartDate.value,
    endDate: auditEndDate.value,
    page: 1,
    size: 100
  });
}

function auditFilterPayload() {
  return {
    action: auditAction.value || null,
    operator: auditKeyword.value || null,
    keyword: auditKeyword.value || null,
    targetType: auditTargetType.value || null,
    targetId: null,
    startDate: auditStartDate.value || null,
    endDate: auditEndDate.value || null
  };
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

function tagValuePayload(category: AnyRecord, tag: AnyRecord) {
  return {
    categoryId: category.id ?? tag.categoryId,
    tagValue: tag.tagValue,
    displayName: tag.displayName,
    sortOrder: tag.sortOrder ?? 99,
    isEnabled: tag.isEnabled !== false
  };
}

function isCurrentAccount(account: AnyRecord) {
  const current = props.accountName?.trim();
  if (!current) return false;
  return [account.username, account.phone, account.displayName].some((value) => String(value ?? '') === current);
}

function isActiveEnvironment(env: AnyRecord) {
  return env.isActive === true || env.active === true;
}

function ruleDraft(item?: AnyRecord) {
  const parsed = parseRuleCondition(item?.conditionJson);
  return {
    name: item?.name ?? '',
    leadType: parsed.leadType ?? 'PENDING',
    actionType: item?.actionType ?? 'ALERT',
    thresholdHours: parsed.thresholdHours ?? 24,
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
    const leadType = conditions.find((item: AnyRecord) => item.field === 'leadType')?.value;
    const threshold = conditions.find((item: AnyRecord) => ['hoursSinceLastFollowup', 'lastFollowupHours'].includes(item.field))?.value;
    return { leadType, thresholdHours: Number(threshold) || undefined };
  } catch {
    return fallback;
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
  return ({ OPENING: '开场白', ACTIVE_REPLY: '主动回复', REGENERATE: '换一组' } as Record<string, string>)[value] ?? value ?? '-';
}

function leadTypeLabel(value: string) {
  return ({ GENERAL: '通用', TUAN_GOU: '团购客资', XIAN_SUO: '线索客资', PENDING: '待确认' } as Record<string, string>)[value] ?? value ?? '-';
}

function contentTypeLabel(value: string) {
  return ({ TEMPLATE: '话术模板', KNOWLEDGE: '知识片段', LOCATION: '门店定位', IMAGE: '图片素材', MINI_PROGRAM: '小程序引导' } as Record<string, string>)[value] ?? value ?? '-';
}

function roleLabel(value: string) {
  return ({ ADMIN: '管理员', LEADER: '组长', KEEPER: '管家' } as Record<string, string>)[value] ?? value ?? '-';
}

function actionTypeLabel(value: string) {
  return ({ ALERT: '提醒/告警', TAG_SUGGESTION: '标签建议', NOTIFY_LEADER: '通知组长' } as Record<string, string>)[value] ?? value ?? '-';
}

function updateStrategyLabel(value: string) {
  return ({ OPTIONAL: '可选升级', FORCED: '强制升级', FORCE: '强制升级', GRADUAL: '灰度发布' } as Record<string, string>)[value] ?? value ?? '-';
}

function leaderName(leaderId: unknown) {
  if (!leaderId) return '-';
  const leader = accounts.value.find((account) => String(account.id) === String(leaderId));
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

function imageTestLabel(env: AnyRecord) {
  if (env.lastTestOk === true || env.lastTestOk === 1) return `连接正常 · ${formatDate(env.lastTestAt)}`;
  if (env.lastTestOk === false || env.lastTestOk === 0) return `测试失败 · ${formatDate(env.lastTestAt)}`;
  return '未完成连接测试';
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
  if (!value || typeof value !== 'object') return '暂无数据';
  return Object.entries(value as AnyRecord).slice(0, 3).map(([key, item]) => `${key}: ${item}`).join(' · ') || '暂无数据';
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
  const date = new Date(String(value));
  return Number.isNaN(date.getTime()) ? String(value) : date.toLocaleString('zh-CN');
}
</script>
