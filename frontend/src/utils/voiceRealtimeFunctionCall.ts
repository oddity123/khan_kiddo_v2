/**
 * 豆包全双工 Function Calling：工具声明、学习协议指令、下行事件解析。
 * 协议形状见 openspeech Seeduplex / conversation.item.create。
 * 工具由后端代理执行（本场材料范围内），前端只声明 schema 与消费面板事件。
 */

export const SPIKE_PING_TOOL_NAME = 'ping_practice_lab'
export const GET_PRACTICE_MATERIALS_TOOL_NAME = 'get_practice_materials'
export const FOCUS_PRACTICE_CARD_TOOL_NAME = 'focus_practice_card'
export const RECORD_PRACTICE_ATTEMPT_TOOL_NAME = 'record_practice_attempt'
export const APPEND_PRACTICE_NOTE_TOOL_NAME = 'append_practice_note'

/** FC 联调假工具：后端仍保留 handler，但不再声明给模型（降级）。 */
export const SPIKE_PING_TOOL = {
  type: 'function' as const,
  name: SPIKE_PING_TOOL_NAME,
  description:
    'Ping the practice lab. Call this when the learner asks to test tools, says 测试工具, or asks to ping. Returns a simple ok JSON.',
  parameters: {
    type: 'object',
    properties: {},
  },
}

/** 本场勾选材料查询（后端只返回快照范围内的卡/分析）。 */
export const GET_PRACTICE_MATERIALS_TOOL = {
  type: 'function' as const,
  name: GET_PRACTICE_MATERIALS_TOOL_NAME,
  description:
    'Load this session practice materials (selected growth cards and analysis summaries). Call at the start of practice or when you need the next card to drill.',
  parameters: {
    type: 'object',
    properties: {},
  },
}

/** 把一张本场卡设为当前焦点，右侧面板会展示它。 */
export const FOCUS_PRACTICE_CARD_TOOL = {
  type: 'function' as const,
  name: FOCUS_PRACTICE_CARD_TOOL_NAME,
  description:
    'Set the card you are about to drill as the current focus card (shown on the learner panel). Call before asking the learner to produce the target. cardId must come from get_practice_materials.',
  parameters: {
    type: 'object',
    properties: {
      cardId: {type: 'string', description: 'cardId from get_practice_materials'},
      promptHint: {type: 'string', description: 'Optional short note on how you will elicit the target'},
    },
    required: ['cardId'],
  },
}

/** 记录一次回合证据；只有 independent_success 才可能上调进度。 */
export const RECORD_PRACTICE_ATTEMPT_TOOL = {
  type: 'function' as const,
  name: RECORD_PRACTICE_ATTEMPT_TOOL_NAME,
  description:
    'Record the learner attempt on a card after they answer. outcome: independent_success (said a correct natural form with no hint from you), prompted (needed your hint), uncertain (unsure, silent, or only said they understand), miss (wrong or could not say it).',
  parameters: {
    type: 'object',
    properties: {
      cardId: {type: 'string', description: 'cardId from get_practice_materials'},
      outcome: {
        type: 'string',
        enum: ['independent_success', 'prompted', 'uncertain', 'miss'],
        description: 'Your judgement of this attempt',
      },
      userUtterance: {type: 'string', description: 'What the learner actually said (short)'},
      reason: {type: 'string', description: 'One short reason for the outcome'},
    },
    required: ['cardId', 'outcome'],
  },
}

/** 本场备注：一句值得留痕的观察。 */
export const APPEND_PRACTICE_NOTE_TOOL = {
  type: 'function' as const,
  name: APPEND_PRACTICE_NOTE_TOOL_NAME,
  description:
    'Append one short coaching note for this session (an observation worth remembering). Use sparingly. Optionally tie it to a card or analysis from this session.',
  parameters: {
    type: 'object',
    properties: {
      text: {type: 'string', description: 'The note, one or two sentences'},
      cardId: {type: 'string', description: 'Optional cardId from this session'},
      analysisId: {type: 'string', description: 'Optional analysisId from this session'},
    },
    required: ['text'],
  },
}

export const VOICE_REALTIME_TOOLS = [
  GET_PRACTICE_MATERIALS_TOOL,
  FOCUS_PRACTICE_CARD_TOOL,
  RECORD_PRACTICE_ATTEMPT_TOOL,
  APPEND_PRACTICE_NOTE_TOOL,
]

/** 追加到 session instructions：材料驱动的教练协议（教练人设在后端 defaultInstructions）。 */
export const LEARNING_TOOL_INSTRUCTIONS = `
Practice tools (use silently; never read tool JSON aloud):
1) At the start call ${GET_PRACTICE_MATERIALS_TOOL_NAME} once. If there are no cards, just hold a natural conversation and do not call the other tools.
2) Coach only from the loaded cards/analyses; never invent a cardId. Drill one card at a time: call ${FOCUS_PRACTICE_CARD_TOOL_NAME}, then set up a short situation so the learner has to produce the target themselves. Do not reveal the reference answer before they try.
3) After the learner answers, call ${RECORD_PRACTICE_ATTEMPT_TOOL_NAME}. Use independent_success only when they said a correct, natural form with no hint from you. Use prompted if they needed a hint, uncertain if they hesitated, stayed silent, or only said they understand, and miss if it was wrong. Saying "I understand" is never independent_success.
4) Call ${APPEND_PRACTICE_NOTE_TOOL_NAME} sparingly for one short observation worth remembering.
5) Keep spoken turns short and move to the next card once the current one is settled.
`.trim()
export interface FunctionCallRequest {
  callId: string
  name: string
  argumentsJson: string
}

export function parseFunctionCallArgumentsDone(
  event: Record<string, unknown>,
): FunctionCallRequest[] {
  if (String(event.type ?? '') !== 'response.function_call_arguments.done') {
    return []
  }
  const items = event.items
  if (!Array.isArray(items)) {
    return []
  }
  const out: FunctionCallRequest[] = []
  for (const raw of items) {
    if (!raw || typeof raw !== 'object') {
      continue
    }
    const item = raw as Record<string, unknown>
    const callId = typeof item.call_id === 'string' ? item.call_id.trim() : ''
    const name = typeof item.name === 'string' ? item.name.trim() : ''
    if (!callId || !name) {
      continue
    }
    const argumentsJson =
      typeof item.arguments === 'string' && item.arguments.trim().length > 0
        ? item.arguments
        : '{}'
    out.push({callId, name, argumentsJson})
  }
  return out
}

export function buildFunctionCallResultEvent(callId: string, outputText: string) {
  // 与豆包 SDK SendFunctionCallOutputs 对齐：items[].type 必须为 message
  return {
    type: 'conversation.item.create',
    items: [
      {
        type: 'message',
        call_id: callId,
        role: 'tool',
        content: [{type: 'input_text', text: outputText}],
      },
    ],
  }
}

export function runSpikePingTool(
  name: string,
  _argumentsJson: string,
): {handled: boolean; outputText: string} {
  if (name !== SPIKE_PING_TOOL_NAME) {
    return {handled: false, outputText: ''}
  }
  return {
    handled: true,
    outputText: JSON.stringify({
      ok: true,
      tool: SPIKE_PING_TOOL_NAME,
      note: 'spike function call succeeded',
    }),
  }
}

/** 把学习工具协议接到业务 instructions 后面（去空白）。 */
export function mergeLearningInstructions(base: string | undefined | null): string {
  const trimmed = (base ?? '').trim()
  if (!trimmed) {
    return LEARNING_TOOL_INSTRUCTIONS
  }
  return `${trimmed}\n\n${LEARNING_TOOL_INSTRUCTIONS}`
}
