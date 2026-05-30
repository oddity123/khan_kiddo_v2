import axios from 'axios'

const http = axios.create({
  baseURL: import.meta.env.VITE_API_BASE_URL || '',
  timeout: 30_000,
})

http.interceptors.response.use(
  (response) => response,
  (error) => Promise.reject(error),
)

export default http
