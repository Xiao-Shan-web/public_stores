/**
 * 门店管理接口（管理端）
 * baseURL 已含 /api/v1
 */
import { request } from './request'

/** 门店 */
export interface Store {
  id: number
  name: string
  address?: string | null
  createdAt?: string
  updatedAt?: string
}

export interface StoreListResult {
  list: Store[]
}

export interface StoreSaveParams {
  name: string
  address?: string | null
}

/** 门店列表（未删除） */
export const getStores = () =>
  request<StoreListResult>({ method: 'get', url: '/admin/stores' })

/** 新建门店 */
export const createStore = (data: StoreSaveParams) =>
  request<Store>({ method: 'post', url: '/admin/stores', data })

/** 编辑门店 */
export const updateStore = (id: number, data: StoreSaveParams) =>
  request<Store>({ method: 'put', url: `/admin/stores/${id}`, data })

/** 删除门店（软删除，被卡类型引用时后端拒绝） */
export const deleteStore = (id: number) =>
  request({ method: 'delete', url: `/admin/stores/${id}` })

export default {
  getStores,
  createStore,
  updateStore,
  deleteStore
}
