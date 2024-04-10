// Copyright 2024 Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: Apache-2.0

// eslint-disable-next-line header/header
import { createApp } from 'vue'
import {createStore, Store} from 'vuex'
import HelloWorld from './components/root.vue'
import {Region, Stage} from "../model";
import {IdeClient} from "../ideClient";
import './assets/common.scss'

declare global {
    interface Window {
        ideApi: { postMessage: (arg: { command: string } & any) => any }
        ideClient: IdeClient
        changeTheme: (darkMode: boolean) => void
    }
}

export interface IdcInfo {
    profileName: string,
    directoryId: string,
    region: string,
}

export interface State {
    stage: Stage,
    ssoRegions: Region[],
    authorizationCode: string,
    lastLoginIdcInfo: IdcInfo
}

declare module '@vue/runtime-core' {
    interface ComponentCustomProperties {
        $store: Store<State>
    }
}

const app = createApp(HelloWorld)
const store = createStore<State>({
    state: {
        stage: 'START' as Stage,
        ssoRegions: [] as Region[],
        authorizationCode: '',
        lastLoginIdcInfo: {
            profileName: '',
            directoryId: '',
            region: '',
        }
    },
    getters: {},
    mutations: {
        setStage(state: State, stage: Stage) {
            state.stage = stage
        },
        setSsoRegions(state: State, regions: Region[]) {
            state.ssoRegions = regions
        },
        setAuthorizationCode(state: State, code: string) {
            state.authorizationCode = code
        },
        setLastLoginIdcInfo(state: State, idcInfo: IdcInfo) {
            console.log('state idc info is updated')
            state.lastLoginIdcInfo.profileName = idcInfo.profileName
            state.lastLoginIdcInfo.directoryId = idcInfo.directoryId
            state.lastLoginIdcInfo.region = idcInfo.region
        },
        reset(state: State) {
            state.stage = 'START'
            state.ssoRegions = []
            state.authorizationCode = ''
            state.lastLoginIdcInfo = {
                profileName: '',
                directoryId: '',
                region: ''
            }
        }
    },
    actions: {},
    modules: {},
})

window.ideClient = new IdeClient(store)
app.directive('autofocus', {
    // When the bound element is inserted into the DOM...
    mounted: function (el) {
        el.focus();
    }
});
app.use(store).mount('#app')
