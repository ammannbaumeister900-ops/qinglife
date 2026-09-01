const activities = [
  {
    id: 'session-016',
    sessionNumber: 16,
    name: '身体知道答案 · 三日轻体营',
    shortName: '身体知道答案',
    date: '9月12日—14日',
    city: '上海',
    place: '青浦',
    fee: 2500,
    status: '开放报名',
    confirmMode: 'manual',
    leader: '公主、大海',
    intro: '连续三天慢下来，从饮食、呼吸与身体感受中，找到适合自己的生活节奏。',
    days: [
      { id: 'day-1', label: '第1天', time: '10:00—17:00', theme: '慢下来' },
      { id: 'day-2', label: '第2天', time: '10:00—17:00', theme: '感受生机' },
      { id: 'day-3', label: '第3天', time: '10:00—16:30', theme: '带回生活' }
    ]
  },
  {
    id: 'session-017',
    sessionNumber: 17,
    name: '秋日生机饮食 · 三日轻体营',
    shortName: '秋日生机饮食',
    date: '10月17日—19日',
    city: '杭州',
    place: '余杭',
    fee: 2500,
    status: '计划开放',
    confirmMode: 'auto',
    leader: '公主、大海',
    intro: '围绕有生机的饮食与身体感受，用三天建立更松弛的生活节奏。',
    days: [
      { id: 'day-1', label: '第1天', time: '10:00—17:00', theme: '慢下来' },
      { id: 'day-2', label: '第2天', time: '10:00—17:00', theme: '感受生机' },
      { id: 'day-3', label: '第3天', time: '10:00—16:30', theme: '带回生活' }
    ]
  }
]

const leaders = [
  {
    name: '公主',
    avatarText: '公',
    tone: 'clay',
    intro: '陪你慢下来，重新听见身体当下真实的回应。'
  },
  {
    name: '大海',
    avatarText: '海',
    tone: 'mist',
    intro: '从呼吸、饮食和日常行动出发，陪伴体验自然发生。'
  },
  {
    name: '军军',
    avatarText: '军',
    tone: 'sage',
    intro: '关注现场节奏，让每个人都能舒适地参与和表达。'
  }
]

const articles = [
  {
    id: 'demo-article-1',
    title: '慢一点吃饭，也是在重新听见身体',
    summary: '不计算热量，先从一顿饭的速度、呼吸和满足感开始。',
    author: '轻生活编辑部',
    category: '轻饮食',
    date: '2026-08-25',
    status: 'published',
    recommended: true,
    content: '<p>我们常常在忙碌里完成一餐，却没有真正感受到自己正在吃什么。</p><p>下一次吃饭时，可以先不用改变食物，只试着把第一口放慢一点。看看温度、味道和身体的回应。</p><p>这不是一项必须完成的训练。如果今天不适合，记住这一刻也已经足够。</p>'
  },
  {
    id: 'demo-article-2',
    title: '给下午留三分钟，不解决问题',
    summary: '短暂离开屏幕，让呼吸带你回到当下。',
    author: '带领人大海',
    category: '轻呼吸',
    date: '2026-08-23',
    status: 'published',
    recommended: true,
    content: '<p>三分钟很短，短到不需要为它做额外准备。</p><p>把双脚放在地面，肩膀松一点，留意三次自然的呼吸。</p>'
  },
  {
    id: 'demo-article-3',
    title: '习惯不是连续完成，而是愿意再回来',
    summary: '错过一天并不会清空之前走过的路。',
    author: '轻生活研究室',
    category: '轻行动',
    date: '2026-08-21',
    status: 'published',
    recommended: false,
    content: '<p>真正稳定的小习惯，不是从不间断，而是在停顿之后，仍然知道怎样用更轻的方式回来。</p>'
  }
]

const hierarchy = [
  {
    id: 'upper',
    shortTitle: '肩颈久坐',
    scene: '坐久了，肩颈有点紧',
    title: '久坐与上肢不适',
    note: '肩颈、手臂和手腕的日常紧绷',
    subtopics: ['后颈发紧', '肩背酸胀', '手臂疲劳'],
    region: '后颈两侧、肩部与上背',
    points: '风池、肩井、内关',
    reminder: '避开颈椎正中和明显疼痛处，力度要轻。'
  },
  {
    id: 'digest',
    shortTitle: '饮食腹部',
    scene: '吃过饭，腹部不太轻松',
    title: '饮食与腹部感受',
    note: '饭后胀满、食欲与腹部紧张',
    subtopics: ['饭后胀满', '腹部紧张', '食欲波动'],
    region: '腹部周围与小腿外侧',
    points: '中脘、足三里',
    reminder: '饭后不要立即用力拍打腹部。'
  },
  {
    id: 'lower',
    shortTitle: '腰背下肢',
    scene: '走了一天，腰腿想歇一歇',
    title: '腰背与下肢疲劳',
    note: '久站、走路后或身体下半段发紧',
    subtopics: ['腰背发紧', '腿部疲劳', '足底沉重'],
    region: '腰背肌肉两侧、大腿与小腿',
    points: '足三里、阳陵泉、涌泉',
    reminder: '急性疼痛、红肿热痛时停止练习并寻求评估。'
  },
  {
    id: 'relax',
    shortTitle: '睡前放松',
    scene: '夜深了，身体还没慢下来',
    title: '睡前与情绪放松',
    note: '难安静下来、精神疲惫与睡前紧张',
    subtopics: ['难以安静', '精神疲惫', '睡前紧张'],
    region: '前臂内侧、腕部与足底',
    points: '内关、神门、涌泉',
    reminder: '只做舒缓练习；长期睡眠问题需要专业评估。'
  }
]

