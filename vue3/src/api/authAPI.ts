/**
 * 认证相关接口
 */
import { request } from './request'

export interface LoginParams {
  phone: string
}

export interface LoginResult {
  token: string
  expiresIn: number
  expiresAt: number
  type: string
  userInfo: UserInfo
}

export interface UserInfo {
  id: number
  phone: string
  appId: string | null
  /** 账号状态：1-正常，0-禁用（TINYINT(1)，后端以 0/1 整型下发） */
  status: number
}

export interface AdminLoginParams {
  username: string
  password: string
}

export interface AdminInfo {
  id: number
  username: string
}

export interface AdminLoginResult {
  token: string
  expiresIn: number
  expiresAt: number
  type: string
  adminInfo: AdminInfo
}

export const authAPI = {
  /** 用户手机号一键登录 */
  login(params: LoginParams) {
    return request<LoginResult>({
      method: 'post',
      url: '/auth/login',
      data: params
    })
  },

  /** 管理员账号密码登录 */
  adminLogin(params: AdminLoginParams) {
    return request<AdminLoginResult>({
      method: 'post',
      url: '/admin/login',
      data: params
    })
  },

  /** 获取当前用户信息 */
  getUserInfo() {
    return request<UserInfo>({
      method: 'get',
      url: '/auth/user-info'
    })
  },

  /** 获取当前管理员信息（刷新页面恢复登录态） */
  getAdminInfo() {
    return request<AdminInfo>({
      method: 'get',
      url: '/admin/user-info'
    })
  },

  /** 退出登录 */
  logout() {
    return request<void>({
      method: 'post',
      url: '/auth/logout'
    })
  }
}

export default authAPI
