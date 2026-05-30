export interface UserProfile {
  id: number
  username: string
  email?: string | null
}

export interface LoginResponse {
  token: string
  tokenType: string
  user: UserProfile
}
