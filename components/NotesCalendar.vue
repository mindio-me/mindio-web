<template>
  <div class="notes-calendar" ref="calendarContainer">
    <!-- 加载更多按钮（在列表顶部） -->
    <div class="load-more-wrapper">
      <button class="load-more-btn" @click="loadMoreMonths">
        <i class="el-icon-arrow-up"></i>
        继续查看更早的时间
      </button>
    </div>

    <div
      v-for="month in months"
      :key="month.key"
      :data-month="month.key"
      class="calendar-month"
      :class="{ 'is-selected-month': month.key === selectedMonth }"
    >
      <div class="month-header month-header--clickable" @click="handleMonthClick(month.key)">
        {{ month.year }}年{{ month.month }}月
      </div>
      <div class="month-weekdays">
        <div v-for="day in weekdays" :key="day" class="weekday">{{ day }}</div>
      </div>
      <div class="month-days">
        <template v-for="(day, index) in month.days">
          <div
            v-if="day"
            :key="day.dateStr"
            class="calendar-day"
            :class="{
              'is-today': day.isToday,
              'is-selected': day.dateStr === selectedDate,
              'has-notes': day.noteCount > 0
            }"
            @click="$emit('date-selected', day.dateStr === selectedDate ? null : day.dateStr)"
          >
            <span class="day-number">{{ day.day }}</span>
            <span v-if="day.noteCount > 0" class="day-marker">
              <span v-if="day.noteCount === 1" class="marker-dot">•</span>
              <span v-else class="marker-count">{{ day.noteCount > 9 ? '9+' : day.noteCount }}</span>
            </span>
          </div>
          <div v-else :key="`empty-${month.key}-${index}`" class="calendar-day-empty"></div>
        </template>
      </div>
    </div>
  </div>
</template>

