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

export interface RegisterPayload {
    username: string
    password: string
    email?: string
}

export interface RegisterResponse {
    message: string
    user: UserProfile
}
