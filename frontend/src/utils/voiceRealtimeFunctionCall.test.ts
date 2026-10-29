import assert from 'node:assert/strict'
import {test} from 'node:test'

import {
  buildFunctionCallResultEvent,
  mergeLearningInstructions,
  parseFunctionCallArgumentsDone,
  LEARNING_TOOL_INSTRUCTIONS,
  SPIKE_PING_TOOL,
  SPIKE_PING_TOOL_NAME,
  runSpikePingTool,
} from './voiceRealtimeFunctionCall.ts'

test('parseFunctionCallArgumentsDone reads items with call_id name arguments', () => {
  const calls = parseFunctionCallArgumentsDone({
    type: 'response.function_call_arguments.done',
    items: [
      {call_id: 'call-1', name: 'ping_practice_lab', arguments: '{"message":"hi"}'},
      {call_id: 'call-2', name: 'other', arguments: '{}'},
    ],
  })
  assert.deepEqual(calls, [
    {callId: 'call-1', name: 'ping_practice_lab', argumentsJson: '{"message":"hi"}'},
    {callId: 'call-2', name: 'other', argumentsJson: '{}'},
  ])
})

test('parseFunctionCallArgumentsDone ignores malformed items', () => {
  const calls = parseFunctionCallArgumentsDone({
    type: 'response.function_call_arguments.done',
    items: [
      {name: 'missing_call_id', arguments: '{}'},
      {call_id: 'c1', name: '', arguments: '{}'},
      null,
      'x',
      {call_id: 'c2', name: 'ok'},
    ],
  })
  assert.deepEqual(calls, [{callId: 'c2', name: 'ok', argumentsJson: '{}'}])
})

test('parseFunctionCallArgumentsDone returns empty for wrong type or missing items', () => {
  assert.deepEqual(parseFunctionCallArgumentsDone({type: 'error'}), [])
  assert.deepEqual(parseFunctionCallArgumentsDone({type: 'response.function_call_arguments.done'}), [])
})

test('buildFunctionCallResultEvent matches Doubao conversation.item.create shape', () => {
  assert.deepEqual(buildFunctionCallResultEvent('call-1', '{"ok":true}'), {
    type: 'conversation.item.create',
    items: [
      {
        type: 'message',
        call_id: 'call-1',
        role: 'tool',
        content: [{type: 'input_text', text: '{"ok":true}'}],
      },
    ],
  })
})

test('SPIKE_PING_TOOL is a JSON-schema function declaration', () => {
  assert.equal(SPIKE_PING_TOOL.type, 'function')
  assert.equal(SPIKE_PING_TOOL.name, SPIKE_PING_TOOL_NAME)
  assert.equal(typeof SPIKE_PING_TOOL.description, 'string')
  assert.equal(SPIKE_PING_TOOL.parameters?.type, 'object')
  // Doubao duplex hangs if additionalProperties:false is present on tools
  assert.equal(
    Object.prototype.hasOwnProperty.call(SPIKE_PING_TOOL.parameters ?? {}, 'additionalProperties'),
    false,
  )
})

test('VOICE_REALTIME_TOOLS declares materials + three learning tools, ping demoted', async () => {
  const {
    VOICE_REALTIME_TOOLS,
    GET_PRACTICE_MATERIALS_TOOL_NAME,
    FOCUS_PRACTICE_CARD_TOOL_NAME,
    RECORD_PRACTICE_ATTEMPT_TOOL_NAME,
    APPEND_PRACTICE_NOTE_TOOL_NAME,
  } = await import('./voiceRealtimeFunctionCall.ts')
  const names = VOICE_REALTIME_TOOLS.map((t) => t.name)
  assert.deepEqual(names, [
    GET_PRACTICE_MATERIALS_TOOL_NAME,
    FOCUS_PRACTICE_CARD_TOOL_NAME,
    RECORD_PRACTICE_ATTEMPT_TOOL_NAME,
    APPEND_PRACTICE_NOTE_TOOL_NAME,
  ])
  assert.ok(!names.includes(SPIKE_PING_TOOL_NAME))
})

test('learning tools keep simple schemas (no additionalProperties) and mark required args', async () => {
  const {VOICE_REALTIME_TOOLS} = await import('./voiceRealtimeFunctionCall.ts')
  for (const tool of VOICE_REALTIME_TOOLS) {
    assert.equal(tool.type, 'function')
    assert.equal(
      Object.prototype.hasOwnProperty.call(tool.parameters ?? {}, 'additionalProperties'),
      false,
      tool.name,
    )
  }
  const attempt = VOICE_REALTIME_TOOLS.find((t) => t.name === 'record_practice_attempt')
  assert.deepEqual((attempt?.parameters as {required: string[]}).required, ['cardId', 'outcome'])
  const outcome = (attempt?.parameters as {properties: {outcome: {enum: string[]}}}).properties.outcome
  assert.deepEqual(outcome.enum, ['independent_success', 'prompted', 'uncertain', 'miss'])
  const note = VOICE_REALTIME_TOOLS.find((t) => t.name === 'append_practice_note')
  assert.deepEqual((note?.parameters as {required: string[]}).required, ['text'])
})

test('runSpikePingTool returns ok payload for known tool', () => {
  const result = runSpikePingTool(SPIKE_PING_TOOL_NAME, '{}')
  assert.equal(result.handled, true)
  const parsed = JSON.parse(result.outputText) as {ok: boolean; tool: string; note: string}
  assert.equal(parsed.ok, true)
  assert.equal(parsed.tool, SPIKE_PING_TOOL_NAME)
  assert.match(parsed.note, /succeeded/)
})

test('runSpikePingTool ignores unknown tools', () => {
  assert.deepEqual(runSpikePingTool('unknown', '{}'), {handled: false, outputText: ''})
})

test('mergeLearningInstructions appends the learning protocol and names every tool', () => {
  assert.equal(mergeLearningInstructions(''), LEARNING_TOOL_INSTRUCTIONS)
  assert.match(mergeLearningInstructions('Be brief.'), /Be brief\.\n\nPractice tools/)
  for (const name of [
    'get_practice_materials',
    'focus_practice_card',
    'record_practice_attempt',
    'append_practice_note',
  ]) {
    assert.ok(LEARNING_TOOL_INSTRUCTIONS.includes(name), name)
  }
})
