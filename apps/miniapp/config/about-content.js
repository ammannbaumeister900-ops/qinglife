// Public editorial configuration. Unapproved people/photos remain outside the miniapp package.
// See the independent delivery review list before any public release.
// assets 沿用 state/authorization/identityCheck 作为唯一发布开关（state:"releaseReady" 即 releaseReady:true）；url 在上传 CDN 后填入。
const media = require('./visual-media')
module.exports = {
  "schemaVersion": 1,
  "entry": {
    "title": "为身体减负，\n为心灵减压",
    "intro": "忙着生活，有多久没好好关心自己了？",
    "photoAssetId": "scene-50"
  },
  "founders": null,
  "everyday": {
    "title": "把轻生活，\n带回每一天",
    "motto": "轻生活的 GPS：让身体来带路",
    "lead": "先一起体验，再带着自己的问题回去。",
    "glyphs": [
      {
        "glyph": "食",
        "text": "吃饭时，多留意食物和自己的感受"
      },
      {
        "glyph": "动",
        "text": "活动时，找到愿意持续的节奏"
      },
      {
        "glyph": "歇",
        "text": "疲惫时，允许自己慢下来"
      }
    ],
    "glyphsCopyState": "draft",
    "glyphsSource": "认知转化页 Brief V1.1 第三部分正文建议稿",
    "heading": "有人带着做，也有人一起聊。",
    "body": "导游讲解理念、带领实践；轻友交流各自的经历。三天里，听到的想法有机会被尝试，也有机会拿出来讨论。",
    "continuationHeading": "回到家，日子照常。",
    "continuation": "早晨赶时间，出差在外——怎样把三天的体验放进这些具体的日程里，还要慢慢摸索。"
  },
  "storySlot": {
    "heading": "为什么有人愿意\n一次次回来？",
    "item": {
      "id": "story-huahua-family",
      "personKey": "huahua-family",
      "name": "花花一家",
      "state": "releaseReady",
      "authorization": "confirmed",
      "identityCheck": "confirmed",
      "editorialReview": "approved",
      "factCheck": "confirmed",
      "source": {
        "document": "品牌简介/我们一家三口的身体课.pdf",
        "published": "轻生活公众号 2026-10-14（经授权摘编自公众号「罗莎小筑 坐看开花」）",
        "authorizedBy": "用户 2026-10-18 会话确认可作为转化页素材",
        "approvedBy": "用户 2026-10-18 会话审定故事文案"
      },
      "history": [
        "2025年10月6日，一家三口第一次走进清体营",
        "到第500期，一共参加了七次"
      ],
      "intro": "花花一家三口，最早是听一位老轻友推荐来的。那时花花和女儿正卡在减肥的瓶颈期，先生纯粹是陪着来。三天里，喝汁、早睡、泡脚、轻运动，让身体好好歇一歇。",
      "quote": "这不是在“坚持”，是真的“喜欢”。",
      "attribution": "花花，谈饮食变得清淡之后",
      "after": "回家以后，变化落在日常里：一家人的口味变淡了，冰箱里装满五颜六色的蔬果；二十岁的女儿主动断糖，晚上11点前睡觉，去健身房也更勤了。",
      "closingQuote": "好好吃，早点睡，动起来，常常笑。都是老道理，难的是真的去做。",
      "experienceNotice": "个人体验分享，不代表课程疗效。身体有不适或正在服药，请遵医嘱，不要因此停药或放弃必要的诊疗。",
      "portraitAssetId": "family-huahua",
      "fullStory": null
    },
    "photoAssetId": "scene-46"
  },
  "assets": [
    {
      "id": "scene-50",
      "role": "认知页开场 · 慢下来",
      "file": "qing-about-opening-50.jpg",
      "url": media.aboutOpening,
      "caption": "轻生活导游 · 清体营场地园林",
      "alt": "轻生活导游在清体营场地园林中慢慢走",
      "state": "releaseReady",
      "authorization": "confirmed",
      "identityCheck": "confirmed",
      "editorialReview": "approved",
      "factCheck": "confirmed",
      "source": {
        "document": "docs/design/redesign-2026q4/ART-DIRECTION.md",
        "section": "7.1 素材账本",
        "approvedBy": "用户 2026-10-19 会话确认画面人物为导游公主，本人同意公开使用"
      }
    },
    {
      "id": "scene-46",
      "role": "认知页高点 · 被照顾",
      "file": "qing-about-return-46.jpg",
      "url": media.aboutReturn,
      "caption": "导游军军在课程中协助轻友放松",
      "alt": "导游军军在课程中协助一位轻友放松",
      "state": "releaseReady",
      "authorization": "confirmed",
      "identityCheck": "confirmed",
      "editorialReview": "approved",
      "factCheck": "confirmed",
      "source": {
        "document": "docs/design/redesign-2026q4/ART-DIRECTION.md",
        "section": "7.1 素材账本",
        "approvedBy": "用户 2026-10-19 会话确认画面人物为导游军军，本人同意公开使用"
      }
    },
    {
      "id": "scene-67",
      "role": "饮食说明旁 · 细节（已裁掉标签手机号与二维码）",
      "file": "qing-about-juice-67.jpg",
      "url": media.aboutJuice,
      "caption": "清体营期间的蔬菜榨汁",
      "alt": "一只装着绿色蔬菜榨汁的随行瓶",
      "state": "releaseReady",
      "authorization": "confirmed",
      "editorialReview": "approved",
      "factCheck": "confirmed",
      "source": {
        "document": "docs/design/redesign-2026q4/ART-DIRECTION.md",
        "section": "7.1 素材账本"
      }
    },
    {
      "id": "family-huahua",
      "role": "认知页人物故事 · 花花一家",
      "file": "qing-about-story-huahua.jpg",
      "url": media.storyHuahua,
      "caption": "花花一家三口在清体营",
      "alt": "花花一家三口在清体营场地，一起抬头看向远处",
      "state": "releaseReady",
      "authorization": "confirmed",
      "identityCheck": "confirmed",
      "editorialReview": "approved",
      "factCheck": "confirmed",
      "source": {
        "document": "用户 2026-10-18 会话提供的合影",
        "ledger": "tools/images/export.py family-huahua"
      }
    }
  ],
  "historicalEvidence": {
    "foundedYear": 2007,
    "campsLabel": "500多期",
    "asOf": "2026-10-13-context",
    "report": {
      "year": 2025,
      "campCount": 18,
      "participantOccurrences": 676,
      "returningOccurrences": 405,
      "returningPercent": 59.9,
      "metric": "returning-participation-occurrences-share",
      "definition": "老轻友指本期之前已有参营经历的人；同年首次参加后再来，后次计入老轻友人次。按参营期次累计，非独立人数复购率。",
      "state": "releaseReady",
      "factCheck": "user-confirmed-brief",
      "source": {
        "document": "认知转化页_Codex设计交接Brief_v1.1.md",
        "section": "2025年统计范围已确认"
      },
      "notALiveDatabaseMetric": true
    },
    "state": "releaseReady",
    "factCheck": "user-confirmed-brief",
    "source": {
      "document": "认知转化页_Codex设计交接Brief_v1.1.md"
    }
  },
  "transparency": {
    "title": "先了解三天，\n再决定是否出发。",
    "intro": "清体营是一段线下生活方式体验。实际安排与参与限制，都应在作决定之前了解清楚。",
    "participation": [
      {
        "label": "饮食",
        "text": "三天以纯蔬菜榨汁代替日常餐食，属于限制性饮食（营养液断），并非普通健康餐。",
        "parts": [
          {
            "text": "三天以"
          },
          {
            "text": "纯蔬菜榨汁代替日常餐食",
            "emphasis": true
          },
          {
            "text": "，属于限制性饮食（营养液断），并非普通健康餐。"
          }
        ]
      },
      {
        "label": "活动",
        "text": "由老师带领拍打与运动，安排冥想和休息。"
      },
      {
        "label": "同行",
        "text": "导游讲解理念和实践方式，与轻友交流各自的体验。"
      }
    ],
    "riskTitle": "参加前，请先判断是否适合",
    "risks": [
      "限制性饮食与体力活动不适合所有人，可能存在营养不足、低血糖、脱水等风险。有慢性病、正在服药或怀孕等情况，应先由具备资质的专业人员评估。",
      "课程不能替代诊疗。不要因此停药，或放弃必要的医疗措施。"
    ],
    "professionalReview": "pending",
    "endnote": "把信息看清楚，\n再给自己一个选择。",
    "photoAssetId": "scene-67"
  },
  "share": {
    "title": "一起来轻生活吧！",
    "imageUrl": "/assets/brand/share-about.jpg"
  }
}
