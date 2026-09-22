/*
 * Copyright (c) 2026 Fasong Wu
 * SPDX-License-Identifier: AGPL-3.0-only
 */

import Vue from 'vue'

const MOBILE_BREAKPOINT = 768

export default (_, inject) => {
  const state = Vue.observable({
    isMobile: process.client ? window.innerWidth <= MOBILE_BREAKPOINT : false
  })

  if (process.client) {
    window.addEventListener('resize', () => {
      state.isMobile = window.innerWidth <= MOBILE_BREAKPOINT
    })
  }

  inject('device', state)
}