<script>
export default {
  name: 'NotesCalendar',
  props: {
    notesByDate: {
      type: Object,
      default: () => ({})
    },
    selectedDate: {
      type: String,
      default: null
    },
    selectedMonth: {
      type: String,
      default: null
    },
    currentDate: {
      type: String,
      default: () => new Date().toISOString().split('T')[0]
    },
    storageKey: {
      type: String,
      default: null
    }
  },
  data() {
    return {
      weekdays: ['一', '二', '三', '四', '五', '六', '日'],
      // 往前偏移月数：默认显示过去 12 个月（本月 + 前 12 个月 + 下个月 = 14 个月）
      pastMonths: 12,
      // 往后偏移月数：固定显示下个月
      futureMonths: 1,
      // 防抖定时器
      scrollDebounceTimer: null
    }
  },
  computed: {
    months() {
      const months = []
      const now = new Date()
      const currentYear = now.getFullYear()
      const currentMonth = now.getMonth()

      for (let i = -this.pastMonths; i <= this.futureMonths; i++) {
        const date = new Date(currentYear, currentMonth + i, 1)
        const year = date.getFullYear()
        const month = date.getMonth()
        const monthKey = `${year}-${String(month + 1).padStart(2, '0')}`

        // 转换为周一在前：0(周日) -> 6, 1(周一) -> 0, ...
        const firstDayOfWeek = new Date(year, month, 1).getDay()
        const firstDay = firstDayOfWeek === 0 ? 6 : firstDayOfWeek - 1
        const daysInMonth = new Date(year, month + 1, 0).getDate()

        const days = []
        for (let j = 0; j < firstDay; j++) {
          days.push(null)
        }
        for (let day = 1; day <= daysInMonth; day++) {
          const dateStr = `${year}-${String(month + 1).padStart(2, '0')}-${String(day).padStart(2, '0')}`
          const dayDate = new Date(year, month, day)
          const isToday = dateStr === this.currentDate
          const notes = this.notesByDate[dateStr] || []
          const noteCount = notes.length

          days.push({
            day,
            dateStr,
            dayDate,
            isToday,
            noteCount,
            weekday: dayDate.getDay()
          })
        }

        months.push({
          key: monthKey,
          year,
          month: month + 1,
          firstDay,
          days
        })
      }

      return months
    }
  },
  mounted() {
    this.$nextTick(() => {
      // 先尝试恢复保存的滚动位置
      if (!this.restoreScrollPosition()) {
        // 如果没有保存的位置，滚动到当前月份
        this.scrollToCurrentMonth()
      }
      // 添加滚动事件监听器
      this.setupScrollListener()
    })
  },
  beforeDestroy() {
    // 清理滚动事件监听器
    this.removeScrollListener()
    if (this.scrollDebounceTimer) {
      clearTimeout(this.scrollDebounceTimer)
    }
  },
  methods: {
    handleMonthClick(monthKey) {
      this.$emit('month-header-click', monthKey === this.selectedMonth ? null : monthKey)
    },
    loadMoreMonths() {
      // 记录当前最早月份的 key，加载后用于恢复滚动位置
      const firstMonthKey = this.months[0] ? this.months[0].key : null
      this.pastMonths += 12
      this.$nextTick(() => {
        if (firstMonthKey) {
          const el = this.$el.querySelector(`[data-month="${firstMonthKey}"]`)
          if (el) el.scrollIntoView({ behavior: 'instant', block: 'start' })
        }
      })
    },

    scrollToCurrentMonth() {
      const currentMonthKey = this.currentDate.substring(0, 7)
      const monthEl = this.$el.querySelector(`[data-month="${currentMonthKey}"]`)
      if (monthEl && this.$refs.calendarContainer) {
        monthEl.scrollIntoView({ behavior: 'smooth', block: 'start' })
        // 滚动完成后保存位置
        setTimeout(() => {
          this.saveScrollPosition()
        }, 500)
      }
    },
    saveScrollPosition() {
      if (!this.storageKey || !process.client) return
      if (!this.$refs.calendarContainer) return
      
      const scrollTop = this.$refs.calendarContainer.scrollTop
      const scrollData = {
        scrollTop,
        timestamp: Date.now()
      }
      try {
        localStorage.setItem(this.storageKey, JSON.stringify(scrollData))
      } catch (e) {
        console.warn('保存滚动位置失败:', e)
      }
    },
    restoreScrollPosition() {
      if (!this.storageKey || !process.client) return false
      if (!this.$refs.calendarContainer) return false
      
      try {
        const saved = localStorage.getItem(this.storageKey)
        if (!saved) return false
        
        const scrollData = JSON.parse(saved)
        if (typeof scrollData.scrollTop === 'number' && scrollData.scrollTop >= 0) {
          // 直接设置滚动位置（已在 mounted 的 $nextTick 中调用）
          this.$refs.calendarContainer.scrollTop = scrollData.scrollTop
          return true
        }
      } catch (e) {
        console.warn('恢复滚动位置失败:', e)
      }
      return false
    },
    setupScrollListener() {
      if (!this.$refs.calendarContainer) return
      
      const container = this.$refs.calendarContainer
      this._scrollHandler = () => {
        // 使用防抖，避免频繁保存
        if (this.scrollDebounceTimer) {
          clearTimeout(this.scrollDebounceTimer)
        }
        this.scrollDebounceTimer = setTimeout(() => {
          this.saveScrollPosition()
        }, 300)
      }
      container.addEventListener('scroll', this._scrollHandler)
    },
    removeScrollListener() {
      if (this._scrollHandler && this.$refs.calendarContainer) {
        this.$refs.calendarContainer.removeEventListener('scroll', this._scrollHandler)
        this._scrollHandler = null
      }
    }
  }
}
</script>

