<!--
  - Copyright (c) 2022 dzikoysk
  -
  - Licensed under the Apache License, Version 2.0 (the "License");
  - you may not use this file except in compliance with the License.
  - You may obtain a copy of the License at
  -
  -     http://www.apache.org/licenses/LICENSE-2.0
  -
  - Unless required by applicable law or agreed to in writing, software
  - distributed under the License is distributed on an "AS IS" BASIS,
  - WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
  - See the License for the specific language governing permissions and
  - limitations under the License.
  -->

<script setup>
import { computed, ref, watchEffect, defineAsyncComponent } from 'vue'
import { useSession } from '../store/session'
import useQualifier from '../helpers/qualifier'
import DefaultHeader from '../components/header/DefaultHeader.vue'
import FileBrowserView from '../components/browser/FileBrowserView.vue'
import {Tabs, Tab, TabPanels, TabPanel} from 'vue3-tabs'

const ConsoleView = defineAsyncComponent(() => import('../components/console/ConsoleView.vue'))
const DashboardView = defineAsyncComponent(() => import('../components/dashboard/DashboardView.vue'))
const SettingsView = defineAsyncComponent(() => import('../components/settings/SettingsView.vue'))

defineProps({
  qualifier: {
    type: Object,
    required: true
  }
})

const listOfTabs = [
  { name: 'Overview' },
  { name: 'Dashboard', manager: true },
  { name: 'Console', manager: true },
  { name: 'Settings', manager: true },
]

const { isManager } = useSession()
const { redirectTo } = useQualifier()

const menuTabs = computed(() =>
  listOfTabs
    .filter(entry => !entry?.manager || isManager.value)
    .map(entry => entry.name)
)

const selectedTab = ref(localStorage.getItem('selectedTab') || 'Overview')

watchEffect(() => {
  localStorage.setItem('selectedTab', selectedTab.value)
})

const createTabClick = (newTab) => {
  if (newTab == 'Overview') {
    redirectTo('/')
  }
}

const selectHomepage = () => 
  selectedTab.value = 'Overview'
</script>

<template>
  <div>
    <DefaultHeader :logoClickCallback="selectHomepage" />
    <div class="bg-gray-100 dark:bg-black overflow-y-visible">
      <div class="container mx-auto <sm:px-0">
        <Tabs 
          v-model="selectedTab"
          @update:modelValue="createTabClick"
        >
          <Tab
            v-for="(tab, i) in menuTabs"
            class="item font-normal"
            :key="`menu${i}`"
            :val="tab"
            :label="tab"
            :indicator="true"
          />
        </Tabs>
      </div>
      <hr class="dark:border-gray-700">
      <div class="overflow-auto">
        <TabPanels v-model="selectedTab">
          <TabPanel :val="'Overview'">
            <FileBrowserView v-if="selectedTab == 'Overview'" :qualifier="qualifier" ref=""/>
          </TabPanel>
          <TabPanel :val="'Dashboard'" v-if="isManager">
            <DashboardView v-if="selectedTab == 'Dashboard'" :selectedTab="selectedTab" />
          </TabPanel>
          <TabPanel :val="'Console'" v-if="isManager">
            <ConsoleView v-if="selectedTab == 'Console'" :selectedTab="selectedTab" />
          </TabPanel>
           <TabPanel :val="'Settings'" v-if="isManager">
            <SettingsView v-if="selectedTab == 'Settings'" :selectedTab="selectedTab" />
          </TabPanel>
        </TabPanels>
      </div>
    </div>
  </div>
</template>

<style>
.tabs .tab {
  cursor: pointer;
  text-transform: capitalize;
}
.tabs .item:hover {
  @apply bg-gray-150 dark:bg-gray-900;
  transition: background-color 0.5s;
}
</style>

<style scoped>
.item {
  @apply px-1;
  @apply pb-1;
  @apply pt-1.5;
  @apply cursor-pointer;
  @apply text-gray-600 dark:text-gray-300;
  @apply bg-gray-100 dark:bg-black;
}
.selected {
  @apply border-b-2;
  @apply border-black dark:border-white;
  @apply text-black dark:text-white;
}
.tabs .item {
  border-top-left-radius: 10%;
  border-top-right-radius: 10%;
}
</style>
