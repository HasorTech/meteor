<script setup>
import SideNavigation from './components/SideNavigation.vue';
import { computed, onMounted, provide, ref, watch } from 'vue';
import { createApi } from './api';
import { useNavigation } from './navigation';
import { provideNavigationGuard } from './useNavigationGuard';
import { useFeedback } from './useFeedback';
import LoginPage from './pages/LoginPage.vue';
import ClusterList from './pages/ClusterList.vue';
import ConnectionPage from './pages/ConnectionPage.vue';
import ApplicationsPage from './pages/ApplicationsPage.vue';
import ServiceDirectory from './pages/ServiceDirectory.vue';
import GlobalPage from './pages/GlobalPage.vue';
import DirectPage from './pages/DirectPage.vue';
import InstanceDetail from './components/InstanceDetail.vue';
import ServiceDetail from './components/ServiceDetail.vue';
import Feedback from './components/Feedback.vue';
import ExamplesDrawer from './components/ExamplesDrawer.vue';
const user = ref(null);
const starting = ref(true);
const clusters = ref([]);
const settings = ref(null);
const endpointTypes = computed(() => settings.value?.endpointTypes || []);
const picker = ref(false);
const canLeave = provideNavigationGuard();
const { route, navigate } = useNavigation(canLeave);
const { error, busy, run } = useFeedback();
const directCredentials = new Map();
const examples = ref(null);
let examplesKey = 0;
const api = createApi(
  () => {
    user.value = null;
    settings.value = null;
    directCredentials.clear();
  },
  (target) => directCredentials.get(target)
);
provide('api', api);
provide('endpointTypes', endpointTypes);
provide('consoleSettings', settings);
provide('directCredentials', directCredentials);
provide('route', route);
provide('navigate', navigate);
provide('openExamples', (request) => {
  examples.value = { key: ++examplesKey, request: { ...request } };
});
watch(route, () => (examples.value = null));
watch(user, (value) => {
  if (!value) examples.value = null;
});
const selected = computed(() => clusters.value.find((c) => c.id === route.value.clusterId));
const view = computed(() => route.value.view || 'clusters');
const scope = computed(() => ({
  mode: route.value.target ? 'direct' : 'registry',
  clusterId: route.value.clusterId,
  target: route.value.target,
  instanceId: route.value.instanceId,
  serviceId: route.value.serviceId
}));
async function loadClusters() {
  const [profiles, definitions] = await Promise.all([api('clusters'), api('settings')]);
  clusters.value = profiles;
  settings.value = definitions;
}
async function signedIn(session) {
  await run(async () => {
    await loadClusters();
    user.value = session;
  });
}
async function saveCluster(cluster) {
  await run(loadClusters);
  navigate({ view: 'applications', clusterId: cluster.id });
}
function choose(clusterId) {
  picker.value = false;
  navigate(clusterId ? { view: 'applications', clusterId } : { view: 'clusters' });
}
function logout() {
  if (!canLeave()) return;
  run(async () => {
    await api('auth/logout', {}, 'POST');
    user.value = null;
    settings.value = null;
    directCredentials.clear();
    clusters.value = [];
    picker.value = false;
  });
}
onMounted(async () => {
  try {
    user.value = await api('auth/session', {}, 'GET', undefined, { timeout: 5000 });
    await loadClusters();
  } catch (failure) {
    if (failure.status !== 401) error.value = failure.message;
  } finally {
    starting.value = false;
  }
});
</script>
<template>
  <main
    v-if="starting"
    class="login-page"
    role="status"
  >
    正在加载控制台…
  </main>
  <LoginPage
    v-else-if="!user"
    :connection-error="error"
    @login="signedIn"
  />
  <div
    v-else
    class="console-shell"
  >
    <header class="topbar">
      <button
        class="brand"
        @click="navigate({ view: 'clusters' })"
      >
        Meteor
      </button>
      <nav
        class="top-navigation"
        aria-label="主要导航"
      >
        <button
          class="selected"
          @click="
            navigate(selected ? { view: 'applications', clusterId: selected.id } : { view: 'clusters' })
          "
        >
          服务治理
        </button>
        <button
          disabled
          title="暂未开放"
        >
          可观测性
        </button>
      </nav>
      <div class="header-actions">
        <div
          class="cluster-picker"
          @keydown.esc="picker = false"
        >
          <button
            class="cluster-select"
            :aria-expanded="picker"
            aria-haspopup="true"
            @click="picker = !picker"
          >
            {{
              selected ? `集群 ${selected.name}` : route.target || view === 'direct' ? '直连实例' : '所有集群'
            }}
            <span aria-hidden="true">⌄</span>
          </button>
          <template v-if="picker">
            <div
              class="dismiss-picker"
              @click="picker = false"
            />
            <div
              class="cluster-menu"
              aria-label="切换集群"
            >
              <button
                :class="{ active: !selected }"
                @click="choose(null)"
              >
                所有集群
              </button>
              <button
                v-for="cluster in clusters"
                :key="cluster.id"
                :class="{ active: selected?.id === cluster.id }"
                @click="choose(cluster.id)"
              >
                <strong>集群 {{ cluster.name }}</strong>
                <small>{{ cluster.address }}</small>
              </button>
              <button
                class="menu-separator"
                @click="
                  picker = false;
                  navigate({ view: 'direct' });
                "
              >
                直连实例
              </button>
            </div>
          </template>
        </div>
        <button
          v-if="user.securityEnabled"
          class="logout text-button"
          :disabled="busy"
          @click="logout"
          :title="user.userName + ' · 退出登录'"
        >
          退出
        </button>
      </div>
    </header>
    <div
      class="workspace-layout"
      :class="{ 'with-sidebar': selected && !route.target }"
    >
      <SideNavigation v-if="selected && !route.target" />
      <div class="workspace-content">
        <nav
          id="breadcrumb-bar"
          aria-label="面包屑导航"
        >
          <div
            v-if="!['instance', 'service', 'connection', 'application'].includes(view)"
            class="breadcrumbs"
          >
            <strong>
              {{
                {
                  applications: '应用列表',
                  services: '服务目录',
                  global: '全局配置',
                  clusters: '集群连接',
                  direct: '直连实例'
                }[view] || '控制台'
              }}
            </strong>
          </div>
        </nav>
        <main class="workspace">
          <Feedback :error="error" />
          <template v-if="!route.clusterId || selected">
            <ClusterList
              v-if="view === 'clusters'"
              :clusters="clusters"
              @changed="run(loadClusters)"
            />
            <ConnectionPage
              v-else-if="view === 'connection'"
              :key="route.id || 'new'"
              :profile="clusters.find((c) => c.id === route.id)"
              @saved="saveCluster"
            />
            <ApplicationsPage
              v-else-if="view === 'applications' || view === 'application'"
              :key="view + route.clusterId + route.application"
              :scope="scope"
              :application="route.application"
            />
            <ServiceDirectory
              v-else-if="view === 'services'"
              :key="route.clusterId"
              :scope="scope"
            />
            <InstanceDetail
              v-else-if="view === 'instance'"
              :key="JSON.stringify(scope)"
              :scope="scope"
            />
            <ServiceDetail
              v-else-if="view === 'service'"
              :key="JSON.stringify(scope)"
              :scope="scope"
            />
            <GlobalPage
              v-else-if="view === 'global'"
              :key="route.clusterId"
              :scope="scope"
            />
            <DirectPage v-else-if="view === 'direct'" />
            <div
              v-else
              class="empty-state"
            >
              <h2>页面不存在</h2>
              <button @click="navigate({ view: 'clusters' })">集群连接</button>
            </div>
          </template>
          <div
            v-else
            class="empty-state"
          >
            <h2>集群连接已被移除</h2>
            <button @click="navigate({ view: 'clusters' })">查看集群连接</button>
          </div>
        </main>
      </div>
    </div>
    <ExamplesDrawer
      v-if="examples"
      :key="examples.key"
      :request="examples.request"
      @close="examples = null"
    />
  </div>
</template>