<style scoped lang="scss">
.notes-calendar {
  flex: 1;
  overflow-y: auto;
  padding: 8px 4px;
  min-height: 0;

  // 自定义滚动条样式
  &::-webkit-scrollbar {
    width: 6px;
  }
  &::-webkit-scrollbar-track {
    background: transparent;
    border-radius: 3px;
  }
  &::-webkit-scrollbar-thumb {
    background: var(--border-color);
    border-radius: 3px;
    &:hover {
      background: var(--text-muted);
    }
  }
  scrollbar-width: thin;
  scrollbar-color: var(--border-color) transparent;
}

.load-more-wrapper {
  text-align: center;
  padding: 4px 4px 16px;
}

.load-more-btn {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  padding: 5px 14px;
  border: 1px solid var(--border-color);
  border-radius: 20px;
  background: var(--bg-secondary);
  color: var(--text-secondary);
  font-size: 12px;
  cursor: pointer;
  transition: all 0.2s;

  &:hover {
    border-color: #667eea;
    color: #667eea;
    background: rgba(102, 126, 234, 0.08);
  }

  i {
    font-size: 12px;
  }
}

.calendar-month {
  margin-bottom: 24px;

  &:last-child {
    margin-bottom: 0;
  }
}

.month-header {
  font-size: 14px;
  font-weight: 600;
  color: var(--text-color);
  margin-bottom: 8px;
  padding: 0 4px;
}
.month-header--clickable {
  cursor: pointer;
}
.month-header--clickable:hover {
  color: var(--el-color-primary, #6c63ff);
}

.calendar-month.is-selected-month {
  .month-header {
    color: #667eea;
  }

  .month-header::before {
    content: '';
    display: inline-block;
    width: 6px;
    height: 6px;
    margin-right: 6px;
    border-radius: 50%;
    background: #667eea;
    vertical-align: 2px;
  }
}

.month-weekdays {
  display: grid;
  grid-template-columns: repeat(7, 1fr);
  gap: 2px;
  margin-bottom: 4px;
}

.weekday {
  text-align: center;
  font-size: 12px;
  color: var(--text-muted);
  padding: 4px 0;
  font-weight: 500;
}

.month-days {
  display: grid;
  grid-template-columns: repeat(7, 1fr);
  gap: 2px;
}

.calendar-day-empty {
  aspect-ratio: 1;
  min-height: 32px;
}

.calendar-day {
  aspect-ratio: 1;
  min-height: 32px;
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  position: relative;
  border-radius: 6px;
  cursor: pointer;
  transition: all 0.2s ease;
  padding: 2px;
  background: transparent;

  &:hover:not(.is-selected) {
    background: var(--bg-secondary);
  }

  &.is-today:not(.is-selected) {
    border: 1px solid #667eea;
    background: rgba(102, 126, 234, 0.05);
  }

  &.is-selected {
    background: #667eea;
    color: #fff;
    transform: scale(1.05);
    box-shadow: 0 2px 8px rgba(102, 126, 234, 0.3);

    .day-number {
      color: #fff;
      font-weight: 600;
    }

    .day-marker {
      .marker-dot,
      .marker-count {
        color: #fff;
      }
    }
  }

  &.has-notes:not(.is-selected) {
    .day-number {
      font-weight: 500;
    }
  }
}

.day-number {
  font-size: 13px;
  color: var(--text-color);
  line-height: 1.2;
}

.day-marker {
  position: absolute;
  top: 2px;
  right: 4px;
  font-size: 10px;
  line-height: 1;
  z-index: 1;

  .marker-dot {
    color: #667eea;
    font-size: 14px;
    line-height: 1;
    display: block;
  }

  .marker-count {
    color: #667eea;
    font-size: 10px;
    font-weight: 600;
    background: rgba(102, 126, 234, 0.15);
    border-radius: 8px;
    padding: 1px 4px;
    min-width: 16px;
    text-align: center;
    display: inline-block;
    line-height: 1.4;
  }
}

.calendar-day.is-selected .day-marker {
  .marker-dot,
  .marker-count {
    color: #fff;
    background: rgba(255, 255, 255, 0.2);
  }
}
</style>
