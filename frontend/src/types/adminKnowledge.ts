import type {PointChannel} from '@/types/conversation'

export interface AdminPointDictionaryResponse {
  version: string
  stats: {
    familyCount: number
    pointCount: number
    pointCountByChannel: Record<string, number>
  }
  discriminators: AdminPointDiscriminator[]
  channels: AdminPointChannelSummary[]
  families: AdminPointFamilyView[]
}

export interface AdminPointDiscriminator {
  id: string
  rule: string
}

export interface AdminPointChannelSummary {
  channel: PointChannel
  labelZh: string
  familyCount: number
  pointCount: number
}

export interface AdminPointFamilyView {
  familyId: string
  titleZh: string
  channel: PointChannel
  fixability?: number | null
  otherPointId: string
  impactWeight: number
  habitUnit: string
  pointCount: number
  points: AdminPointLeafView[]
}

export interface AdminPointLeafView {
  pointId: string
  familyId: string
  channel: PointChannel
  cardKind: string
  cardPolicy: string
  habitUnit: string
  impactWeight: number
  fixability?: number | null
  errorLevel?: string | null
  problemType?: string | null
  titleZh?: string | null
  catchAllLeaf: boolean
  globalFallback: boolean
  familyOtherLeaf: boolean
}

export type AdminKnowledgeTreeType = 'channel' | 'family' | 'point'

export interface AdminKnowledgeTreeNode {
  id: string
  label: string
  type: AdminKnowledgeTreeType
  channel?: PointChannel
  family?: AdminPointFamilyView
  point?: AdminPointLeafView
  children?: AdminKnowledgeTreeNode[]
}
