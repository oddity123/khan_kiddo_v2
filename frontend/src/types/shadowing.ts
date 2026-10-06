export interface ShadowingStatus {
  scoringEnabled: boolean
  ttsEnabled: boolean
}

export interface ShadowingWord {
  text: string
  hit: boolean
  /** 数字、纯标点等不计分 */
  scored: boolean
}

export interface ShadowingScore {
  score: number
  passed: boolean
  recognizedText: string
  words: ShadowingWord[]
}
