/*
 * Copyright (c) 2026 Fasong Wu
 * SPDX-License-Identifier: AGPL-3.0-only
 */

export const state = () => ({
  headers: true,
  footers: true,
  cartnumber: 0,
  logoUrl: '/brand/mindio-mark.svg',
  titleCon: '',
  unreadNum: 0,
  service_num: 0
})

export const mutations = {
  isHeader (state, data) {
    state.headers = data;
  },
  isFooter (state, data) {
    state.footers = data;
  },
  cartNum (state, data) {
    state.cartnumber = data;
  },
  serviceNum (state, data) {
    state.service_num = data;
  },
  logo (state, data) {
    state.logoUrl = data;
  },
  titles (state, data) {
    state.titleCon = data;
  },
  unreadKefu (state, data) {
    state.unreadNum = data;
  }
}

export const actions = {
  // nuxtServerInit can be used to initialize store with server-side data
  // Currently not needed for this application
}