const legacyPosts = [
  { id: 'legacy-1', author: '阿禾', time: '2023-02-10', tag: '今日饮食', content: '旧版公开打卡示例。迁移后只读展示，不能继续互动。' },
  { id: 'legacy-2', author: '知秋', time: '2022-10-20', tag: '今日运动', content: '这是历史动态归档示例，原始时间和公开状态会在正式迁移时保留。' }
]

const seedPosts = [
  { id: 'post-1', author: '阿禾', avatar: '禾', time: '今天 08:42', tag: '今日饮食', content: '今天把早餐放慢了一点，只是第一次认真吃完了这一碗。', likes: 12, liked: false, comments: [{ author: '小满', content: '看起来很舒服。' }] },
  { id: 'post-2', author: '林乔', avatar: '乔', time: '昨天 21:16', tag: '三日轻体', content: '散步回来的路上，忽然发现自己没有那么着急了。', likes: 8, liked: true, comments: [{ author: '大海', content: '谢谢你愿意分享。' }] }
]

const participations = [
  {
    id: 'participation-501',
    sessionNumber: 501,
    title: '身体知道答案 · 三日轻体营',
    period: '2026年8月',
    location: '上海青浦',
    status: '已完成',
    summary: '再次回到轻生活，把新的身体感受带回日常。',
    details: [
      { id: 'register', label: '报名确认', date: '8月08日', note: '报名信息与实际参与人已经确认。' },
      { id: 'start', label: '三日体验开始', date: '8月20日', note: '进入第501期三日轻体营现场体验。' },
      { id: 'finish', label: '三日体验完成', date: '8月22日', note: '三日参与记录已完整归入本期经历。' },
      { id: 'followup', label: '结营回访', date: '8月25日', note: '回访与个人感受仅在本期经历中保存。' }
    ]
  },
  {
    id: 'participation-357',
    sessionNumber: 357,
    title: '秋日生机 · 三日轻体营',
    period: '2024年10月',
    location: '杭州余杭',
    status: '已完成',
    summary: '在三天里重新找到饮食、呼吸与行动的节奏。',
    details: [
      { id: 'register', label: '报名确认', date: '10月06日', note: '完成本期报名与服务确认。' },
      { id: 'start', label: '三日体验开始', date: '10月18日', note: '抵达杭州，开始本期三日体验。' },
      { id: 'finish', label: '三日体验完成', date: '10月20日', note: '三日现场参与记录已归档。' },
      { id: 'followup', label: '结营回访', date: '10月23日', note: '完成本期结营后的轻量回访。' }
    ]
  },
  {
    id: 'participation-100',
    sessionNumber: 100,
    title: '初见轻生活 · 三日轻体营',
    period: '2020年6月',
    location: '上海',
    status: '已归档',
    summary: '第一次参加轻生活，也从这里开始成为轻友。',
    details: [
      { id: 'register', label: '报名确认', date: '6月05日', note: '第一次完成轻生活活动报名。' },
      { id: 'start', label: '三日体验开始', date: '6月19日', note: '开始第100期三日轻体营体验。' },
      { id: 'finish', label: '三日体验完成', date: '6月21日', note: '完成三日参与并成为轻友。' }
    ]
  }
]

const habitDays = Array.from({ length: 21 }, (_, index) => ({
  day: index + 1,
  title: [
    '吃第一口时，慢一点',
    '饭后慢慢走一小段',
    '为下午留三次呼吸',
    '今晚提前放下屏幕',
    '给今天留一句话',
    '喝水时暂时不做别的',
    '整理一小块生活空间'
  ][index % 7],
  duration: ['约3分钟', '约8分钟', '约2分钟', '约10分钟', '约3分钟', '约2分钟', '约6分钟'][index % 7]
}))

module.exports = {
  activities,
  leaders,
  articles,
  hierarchy,
  legacyPosts,
  seedPosts,
  participations,
  habitDays
}
